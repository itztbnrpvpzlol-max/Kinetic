package com.szkinetic

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    lateinit var st: Settings
    lateinit var cur: Cur

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        hideBars()
        st = Settings(this)
        cur = Cur(st)
        setContent { App(st, cur) }
    }

    private fun hideBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (f) hideBars()
    }

    private fun vb(k: Int): Int {
        if (!st.cursor || !st.vol) return -1
        val m = when (k) {
            KeyEvent.KEYCODE_VOLUME_UP -> st.up
            KeyEvent.KEYCODE_VOLUME_DOWN -> st.down
            else -> return -1
        }
        return if (m < 2) m else -1
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        val b = vb(k)
        if (b >= 0) {
            if (e.repeatCount == 0) cur.press(b)
            return true
        }
        return super.onKeyDown(k, e)
    }

    override fun onKeyUp(k: Int, e: KeyEvent): Boolean {
        val b = vb(k)
        if (b >= 0) {
            cur.release(b)
            return true
        }
        return super.onKeyUp(k, e)
    }
}
