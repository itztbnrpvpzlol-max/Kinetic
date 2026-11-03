package com.szkinetic

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs

data class Kf(val t: Float, val v: Float)

val PN = arrayOf("Position X", "Position Y", "Scale", "Rotation")
val ST = floatArrayOf(0.05f, 0.05f, 0.1f, 15f)
val COLS = longArrayOf(0xFF4A90E2, 0xFFE2574A, 0xFF4AE27B, 0xFFE2C84A)
val MCOL = longArrayOf(0xFF7FB2D9, 0xFF9C8FDB, 0xFF6DBF8B)
val KN = arrayOf("Image", "Video", "Audio")

fun ev(k: List<Kf>, t: Float): Float {
    if (k.isEmpty()) return 0f
    if (t <= k.first().t) return k.first().v
    if (t >= k.last().t) return k.last().v
    for (i in 0 until k.size - 1) {
        val a = k[i]
        val b = k[i + 1]
        if (t >= a.t && t <= b.t) {
            val u = (t - a.t) / (b.t - a.t)
            val e = u * u * (3f - 2f * u)
            return a.v + (b.v - a.v) * e
        }
    }
    return k.last().v
}

fun bump(k: SnapshotStateList<Kf>, t: Float, d: Float) {
    val v = ev(k, t) + d
    val i = k.indexOfFirst { abs(it.t - t) < 0.04f }
    val n = if (i >= 0) k.toMutableList().also { it[i] = Kf(it[i].t, v) } else (k + Kf(t, v)).sortedBy { it.t }
    k.clear()
    k.addAll(n)
}

fun tc(t: Float, fps: Int): String {
    val f = (t * fps).toInt()
    return "%d:%02d:%02d".format(f / fps / 60, f / fps % 60, f % fps)
}

