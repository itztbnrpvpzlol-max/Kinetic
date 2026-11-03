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

// Full-screen effect pass: md 0 = one-axis gaussian blur, md 1 = brightness/contrast/hue/saturation.
const val FVS = "attribute vec2 p;varying vec2 uv;void main(){uv=p*0.5+0.5;gl_Position=vec4(p,0.0,1.0);}"
const val FFS = "precision mediump float;varying vec2 uv;uniform sampler2D tx;uniform vec2 dir;uniform float md,rad;uniform vec4 adj;" +
    "void main(){" +
    "if(md<0.5){vec4 s=vec4(0.0);float ws=0.0;" +
    "for(int i=-8;i<=8;i++){float x=float(i)/8.0;float w=exp(-4.5*x*x);s+=texture2D(tx,uv+dir*(rad*x))*w;ws+=w;}" +
    "gl_FragColor=s/ws;return;}" +
    "vec4 c=texture2D(tx,uv);vec3 r=c.rgb;" +
    "r+=adj.x;r=(r-0.5)*(1.0+adj.y)+0.5;" +
    "float l=dot(r,vec3(0.299,0.587,0.114));r=mix(vec3(l),r,1.0+adj.w);" +
    "float cs=cos(adj.z);float sn=sin(adj.z);" +
    "mat3 hm=mat3(0.213+cs*0.787-sn*0.213,0.213-cs*0.213+sn*0.143,0.213-cs*0.213-sn*0.787," +
    "0.715-cs*0.715-sn*0.715,0.715+cs*0.285+sn*0.140,0.715-cs*0.715+sn*0.715," +
    "0.072-cs*0.072+sn*0.928,0.072-cs*0.072-sn*0.283,0.072+cs*0.928+sn*0.072);" +
    "r=hm*r;gl_FragColor=vec4(clamp(r,0.0,1.0),c.a);}"

var FrameMs by mutableStateOf(0f)

