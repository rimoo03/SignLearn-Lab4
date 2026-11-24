package com.example.signlearn

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.random.Random

/**
 * A Duolingo-style lesson: learn new signs, then answer quick questions.
 * Wrong answers cost a heart and come back again at the end.
 */
class LessonActivity : BaseActivity() {

    companion object {
        const val EXTRA_LESSON_ID = "lesson_id"
    }

    /** The kinds of exercise screens. */
    private sealed class Ex {
        class Intro(val sign: Sign) : Ex()
        class PickMeaning(val sign: Sign, val options: List<Sign>) : Ex()
        class PickSign(val sign: Sign, val options: List<Sign>) : Ex()
        class Match(val signs: List<Sign>) : Ex()
    }

    private lateinit var lesson: Lesson
    private val queue = ArrayList<Ex>()
    private val retried = HashSet<Ex>()
    private var pos = 0

    private var answered = 0
    private var correctCount = 0
    private var mistakes = 0
    private var combo = 0

    private var selected = -1
    private var optionViews: List<View> = emptyList()
    private var checkAction: (() -> Unit)? = null

    private lateinit var progress: LinearProgressIndicator
    private lateinit var tvHearts: TextView
    private lateinit var tvCombo: TextView
    private lateinit var container: FrameLayout
    private lateinit var btnCheck: TextView
    private lateinit var feedbackPanel: LinearLayout
    private lateinit var tvFeedbackTitle: TextView
    private lateinit var tvFeedbackBody: TextView
    private lateinit var btnContinue: TextView

    private val praise = listOf("Nice! ✨", "Great job! 🎉", "Awesome! 💪", "You got it! 👏", "Perfect! ⭐", "Amazing! 🙌")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = Course.lesson(intent.getStringExtra(EXTRA_LESSON_ID) ?: "")
        if (found == null) {
            finish()
            return
        }
        lesson = found
        setContentView(R.layout.activity_lesson)

        progress = findViewById(R.id.progress)
        tvHearts = findViewById(R.id.tvHearts)
        tvCombo = findViewById(R.id.tvCombo)
        container = findViewById(R.id.exerciseContainer)
        btnCheck = findViewById(R.id.btnCheck)
        feedbackPanel = findViewById(R.id.feedbackPanel)
        tvFeedbackTitle = findViewById(R.id.tvFeedbackTitle)
        tvFeedbackBody = findViewById(R.id.tvFeedbackBody)
        btnContinue = findViewById(R.id.btnContinue)

