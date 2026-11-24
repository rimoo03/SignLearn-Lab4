package com.example.signlearn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * "Sign It!" — the camera watches your hand and checks if you're making the right sign.
 * Modes:
 *  FREE   – practice any supported sign
 *  LESSON – a camera checkpoint on the lesson path
 *  SPEED  – sign as many as you can in 60 seconds
 *  SPELL  – fingerspell a word letter by letter
 */
class CameraActivity : BaseActivity() {

    override val fullBleed = true
    override val darkSystemBars = true

    enum class Mode { FREE, LESSON, SPEED, SPELL }

    companion object {
        private const val EX_MODE = "mode"
        private const val EX_KEY = "key"
        private const val EX_LESSON = "lesson"
        private const val EX_WORD = "word"

        private const val HOLD_MS = 700L        // hold the sign this long to count it
        private const val GRACE_MS = 250L       // short wobbles don't reset the hold
        private const val COOLDOWN_MS = 1100L   // pause after a success
        private const val SPEED_SECONDS = 60

        fun forFree(context: Context, key: String?): Intent =
            Intent(context, CameraActivity::class.java).putExtra(EX_MODE, Mode.FREE.name).putExtra(EX_KEY, key)

        fun forLesson(context: Context, lessonId: String): Intent =
            Intent(context, CameraActivity::class.java).putExtra(EX_MODE, Mode.LESSON.name).putExtra(EX_LESSON, lessonId)

        fun forSpeed(context: Context): Intent =
            Intent(context, CameraActivity::class.java).putExtra(EX_MODE, Mode.SPEED.name)

        fun forSpell(context: Context, word: String): Intent =
            Intent(context, CameraActivity::class.java).putExtra(EX_MODE, Mode.SPELL.name).putExtra(EX_WORD, word)
    }

    private lateinit var mode: Mode
    private var lesson: Lesson? = null
    private var word: String? = null
    private var targets: List<String> = emptyList()
    private var targetIndex = 0
    private var target = "A"

    // camera + tracking
    private lateinit var cameraExecutor: ExecutorService
    @Volatile private var tracker: HandTracker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var useFront = true

    // detection state
    private val recent = ArrayDeque<String?>()
    private var holdStart = 0L
    private var lastMatchAt = 0L
    private var cooldownUntil = 0L
    private var lastHandAt = 0L
    private var hintUntil = 0L
    private var score = 0
    private var skipped = 0
    private var finished = false
    private var speedRunning = false
    private var debugOn = false
    private var timer: CountDownTimer? = null

    // views
    private lateinit var previewView: PreviewView
    private lateinit var overlay: HandOverlayView
    private lateinit var confetti: ConfettiView
    private lateinit var holdRing: CircularProgressIndicator
    private lateinit var tvTarget: TextView
    private lateinit var tvTargetName: TextView
    private lateinit var tvSignThis: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBubble: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvDebug: TextView
    private lateinit var handRef: HandSignView
    private lateinit var wordRow: LinearLayout

    private val praise = listOf("Nice! ✓", "Great! 🎉", "Perfect! ⭐", "Yes! 🙌", "Awesome! 💪")

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else showError("SignLearn needs the camera to check your hand signs. You can allow it in your phone's Settings → Apps → SignLearn → Permissions.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SignLearn_Camera)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        confetti = findViewById(R.id.confetti)
        holdRing = findViewById(R.id.holdRing)
        tvTarget = findViewById(R.id.tvTarget)
        tvTargetName = findViewById(R.id.tvTargetName)
        tvSignThis = findViewById(R.id.tvSignThis)
        tvStatus = findViewById(R.id.tvStatus)
        tvBubble = findViewById(R.id.tvBubble)
        tvTimer = findViewById(R.id.tvTimer)
        tvDebug = findViewById(R.id.tvDebug)
        handRef = findViewById(R.id.handRef)
        wordRow = findViewById(R.id.wordRow)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)

