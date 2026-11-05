package com.example.signlearn

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class QuizActivity : AppCompatActivity() {

    private data class Question(val text: String, val options: List<String>, val answer: Int)

    private val questions = listOf(
        Question(
            "How do you sign \"Thank you\" in ASL?",
            listOf("Flat hand from chin moving outward", "Fist tapping the chest", "Waving the hand"), 0
        ),
        Question(
            "What does ASL stand for?",
            listOf("Advanced Sign Language", "American Sign Language", "Alphabet Sign Letters"), 1
        ),
        Question(
            "What is the handshape for the letter \"A\"?",
            listOf("Open palm", "Two fingers up", "Closed fist with thumb at the side"), 2
        ),
        Question(
            "The sign for \"Hello\" looks most like...",
            listOf("A salute", "Clapping", "Pointing at the floor"), 0
        ),
        Question(
            "Sign language uses...",
            listOf("Only the hands", "Hands, facial expressions and body", "Only the face"), 1
        )
    )

    private var index = 0
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        val buttons = listOf(
            findViewById<Button>(R.id.btnOption1),
            findViewById<Button>(R.id.btnOption2),
            findViewById<Button>(R.id.btnOption3)
        )
        buttons.forEachIndexed { i, b -> b.setOnClickListener { onAnswer(i) } }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        showQuestion()
    }

    private fun showQuestion() {
        val q = questions[index]
        findViewById<TextView>(R.id.tvCount).text = "Question ${index + 1} of ${questions.size}"
        findViewById<TextView>(R.id.tvQuestion).text = q.text
        findViewById<Button>(R.id.btnOption1).text = q.options[0]
        findViewById<Button>(R.id.btnOption2).text = q.options[1]
        findViewById<Button>(R.id.btnOption3).text = q.options[2]
    }

    private fun onAnswer(choice: Int) {
        if (choice == questions[index].answer) {
            score++
            Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show()   // TOAST
        } else {
            Toast.makeText(this, "Wrong answer", Toast.LENGTH_SHORT).show() // TOAST
        }
        index++
        if (index < questions.size) showQuestion() else onQuizFinished()
    }

    private fun onQuizFinished() {
        val total = questions.size

        // Save best score
        val prefs = getSharedPreferences("signlearn", MODE_PRIVATE)
        if (score > prefs.getInt("best", 0)) prefs.edit().putInt("best", score).apply()

        // TOAST
        Toast.makeText(this, "You scored $score/$total!", Toast.LENGTH_LONG).show()

        // NOTIFICATION
        NotificationHelper.showQuizNotification(
            this, "Quiz Complete!", "You got $score out of $total. Tap to try again."
        )

        // EXPLICIT INTENT with extra: go to the progress screen
        val intent = Intent(this, ProgressActivity::class.java)
        intent.putExtra("LAST_SCORE", score)
        intent.putExtra("TOTAL", total)
        startActivity(intent)
        finish()
    }
}
