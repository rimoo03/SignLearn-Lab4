package com.example.signlearn

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Every screen extends this. It turns on edge-to-edge drawing (required on new Android
 * versions) and pads the content so nothing hides under the status bar / navigation bar.
 */
open class BaseActivity : AppCompatActivity() {

    /** Screens that want to draw behind the bars (the camera) set this to true. */
    protected open val fullBleed: Boolean = false

    /** Dark screens (camera) want white status-bar icons. */
    protected open val darkSystemBars: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val style = if (darkSystemBars) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        super.onCreate(savedInstanceState)
        ProgressStore.init(this)
        Sfx.init(this)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            if (!fullBleed) v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            onSystemBarInsets(bars)
            insets
        }
    }

    /** Called with the size of the system bars; the camera screen uses it to place its controls. */
    protected open fun onSystemBarInsets(bars: Insets) {}
}