        mode = try { Mode.valueOf(intent.getStringExtra(EX_MODE) ?: "FREE") } catch (e: Exception) { Mode.FREE }
        when (mode) {
            Mode.FREE -> {
                target = intent.getStringExtra(EX_KEY)?.takeIf { it in SignClassifier.SUPPORTED } ?: randomTarget()
                tvTitle.text = "📷 Sign It! · Free practice"
            }
            Mode.LESSON -> {
                val l = Course.lesson(intent.getStringExtra(EX_LESSON) ?: "")
                if (l == null) { finish(); return }
                lesson = l
                word = l.word
                targets = l.signKeys
                tvTitle.text = "📷 ${l.title}"
            }
            Mode.SPEED -> {
                target = randomTarget()
                tvTitle.text = "⚡ Speed Round"
                tvTimer.visibility = View.VISIBLE
                tvTimer.text = "⏱ $SPEED_SECONDS"
            }
            Mode.SPELL -> {
                val w = (intent.getStringExtra(EX_WORD) ?: "LAB").uppercase()
                word = w
                targets = w.map { it.toString() }.filter { it in SignClassifier.SUPPORTED }
                if (targets.isEmpty()) targets = listOf("L", "A", "B")
                tvTitle.text = "✍️ Spell: $w"
            }
        }

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnErrorOk).setOnClickListener { finish() }
        findViewById<View>(R.id.btnFlip).setOnClickListener {
            Sfx.pop()
            Fx.pop(it)
            useFront = !useFront
            bindCamera()
        }
        findViewById<View>(R.id.btnHint).setOnClickListener {
            Sfx.pop()
            Fx.pop(handRef)
            hintUntil = SystemClock.uptimeMillis() + 4000
            tvStatus.text = "💡 " + Course.sign(target).how
        }
        findViewById<View>(R.id.btnSkip).setOnClickListener { skip() }
        // Long-press the title to see what the recognizer measures (handy for your demo video)
        tvTitle.setOnLongClickListener {
            debugOn = !debugOn
            tvDebug.visibility = if (debugOn) View.VISIBLE else View.GONE
            Toast.makeText(this, if (debugOn) "Debug info ON" else "Debug info OFF", Toast.LENGTH_SHORT).show()
            true
        }

        // Load the hand-tracking model on a background thread
        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraExecutor.execute {
            val t = HandTracker(applicationContext, { frame -> onFrameFromTracker(frame) }, { msg ->
                runOnUiThread { showError(msg) }
            })
            tracker = if (t.isReady) t else null
        }

        showTarget()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onSystemBarInsets(bars: Insets) {
        val top = findViewById<View>(R.id.topBar)
        top.setPadding(top.paddingLeft, bars.top + dp(12), top.paddingRight, top.paddingBottom)
        val bottom = findViewById<View>(R.id.bottomCard)
        bottom.setPadding(bottom.paddingLeft, bottom.paddingTop, bottom.paddingRight, bars.bottom + dp(18))
        val timerView = findViewById<View>(R.id.tvTimer)
        (timerView.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(76)
        timerView.requestLayout()
    }

    override fun onDestroy() {
        timer?.cancel()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.execute { tracker?.close(); tracker = null }
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    // ================================================================== camera
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindCamera()
                if (mode == Mode.SPEED && !speedRunning) startSpeedCountdown()
            } catch (e: Exception) {
                showError("Couldn't open the camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        var selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            if (!provider.hasCamera(selector)) {
                useFront = !useFront
                selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (e: Exception) { /* keep going with the default */ }

        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        val front = useFront
        analysis.setAnalyzer(cameraExecutor) { proxy ->
            val t = tracker
            if (t == null) proxy.close() else t.detect(proxy, front)
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
            overlay.clear()
        } catch (e: Exception) {
            showError("Couldn't start the camera: ${e.message}")
        }
    }

    private fun onFrameFromTracker(frame: HandFrame) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) onFrame(frame)
        }
    }

    // ================================================================== recognition loop
    private fun onFrame(frame: HandFrame) {
        if (finished) return
        val now = SystemClock.uptimeMillis()
        val world = frame.worldPoints
        if (world == null) {
            overlay.clear()
            pushLabel(null)
            if (now - lastHandAt > 600) {
                resetHold()
                setStatus("Show your hand to the camera 👋")
            }
            return
        }
        lastHandAt = now
        val result = SignClassifier.classify(world)
        pushLabel(result.label)
        val stable = stableLabel()
        val match = stable == target
        overlay.show(frame, match)
        if (debugOn) tvDebug.text = "saw: ${result.label ?: "-"}  stable: ${stable ?: "-"}\n${result.debug}"

        if (now < cooldownUntil) return
        if (mode == Mode.SPEED && !speedRunning) return

        if (match) {
            if (holdStart == 0L) holdStart = now
            lastMatchAt = now
            val p = ((now - holdStart) * 100 / HOLD_MS).toInt().coerceAtMost(100)
            holdRing.setProgressCompat(p, false)
            setStatus("Hold it… ✋")
            if (now - holdStart >= HOLD_MS) onSuccess()
        } else {
            if (holdStart != 0L && now - lastMatchAt > GRACE_MS) resetHold()
            setStatus(
                if (stable == null) "Hmm… adjust your hand 🤔"
                else "That looks like ${nameOf(stable)} — try ${nameOf(target)}"
            )
        }
    }

    private fun pushLabel(label: String?) {
        recent.addLast(label)
        while (recent.size > 5) recent.removeFirst()
    }

    /** The label seen in at least 3 of the last 5 frames (smooths out jitter). */
    private fun stableLabel(): String? =
        recent.filterNotNull().groupingBy { it }.eachCount()
            .filter { it.value >= 3 }.maxByOrNull { it.value }?.key

    private fun resetHold() {
        holdStart = 0L
        holdRing.setProgressCompat(0, true)
    }

    private fun setStatus(text: String) {
        if (SystemClock.uptimeMillis() < hintUntil) return
        if (tvStatus.text != text) tvStatus.text = text
    }

    private fun nameOf(key: String) = if (key == "ILY") "🤟" else key

    // ================================================================== success / skip
    private fun onSuccess() {
        val now = SystemClock.uptimeMillis()
        cooldownUntil = now + COOLDOWN_MS
        holdStart = 0L
        holdRing.setProgressCompat(100, false)
        recent.clear()
        score++

        Sfx.correct()
        Fx.haptic(previewView, true)
        showBubble(praise.random())
        confetti.pop(confetti.width / 2f, confetti.height * 0.45f)

        // rewards
        ProgressStore.addXp(1)
        if (ProgressStore.addCameraSign()) {
            Toast.makeText(this, "+1 ❤️ earned! (${ProgressStore.hearts}/${ProgressStore.MAX_HEARTS})", Toast.LENGTH_SHORT).show()
        }
        ProgressStore.checkBadges().forEach { b ->
            Notifier.showBadge(this, b)
            Toast.makeText(this, "🏅 Badge unlocked: ${b.emoji} ${b.title}", Toast.LENGTH_LONG).show()
        }

        advance()
    }

    private fun skip() {
        if (finished) return
        Sfx.pop()
        skipped++
        resetHold()
        recent.clear()
        advance(skippedThis = true)
    }

    /** Moves to the next target, or finishes the challenge. */
    private fun advance(skippedThis: Boolean = false) {
        when (mode) {
            Mode.FREE, Mode.SPEED -> {
                target = randomTarget()
                tvTarget.postDelayed({ if (!finished) showTarget() }, if (skippedThis) 0 else 700)
            }
            Mode.LESSON, Mode.SPELL -> {
                targetIndex++
                updateWordRow()
                if (targetIndex >= targets.size) {
                    finished = true
                    tvTarget.postDelayed({ completeChallenge() }, 800)
                } else {
                    tvTarget.postDelayed({ if (!finished) showTarget() }, if (skippedThis) 0 else 700)
                }
            }
        }
    }

    private fun randomTarget(): String = SignClassifier.SUPPORTED.filter { it != target }.random()

    private fun showTarget() {
        if (mode == Mode.LESSON || mode == Mode.SPELL) target = targets[targetIndex]
        val sign = Course.sign(target)
        tvTarget.text = nameOf(target)
        tvTargetName.text = if (sign.kind == SignKind.WORD) sign.label else "Letter ${sign.label}"
        handRef.setSign(target)
        holdRing.setProgressCompat(0, false)
        tvSignThis.text = when (mode) {
            Mode.LESSON -> "Sign ${targetIndex + 1} of ${targets.size}:"
            Mode.SPELL -> "Letter ${targetIndex + 1} of ${targets.size}:"
            Mode.SPEED -> "Score: $score  ·  sign this:"
            Mode.FREE -> if (score > 0) "✓ $score correct  ·  next:" else "Sign this:"
        }
        Fx.popIn(tvTarget)
        updateWordRow()
    }

    private fun updateWordRow() {
        if (mode != Mode.LESSON && mode != Mode.SPELL) return
        wordRow.visibility = View.VISIBLE
        wordRow.removeAllViews()
        targets.forEachIndexed { i, key ->
            val tv = TextView(this)
            tv.text = nameOf(key)
            tv.textSize = 22f
            tv.gravity = android.view.Gravity.CENTER
            tv.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            when {
                i < targetIndex -> {
                    tv.setBackgroundResource(R.drawable.bg_letter_done)
                    tv.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
                i == targetIndex -> {
                    tv.setBackgroundResource(R.drawable.bg_letter_now)
                    tv.setTextColor(ContextCompat.getColor(this, R.color.primary))
                }
                else -> {
                    tv.setBackgroundResource(R.drawable.bg_letter_slot)
                    tv.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
                }
            }
            val lp = LinearLayout.LayoutParams(dp(44), dp(48))
            lp.marginStart = dp(3)
            lp.marginEnd = dp(3)
            wordRow.addView(tv, lp)
            if (i == targetIndex - 1) Fx.pop(tv)
        }
    }

    private fun showBubble(text: String) {
        tvBubble.text = text
        tvBubble.visibility = View.VISIBLE
        Fx.popIn(tvBubble)
        tvBubble.removeCallbacks(hideBubble)
        tvBubble.postDelayed(hideBubble, 900)
    }

    private val hideBubble = Runnable {
        tvBubble.animate().alpha(0f).setDuration(200).withEndAction {
            tvBubble.visibility = View.INVISIBLE
            tvBubble.alpha = 1f
        }.start()
    }

    // ================================================================== speed round
    private fun startSpeedCountdown() {
        speedRunning = false
        val steps = listOf("3", "2", "1", "GO! 🚀")
        steps.forEachIndexed { i, s ->
            tvBubble.postDelayed({
                if (finished) return@postDelayed
                Sfx.tick()
                showBubble(s)
                if (i == steps.size - 1) startSpeedTimer()
            }, 400L + i * 800L)
        }
    }

    private fun startSpeedTimer() {
        speedRunning = true
        timer = object : CountDownTimer(SPEED_SECONDS * 1000L, 1000L) {
            override fun onTick(msLeft: Long) {
                val sec = (msLeft / 1000L).toInt() + 1
                tvTimer.text = "⏱ $sec"
                if (sec <= 5) {
                    Sfx.tick()
                    Fx.pop(tvTimer)
                }
            }

            override fun onFinish() {
                tvTimer.text = "⏱ 0"
                endSpeedRound()
            }
        }.start()
    }

    private fun endSpeedRound() {
        if (finished) return
        finished = true
        speedRunning = false
        val xp = score * 2
        if (score > 0) ProgressStore.recordActivity()
        ProgressStore.addXp(xp)
        val newBest = ProgressStore.saveSpeedScore(score)
        ProgressStore.checkBadges().forEach { Notifier.showBadge(this, it) }
        Sfx.complete()
        if (score > 0) confetti.burst()
        if (isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setTitle(if (newBest) "🏆 New best score!" else "⏱ Time's up!")
            .setMessage("You signed $score letter${if (score == 1) "" else "s"} in $SPEED_SECONDS seconds.\n+$xp XP ⭐\n\nBest: ${ProgressStore.speedBest}")
            .setCancelable(false)
            .setPositiveButton("Play again") { _, _ -> recreate() }
            .setNegativeButton("Done") { _, _ -> finish() }
            .show()
    }

    // ================================================================== finishing lesson / spelling
    private fun completeChallenge() {
        if (isFinishing) return
        when (mode) {
            Mode.LESSON -> {
                val l = lesson ?: return finish()
                val perfect = skipped == 0
                val xpGain = 15 + if (perfect) 5 else 0
                val streakGrew = ProgressStore.recordActivity()
                val leveledUp = ProgressStore.addXp(xpGain)
                ProgressStore.completeLesson(l.id, perfect)
                val badges = ProgressStore.checkBadges()
                badges.forEach { Notifier.showBadge(this, it) }
                val acc = (targets.size - skipped).coerceAtLeast(0) * 100 / targets.size
                startActivity(
                    LessonCompleteActivity.newIntent(this, l.title, xpGain, acc, perfect, leveledUp, streakGrew, badges.map { it.id })
                )
                finish()
            }
            Mode.SPELL -> {
                ProgressStore.recordActivity()
                ProgressStore.addXp(5)
                Sfx.complete()
                confetti.burst()
                MaterialAlertDialogBuilder(this)
                    .setTitle("🎉 You spelled $word!")
                    .setMessage("+5 XP ⭐  ·  Skipped: $skipped")
                    .setCancelable(false)
                    .setPositiveButton("Another word") { _, _ ->
                        val next = Course.spellWords.filter { it != word }.random()
                        startActivity(forSpell(this, next))
                        finish()
                    }
                    .setNegativeButton("Done") { _, _ -> finish() }
                    .show()
            }
            else -> finish()
        }
    }

    private fun showError(message: String) {
        if (isFinishing) return
        findViewById<View>(R.id.errorBox).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvError).text = message
        findViewById<View>(R.id.bottomCard).visibility = View.GONE
    }
}
