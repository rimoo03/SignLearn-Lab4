package com.example.signlearn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat

/** The celebration screen after finishing a lesson or camera challenge. */
class LessonCompleteActivity : BaseActivity() {

    companion object {
        private const val EX_TITLE = "title"
        private const val EX_XP = "xp"
        private const val EX_ACC = "accuracy"
        private const val EX_PERFECT = "perfect"
        private const val EX_LEVEL_UP = "level_up"
        private const val EX_STREAK_UP = "streak_up"
        private const val EX_BADGES = "badges"

        /** Builds the EXPLICIT INTENT (with extras) that opens this screen. */
        fun newIntent(
            context: Context, title: String, xp: Int, accuracy: Int, perfect: Boolean,
            leveledUp: Boolean, streakGrew: Boolean, badgeIds: List<String>
        ): Intent = Intent(context, LessonCompleteActivity::class.java)
            .putExtra(EX_TITLE, title)
            .putExtra(EX_XP, xp)
            .putExtra(EX_ACC, accuracy)
            .putExtra(EX_PERFECT, perfect)
            .putExtra(EX_LEVEL_UP, leveledUp)
            .putExtra(EX_STREAK_UP, streakGrew)
            .putStringArrayListExtra(EX_BADGES, ArrayList(badgeIds))
    }

    private var waveAnim: android.animation.Animator? = null

    override fun onDestroy() {
        waveAnim?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson_complete)

        val title = intent.getStringExtra(EX_TITLE) ?: "Lesson"
        val xp = intent.getIntExtra(EX_XP, 10)
        val acc = intent.getIntExtra(EX_ACC, 100)
        val perfect = intent.getBooleanExtra(EX_PERFECT, false)
        val leveledUp = intent.getBooleanExtra(EX_LEVEL_UP, false)
        val streakGrew = intent.getBooleanExtra(EX_STREAK_UP, false)
        val badges = intent.getStringArrayListExtra(EX_BADGES) ?: arrayListOf()

        val trophy = findViewById<TextView>(R.id.tvTrophy)
        trophy.text = if (perfect) "🏆" else "🎉"
        findViewById<TextView>(R.id.tvTitle).text = if (perfect) "Perfect lesson!" else "Lesson complete!"
        findViewById<TextView>(R.id.tvSubtitle).text =
            "You finished “$title”" + if (perfect) " with no mistakes!" else ". Keep it up!"

        // Celebrate!
        Sfx.complete()
        findViewById<ConfettiView>(R.id.confetti).burst()
        Fx.popIn(trophy, 100)
        trophy.postDelayed({ if (!isFinishing) waveAnim = Fx.wave(trophy) }, 600)
        Fx.popIn(findViewById(R.id.tvTitle), 250)

        // Stat cards pop in one after another with counting numbers
        Fx.popIn(findViewById(R.id.cardXp), 500)
        Fx.popIn(findViewById(R.id.cardAcc), 700)
        Fx.popIn(findViewById(R.id.cardStreak), 900)
        val tvXp = findViewById<TextView>(R.id.tvXpValue)
        val tvAcc = findViewById<TextView>(R.id.tvAccValue)
        val tvStreak = findViewById<TextView>(R.id.tvStreakValue)
        tvXp.postDelayed({ Fx.countUp(tvXp, xp, "⭐ +") }, 550)
        tvAcc.postDelayed({ Fx.countUp(tvAcc, acc, "🎯 ", "%") }, 750)
        tvStreak.text = "🔥 ${ProgressStore.streak}"
        if (streakGrew) {
            tvStreak.postDelayed({
                Fx.pop(tvStreak)
                Toast.makeText(this, "🔥 Streak extended to ${ProgressStore.streak} days!", Toast.LENGTH_SHORT).show()
            }, 1200)
        }

        if (leveledUp) {
            val lv = findViewById<TextView>(R.id.tvLevelUp)
            lv.text = "⬆️ Level up! You're now level ${ProgressStore.level}"
            lv.visibility = View.VISIBLE
            Fx.popIn(lv, 1200)
            lv.postDelayed({ Sfx.correct() }, 1200)
        }

        if (badges.isNotEmpty()) {
            val head = findViewById<TextView>(R.id.tvBadgesTitle)
            head.visibility = View.VISIBLE
            val list = findViewById<LinearLayout>(R.id.badgeList)
            badges.mapNotNull { ProgressStore.badge(it) }.forEachIndexed { i, b ->
                val row = TextView(this)
                row.text = "${b.emoji}  ${b.title}\n${b.desc}"
                row.textSize = 16f
                row.setTextColor(ContextCompat.getColor(this, R.color.text))
                row.gravity = Gravity.CENTER_VERTICAL
                row.setBackgroundResource(R.drawable.opt_selected)
                row.setPadding(dp(16), dp(12), dp(16), dp(16))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(8)
                list.addView(row, lp)
                Fx.popIn(row, 1400L + i * 200L)
            }
        }

        val btn = findViewById<View>(R.id.btnContinue)
        Fx.slideUp(btn, 800)
        btn.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }
}
