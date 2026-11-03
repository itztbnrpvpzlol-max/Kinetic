package com.szkinetic

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

data class Kf(val t: Float, val v: Float)

val PN = arrayOf("Position X", "Position Y", "Scale", "Rotation")
val ST = floatArrayOf(0.05f, 0.05f, 0.1f, 15f)
val COLS = longArrayOf(0xFF4A90E2, 0xFFE2574A, 0xFF4AE27B, 0xFFE2C84A)

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

class Layer(val name: String, val col: Long, x: Float, y: Float, s: Float, r: Float) {
    val p = Array(4) { mutableStateListOf<Kf>() }

    init {
        p[0].add(Kf(0f, x))
        p[1].add(Kf(0f, y))
        p[2].add(Kf(0f, s))
        p[3].add(Kf(0f, r))
    }

    fun copy(): Layer {
        val n = Layer(name + " copy", col, 0f, 0f, 1f, 0f)
        for (i in 0 until 4) {
            n.p[i].clear()
            n.p[i].addAll(p[i])
        }
        return n
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
            return l
        }
    }
}

class Comp(val name: String, val w: Int, val h: Int, val fps: Int, val dur: Float) {
    val layers = mutableStateListOf<Layer>()
    var time by mutableStateOf(0f)
    var playing by mutableStateOf(false)
    var sel by mutableStateOf(0)

    fun seed() {
        layers.add(Layer("Shape 1", COLS[0], -0.5f, 0f, 1f, 0f).also {
            it.p[0].add(Kf(dur / 2, 0.5f))
            it.p[3].add(Kf(dur / 2, 180f))
        })
    }

    fun add() {
        val n = layers.size + 1
        layers.add(Layer("Shape $n", COLS[n % COLS.size], 0f, 0f, 1f, 0f))
        sel = layers.size - 1
    }

    fun dup(i: Int) {
        if (i in layers.indices) {
            layers.add(i + 1, layers[i].copy())
            sel = i + 1
        }
    }

    fun del(i: Int) {
        if (i in layers.indices) {
            layers.removeAt(i)
            sel = sel.coerceIn(0, maxOf(layers.size - 1, 0))
        }
    }

    fun snap(): FloatArray {
        val a = FloatArray(layers.size * 8)
        layers.forEachIndexed { i, l ->
            val o = i * 8
            a[o] = ev(l.p[0], time)
            a[o + 1] = ev(l.p[1], time)
            a[o + 2] = ev(l.p[2], time)
            a[o + 3] = Math.toRadians(ev(l.p[3], time).toDouble()).toFloat()
            a[o + 4] = ((l.col shr 16) and 255L) / 255f
            a[o + 5] = ((l.col shr 8) and 255L) / 255f
            a[o + 6] = (l.col and 255L) / 255f
            a[o + 7] = 1f
        }
        return a
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
            for (i in 0 until a.length()) c.layers.add(Layer.fromJs(a.getJSONObject(i)))
            return c
        }
    }
}

class Project(val id: String, val name: String, var mod: Long, val comps: MutableList<Comp>) {
    fun js(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("n", name)
        o.put("m", mod)
        val a = JSONArray()
        for (c in comps) a.put(c.js())
        o.put("c", a)
        return o
    }

    companion object {
        fun fromJs(o: JSONObject): Project {
            val l = mutableListOf<Comp>()
            val a = o.getJSONArray("c")
            for (i in 0 until a.length()) l.add(Comp.fromJs(a.getJSONObject(i)))
            if (l.isEmpty()) throw IllegalArgumentException("empty")
            return Project(o.getString("id"), o.getString("n"), o.getLong("m"), l)
        }

        fun create(c: Comp) = Project(UUID.randomUUID().toString(), c.name, System.currentTimeMillis(), mutableListOf(c))
    }
}
