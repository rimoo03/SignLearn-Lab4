package com.example.signlearn

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Browse and search every sign in the course. Tap one to see it big. */
class DictionaryActivity : BaseActivity() {

    private lateinit var grid: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)
        grid = findViewById(R.id.gridContainer)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = build(s?.toString() ?: "")
        })
        build("")
    }

    private fun build(query: String) {
        grid.removeAllViews()
        val q = query.trim().lowercase()
        val match = { s: Sign -> q.isEmpty() || s.label.lowercase().contains(q) || s.key.lowercase() == q }
        section("Alphabet", Course.letters.filter(match))
        section("Words & phrases", Course.words.filter(match))
        if (grid.childCount == 0) {
            val empty = TextView(this)
            empty.text = "No signs found for “$query” 🤷"
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(40), 0, 0)
            grid.addView(empty)
        }
    }

    private fun section(title: String, signs: List<Sign>) {
        if (signs.isEmpty()) return
        val head = TextView(this)
        head.text = title
        head.textSize = 18f
        head.setTextColor(ContextCompat.getColor(this, R.color.text))
        head.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        head.setPadding(dp(4), dp(12), 0, dp(4))
        grid.addView(head)

        val learned = ProgressStore.learnedSigns
        var row: LinearLayout? = null
        signs.forEachIndexed { i, s ->
            if (i % 3 == 0) {
                row = LinearLayout(this).also {
                    it.orientation = LinearLayout.HORIZONTAL
                    grid.addView(it)
                }
            }
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setBackgroundResource(if (s.key in learned) R.drawable.opt_selected else R.drawable.opt_default)
            cell.setPadding(dp(6), dp(8), dp(6), dp(12))
            val hand = HandSignView(this)
            hand.setSign(s.key)
            cell.addView(hand, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(84)))
            val label = TextView(this)
            label.text = if (s.kind == SignKind.WORD) "${s.emoji} ${s.label}" else s.label
            label.gravity = Gravity.CENTER
            label.textSize = if (s.kind == SignKind.WORD) 13f else 18f
            label.setTextColor(ContextCompat.getColor(this, R.color.text))
            label.typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            label.maxLines = 1
            cell.addView(label)
            if (s.cameraReady) {
                val cam = TextView(this)
                cam.text = "📷"
                cam.textSize = 11f
                cam.gravity = Gravity.CENTER
                cell.addView(cam)
            }
            cell.setOnClickListener {
                Sfx.pop()
                Fx.pop(cell)
                showSign(s)
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            row?.addView(cell, lp)
        }
        // keep columns aligned in the last row
        val last = row
        if (last != null) {
            while (last.childCount < 3) {
                val lp = LinearLayout.LayoutParams(0, 1, 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                last.addView(FrameLayout(this), lp)
            }
        }
    }

    private fun showSign(s: Sign) {
        val v = layoutInflater.inflate(R.layout.dialog_sign, null)
        val hand = v.findViewById<HandSignView>(R.id.handBig)
        hand.setSign(s.key)
        v.findViewById<TextView>(R.id.tvName).text = if (s.kind == SignKind.WORD) "${s.emoji} ${s.label}" else "Letter ${s.label}"
        v.findViewById<TextView>(R.id.tvHow).text = s.how
        v.findViewById<TextView>(R.id.tvTip).text = "💡 ${s.tip}"
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(v)
            .setPositiveButton("Got it", null)
            .create()
        val cam = v.findViewById<View>(R.id.btnCamera)
        if (s.cameraReady) {
            cam.visibility = View.VISIBLE
            cam.setOnClickListener {
                dialog.dismiss()
                startActivity(CameraActivity.forFree(this, s.key))
            }
        }
        dialog.show()
        hand.postDelayed({ Fx.pop(hand) }, 200)
    }
}