fun dl(t: Float): String {
    val s = t.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

class Media(val id: String, val name: String, val kind: Int, val fn: String, val dur: Float, val w: Int, val h: Int) {
    var path = ""

    fun js(): JSONObject = JSONObject()
        .put("i", id).put("n", name).put("k", kind).put("f", fn)
        .put("d", dur.toDouble()).put("w", w).put("h", h)

    companion object {
        fun fromJs(o: JSONObject) = Media(
            o.getString("i"), o.getString("n"), o.getInt("k"), o.getString("f"),
            o.getDouble("d").toFloat(), o.getInt("w"), o.getInt("h")
        )
    }
}

class Layer(val name: String, val col: Long, x: Float, y: Float, s: Float, r: Float) {
    val p = Array(4) { mutableStateListOf<Kf>() }
    var mid by mutableStateOf("")
    var kind = -1
    var start by mutableStateOf(0f)
    var off = 0f
    var len by mutableStateOf(1e6f)
    var vis by mutableStateOf(true)
    var lock by mutableStateOf(false)
    var mute by mutableStateOf(false)

    init {
        p[0].add(Kf(0f, x))
        p[1].add(Kf(0f, y))
        p[2].add(Kf(0f, s))
        p[3].add(Kf(0f, r))
    }

    fun end(d: Float) = minOf(start + len, d)

    fun copy(n: String = name + " copy"): Layer {
        val q = Layer(n, col, 0f, 0f, 1f, 0f)
        for (i in 0 until 4) {
            q.p[i].clear()
            q.p[i].addAll(p[i])
        }
        q.mid = mid
        q.kind = kind
        q.start = start
        q.off = off
        q.len = len
        q.vis = vis
        q.lock = lock
        q.mute = mute
        return q
    }

    fun js(): JSONObject {
        val o = JSONObject()
        o.put("n", name)
        o.put("c", col)
        val a = JSONArray()
        for (k in p) {
            val b = JSONArray()
            for (f in k) b.put(JSONArray().put(f.t.toDouble()).put(f.v.toDouble()))
            a.put(b)
        }
        o.put("p", a)
        o.put("mi", mid)
        o.put("k", kind)
        o.put("s", start.toDouble())
        o.put("of", off.toDouble())
        o.put("ln", len.toDouble())
        o.put("v", vis)
        o.put("lk", lock)
        o.put("mu", mute)
        return o
    }

    companion object {
        fun fromJs(o: JSONObject): Layer {
            val l = Layer(o.getString("n"), o.getLong("c"), 0f, 0f, 1f, 0f)
            val a = o.getJSONArray("p")
            for (i in 0 until 4) {
                l.p[i].clear()
                val b = a.getJSONArray(i)
                for (j in 0 until b.length()) {
                    val k = b.getJSONArray(j)
                    l.p[i].add(Kf(k.getDouble(0).toFloat(), k.getDouble(1).toFloat()))
                }
            }
            l.mid = o.optString("mi", "")
            l.kind = o.optInt("k", -1)
            l.start = o.optDouble("s", 0.0).toFloat()
            l.off = o.optDouble("of", 0.0).toFloat()
            l.len = o.optDouble("ln", 1e6).toFloat()
            l.vis = o.optBoolean("v", true)
            l.lock = o.optBoolean("lk", false)
            l.mute = o.optBoolean("mu", false)
            return l
        }
    }
}

class Comp(val name: String, val w: Int, val h: Int, val fps: Int, val dur: Float) {
    val layers = mutableStateListOf<Layer>()
    var lib: List<Media> = emptyList()
    var time by mutableStateOf(0f)
    var playing by mutableStateOf(false)
    var sel by mutableStateOf(0)

    fun seed() {
        layers.add(Layer("Shape 1", COLS[0], -0.5f, 0f, 1f, 0f).also {
            it.len = dur
            it.p[0].add(Kf(dur / 2, 0.5f))
            it.p[3].add(Kf(dur / 2, 180f))
        })
    }

    fun add() {
        val n = layers.size + 1
        layers.add(0, Layer("Shape $n", COLS[n % COLS.size], 0f, 0f, 1f, 0f).also { it.len = dur })
        sel = 0
    }

    fun addMedia(m: Media) {
        val l = Layer(m.name.substringBeforeLast('.'), MCOL[m.kind], 0f, 0f, 1f, 0f)
        l.mid = m.id
        l.kind = m.kind
        val st = if (time >= dur - 0.1f) 0f else time
        val room = dur - st
        l.start = st
        l.len = if (m.kind == 0 || m.dur <= 0f) room else minOf(m.dur, room)
        layers.add(0, l)
        sel = 0
    }

    fun dup(i: Int) {
        if (i in layers.indices) {
            layers.add(i, layers[i].copy())
            sel = i
        }
    }

    fun del(i: Int) {
        if (i in layers.indices) {
            layers.removeAt(i)
            sel = sel.coerceIn(0, maxOf(layers.size - 1, 0))
        }
    }

    fun split(i: Int) {
        val l = layers.getOrNull(i) ?: return
        val e = l.end(dur)
        if (time <= l.start + 0.05f || time >= e - 0.05f) return
        val n = l.copy(l.name)
        n.off = l.off + (time - l.start)
        n.start = time
        n.len = e - time
        l.len = time - l.start
        layers.add(i, n)
        sel = i
    }

    fun move(l: Layer, s: Float) {
        val e = l.end(dur) - l.start
        l.start = s.coerceIn(0f, maxOf(dur - e, 0f))
    }

    fun trim(l: Layer, e: Int, t: Float) {
        val m = lib.firstOrNull { it.id == l.mid }
        val fin = m != null && m.kind != 0 && m.dur > 0f
        val en = l.end(dur)
        if (e == 1) {
            var ns = t.coerceAtLeast(0f).coerceAtMost(maxOf(en - 0.1f, 0f))
            if (fin) ns = maxOf(ns, l.start - l.off)
            l.off += ns - l.start
            l.len = en - ns
            l.start = ns
        } else {
            var ne = t.coerceAtLeast(l.start + 0.1f).coerceAtMost(dur)
            if (fin) ne = minOf(ne, l.start + (m!!.dur - l.off))
            l.len = maxOf(ne - l.start, 0.1f)
        }
    }

    fun frame(): Frame {
        val n = layers.size
        val a = FloatArray(n * 10)
        val b = arrayOfNulls<Bitmap>(n)
        val asp = w.toFloat() / h
        var k = 0
        for (i in n - 1 downTo 0) {
            val l = layers[i]
            if (!l.vis || l.kind == 2 || time < l.start || time > l.end(dur)) continue
            var ex = 0.3f
            var ey = 0.3f
            var bm: Bitmap? = null
            if (l.kind >= 0) {
                val m = lib.firstOrNull { it.id == l.mid } ?: continue
                bm = Frames.get(m, time - l.start + l.off) ?: continue
                val ma = bm.width.toFloat() / bm.height
                if (ma >= asp) {
                    ex = asp
                    ey = asp / ma
                } else {
                    ex = ma
                    ey = 1f
                }
            }
            val o = k * 10
            a[o] = ev(l.p[0], time)
            a[o + 1] = ev(l.p[1], time)
            a[o + 2] = ev(l.p[2], time)
            a[o + 3] = Math.toRadians(ev(l.p[3], time).toDouble()).toFloat()
            if (bm != null) {
                a[o + 4] = 1f
                a[o + 5] = 1f
                a[o + 6] = 1f
            } else {
                a[o + 4] = ((l.col shr 16) and 255L) / 255f
                a[o + 5] = ((l.col shr 8) and 255L) / 255f
                a[o + 6] = (l.col and 255L) / 255f
            }
            a[o + 7] = 1f
            a[o + 8] = ex
            a[o + 9] = ey
            b[k] = bm
            k++
        }
        return Frame(a.copyOf(k * 10), b.copyOf(k))
    }

    fun js(): JSONObject {
        val o = JSONObject()
        o.put("n", name)
        o.put("w", w)
        o.put("h", h)
        o.put("f", fps)
        o.put("d", dur.toDouble())
        val a = JSONArray()
        for (l in layers) a.put(l.js())
        o.put("l", a)
        return o
    }

    companion object {
        fun fromJs(o: JSONObject): Comp {
            val c = Comp(o.getString("n"), o.getInt("w"), o.getInt("h"), o.getInt("f"), o.getDouble("d").toFloat())
            val a = o.getJSONArray("l")
            for (i in 0 until a.length()) {
                val l = Layer.fromJs(a.getJSONObject(i))
                l.len = minOf(l.len, c.dur)
                c.layers.add(l)
            }
            return c
        }
    }
}

class Project(
    val id: String,
    val name: String,
    var mod: Long,
    val comps: MutableList<Comp>,
    val media: SnapshotStateList<Media> = mutableStateListOf()
) {
    init {
        comps.forEach { it.lib = media }
    }

    fun rm(m: Media) {
        comps.forEach { c ->
            c.layers.removeAll { it.mid == m.id }
            c.sel = c.sel.coerceIn(0, maxOf(c.layers.size - 1, 0))
        }
        media.remove(m)
        try {
            File(m.path).delete()
        } catch (e: Exception) {
        }
    }

    fun js(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("n", name)
        o.put("m", mod)
        val a = JSONArray()
        for (c in comps) a.put(c.js())
        o.put("c", a)
        val b = JSONArray()
        for (m in media) b.put(m.js())
        o.put("md", b)
        return o
    }

    companion object {
        fun fromJs(o: JSONObject): Project {
            val l = mutableListOf<Comp>()
            val a = o.getJSONArray("c")
            for (i in 0 until a.length()) l.add(Comp.fromJs(a.getJSONObject(i)))
            if (l.isEmpty()) throw IllegalArgumentException("empty")
            val ms = mutableStateListOf<Media>()
            val b = o.optJSONArray("md")
            if (b != null) for (i in 0 until b.length()) ms.add(Media.fromJs(b.getJSONObject(i)))
            return Project(o.getString("id"), o.getString("n"), o.getLong("m"), l, ms)
        }

        fun create(c: Comp) = Project(UUID.randomUUID().toString(), c.name, System.currentTimeMillis(), mutableListOf(c))
    }
}
