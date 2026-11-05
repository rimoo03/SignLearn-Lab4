package com.example.signlearn

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ProgressActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        val best = getSharedPreferences("signlearn", MODE_PRIVATE).getInt("best", 0)
        val last = intent.getIntExtra("LAST_SCORE", -1)
        val total = intent.getIntExtra("TOTAL", 5)

        findViewById<TextView>(R.id.tvLast).text =
            if (last >= 0) "Last quiz: $last / $total" else "Last quiz: take a quiz first!"
        findViewById<TextView>(R.id.tvBest).text = "Best score: $best / 5"

        // IMPLICIT INTENT: share progress with other apps
        findViewById<Button>(R.id.btnShare).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "I'm learning sign language on SignLearn! My best score is $best/5!")
            }
            startActivity(Intent.createChooser(share, "Share progress via"))
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
}
