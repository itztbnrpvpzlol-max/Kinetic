package com.szkinetic

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
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

class Layer(val name: String, val col: Long, x: Float, y: Float, s: Float, r: Float) {
    val p = Array(4) { mutableStateListOf<Kf>() }
    init {
        p[0].add(Kf(0f, x))
        p[1].add(Kf(0f, y))
        p[2].add(Kf(0f, s))
        p[3].add(Kf(0f, r))
    }
}

class Comp {
    val layers = mutableStateListOf<Layer>()
    var time by mutableStateOf(0f)
    var playing by mutableStateOf(false)
    var sel by mutableStateOf(0)
    val dur = 6f

    init {
        layers.add(Layer("Shape 1", COLS[0], -0.5f, 0f, 1f, 0f).also {
            it.p[0].add(Kf(3f, 0.5f))
            it.p[3].add(Kf(3f, 180f))
        })
        layers.add(Layer("Shape 2", COLS[1], 0.4f, 0.2f, 0.7f, 0f).also {
            it.p[1].add(Kf(2f, -0.3f))
            it.p[2].add(Kf(5f, 1.6f))
        })
    }

    fun add() {
        val n = layers.size + 1
        layers.add(Layer("Shape $n", COLS[n % COLS.size], 0f, 0f, 1f, 0f))
        sel = layers.size - 1
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
}