        findViewById<View>(R.id.btnClose).setOnClickListener { confirmQuit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmQuit()
        })
        btnCheck.setOnClickListener { checkAction?.invoke() }
        btnContinue.setOnClickListener {
            if (ProgressStore.hearts <= 0) outOfHearts() else next()
        }

        buildExercises()
        updateHearts(false)
        showExercise()
    }

    // ------------------------------------------------------------------ building the lesson
    private fun buildExercises() {
        val signs = lesson.signKeys.map { Course.sign(it) }
        val pool = if (signs.first().kind == SignKind.LETTER) Course.letters else Course.words

        fun options(s: Sign): List<Sign> {
            val others = (signs - s).shuffled() + (pool - signs.toSet()).shuffled()
            return (listOf(s) + others.take(3)).shuffled()
        }
        fun quiz(s: Sign): Ex =
            if (Random.nextBoolean()) Ex.PickMeaning(s, options(s)) else Ex.PickSign(s, options(s))

        // Teach each sign, reviewing the previous one in between
        signs.forEachIndexed { i, s ->
            queue.add(Ex.Intro(s))
            if (i >= 1) queue.add(quiz(signs[i - 1]))
        }
        queue.add(quiz(signs.last()))
        // Practice round
        queue.add(Ex.Match(signs.shuffled().take(4)))
        signs.shuffled().take(3).forEach { queue.add(quiz(it)) }
    }

    private fun labelOf(s: Sign) = if (s.kind == SignKind.WORD) "${s.emoji} ${s.label}" else s.label

    // ------------------------------------------------------------------ flow
    private fun showExercise() {
        if (pos >= queue.size) {
            finishLesson()
            return
        }
        container.removeAllViews()
        selected = -1
        optionViews = emptyList()
        feedbackPanel.visibility = View.GONE
        btnCheck.visibility = View.VISIBLE
        progress.setProgressCompat(pos * 1000 / queue.size, true)

        when (val ex = queue[pos]) {
            is Ex.Intro -> showIntro(ex)
            is Ex.PickMeaning -> showPickMeaning(ex)
            is Ex.PickSign -> showPickSign(ex)
            is Ex.Match -> showMatch(ex)
        }
        container.getChildAt(0)?.let { Fx.slideUp(it, 0, 80f) }
    }

    private fun next() {
        pos++
        showExercise()
    }

    // ------------------------------------------------------------------ 1) new sign card
    private fun showIntro(ex: Ex.Intro) {
        val v = layoutInflater.inflate(R.layout.ex_intro, container, false)
        container.addView(v)
        val s = ex.sign
        val hand = v.findViewById<HandSignView>(R.id.handView)
        hand.setSign(s.key)
        v.findViewById<TextView>(R.id.tvLabel).text = if (s.kind == SignKind.WORD) labelOf(s) else "Letter ${s.label}"
        v.findViewById<TextView>(R.id.tvHow).text = s.how
        v.findViewById<TextView>(R.id.tvTip).text = s.tip
        hand.postDelayed({ Fx.pop(hand) }, 300)

        // Tap to flip the card (front = drawing, back = memory tip)
        val card = v.findViewById<View>(R.id.cardSign)
        val front = v.findViewById<View>(R.id.frontSide)
        val back = v.findViewById<View>(R.id.backSide)
        card.cameraDistance = 8000 * resources.displayMetrics.density
        card.setOnClickListener {
            Sfx.pop()
            card.animate().rotationY(90f).setDuration(140).withEndAction {
                val showBack = front.visibility == View.VISIBLE
                front.visibility = if (showBack) View.GONE else View.VISIBLE
                back.visibility = if (showBack) View.VISIBLE else View.GONE
                card.rotationY = -90f
                card.animate().rotationY(0f).setDuration(140).start()
            }.start()
        }

        val tryCam = v.findViewById<View>(R.id.btnTryCamera)
        if (s.cameraReady) {
            tryCam.visibility = View.VISIBLE
            tryCam.setOnClickListener { startActivity(CameraActivity.forFree(this, s.key)) }
        }

        ProgressStore.markLearned(s.key)
        btnCheck.text = "Continue"
        btnCheck.isEnabled = true
        checkAction = { next() }
    }

    // ------------------------------------------------------------------ 2) what does this sign mean?
    private fun showPickMeaning(ex: Ex.PickMeaning) {
        val v = layoutInflater.inflate(R.layout.ex_pick_meaning, container, false)
        container.addView(v)
        v.findViewById<HandSignView>(R.id.handView).setSign(ex.sign.key)
        v.findViewById<TextView>(R.id.tvPrompt).text =
            if (ex.sign.kind == SignKind.LETTER) "Which letter is this?" else "What does this sign mean?"
        val ids = listOf(R.id.opt1, R.id.opt2, R.id.opt3, R.id.opt4)
        optionViews = ids.mapIndexed { i, id ->
            v.findViewById<TextView>(id).also { tv ->
                tv.text = labelOf(ex.options[i])
                tv.setOnClickListener { select(i) }
            }
        }
        btnCheck.text = "Check"
        btnCheck.isEnabled = false
        checkAction = { grade(ex, ex.options, ex.sign) }
    }

    // ------------------------------------------------------------------ 3) which picture is X?
    private fun showPickSign(ex: Ex.PickSign) {
        val v = layoutInflater.inflate(R.layout.ex_pick_sign, container, false)
        container.addView(v)
        v.findViewById<TextView>(R.id.tvPrompt).text = "Which one is “${labelOf(ex.sign)}”?"
        val frames = listOf(R.id.opt1, R.id.opt2, R.id.opt3, R.id.opt4)
        val hands = listOf(R.id.hand1, R.id.hand2, R.id.hand3, R.id.hand4)
        optionViews = frames.mapIndexed { i, id ->
            v.findViewById<HandSignView>(hands[i]).setSign(ex.options[i].key)
            v.findViewById<View>(id).also { f -> f.setOnClickListener { select(i) } }
        }
        btnCheck.text = "Check"
        btnCheck.isEnabled = false
        checkAction = { grade(ex, ex.options, ex.sign) }
    }

    private fun select(i: Int) {
        Sfx.pop()
        selected = i
        optionViews.forEachIndexed { k, view ->
            view.setBackgroundResource(if (k == i) R.drawable.opt_selected else R.drawable.opt_default)
        }
        Fx.pop(optionViews[i])
        btnCheck.isEnabled = true
    }

    private fun grade(ex: Ex, options: List<Sign>, answer: Sign) {
        if (selected < 0) return
        answered++
        optionViews.forEach { it.isEnabled = false }
        val chosen = optionViews[selected]
        if (options[selected] == answer) {
            correctCount++
            combo++
            chosen.setBackgroundResource(R.drawable.opt_correct)
            Sfx.correct()
            Fx.haptic(chosen, true)
            Fx.pop(chosen)
            showFeedback(true, praise.random(), "")
            if (combo >= 3) showCombo()
        } else {
            mistakes++
            combo = 0
            chosen.setBackgroundResource(R.drawable.opt_wrong)
            optionViews[options.indexOf(answer)].setBackgroundResource(R.drawable.opt_correct)
            Sfx.wrong()
            Fx.haptic(chosen, false)
            Fx.shake(chosen)
            ProgressStore.loseHeart()
            updateHearts(true)
            requeue(ex)
            showFeedback(false, "Not quite 😅", "Correct answer: ${labelOf(answer)}\n${answer.how}")
        }
    }

    // ------------------------------------------------------------------ 4) match the pairs
    private fun showMatch(ex: Ex.Match) {
        val v = layoutInflater.inflate(R.layout.ex_match, container, false)
        container.addView(v)
        val left = v.findViewById<LinearLayout>(R.id.colLeft)
        val right = v.findViewById<LinearLayout>(R.id.colRight)
        val signs = ex.signs
        val rightOrder = signs.shuffled()
        val leftViews = ArrayList<View>()
        val rightViews = ArrayList<TextView>()
        val matched = HashSet<Sign>()
        var selL = -1
        var selR = -1
        var hadMistake = false

        btnCheck.visibility = View.INVISIBLE

        fun paint() {
            leftViews.forEachIndexed { i, view ->
                if (signs[i] !in matched) view.setBackgroundResource(if (i == selL) R.drawable.opt_selected else R.drawable.opt_default)
            }
            rightViews.forEachIndexed { i, view ->
                if (rightOrder[i] !in matched) view.setBackgroundResource(if (i == selR) R.drawable.opt_selected else R.drawable.opt_default)
            }
        }

        fun tryMatch() {
            if (selL < 0 || selR < 0) return
            val lv = leftViews[selL]
            val rv = rightViews[selR]
            if (signs[selL] == rightOrder[selR]) {
                matched.add(signs[selL])
                lv.setBackgroundResource(R.drawable.opt_correct)
                rv.setBackgroundResource(R.drawable.opt_correct)
                lv.isEnabled = false
                rv.isEnabled = false
                lv.animate().alpha(0.6f).setDuration(300).start()
                rv.animate().alpha(0.6f).setDuration(300).start()
                Sfx.correct()
                Fx.pop(lv); Fx.pop(rv)
                Fx.haptic(rv, true)
                selL = -1; selR = -1
                if (matched.size == signs.size) {
                    answered++
                    if (!hadMistake) { correctCount++; combo++ } else combo = 0
                    container.postDelayed({
                        showFeedback(true, "Perfect pairs! 🎉", if (hadMistake) "You matched them all." else "No mistakes!")
                    }, 350)
                }
            } else {
                hadMistake = true
                mistakes++
                lv.setBackgroundResource(R.drawable.opt_wrong)
                rv.setBackgroundResource(R.drawable.opt_wrong)
                Fx.shake(lv); Fx.shake(rv)
                Fx.haptic(rv, false)
                Sfx.wrong()
                ProgressStore.loseHeart()
                updateHearts(true)
                selL = -1; selR = -1
                container.postDelayed({
                    if (ProgressStore.hearts <= 0) outOfHearts() else paint()
                }, 550)
            }
        }

        signs.forEachIndexed { i, s ->
            val cell = FrameLayout(this)
            cell.setBackgroundResource(R.drawable.opt_default)
            cell.setPadding(dp(6), dp(6), dp(6), dp(10))
            val hand = HandSignView(this)
            hand.setSign(s.key)
            cell.addView(hand, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            cell.setOnClickListener {
                if (s in matched) return@setOnClickListener
                Sfx.pop()
                selL = i
                paint()
                Fx.pop(cell)
                tryMatch()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96))
            lp.bottomMargin = dp(10)
            left.addView(cell, lp)
            leftViews.add(cell)
        }
        rightOrder.forEachIndexed { i, s ->
            val tv = TextView(this)
            tv.text = labelOf(s)
            tv.textSize = if (s.kind == SignKind.LETTER) 28f else 17f
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(dp(6), 0, dp(6), dp(4))
            tv.setTextColor(ContextCompat.getColor(this, R.color.text))
            tv.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            tv.setBackgroundResource(R.drawable.opt_default)
            tv.setOnClickListener {
                if (s in matched) return@setOnClickListener
                Sfx.pop()
                selR = i
                paint()
                Fx.pop(tv)
                tryMatch()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96))
            lp.bottomMargin = dp(10)
            right.addView(tv, lp)
            rightViews.add(tv)
        }
        checkAction = null
    }

    // ------------------------------------------------------------------ feedback + hearts
    private fun showFeedback(good: Boolean, title: String, body: String) {
        feedbackPanel.setBackgroundResource(if (good) R.drawable.bg_feedback_good else R.drawable.bg_feedback_bad)
        tvFeedbackTitle.text = title
        tvFeedbackTitle.setTextColor(ContextCompat.getColor(this, if (good) R.color.green_dark else R.color.red_dark))
        tvFeedbackBody.text = body
        tvFeedbackBody.visibility = if (body.isEmpty()) View.GONE else View.VISIBLE
        btnContinue.setBackgroundResource(if (good) R.drawable.btn_green else R.drawable.btn_red)
        btnCheck.visibility = View.INVISIBLE
        feedbackPanel.visibility = View.VISIBLE
        Fx.slideUp(feedbackPanel, 0, 300f)
        progress.setProgressCompat((pos + 1) * 1000 / queue.size, true)
    }

    private fun showCombo() {
        tvCombo.text = "🔥 $combo in a row!"
        tvCombo.visibility = View.VISIBLE
        Fx.popIn(tvCombo)
        tvCombo.removeCallbacks(hideCombo)
        tvCombo.postDelayed(hideCombo, 1600)
    }

    private val hideCombo = Runnable { tvCombo.visibility = View.INVISIBLE }

    private fun updateHearts(lost: Boolean) {
        tvHearts.text = "❤️ ${ProgressStore.hearts}"
        if (lost) {
            Fx.shake(tvHearts)
            Fx.pop(tvHearts)
        }
    }

    private fun requeue(ex: Ex) {
        if (retried.add(ex)) queue.add(ex)
    }

    private fun outOfHearts() {
        if (isFinishing) return
        MaterialAlertDialogBuilder(this)
            .setTitle("💔 Out of hearts!")
            .setMessage(
                "Take a short break — you get a free heart every 20 minutes.\n\n" +
                    "Or earn hearts now: every ${ProgressStore.SIGNS_PER_HEART} correct camera signs = +1 ❤️"
            )
            .setCancelable(false)
            .setPositiveButton("Earn hearts 📷") { _, _ ->
                startActivity(CameraActivity.forFree(this, null))
                finish()
            }
            .setNegativeButton("Quit") { _, _ -> finish() }
            .show()
    }

    private fun confirmQuit() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Quit this lesson?")
            .setMessage("You'll lose your progress in this lesson.")
            .setPositiveButton("Keep learning", null)
            .setNegativeButton("Quit") { _, _ -> finish() }
            .show()
    }

    // ------------------------------------------------------------------ done!
    private fun finishLesson() {
        val perfect = mistakes == 0
        val xpGain = 10 + (if (perfect) 5 else 0)
        val streakGrew = ProgressStore.recordActivity()
        val leveledUp = ProgressStore.addXp(xpGain)
        ProgressStore.completeLesson(lesson.id, perfect)
        val badges = ProgressStore.checkBadges()
        badges.forEach { Notifier.showBadge(this, it) }
        val accuracy = if (answered == 0) 100 else correctCount * 100 / answered

        startActivity(
            LessonCompleteActivity.newIntent(
                this, lesson.title, xpGain, accuracy, perfect, leveledUp, streakGrew, badges.map { it.id }
            )
        )
        finish()
    }
}
