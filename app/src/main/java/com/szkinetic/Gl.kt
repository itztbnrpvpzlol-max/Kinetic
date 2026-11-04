package com.szkinetic

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.sqrt

const val VS = "attribute vec2 p;uniform vec2 e,t;uniform vec4 m;uniform float a;varying vec2 uv;void main(){uv=p*0.5+0.5;vec2 z=p*e;vec2 w=vec2(m.x*z.x+m.z*z.y,m.y*z.x+m.w*z.y)+t;w.x/=a;gl_Position=vec4(w,0.0,1.0);}"
const val FS = "precision mediump float;varying vec2 uv;uniform vec4 col;uniform float tm,u;uniform sampler2D tx;void main(){if(u>1.5){vec4 c=texture2D(tx,uv);gl_FragColor=vec4(c.rgb,col.a);return;}if(u>0.5){vec4 t=texture2D(tx,vec2(uv.x,1.0-uv.y));gl_FragColor=vec4(t.rgb*col.a,t.a*col.a);return;}float g=0.5+0.5*sin(uv.x*6.0+tm*3.0);gl_FragColor=vec4(col.rgb*(0.7+0.3*g),col.a);}"

var FrameMs by mutableStateOf(0f)

class Frame(val d: FloatArray, val b: Array<Bitmap?>)

fun sh(t: Int, s: String): Int {
    val i = GLES20.glCreateShader(t)
    if (i == 0) return 0
    GLES20.glShaderSource(i, s)
    GLES20.glCompileShader(i)
    val st = IntArray(1)
    GLES20.glGetShaderiv(i, GLES20.GL_COMPILE_STATUS, st, 0)
    if (st[0] == 0) {
        GLES20.glDeleteShader(i)
        return 0
    }
    return i
}

fun prog(vs: String = VS, fs: String = FS): Int {
    val v = sh(GLES20.GL_VERTEX_SHADER, vs)
    val f = sh(GLES20.GL_FRAGMENT_SHADER, fs)
    if (v == 0 || f == 0) return 0
    val p = GLES20.glCreateProgram()
    if (p == 0) return 0
    GLES20.glAttachShader(p, v)
    GLES20.glAttachShader(p, f)
    GLES20.glLinkProgram(p)
    GLES20.glDeleteShader(v)
    GLES20.glDeleteShader(f)
    val st = IntArray(1)
    GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
    if (st[0] == 0) {
        GLES20.glDeleteProgram(p)
        return 0
    }
    return p
}

fun up(b: Bitmap): Int {
    val i = IntArray(1)
    GLES20.glGenTextures(1, i, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, i[0])
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
    return i[0]
}

class Rnd : GLSurfaceView.Renderer {
    @Volatile var f = Frame(FloatArray(0), arrayOfNulls<Bitmap>(0))
    @Volatile var t = 0f
    @Volatile var ar = 16f / 9f
    @Volatile var zm = 1f
    @Volatile var pnx = 0f
    @Volatile var pny = 0f
    @Volatile var live: Comp? = null
    private var fc = 0
    private var pg = 0
    private var W = 0
    private var H = 0
    private var tx = LinkedHashMap<Bitmap, Int>(16, 0.75f, true)
    private val ds = IntArray(1)
    private var lp = 0
    private var uE = 0
    private var uT = 0
    private var uM = 0
    private var uA = 0
    private var uC = 0
    private var uTm = 0
    private var uU = 0
    private var uX = 0
    private var cm = -1
    private val vb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    override fun onSurfaceCreated(g: GL10?, c: EGLConfig?) {
        pg = prog()
        tx = LinkedHashMap(16, 0.75f, true)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DITHER)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        if (pg != 0) {
            lp = GLES20.glGetAttribLocation(pg, "p")
            uE = GLES20.glGetUniformLocation(pg, "e")
            uT = GLES20.glGetUniformLocation(pg, "t")
            uM = GLES20.glGetUniformLocation(pg, "m")
            uA = GLES20.glGetUniformLocation(pg, "a")
            uC = GLES20.glGetUniformLocation(pg, "col")
            uTm = GLES20.glGetUniformLocation(pg, "tm")
            uU = GLES20.glGetUniformLocation(pg, "u")
            uX = GLES20.glGetUniformLocation(pg, "tx")
            GLES20.glUseProgram(pg)
            GLES20.glUniform1i(uX, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
    }

    override fun onSurfaceChanged(g: GL10?, w: Int, h: Int) {
        W = w
        H = h
    }

    private fun bindVb(loc: Int) {
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 8, vb)
    }

