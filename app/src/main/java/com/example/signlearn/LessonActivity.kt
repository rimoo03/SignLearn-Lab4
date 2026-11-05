package com.example.signlearn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LessonActivity : AppCompatActivity() {

    private val signs = listOf(
        "Hello" to "Hold a flat hand near your forehead and move it outward like a salute.",
        "Thank you" to "Touch your fingertips to your chin, then move your hand forward and down.",
        "Please" to "Rub a flat hand in a circle on your chest.",
        "Yes" to "Make a fist and nod it up and down, like a head nodding.",
        "No" to "Tap your index and middle fingers against your thumb, like a mouth closing."
    )
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lesson)

        // Receive the extra sent by MainActivity
        val name = intent.getStringExtra("USER_NAME") ?: "Learner"
        Toast.makeText(this, "Hi $name, let's learn some signs!", Toast.LENGTH_SHORT).show()

        showSign()

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            index = (index + 1) % signs.size
            showSign()
        }

        // IMPLICIT INTENT: open a website in the browser
        findViewById<Button>(R.id.btnLearnMore).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.lifeprint.com")))
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun showSign() {
        findViewById<TextView>(R.id.tvSignName).text = signs[index].first
        findViewById<TextView>(R.id.tvSignDesc).text = signs[index].second
    }
}
