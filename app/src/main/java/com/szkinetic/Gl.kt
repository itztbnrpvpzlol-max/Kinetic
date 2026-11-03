package com.szkinetic

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

const val VS = "attribute vec2 p;uniform vec2 o;uniform float s,r,a;varying vec2 uv;void main(){uv=p*0.5+0.5;float c=cos(r),n=sin(r);vec2 q=vec2(p.x*c-p.y*n,p.x*n+p.y*c)*s*0.3;q.x/=a;gl_Position=vec4(q+o,0.0,1.0);}"
const val FS = "precision mediump float;varying vec2 uv;uniform vec4 col;uniform float tm;void main(){float g=0.5+0.5*sin(uv.x*6.0+tm*3.0);gl_FragColor=vec4(col.rgb*(0.7+0.3*g),col.a);}"

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

class Rnd : GLSurfaceView.Renderer {
    @Volatile var s = FloatArray(0)
    @Volatile var t = 0f
    var pg = 0
    var W = 0
    var H = 0
    val vb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    override fun onSurfaceCreated(g: GL10?, c: EGLConfig?) {
        pg = prog()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun onSurfaceChanged(g: GL10?, w: Int, h: Int) {
        W = w
        H = h
    }

    override fun onDrawFrame(g: GL10?) {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glViewport(0, 0, W, H)
        GLES20.glClearColor(0.11f, 0.11f, 0.11f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (W == 0 || H == 0) return
        var vw = W
        var vh = W * 9 / 16
        if (vh > H) {
            vh = H
            vw = H * 16 / 9
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
        val us = GLES20.glGetUniformLocation(pg, "s")
        val ur = GLES20.glGetUniformLocation(pg, "r")
        val ua = GLES20.glGetUniformLocation(pg, "a")
        val uc = GLES20.glGetUniformLocation(pg, "col")
        val ut = GLES20.glGetUniformLocation(pg, "tm")
        GLES20.glUniform1f(ua, 16f / 9f)
        GLES20.glUniform1f(ut, t)
        val d = s
        for (i in 0 until d.size / 8) {
            val o = i * 8
            GLES20.glUniform2f(uo, d[o], d[o + 1])
            GLES20.glUniform1f(us, d[o + 2])
            GLES20.glUniform1f(ur, d[o + 3])
            GLES20.glUniform4f(uc, d[o + 4], d[o + 5], d[o + 6], d[o + 7])
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
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
            r.s = comp.snap()
            r.t = comp.time
            v.requestRender()
        }
    )
}
