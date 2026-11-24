package com.example.signlearn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Full-screen overlay that shoots confetti. Call burst() to celebrate. Does not block touches. */
class ConfettiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Piece(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, val vr: Float, val w: Float, val h: Float, val color: Int, val circle: Boolean
    )

    private val pieces = ArrayList<Piece>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTime = 0L
    private val colors = intArrayOf(
        0xFF7C4DFF.toInt(), 0xFF2BC48A.toInt(), 0xFFFFC800.toInt(),
        0xFFFF4B4B.toInt(), 0xFF1CB0F6.toInt(), 0xFFFF6FB1.toInt(), 0xFFFF9600.toInt()
    )

    init {
        isClickable = false
        isFocusable = false
    }

    /** Big celebration burst from the top of the screen. */
    fun burst(count: Int = 140) {
        if (width == 0) { post { burst(count) }; return }
        val d = resources.displayMetrics.density
        repeat(count) {
            val angle = Math.toRadians(Random.nextDouble(200.0, 340.0))
            val speed = Random.nextDouble(500.0, 1300.0).toFloat() * d / 2.6f
            pieces.add(
                Piece(
                    x = width * Random.nextFloat(),
                    y = -20f * d + Random.nextFloat() * height * 0.15f,
                    vx = (cos(angle) * speed * 0.6).toFloat(),
                    vy = (-sin(angle) * speed * 0.3).toFloat(),
                    rot = Random.nextFloat() * 360f,
                    vr = Random.nextFloat() * 720f - 360f,
                    w = (6 + Random.nextInt(6)) * d,
                    h = (10 + Random.nextInt(8)) * d,
                    color = colors[Random.nextInt(colors.size)],
                    circle = Random.nextInt(4) == 0
                )
            )
        }
        start()
    }

    /** Small pop of confetti around a point (x, y in this view's pixels). */
    fun pop(x: Float, y: Float, count: Int = 40) {
        val d = resources.displayMetrics.density
        repeat(count) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextDouble(150.0, 520.0).toFloat() * d / 2.6f
            pieces.add(
                Piece(
                    x, y,
                    (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat() - 200f * d / 2.6f,
                    Random.nextFloat() * 360f, Random.nextFloat() * 720f - 360f,
                    (5 + Random.nextInt(5)) * d, (8 + Random.nextInt(6)) * d,
                    colors[Random.nextInt(colors.size)], Random.nextInt(3) == 0
                )
            )
        }
        start()
    }

    private fun start() {
        lastTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pieces.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastTime).coerceIn(0L, 50L)) / 1000f
        lastTime = now
        val gravity = 900f * resources.displayMetrics.density / 2.6f
        val it = pieces.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += gravity * dt
            p.vx *= 0.99f
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vr * dt
            if (p.y > height + 40) { it.remove(); continue }
            paint.color = p.color
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rot)
            if (p.circle) canvas.drawCircle(0f, 0f, p.w / 2f, paint)
            else canvas.drawRect(-p.w / 2f, -p.h / 2f, p.w / 2f, p.h / 2f, paint)
            canvas.restore()
        }
        if (pieces.isNotEmpty()) postInvalidateOnAnimation()
    }
}
