package com.example.signlearn

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher screen. First time: set up a profile (name + daily goal).
 * After that it jumps straight to the dashboard.
 */
class WelcomeActivity : BaseActivity() {

    private var goal = 30
    private lateinit var goalViews: List<Pair<TextView, Int>>

    private var waveAnim: android.animation.Animator? = null

    override fun onDestroy() {
        waveAnim?.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ProgressStore.hasProfile) {
            openDashboard(false)
            return
        }

        setContentView(R.layout.activity_welcome)

        val hand = findViewById<HandSignView>(R.id.handWave)
        hand.setSign("HELLO")
        hand.post { waveAnim = Fx.wave(hand) }

        goalViews = listOf(
            findViewById<TextView>(R.id.goalCasual) to 20,
            findViewById<TextView>(R.id.goalRegular) to 30,
            findViewById<TextView>(R.id.goalSerious) to 50
        )
        goalViews.forEach { (view, value) ->
            view.setOnClickListener {
                goal = value
                Sfx.pop()
                Fx.pop(view)
                refreshGoals()
            }
        }
        refreshGoals()

        // little entrance animation
        Fx.slideUp(findViewById(R.id.cardSetup), 150)
        Fx.popIn(findViewById(R.id.tvAppName), 50)

        val etName = findViewById<EditText>(R.id.etName)
        val btn = findViewById<View>(R.id.btnStart)
        btn.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Fx.shake(etName)
                Fx.haptic(etName, false)
                Toast.makeText(this, "Please type your name first 🙂", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ProgressStore.name = name
            ProgressStore.dailyGoal = goal
            Sfx.complete()
            findViewById<ConfettiView>(R.id.confetti).burst()
            Toast.makeText(this, "Welcome, $name! 🎉", Toast.LENGTH_SHORT).show()
            btn.isEnabled = false
            btn.postDelayed({ openDashboard(true) }, 900)
        }
    }

    private fun refreshGoals() {
        goalViews.forEach { (view, value) ->
            view.setBackgroundResource(if (value == goal) R.drawable.opt_selected else R.drawable.opt_default)
        }
    }

    /** EXPLICIT INTENT with an extra → open the dashboard. */
    private fun openDashboard(firstTime: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_FIRST_TIME, firstTime)
        startActivity(intent)
        finish()
    }
}
