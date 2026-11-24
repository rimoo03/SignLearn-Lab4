package com.example.signlearn

import android.animation.Animator
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Calendar
import kotlin.math.min

/**
 * The dashboard. Three tabs:
 *  - Learn: daily goal, level and the Duolingo-style lesson path
 *  - Practice: camera games + dictionary
 *  - Profile: stats, badges and settings
 */
class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_FIRST_TIME = "first_time"
    }

    private val runningAnims = mutableListOf<Animator>()
    private var currentTab = 0
    private var scrolledToCurrent = false
    private var currentNode: View? = null

    private lateinit var tabs: List<View>
    private lateinit var navs: List<View>
    private lateinit var navLabels: List<TextView>

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) Notifier.scheduleDaily(this)
            else Toast.makeText(this, "Notifications are off — you won't get streak reminders.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Notifications: channels + permission + daily reminder alarm
        Notifier.createChannels(this)
        if (!Notifier.hasPermission(this) && android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        Notifier.scheduleDaily(this)

        tabs = listOf(findViewById(R.id.scrollLearn), findViewById(R.id.scrollPractice), findViewById(R.id.scrollProfile))
        navs = listOf(findViewById(R.id.navLearn), findViewById(R.id.navPractice), findViewById(R.id.navProfile))
        navLabels = listOf(findViewById(R.id.navLearnLabel), findViewById(R.id.navPracticeLabel), findViewById(R.id.navProfileLabel))
        navs.forEachIndexed { i, v -> v.setOnClickListener { selectTab(i, true) } }

        setupTopBar()
        setupPractice()
        setupProfile()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != 0) selectTab(0, true) else finish()
            }
        })

        selectTab(savedInstanceState?.getInt("tab") ?: 0, false)

        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_FIRST_TIME, false)) {
            findViewById<ConfettiView>(R.id.confetti).burst()
            Toast.makeText(this, "Tap START to learn your first sign! 👇", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        stopAnims()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
    }

    private fun refreshAll() {
        refreshTopBar()
        refreshLearn()
        refreshPractice()
        refreshProfile()
    }

    private fun stopAnims() {
        runningAnims.forEach { it.cancel() }
        runningAnims.clear()
    }

    // ================================================================== tabs
    private fun selectTab(index: Int, animate: Boolean) {
        currentTab = index
        tabs.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        navs.forEachIndexed { i, v -> v.setBackgroundResource(if (i == index) R.drawable.bg_tab_selected else 0) }
        navLabels.forEachIndexed { i, tv ->
            tv.setTextColor(ContextCompat.getColor(this, if (i == index) R.color.primary else R.color.text_muted))
        }
        if (animate) {
            Sfx.pop()
            Fx.pop(navs[index])
            Fx.slideUp(tabs[index], 0, 40f)
        }
    }

    // ================================================================== top bar
    private fun setupTopBar() {
        findViewById<View>(R.id.tvStreak).setOnClickListener {
            Fx.pop(it)
            val s = ProgressStore.streak
            val msg = when {
                s == 0 -> "🔥 Finish a lesson today to start a streak!"
                ProgressStore.practicedToday -> "🔥 $s-day streak! You've practiced today."
                else -> "🔥 $s-day streak — practice today to keep it!"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.tvXp).setOnClickListener {
            Fx.pop(it)
            Toast.makeText(this, "⭐ ${ProgressStore.xp} XP · Level ${ProgressStore.level}", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.tvHearts).setOnClickListener {
            Fx.pop(it)
            val h = ProgressStore.hearts
            val msg = if (h >= ProgressStore.MAX_HEARTS) "❤️ Hearts are full!"
            else "❤️ $h/${ProgressStore.MAX_HEARTS} · next free heart in ${ProgressStore.minutesToNextHeart} min"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.tvCourse).setOnClickListener {
            Fx.pop(it)
            Toast.makeText(this, "🤟 You're learning American Sign Language (ASL)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshTopBar() {
        val streakView = findViewById<TextView>(R.id.tvStreak)
        streakView.text = "🔥 ${ProgressStore.streak}"
        streakView.alpha = if (ProgressStore.practicedToday) 1f else 0.55f
        findViewById<TextView>(R.id.tvXp).text = "⭐ ${ProgressStore.xp}"
        findViewById<TextView>(R.id.tvHearts).text = "❤️ ${ProgressStore.hearts}"
    }

    // ================================================================== learn tab
    private fun refreshLearn() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        findViewById<TextView>(R.id.tvGreeting).text = "$hello, ${ProgressStore.name}! 👋"
        findViewById<TextView>(R.id.tvLevel).text = "LV ${ProgressStore.level}"

        val today = ProgressStore.xpToday
        val goal = ProgressStore.dailyGoal
        findViewById<TextView>(R.id.tvGoalLabel).text =
            if (today >= goal) "🎯 Daily goal reached! $today / $goal XP ✅" else "🎯 Daily goal: $today / $goal XP"
        findViewById<LinearProgressIndicator>(R.id.progressGoal)
            .setProgressCompat(min(100, today * 100 / goal.coerceAtLeast(1)), true)

        val lvl = ProgressStore.level
        val start = ProgressStore.xpForLevel(lvl)
        val end = ProgressStore.xpForLevel(lvl + 1)
        findViewById<TextView>(R.id.tvLevelLabel).text =
            "⭐ Level $lvl · ${ProgressStore.xp - start} / ${end - start} XP to level ${lvl + 1}"
        findViewById<LinearProgressIndicator>(R.id.progressLevel)
            .setProgressCompat((ProgressStore.levelProgress * 100).toInt(), true)

        buildPath()
    }

    private fun buildPath() {
        stopAnims()
        val container = findViewById<LinearLayout>(R.id.pathContainer)
        container.removeAllViews()
        currentNode = null
        val current = ProgressStore.currentLesson
        val offsets = intArrayOf(0, 55, 85, 55, 0, -55, -85, -55)
        var i = 0

        for (unit in Course.units) {
            container.addView(makeUnitHeader(unit))
            for (lesson in unit.lessons) {
                val done = ProgressStore.isCompleted(lesson.id)
                val unlocked = ProgressStore.isUnlocked(lesson.id)
                val isCurrent = lesson.id == current?.id

                val row = FrameLayout(this)
                row.clipChildren = false
                row.setPadding(0, dp(8), 0, dp(8))

                val col = LinearLayout(this)
                col.orientation = LinearLayout.VERTICAL
                col.gravity = Gravity.CENTER_HORIZONTAL
                col.clipChildren = false
                col.translationX = dp(offsets[i % offsets.size]).toFloat()
                row.addView(col, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))

                if (isCurrent) {
                    val bubble = TextView(this)
                    bubble.text = if (lesson.type == LessonType.CAMERA) "CAMERA!" else "START"
                    bubble.setTextColor(ContextCompat.getColor(this, unit.color))
                    bubble.textSize = 14f
                    bubble.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
                    bubble.setBackgroundResource(R.drawable.bg_bubble)
                    bubble.setPadding(dp(14), dp(6), dp(14), dp(6))
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dp(6)
                    col.addView(bubble, lp)
                    runningAnims.add(Fx.float(bubble))
                }

                val node = TextView(this)
                node.gravity = Gravity.CENTER
                node.setPadding(0, 0, 0, dp(6))
                node.textSize = 30f
                when {
                    done -> {
                        node.setBackgroundResource(R.drawable.node_done)
                        node.text = "✔"
                        node.setTextColor(ContextCompat.getColor(this, R.color.white))
                    }
                    unlocked -> {
                        node.setBackgroundResource(unit.nodeBg)
                        node.text = lesson.icon
                    }
                    else -> {
                        node.setBackgroundResource(R.drawable.node_locked)
                        node.text = "🔒"
                        node.alpha = 0.85f
                    }
                }
                col.addView(node, LinearLayout.LayoutParams(dp(78), dp(82)))

                val label = TextView(this)
                label.text = lesson.title
                label.textSize = 13f
                label.setTypeface(label.typeface, android.graphics.Typeface.BOLD)
                label.setTextColor(ContextCompat.getColor(this, if (unlocked) R.color.text else R.color.text_muted))
                label.gravity = Gravity.CENTER
                val llp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                llp.topMargin = dp(4)
                col.addView(label, llp)

                node.setOnClickListener {
                    if (!unlocked) {
                        Fx.shake(node)
                        Fx.haptic(node, false)
                        Toast.makeText(this, "🔒 Finish the lesson before this one first!", Toast.LENGTH_SHORT).show()
                    } else {
                        Sfx.pop()
                        Fx.pop(node)
                        node.postDelayed({ openLesson(lesson) }, 180)
                    }
                }

                if (isCurrent) {
                    currentNode = node
                    runningAnims.add(Fx.pulse(node))
                }
                container.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                i++
            }
        }

        if (!scrolledToCurrent) {
            scrolledToCurrent = true
            val scroll = findViewById<ScrollView>(R.id.scrollLearn)
            scroll.post {
                val node = currentNode ?: return@post
                val r = Rect()
                node.getDrawingRect(r)
                scroll.offsetDescendantRectToMyCoords(node, r)
                scroll.smoothScrollTo(0, (r.top - scroll.height / 3).coerceAtLeast(0))
            }
        }
    }

    private fun makeUnitHeader(unit: CourseUnit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(16), dp(14), dp(16), dp(14))
        box.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@MainActivity, unit.color))
            cornerRadius = dpf(18f)
        }

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = unit.title
        title.textSize = 18f
        title.setTextColor(ContextCompat.getColor(this, R.color.white))
        title.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        val sub = TextView(this)
        sub.text = unit.subtitle
        sub.textSize = 14f
        sub.setTextColor(0xE6FFFFFF.toInt())
        texts.addView(title)
        texts.addView(sub)
        box.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val doneCount = unit.lessons.count { ProgressStore.isCompleted(it.id) }
        val count = TextView(this)
        count.text = if (doneCount == unit.lessons.size) "🏅" else "$doneCount/${unit.lessons.size}"
        count.textSize = 18f
        count.setTextColor(ContextCompat.getColor(this, R.color.white))
        count.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        box.addView(count)

        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(20)
        lp.bottomMargin = dp(6)
        box.layoutParams = lp
        return box
    }

    private fun openLesson(lesson: Lesson) {
        if (lesson.type == LessonType.LEARN) {
            if (ProgressStore.hearts <= 0) {
                showNoHearts()
                return
            }
            // EXPLICIT INTENT with an extra: which lesson to open
            val intent = Intent(this, LessonActivity::class.java)
            intent.putExtra(LessonActivity.EXTRA_LESSON_ID, lesson.id)
            startActivity(intent)
        } else {
            startActivity(CameraActivity.forLesson(this, lesson.id))
        }
    }

    private fun showNoHearts() {
        Fx.shake(findViewById(R.id.tvHearts))
        MaterialAlertDialogBuilder(this)
            .setTitle("💔 Out of hearts")
            .setMessage(
                "You get a free heart every 20 minutes (next one in about ${ProgressStore.minutesToNextHeart} min).\n\n" +
                    "Or earn one right now: every ${ProgressStore.SIGNS_PER_HEART} correct camera signs = +1 ❤️"
            )
            .setPositiveButton("Earn hearts 📷") { _, _ -> startActivity(CameraActivity.forFree(this, null)) }
            .setNegativeButton("Later", null)
            .show()
    }

    // ================================================================== practice tab
    private fun setupPractice() {
        val grid = findViewById<LinearLayout>(R.id.letterGrid)
        val keys = SignClassifier.SUPPORTED.sortedBy { if (it.length == 1) it else "Z$it" }
        var row: LinearLayout? = null
        keys.forEachIndexed { i, key ->
            if (i % 4 == 0) {
                row = LinearLayout(this).also {
                    it.orientation = LinearLayout.HORIZONTAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(8)
                    grid.addView(it, lp)
                }
            }
            val chip = TextView(this)
            chip.text = if (key == "ILY") "🤟" else key
            chip.textSize = 22f
            chip.gravity = Gravity.CENTER
            chip.setPadding(0, 0, 0, dp(4))
            chip.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            chip.setTextColor(ContextCompat.getColor(this, R.color.primary))
            chip.setBackgroundResource(R.drawable.opt_default)
            chip.setOnClickListener {
                Sfx.pop()
                Fx.pop(chip)
                chip.postDelayed({ startActivity(CameraActivity.forFree(this, key)) }, 150)
            }
            val lp = LinearLayout.LayoutParams(0, dp(58), 1f)
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            row?.addView(chip, lp)
        }
        // pad the last row so chips keep the same width
        val last = row
        if (last != null) {
            while (last.childCount < 4) {
                val lp = LinearLayout.LayoutParams(0, dp(58), 1f)
                lp.marginStart = dp(4)
                lp.marginEnd = dp(4)
                last.addView(View(this), lp)
            }
        }

        findViewById<View>(R.id.btnSpeed).setOnClickListener { startActivity(CameraActivity.forSpeed(this)) }
        findViewById<View>(R.id.btnSpell).setOnClickListener {
            startActivity(CameraActivity.forSpell(this, Course.spellWords.random()))
        }
        findViewById<View>(R.id.btnDictionary).setOnClickListener {
            startActivity(Intent(this, DictionaryActivity::class.java))
        }
    }

    private fun refreshPractice() {
        findViewById<TextView>(R.id.tvSpeedBest).text =
            "Sign as many letters as you can in 60 seconds. Best: ${ProgressStore.speedBest} ⚡"
        val h = ProgressStore.hearts
        findViewById<TextView>(R.id.tvHeartInfo).text =
            "❤️ $h/${ProgressStore.MAX_HEARTS} hearts · every ${ProgressStore.SIGNS_PER_HEART} correct camera signs earns one!"
    }

    // ================================================================== profile tab
    private fun setupProfile() {
        val swReminder = findViewById<MaterialSwitch>(R.id.switchReminder)
        swReminder.isChecked = ProgressStore.reminderOn
        swReminder.setOnCheckedChangeListener { _, checked ->
            ProgressStore.reminderOn = checked
            Notifier.scheduleDaily(this)
            Toast.makeText(this, if (checked) "🔔 Daily reminder on" else "🔕 Daily reminder off", Toast.LENGTH_SHORT).show()
            refreshProfile()
        }

        findViewById<View>(R.id.tvReminderTime).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                ProgressStore.reminderHour = h
                ProgressStore.reminderMinute = m
                Notifier.scheduleDaily(this)
                Toast.makeText(this, "⏰ Reminder set for ${formatTime(h, m)}", Toast.LENGTH_SHORT).show()
                refreshProfile()
            }, ProgressStore.reminderHour, ProgressStore.reminderMinute, false).show()
        }

        val swSound = findViewById<MaterialSwitch>(R.id.switchSound)
        swSound.isChecked = ProgressStore.soundOn
        swSound.setOnCheckedChangeListener { _, checked ->
            ProgressStore.soundOn = checked
            if (checked) Sfx.correct()
        }

        // NOTIFICATION demo button
        findViewById<View>(R.id.btnTestReminder).setOnClickListener {
            if (!Notifier.hasPermission(this)) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                Toast.makeText(this, "Allow notifications first, then tap again.", Toast.LENGTH_SHORT).show()
            } else {
                Notifier.showReminder(this)
                Toast.makeText(this, "🔔 Reminder sent — check your notifications!", Toast.LENGTH_SHORT).show()
            }
        }

        // IMPLICIT INTENT: share progress with any app (Messenger, Gmail, ...)
        findViewById<View>(R.id.btnShare).setOnClickListener {
            val text = "I'm learning sign language on SignLearn! 🤟 " +
                "Level ${ProgressStore.level}, ${ProgressStore.xp} XP and a ${ProgressStore.streak}-day streak 🔥"
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(share, "Share your progress"))
        }

        // IMPLICIT INTENT: open a website in the browser
        findViewById<View>(R.id.btnLearnMore).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.lifeprint.com")))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No browser found on this phone.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnEditName).setOnClickListener {
            val input = EditText(this)
            input.setText(ProgressStore.name)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            input.setSelection(input.text.length)
            val box = FrameLayout(this)
            box.setPadding(dp(20), dp(8), dp(20), 0)
            box.addView(input)
            MaterialAlertDialogBuilder(this)
                .setTitle("✏️ Your name")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val n = input.text.toString().trim()
                    if (n.isNotEmpty()) {
                        ProgressStore.name = n
                        Toast.makeText(this, "Saved! Hi, $n 👋", Toast.LENGTH_SHORT).show()
                        refreshAll()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View>(R.id.btnReset).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("♻️ Reset progress?")
                .setMessage("This clears your XP, streak, lessons and badges. Your name and settings stay.")
                .setPositiveButton("Reset") { _, _ ->
                    ProgressStore.resetProgress()
                    scrolledToCurrent = false
                    refreshAll()
                    Toast.makeText(this, "Progress reset. Fresh start! 🌱", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun formatTime(h: Int, m: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format("%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
    }

    private fun refreshProfile() {
        val name = ProgressStore.name
        findViewById<TextView>(R.id.tvAvatar).text = name.take(1).uppercase()
        findViewById<TextView>(R.id.tvProfileName).text = name
        findViewById<TextView>(R.id.tvProfileLevel).text =
            "Level ${ProgressStore.level} · ${ProgressStore.xp} XP · goal ${ProgressStore.dailyGoal} XP/day"
        findViewById<TextView>(R.id.tvStatStreak).text = "🔥 ${ProgressStore.streak}\nDay streak (best ${ProgressStore.bestStreak})"
        findViewById<TextView>(R.id.tvStatXp).text = "⭐ ${ProgressStore.xp}\nTotal XP"
        findViewById<TextView>(R.id.tvStatLessons).text = "📚 ${ProgressStore.lessonsDone}\nLessons done"
        findViewById<TextView>(R.id.tvStatCamera).text = "📷 ${ProgressStore.cameraSigns}\nCamera signs"
        findViewById<TextView>(R.id.tvReminderTime).text =
            "⏰  Remind me at ${formatTime(ProgressStore.reminderHour, ProgressStore.reminderMinute)}  (tap to change)"
        findViewById<View>(R.id.tvReminderTime).alpha = if (ProgressStore.reminderOn) 1f else 0.4f

        val unlocked = ProgressStore.unlockedBadges
        findViewById<TextView>(R.id.tvBadgesTitle).text = "Badges  (${unlocked.size}/${ProgressStore.allBadges.size})"
        val grid = findViewById<LinearLayout>(R.id.badgeGrid)
        grid.removeAllViews()
        var row: LinearLayout? = null
        ProgressStore.allBadges.forEachIndexed { i, b ->
            if (i % 3 == 0) {
                row = LinearLayout(this).also {
                    it.orientation = LinearLayout.HORIZONTAL
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(8)
                    grid.addView(it, lp)
                }
            }
            val have = b.id in unlocked
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(dp(6), dp(10), dp(6), dp(10))
            cell.setBackgroundResource(if (have) R.drawable.opt_selected else R.drawable.bg_card)
            val emoji = TextView(this)
            emoji.text = if (have) b.emoji else "🔒"
            emoji.textSize = 28f
            emoji.gravity = Gravity.CENTER
            val title = TextView(this)
            title.text = b.title
            title.textSize = 12f
            title.gravity = Gravity.CENTER
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            title.setTextColor(ContextCompat.getColor(this, if (have) R.color.primary else R.color.text_muted))
            cell.addView(emoji)
            cell.addView(title)
            cell.setOnClickListener {
                Fx.pop(cell)
                Toast.makeText(this, "${b.emoji} ${b.title}: ${b.desc}" + if (have) " ✅" else " (locked)", Toast.LENGTH_SHORT).show()
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            row?.addView(cell, lp)
        }
    }
}
