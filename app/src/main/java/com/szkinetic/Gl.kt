package com.szkinetic

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

const val VS = "attribute vec2 p;uniform vec2 o,e;uniform float s,r,a;varying vec2 uv;void main(){uv=p*0.5+0.5;float c=cos(r),n=sin(r);vec2 z=p*e*s;vec2 q=vec2(z.x*c-z.y*n,z.x*n+z.y*c);q.x/=a;gl_Position=vec4(q+o,0.0,1.0);}"
const val FS = "precision mediump float;varying vec2 uv;uniform vec4 col;uniform float tm,u;uniform sampler2D tx;void main(){if(u>0.5){vec4 t=texture2D(tx,vec2(uv.x,1.0-uv.y));gl_FragColor=vec4(t.rgb*col.a,t.a*col.a);return;}float g=0.5+0.5*sin(uv.x*6.0+tm*3.0);gl_FragColor=vec4(col.rgb*(0.7+0.3*g),col.a);}"

class Frame(val d: FloatArray, val b: Array<Bitmap?>)

fun sh(t: Int, s: String): Int {
    val i = GLES20.glCreateShader(t)
    GLES20.glShaderSource(i, s)
    GLES20.glCompileShader(i)
    return i
}

fun prog(): Int {
    val p = GLES20.glCreateProgram()
    GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, VS))
    GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, FS))
    GLES20.glLinkProgram(p)
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
    var pg = 0
    var W = 0
    var H = 0
    var tx = HashMap<Bitmap, Int>()
    val vb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    override fun onSurfaceCreated(g: GL10?, c: EGLConfig?) {
        pg = prog()
        tx = HashMap()
        GLES20.glEnable(GLES20.GL_BLEND)
    }

    override fun onSurfaceChanged(g: GL10?, w: Int, h: Int) {
        W = w
        H = h
    }

    override fun onDrawFrame(g: GL10?) {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glViewport(0, 0, W, H)
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (W == 0 || H == 0) return
        var vw = W
        var vh = (W / ar).toInt()
        if (vh > H) {
            vh = H
            vw = (H * ar).toInt()
        }
        val x = (W - vw) / 2
        val y = (H - vh) / 2
        GLES20.glViewport(x, y, vw, vh)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(x, y, vw, vh)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(pg)
        val pl = GLES20.glGetAttribLocation(pg, "p")
        GLES20.glEnableVertexAttribArray(pl)
        GLES20.glVertexAttribPointer(pl, 2, GLES20.GL_FLOAT, false, 8, vb)
        val uo = GLES20.glGetUniformLocation(pg, "o")
        val ue = GLES20.glGetUniformLocation(pg, "e")
        val us = GLES20.glGetUniformLocation(pg, "s")
        val ur = GLES20.glGetUniformLocation(pg, "r")
        val ua = GLES20.glGetUniformLocation(pg, "a")
        val uc = GLES20.glGetUniformLocation(pg, "col")
        val ut = GLES20.glGetUniformLocation(pg, "tm")
        val uu = GLES20.glGetUniformLocation(pg, "u")
        val ux = GLES20.glGetUniformLocation(pg, "tx")
        GLES20.glUniform1f(ua, ar)
        GLES20.glUniform1f(ut, t)
        val fr = f
        val d = fr.d
        val nt = HashMap<Bitmap, Int>()
        for (i in 0 until d.size / 10) {
            val o = i * 10
            val bm = fr.b[i]
            GLES20.glUniform2f(uo, d[o], d[o + 1])
            GLES20.glUniform1f(us, d[o + 2])
            GLES20.glUniform1f(ur, d[o + 3])
            GLES20.glUniform4f(uc, d[o + 4], d[o + 5], d[o + 6], d[o + 7])
            GLES20.glUniform2f(ue, d[o + 8], d[o + 9])
            if (bm != null) {
                var id = nt[bm] ?: tx[bm]
                if (id == null) id = up(bm)
                nt[bm] = id
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
                GLES20.glUniform1i(ux, 0)
                GLES20.glUniform1f(uu, 1f)
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            } else {
                GLES20.glUniform1f(uu, 0f)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        for ((k, v) in tx) if (!nt.containsKey(k)) GLES20.glDeleteTextures(1, intArrayOf(v), 0)
        tx = nt
    }
}

@Composable
fun Viewer(comp: Comp, m: Modifier) {
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
            r.f = comp.frame()
            r.t = comp.time
            r.ar = comp.w.toFloat() / comp.h
            v.requestRender()
        }
    )
}