    private fun item(d: FloatArray, o: Int, bm: Bitmap?) {
        GLES20.glUniform4f(uM, d[o], d[o + 1], d[o + 2], d[o + 3])
        GLES20.glUniform2f(uT, d[o + 4], d[o + 5])
        GLES20.glUniform4f(uC, d[o + 6], d[o + 7], d[o + 8], d[o + 9])
        GLES20.glUniform2f(uE, d[o + 10], d[o + 11])
        if (bm != null) {
            if (bm.isRecycled) return
            var id = tx[bm]
            if (id == null) {
                id = up(bm)
                tx[bm] = id
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            if (cm != 1) {
                GLES20.glUniform1f(uU, 1f)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                cm = 1
            }
        } else if (cm != 0) {
            GLES20.glUniform1f(uU, 0f)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            cm = 0
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun items(fr: Frame) {
        val d = fr.d
        val n = d.size / STR
        GLES20.glUseProgram(pg)
        bindVb(lp)
        GLES20.glUniform1f(uA, ar)
        GLES20.glUniform1f(uTm, t)
        cm = -1
        for (i in 0 until n) item(d, i * STR, fr.b[i])
    }

    override fun onDrawFrame(g: GL10?) {
        val lc = live
        if (lc != null) {
            // playing: build the frame here, off the UI thread, so Compose never has to recompose per frame
            try {
                val t0 = System.nanoTime()
                f = lc.frame()
                ar = lc.w.toFloat() / lc.h
                t = lc.time
                if (++fc % 20 == 0) FrameMs = (System.nanoTime() - t0) / 1e6f
            } catch (e: Exception) {
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glViewport(0, 0, W, H)
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (W == 0 || H == 0 || pg == 0) return
        val a = ar
        var bw = W
        var bh = (W / a).toInt()
        if (bh > H) {
            bh = H
            bw = (H * a).toInt()
        }
        bw = maxOf(bw, 1)
        bh = maxOf(bh, 1)
        val z = zm.coerceIn(0.1f, 16f)
        val vw = maxOf((bw * z).toInt(), 1)
        val vh = maxOf((bh * z).toInt(), 1)
        val x = (W - vw) / 2 + (pnx * W).toInt()
        val y = (H - vh) / 2 - (pny * H).toInt()
        val sx = maxOf(x, 0)
        val sy = maxOf(y, 0)
        val ex = minOf(x + vw, W)
        val ey = minOf(y + vh, H)
        if (ex <= sx || ey <= sy) return
        val fr = f
        val n = fr.d.size / STR
        run {
            GLES20.glViewport(x, y, vw, vh)
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(sx, sy, ex - sx, ey - sy)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (n > 0) {
                GLES20.glEnable(GLES20.GL_BLEND)
                items(fr)
            }
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        if (tx.size > 8) {
            val ei = tx.entries.iterator()
            while (tx.size > 8 && ei.hasNext()) {
                val e = ei.next()
                ds[0] = e.value
                GLES20.glDeleteTextures(1, ds, 0)
                ei.remove()
            }
        }
    }
}

@Composable
fun Viewer(comp: Comp, res: Int, zm: Float, px: Float, py: Float, m: Modifier) {
    val r = remember { Rnd() }
    AndroidView(
        modifier = m,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setRenderer(r)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
        },
        update = { v ->
            r.zm = zm
            r.pnx = px
            r.pny = py
            if (v.width > 0 && v.tag != res) {
                if (res <= 1) v.holder.setSizeFromLayout() else v.holder.setFixedSize(maxOf(v.width / res, 1), maxOf(v.height / res, 1))
                v.tag = res
            }
            if (comp.playing) {
                r.live = comp
                if (v.renderMode != GLSurfaceView.RENDERMODE_CONTINUOUSLY) v.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            } else {
                r.live = null
                if (v.renderMode != GLSurfaceView.RENDERMODE_WHEN_DIRTY) v.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                val t0 = System.nanoTime()
                r.f = comp.frame()
                FrameMs = (System.nanoTime() - t0) / 1e6f
                r.t = comp.time
                r.ar = comp.w.toFloat() / comp.h
                v.requestRender()
            }
        }
    )
}