class Frame(val d: FloatArray, val b: Array<Bitmap?>, val fx: Array<FloatArray?>)

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
    @Volatile var f = Frame(FloatArray(0), arrayOfNulls<Bitmap>(0), arrayOfNulls<FloatArray>(0))
    @Volatile var t = 0f
    @Volatile var ar = 16f / 9f
    @Volatile var zm = 1f
    @Volatile var pnx = 0f
    @Volatile var pny = 0f
    private var pg = 0
    private var pf = 0
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
    private var flp = 0
    private var fDir = 0
    private var fMd = 0
    private var fRad = 0
    private var fAdj = 0
    private var fX = 0
    private val fb = IntArray(3)
    private val ft = IntArray(3)
    private var fw = 0
    private var fh = 0
    private var fbOk = false
    private var fbMade = false
    private var cm = -1
    private val vb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    override fun onSurfaceCreated(g: GL10?, c: EGLConfig?) {
        pg = prog()
        pf = prog(FVS, FFS)
        tx = LinkedHashMap(16, 0.75f, true)
        fbMade = false
        fbOk = false
        fw = 0
        fh = 0
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
        if (pf != 0) {
            flp = GLES20.glGetAttribLocation(pf, "p")
            fDir = GLES20.glGetUniformLocation(pf, "dir")
            fMd = GLES20.glGetUniformLocation(pf, "md")
            fRad = GLES20.glGetUniformLocation(pf, "rad")
            fAdj = GLES20.glGetUniformLocation(pf, "adj")
            fX = GLES20.glGetUniformLocation(pf, "tx")
            GLES20.glUseProgram(pf)
            GLES20.glUniform1i(fX, 0)
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

    private fun freeFb() {
        if (fbMade) {
            GLES20.glDeleteFramebuffers(3, fb, 0)
            GLES20.glDeleteTextures(3, ft, 0)
        }
        fbMade = false
        fbOk = false
    }

    private fun ensureFb(w: Int, h: Int): Boolean {
        if (pf == 0) return false
        if (fbOk && w == fw && h == fh) return true
        freeFb()
        if (w < 1 || h < 1 || w > 4096 || h > 4096) return false
        GLES20.glGenFramebuffers(3, fb, 0)
        GLES20.glGenTextures(3, ft, 0)
        fbMade = true
        var ok = true
        for (i in 0 until 3) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ft[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, ft[i], 0)
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) ok = false
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        if (!ok) {
            freeFb()
            return false
        }
        fw = w
        fh = h
        fbOk = true
        return true
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

    private fun fxPass(src: Int, md: Float, dx: Float, dy: Float, rad: Float, a0: Float, a1: Float, a2: Float, a3: Float): Int {
        val dst = if (src == 1) 2 else 1
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[dst])
        GLES20.glViewport(0, 0, fw, fh)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(pf)
        bindVb(flp)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ft[src])
        GLES20.glUniform1f(fMd, md)
        GLES20.glUniform2f(fDir, dx, dy)
        GLES20.glUniform1f(fRad, rad)
        GLES20.glUniform4f(fAdj, a0, a1, a2, a3)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glEnable(GLES20.GL_BLEND)
        return dst
    }

    // Runs the adjustment layer's effects on everything drawn so far (fb[0]) and mixes the result back by opacity.
    private fun adjust(enc: FloatArray?, op: Float) {
        if (enc == null || enc.isEmpty()) return
        var cur = 0
        var j = 0
        while (j + 5 <= enc.size) {
            if (enc[j].toInt() == 0) {
                val rpx = enc[j + 1] * fw
                if (rpx >= 0.5f) {
                    val n = if (rpx > 24f) minOf(ceil((rpx / 24f) * (rpx / 24f)).toInt(), 8) else 1
                    val r = rpx / sqrt(n.toFloat())
                    for (q in 0 until n) {
                        cur = fxPass(cur, 0f, 1f / fw, 0f, r, 0f, 0f, 0f, 0f)
                        cur = fxPass(cur, 0f, 0f, 1f / fh, r, 0f, 0f, 0f, 0f)
                    }
                }
            } else {
                cur = fxPass(cur, 1f, 0f, 0f, 0f, enc[j + 1], enc[j + 2], enc[j + 3], enc[j + 4])
            }
            j += 5
        }
        if (cur == 0) return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
        GLES20.glViewport(0, 0, fw, fh)
        GLES20.glUseProgram(pg)
        bindVb(lp)
        GLES20.glUniform1f(uA, ar)
        GLES20.glUniform1f(uU, 2f)
        GLES20.glUniform4f(uM, 1f, 0f, 0f, 1f)
        GLES20.glUniform2f(uT, 0f, 0f)
        GLES20.glUniform2f(uE, ar, 1f)
        GLES20.glUniform4f(uC, 1f, 1f, 1f, op)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ft[cur])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        cm = -1
    }

    private fun items(fr: Frame, fx: Boolean) {
        val d = fr.d
        val n = d.size / STR
        GLES20.glUseProgram(pg)
        bindVb(lp)
        GLES20.glUniform1f(uA, ar)
        GLES20.glUniform1f(uTm, t)
        cm = -1
        for (i in 0 until n) {
            val o = i * STR
            if (d[o + 12] > 0.5f) {
                if (fx) {
                    adjust(fr.fx[i], d[o + 9])
                    GLES20.glUseProgram(pg)
                    bindVb(lp)
                    GLES20.glUniform1f(uA, ar)
                    GLES20.glUniform1f(uTm, t)
                    cm = -1
                }
                continue
            }
            item(d, o, fr.b[i])
        }
    }

    override fun onDrawFrame(g: GL10?) {
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
        var adj = false
        for (i in 0 until n) if (fr.d[i * STR + 12] > 0.5f) adj = true
        val useFb = adj && ensureFb(bw, bh)
        if (useFb) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0])
            GLES20.glViewport(0, 0, fw, fh)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glEnable(GLES20.GL_BLEND)
            items(fr, true)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(x, y, vw, vh)
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(sx, sy, ex - sx, ey - sy)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(pg)
            bindVb(lp)
            GLES20.glUniform1f(uA, a)
            GLES20.glUniform1f(uU, 2f)
            GLES20.glUniform4f(uM, 1f, 0f, 0f, 1f)
            GLES20.glUniform2f(uT, 0f, 0f)
            GLES20.glUniform2f(uE, a, 1f)
            GLES20.glUniform4f(uC, 1f, 1f, 1f, 1f)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ft[0])
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glEnable(GLES20.GL_BLEND)
            cm = -1
        } else {
            GLES20.glViewport(x, y, vw, vh)
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(sx, sy, ex - sx, ey - sy)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (n > 0) {
                GLES20.glEnable(GLES20.GL_BLEND)
                items(fr, false)
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
            val t0 = System.nanoTime()
            r.f = comp.frame()
            FrameMs = (System.nanoTime() - t0) / 1e6f
            r.t = comp.time
            r.ar = comp.w.toFloat() / comp.h
            r.zm = zm
            r.pnx = px
            r.pny = py
            if (v.width > 0 && v.tag != res) {
                if (res <= 1) v.holder.setSizeFromLayout() else v.holder.setFixedSize(maxOf(v.width / res, 1), maxOf(v.height / res, 1))
                v.tag = res
            }
            v.requestRender()
        }
    )
}
