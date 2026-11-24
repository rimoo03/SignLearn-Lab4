package com.example.signlearn

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import kotlin.math.roundToInt

/** Small animation + haptic helpers used everywhere to make the app feel alive. */
object Fx {

    /** Quick "squish and bounce" when something is tapped. */
    fun pop(v: View) {
        ObjectAnimator.ofPropertyValuesHolder(
            v,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f, 1.08f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f, 1.08f, 1f)
        ).setDuration(320).start()
    }

    /** Side-to-side shake for wrong answers / locked things. */
    fun shake(v: View) {
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -22f, 22f, -14f, 14f, -6f, 6f, 0f)
            .setDuration(420).start()
    }

    /** Endless gentle pulse (used on the current lesson node). Cancel it when the view is removed. */
    fun pulse(v: View, amount: Float = 1.07f): Animator {
        val a = ObjectAnimator.ofPropertyValuesHolder(
            v,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, amount),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, amount)
        )
        a.duration = 700
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.interpolator = AccelerateDecelerateInterpolator()
        a.start()
        return a
    }

    /** Endless up-and-down float (the "START" bubble). */
    fun float(v: View, dy: Float = -10f): Animator {
        val a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, dy)
        a.duration = 650
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.interpolator = AccelerateDecelerateInterpolator()
        a.start()
        return a
    }

    /** Endless side-to-side wave (the hand on the welcome screen). */
    fun wave(v: View): Animator {
        v.pivotX = v.width / 2f
        v.pivotY = v.height.toFloat()
        val a = ObjectAnimator.ofFloat(v, View.ROTATION, -10f, 10f)
        a.duration = 450
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.interpolator = AccelerateDecelerateInterpolator()
        a.start()
        return a
    }

    /** Grow in from nothing with a little overshoot. */
    fun popIn(v: View, delay: Long = 0) {
        v.scaleX = 0.3f
        v.scaleY = 0.3f
        v.alpha = 0f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setStartDelay(delay).setDuration(420)
            .setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    /** Slide up from below and fade in. */
    fun slideUp(v: View, delay: Long = 0, distance: Float = 120f) {
        v.translationY = distance
        v.alpha = 0f
        v.animate().translationY(0f).alpha(1f)
            .setStartDelay(delay).setDuration(380)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Count a number up in a TextView, e.g. "+0 XP" → "+15 XP". */
    fun countUp(tv: TextView, to: Int, prefix: String = "", suffix: String = "", duration: Long = 900) {
        val a = ValueAnimator.ofInt(0, to)
        a.duration = duration
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { tv.text = "$prefix${it.animatedValue}$suffix" }
        a.start()
    }

    /** Phone vibration feedback (no permission needed). */
    fun haptic(v: View, success: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 30) {
            if (success) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            if (success) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS
        }
        v.performHapticFeedback(type)
    }
}

/** dp → px helper. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density
