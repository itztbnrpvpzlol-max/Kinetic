package com.szkinetic

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    lateinit var st: Settings
    lateinit var cur: Cur

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        st = Settings(this)
        cur = Cur(st)
        setContent { App(st, cur) }
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
