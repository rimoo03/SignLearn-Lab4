package com.example.signlearn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** Draws the tracked hand "skeleton" on top of the camera preview. */
class HandOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<P3>? = null
    private var imgW = 1
    private var imgH = 1
    private var matched = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f * resources.displayMetrics.density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun show(frame: HandFrame, isMatch: Boolean) {
        points = frame.screenPoints
        imgW = max(1, frame.imageWidth)
        imgH = max(1, frame.imageHeight)
        matched = isMatch
        invalidate()
    }

    fun clear() {
        points = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = points ?: return
        if (p.size < 21) return
        // PreviewView uses "fill center": scale the image to cover the view, then center it.
        val scale = max(width.toFloat() / imgW, height.toFloat() / imgH)
        val dx = (width - imgW * scale) / 2f
        val dy = (height - imgH * scale) / 2f
        fun sx(i: Int) = p[i].x * imgW * scale + dx
        fun sy(i: Int) = p[i].y * imgH * scale + dy

        linePaint.color = if (matched) 0xFF2BC48A.toInt() else 0xEEFFFFFF.toInt()
        for (c in CONNECTIONS) {
            canvas.drawLine(sx(c.first), sy(c.first), sx(c.second), sy(c.second), linePaint)
        }
        val r = 5f * resources.displayMetrics.density
        for (i in 0 until 21) {
            dotPaint.color = when {
                matched -> 0xFF2BC48A.toInt()
                i == 4 || i == 8 || i == 12 || i == 16 || i == 20 -> 0xFFFFC800.toInt()
                else -> 0xFF7C4DFF.toInt()
            }
            canvas.drawCircle(sx(i), sy(i), r, dotPaint)
        }
    }

    companion object {
        private val CONNECTIONS = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,          // thumb
            0 to 5, 5 to 6, 6 to 7, 7 to 8,          // index
            5 to 9, 9 to 10, 10 to 11, 11 to 12,     // middle
            9 to 13, 13 to 14, 14 to 15, 15 to 16,   // ring
            13 to 17, 17 to 18, 18 to 19, 19 to 20,  // pinky
            0 to 17
        )
    }
}
