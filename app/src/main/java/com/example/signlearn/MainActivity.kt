package com.example.signlearn

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Notification setup
        NotificationHelper.createChannel(this)
        NotificationHelper.requestPermissionIfNeeded(this)

        // TOAST
        Toast.makeText(this, "Welcome to SignLearn!", Toast.LENGTH_SHORT).show()

        // EXPLICIT INTENTS (open other screens, with an extra)
        findViewById<Button>(R.id.btnLessons).setOnClickListener {
            val intent = Intent(this, LessonActivity::class.java)
            intent.putExtra("USER_NAME", "Learner")
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnQuiz).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
        }
        findViewById<Button>(R.id.btnProgress).setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }
    }
}
