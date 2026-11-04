package com.szkinetic

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.BlurMaskFilter
import android.graphics.Path as AndroidPath
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

// ---- keyframes -------------------------------------------------------------
// ip: 0 linear, 1 bezier, 2 hold.  ii/isp = incoming influence (0..1) and speed (units/sec),
// oi/osp = outgoing influence and speed.  Easy Ease = influence 1/3, speed 0.
data class Kf(
    val t: Float,
    val v: Float,
    val ip: Int = 0,
    val ii: Float = 1f / 3f,
    val isp: Float = 0f,
    val oi: Float = 1f / 3f,
    val osp: Float = 0f,
    val sel: Boolean = false,
    val spatialInManual: Boolean = false,
    val spatialOutManual: Boolean = false,
    val six: Float = 0f,
    val siy: Float = 0f,
    val sox: Float = 0f,
    val soy: Float = 0f
)

private fun bz(p0: Float, p1: Float, p2: Float, p3: Float, s: Float): Float {
    val u = 1f - s
    return u * u * u * p0 + 3f * u * u * s * p1 + 3f * u * s * s * p2 + s * s * s * p3
}

fun seg(a: Kf, b: Kf, t: Float): Float {
    val dt = b.t - a.t
    if (dt <= 1e-6f) return b.v
    if (a.ip == 2) return a.v
    if (a.ip == 0 && b.ip == 0) return a.v + (b.v - a.v) * ((t - a.t) / dt)
    val sl = (b.v - a.v) / dt
    val oi = (if (a.ip == 1) a.oi else 1f / 3f).coerceIn(0.001f, 1f)
    val os = if (a.ip == 1) a.osp else sl
    val ii = (if (b.ip == 1) b.ii else 1f / 3f).coerceIn(0.001f, 1f)
    val isp = if (b.ip == 1) b.isp else sl
    val x1 = a.t + oi * dt
    val y1 = a.v + os * oi * dt
    val x2 = b.t - ii * dt
    val y2 = b.v - isp * ii * dt
    var lo = 0f
    var hi = 1f
    var s = (t - a.t) / dt
    for (n in 0 until 24) {
        val x = bz(a.t, x1, x2, b.t, s)
        if (x < t) lo = s else hi = s
        s = (lo + hi) * 0.5f
    }
    return bz(a.v, y1, y2, b.v, s)
}

fun ev(k: List<Kf>, t: Float): Float {
    val n = k.size
    if (n == 0) return 0f
    if (n == 1 || t <= k[0].t) return k[0].v
    if (t >= k[n - 1].t) return k[n - 1].v
    var lo = 0
    var hi = n - 1
    while (hi - lo > 1) {
        val m = (lo + hi) ushr 1
        if (k[m].t <= t) lo = m else hi = m
    }
    return seg(k[lo], k[hi], t)
}

// A single animatable value. Stopwatch off = static value (sv); on = keyframes (k).
class Prop(val def: Float) {
    var sv by mutableStateOf(def)
    val k = mutableStateListOf<Kf>()
    val anim: Boolean get() = k.isNotEmpty()
    var gr by mutableStateOf(true) // shown in the Graph Editor

    fun hasSel(): Boolean = k.any { it.sel }

    fun selNone() {
        for (i in k.indices) if (k[i].sel) k[i] = k[i].copy(sel = false)
    }

    fun selAll() {
        for (i in k.indices) if (!k[i].sel) k[i] = k[i].copy(sel = true)
    }

    fun setSel(i: Int, b: Boolean) {
        if (i in k.indices && k[i].sel != b) k[i] = k[i].copy(sel = b)
    }

    fun delSel() {
        if (!hasSel()) return
        val first = k.first { it.sel }
        val keep = k.filter { !it.sel }
        if (keep.isEmpty()) sv = first.v
        k.clear()
        k.addAll(keep)
    }

    // Procedural animation modifier: 0 none, 1 wiggle, 2 bounce, 3 elastic.
    // Values are stored with the property so expressions survive save/load and undo.
    var expression by mutableIntStateOf(0)
    var expressionFrequency by mutableFloatStateOf(3f)
    var expressionAmount by mutableFloatStateOf(0.08f)
    var expressionDecay by mutableFloatStateOf(5f)

    fun get(t: Float): Float {
        val base = if (k.isEmpty()) sv else ev(k, t)
        if (expression == 0 || !t.isFinite()) return base
        val frequency = expressionFrequency.coerceIn(0.1f, 30f)
        val amount = expressionAmount.coerceIn(0f, 2f)
        val scale = maxOf(0.1f, abs(base), if (k.size > 1) abs(k.last().v - k.first().v) else 0f)
        if (expression == 1) {
            val phase = t * frequency * (2.0 * PI).toFloat()
            val wave = sin(phase) * 0.62f + sin(phase * 2.13f + 1.7f) * 0.25f + sin(phase * 3.71f + 0.4f) * 0.13f
            return base + wave * scale * amount
        }
        if (k.size < 2 || t <= k.first().t || t >= k.last().t) return base
        var index = 0
        while (index < k.lastIndex - 1 && k[index + 1].t < t) index++
        val a = k[index]
        val b = k[index + 1]
        val dt = b.t - a.t
        if (dt <= 1e-6f) return base
        val u = ((t - a.t) / dt).coerceIn(0f, 1f)
        val delta = b.v - a.v
        val decay = exp(-expressionDecay.coerceIn(0.1f, 20f) * u)
        val offset = when (expression) {
            2 -> abs(sin(PI.toFloat() * frequency * u)) * decay * delta * amount
            3 -> sin((2.0 * PI).toFloat() * frequency * u) * decay * delta * amount
            else -> 0f
        }
        return base + offset
    }

    fun setExpression(type: Int, frequency: Float = expressionFrequency, amount: Float = expressionAmount, decay: Float = expressionDecay) {
        expression = type.coerceIn(0, 3)
        expressionFrequency = frequency.coerceIn(0.1f, 30f)
        expressionAmount = amount.coerceIn(0f, 2f)
        expressionDecay = decay.coerceIn(0.1f, 20f)
    }

    fun idx(t: Float, e: Float): Int {
        for (i in 0 until k.size) if (abs(k[i].t - t) <= e) return i
        return -1
    }

    fun put(f: Kf, e: Float) {
        val i = idx(f.t, e)
        if (i >= 0) {
            k[i] = f
            return
        }
        var j = 0
        while (j < k.size && k[j].t < f.t) j++
        k.add(j, f)
    }

    fun set(t: Float, v: Float, e: Float) {
        if (k.isEmpty()) {
            sv = v
            return
        }
        val i = idx(t, e)
        if (i >= 0) k[i] = k[i].copy(v = v) else put(Kf(t, v), e)
    }

    // stopwatch toggle
    fun watch(t: Float) {
        if (k.isEmpty()) {
            k.add(Kf(t, sv))
        } else {
            sv = get(t)
            k.clear()
        }
    }

    fun toggle(t: Float, e: Float) {
        val i = idx(t, e)
        if (i >= 0) {
            if (k.size == 1) sv = k[0].v
            k.removeAt(i)
        } else {
            put(Kf(t, get(t)), e)
        }
    }

    fun next(t: Float, e: Float): Float? = k.firstOrNull { it.t > t + e }?.t
    fun prev(t: Float, e: Float): Float? = k.lastOrNull { it.t < t - e }?.t

    private fun slope(i: Int, j: Int): Float {
        if (i < 0 || j < 0 || i >= k.size || j >= k.size) return 0f
        val dt = k[j].t - k[i].t
        return if (abs(dt) < 1e-6f) 0f else (k[j].v - k[i].v) / dt
    }

    // 0 linear, 1 auto bezier, 2 hold, 3 easy ease, 4 ease in, 5 ease out
    fun interp(i: Int, m: Int) {
        if (i < 0 || i >= k.size) return
        val f = k[i]
        val third = 1f / 3f
        k[i] = when (m) {
            0 -> f.copy(ip = 0)
            2 -> f.copy(ip = 2)
            1 -> {
                val sp = if (i == 0 || i == k.size - 1) 0f else slope(i - 1, i + 1)
                f.copy(ip = 1, ii = third, isp = sp, oi = third, osp = sp)
            }
            3 -> f.copy(ip = 1, ii = third, isp = 0f, oi = third, osp = 0f)
            4 -> f.copy(
                ip = 1, ii = third, isp = 0f,
                oi = if (f.ip == 1) f.oi else third, osp = if (f.ip == 1) f.osp else slope(i, i + 1)
            )
            else -> f.copy(
                ip = 1, oi = third, osp = 0f,
                ii = if (f.ip == 1) f.ii else third, isp = if (f.ip == 1) f.isp else slope(i - 1, i)
            )
        }
    }

    fun copyFrom(o: Prop) {
        sv = o.sv
        k.clear()
        k.addAll(o.k)
        expression = o.expression
        expressionFrequency = o.expressionFrequency
        expressionAmount = o.expressionAmount
        expressionDecay = o.expressionDecay
    }

    fun js(): JSONObject {
        val o = JSONObject()
        o.put("v", sv.toDouble())
        o.put("ex", expression)
        o.put("ef", expressionFrequency.toDouble())
        o.put("ea", expressionAmount.toDouble())
        o.put("ed", expressionDecay.toDouble())
        val a = JSONArray()
        for (f in k) {
            a.put(
                JSONArray().put(f.t.toDouble()).put(f.v.toDouble()).put(f.ip)
                     .put(f.ii.toDouble()).put(f.isp.toDouble()).put(f.oi.toDouble()).put(f.osp.toDouble())
                    .put(f.spatialInManual).put(f.spatialOutManual).put(f.six.toDouble()).put(f.siy.toDouble()).put(f.sox.toDouble()).put(f.soy.toDouble())
            )
        }
        o.put("k", a)
        return o
    }

    fun load(o: JSONObject) {
        sv = o.optDouble("v", def.toDouble()).toFloat()
        expression = o.optInt("ex", 0).coerceIn(0, 3)
        expressionFrequency = o.optDouble("ef", 3.0).toFloat().coerceIn(0.1f, 30f)
        expressionAmount = o.optDouble("ea", 0.08).toFloat().coerceIn(0f, 2f)
        expressionDecay = o.optDouble("ed", 5.0).toFloat().coerceIn(0.1f, 20f)
        k.clear()
        val a = o.optJSONArray("k") ?: return
        for (i in 0 until a.length()) {
            val b = a.getJSONArray(i)
            k.add(
                Kf(
                    b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.optInt(2, 0),
                    b.optDouble(3, 1.0 / 3.0).toFloat(), b.optDouble(4, 0.0).toFloat(),
                    b.optDouble(5, 1.0 / 3.0).toFloat(), b.optDouble(6, 0.0).toFloat(), false,
                    b.optBoolean(7, false), b.optBoolean(8, false), b.optDouble(9, 0.0).toFloat(),
                    b.optDouble(10, 0.0).toFloat(), b.optDouble(11, 0.0).toFloat(), b.optDouble(12, 0.0).toFloat()
                )
            )
        }
    }
}

// ---- layer property channels ------------------------------------------------
const val PX = 0
const val PY = 1
const val SX = 2
const val RT = 3
const val SY = 4
const val AX = 5
const val AY = 6
const val OP = 7
const val PZ = 8
const val RX = 9
const val RY = 10
const val NCH = 11

val PN = arrayOf("Position X", "Position Y", "Scale X", "Rotation", "Scale Y", "Anchor X", "Anchor Y", "Opacity", "Position Z", "Rotation X", "Rotation Y")
val PDEF = floatArrayOf(0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f)
val COLS = longArrayOf(0xFF4A90E2, 0xFFE2574A, 0xFF4AE27B, 0xFFE2C84A)
val MCOL = longArrayOf(0xFF7FB2D9, 0xFF9C8FDB, 0xFF6DBF8B)
val KN = arrayOf("Image", "Video", "Audio")
val LABELS = longArrayOf(
    0xFFE2574A, 0xFFE2C84A, 0xFF4AD6D6, 0xFFE28FC0, 0xFF9C8FDB, 0xFFE8A070,
    0xFF6DBF8B, 0xFF4A90E2, 0xFF4AE27B, 0xFFA060D0, 0xFFE08A30, 0xFF9A7A5A
)

// ---- formatting -------------------------------------------------------------
fun tc(t: Float, fps: Int): String {
    val f = (t * fps).toInt()
    return "%d:%02d:%02d".format(f / fps / 60, f / fps % 60, f % fps)
}

fun tf(f: Int, fps: Int): String = "%d:%02d:%02d".format(f / fps / 60, f / fps % 60, f % fps)

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

// 2x3 affine in "e-space" (square units, y up; 1 unit = half comp height)
class Aff(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float) {
    fun mul(o: Aff) = Aff(
        a * o.a + c * o.b, b * o.a + d * o.b,
        a * o.c + c * o.d, b * o.c + d * o.d,
        a * o.tx + c * o.ty + tx, b * o.tx + d * o.ty + ty
    )
}

data class LayerEffect(val id: Int, val amount: Float = 0.5f)

class Layer(n: String, c: Long, x: Float, y: Float, s: Float, r: Float) {
    var name by mutableStateOf(n)
    val col = c
    var lcol by mutableStateOf(c)
    var uid = UUID.randomUUID().toString().take(8)
    var role = 0 // 0 normal, 1 null, 2 adjustment
    var isSolid by mutableStateOf(false)
    val pathPoints = mutableStateListOf<PathPoint>()
    var pathClosed by mutableStateOf(true)
    @Transient var pathBitmap: Bitmap? = null
    val maskPoints = mutableStateListOf<PathPoint>()
    var maskFeather by mutableFloatStateOf(0f)
    var maskOpacity by mutableFloatStateOf(1f)
    var maskInverted by mutableStateOf(false)
    var maskEnabled by mutableStateOf(false)
    var trackAnchorX by mutableFloatStateOf(0.5f)
    var trackAnchorY by mutableFloatStateOf(0.5f)
    var trackPatchSize by mutableIntStateOf(15)
    var trackingStatus by mutableStateOf("Not tracked")
    val trackSamples = mutableStateListOf<TrackSample>()
    // Legacy fields are retained so existing projects and plugin code remain compatible.
    var effectId by mutableIntStateOf(0)
    var effectAmount by mutableFloatStateOf(0.5f)
    val effects = mutableStateListOf<LayerEffect>()
    var timeRemapEnabled by mutableStateOf(false)
    val timeRemap = Prop(0f)

    fun enableTimeRemap() {
        if (timeRemapEnabled) return
        timeRemapEnabled = true
        timeRemap.k.clear()
        timeRemap.k.add(Kf(start, off))
        timeRemap.k.add(Kf(start + len, off + len))
    }

    fun applyMask(source: Bitmap): Bitmap {
        if (!maskEnabled || maskPoints.size < 3 || source.isRecycled) return source
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ALPHA_8)
        val canvas = AndroidCanvas(mask)
        val path = AndroidPath()
        if (maskInverted) {
            path.fillType = AndroidPath.FillType.EVEN_ODD
            path.addRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), AndroidPath.Direction.CW)
        }
        maskPoints.forEachIndexed { index, point ->
            val x = point.x * source.width
            val y = point.y * source.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = (maskOpacity * 255f).toInt().coerceIn(0, 255)
            if (maskFeather > 0f) {
                val radius = (maskFeather * minOf(source.width, source.height) / 1000f).coerceAtLeast(0.5f)
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawPath(path, maskPaint)
        AndroidCanvas(output).drawBitmap(mask, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        mask.recycle()
        return output
    }

    fun renderEffects(): List<LayerEffect> =
        if (effects.isNotEmpty()) effects.take(MAX_LAYER_EFFECTS)
        else if (effectId in 1..16) listOf(LayerEffect(effectId, effectAmount)) else emptyList()

    fun addEffect(id: Int, amount: Float = 0.5f) {
        if (id !in 1..16 || renderEffects().size >= MAX_LAYER_EFFECTS) return
        if (effects.isEmpty() && effectId in 1..16) effects.add(LayerEffect(effectId, effectAmount))
        effects.add(LayerEffect(id, amount.coerceIn(0f, 1f)))
        effectId = id
        effectAmount = amount.coerceIn(0f, 1f)
    }

    private fun migrateLegacyEffect() {
        if (effects.isEmpty() && effectId in 1..16) effects.add(LayerEffect(effectId, effectAmount))
    }

    fun setEffectAmount(index: Int, amount: Float) {
        migrateLegacyEffect()
        if (index !in effects.indices) return
        effects[index] = effects[index].copy(amount = amount.coerceIn(0f, 1f))
        if (index == effects.lastIndex) effectAmount = effects[index].amount
    }

    fun removeEffect(index: Int) {
        migrateLegacyEffect()
        if (index !in effects.indices) return
        effects.removeAt(index)
        effectId = effects.lastOrNull()?.id ?: 0
        effectAmount = effects.lastOrNull()?.amount ?: 0.5f
    }

    fun moveEffect(from: Int, to: Int) {
        migrateLegacyEffect()
        if (from !in effects.indices || to !in effects.indices || from == to) return
        val effect = effects.removeAt(from)
        effects.add(to, effect)
        effectId = effects.lastOrNull()?.id ?: 0
        effectAmount = effects.lastOrNull()?.amount ?: 0.5f
    }
    var parent by mutableStateOf("")
    val pr = Array(NCH) { Prop(PDEF[it]) }
    var mid by mutableStateOf("")
    var preCompId by mutableStateOf("")
    var kind = -1
    var start by mutableStateOf(0f)
    var off = 0f
    var len by mutableStateOf(1e6f)
    var vis by mutableStateOf(true)
    var lock by mutableStateOf(false)
    var mute by mutableStateOf(false)
    var solo by mutableStateOf(false)
    var shy by mutableStateOf(false)
    var is3d by mutableStateOf(false)
    var spatial by mutableStateOf(false)
    var link by mutableStateOf(true)
    var open by mutableStateOf(false)
    var tOpen by mutableStateOf(false)
    var animOnly by mutableStateOf(false)
    var pick by mutableStateOf(false)

    init {
        pr[PX].sv = x
        pr[PY].sv = y
        pr[SX].sv = s
        pr[SY].sv = s
        pr[RT].sv = r
    }

    fun gv(i: Int, t: Float): Float = pr[i].get(t)

    // Smooth spatial interpolation couples Position X/Y into one Catmull-Rom path.
    fun position(t: Float): Pair<Float, Float> {
        val px = pr[PX]
        val py = pr[PY]
        val keys = px.k
        if (!spatial || keys.size < 2 || px.expression != 0 || py.expression != 0) {
            return px.get(t) to py.get(t)
        }
        if (t <= keys.first().t) return keys.first().v to py.get(keys.first().t)
        if (t >= keys.last().t) return keys.last().v to py.get(keys.last().t)
        var i = 0
        while (i < keys.lastIndex - 1 && keys[i + 1].t < t) i++
        val a = keys[i]
        val b = keys[i + 1]
        val dt = b.t - a.t
        if (dt <= 1e-6f) return b.v to py.get(b.t)
        val p0x = a.v
        val p0y = py.get(a.t)
        val p3x = b.v
        val p3y = py.get(b.t)
        val prev = keys[(i - 1).coerceAtLeast(0)]
        val next = keys[(i + 2).coerceAtMost(keys.lastIndex)]
        val autoOutX = (next.v - prev.v) / 6f
        val autoOutY = (py.get(next.t) - py.get(prev.t)) / 6f
        val autoInX = -autoOutX
        val autoInY = -autoOutY
        val p1x = p0x + if (a.spatialOutManual) a.sox else autoOutX
        val p1y = p0y + if (a.spatialOutManual) a.soy else autoOutY
        val p2x = p3x + if (b.spatialInManual) b.six else autoInX
        val p2y = p3y + if (b.spatialInManual) b.siy else autoInY
        val u = ((t - a.t) / dt).coerceIn(0f, 1f)
        return bz(p0x, p1x, p2x, p3x, u) to bz(p0y, p1y, p2y, p3y, u)
    }

    fun end(d: Float) = minOf(start + len, d)

    fun copy(n: String = name + " copy"): Layer {
        val q = Layer(n, col, 0f, 0f, 1f, 0f)
        q.lcol = lcol
        q.role = role
        q.isSolid = isSolid
        q.pathPoints.addAll(pathPoints)
        q.pathClosed = pathClosed
        q.pathBitmap = pathBitmap
        q.maskPoints.addAll(maskPoints)
        q.maskFeather = maskFeather
        q.maskOpacity = maskOpacity
        q.maskInverted = maskInverted
        q.maskEnabled = maskEnabled
        q.trackAnchorX = trackAnchorX
        q.trackAnchorY = trackAnchorY
        q.trackPatchSize = trackPatchSize
        q.trackingStatus = trackingStatus
        q.trackSamples.addAll(trackSamples)
        q.effectId = effectId
        q.effectAmount = effectAmount
        q.effects.addAll(effects)
        q.timeRemapEnabled = timeRemapEnabled
        q.timeRemap.copyFrom(timeRemap)
        q.parent = parent
        for (i in 0 until NCH) q.pr[i].copyFrom(pr[i])
        q.mid = mid
        q.preCompId = preCompId
        q.kind = kind
        q.start = start
        q.off = off
        q.len = len
        q.vis = vis
        q.lock = lock
        q.mute = mute
        q.shy = shy
        q.is3d = is3d
        q.spatial = spatial
        q.link = link
        return q
    }

    fun js(): JSONObject {
        val o = JSONObject()
        o.put("n", name)
        o.put("c", col)
        o.put("lc", lcol)
        o.put("u", uid)
        o.put("pa", parent)
        o.put("ro", role)
        o.put("solid", isSolid)
        val pts = JSONArray()
        for (pt in pathPoints) pts.put(JSONArray().put(pt.x.toDouble()).put(pt.y.toDouble()))
        o.put("path", pts)
        o.put("pathClosed", pathClosed)
        o.put("maskEnabled", maskEnabled)
        o.put("maskFeather", maskFeather.toDouble())
        o.put("maskOpacity", maskOpacity.toDouble())
        o.put("maskInverted", maskInverted)
        val mask = JSONArray()
        for (pt in maskPoints) mask.put(JSONArray().put(pt.x.toDouble()).put(pt.y.toDouble()))
        o.put("maskPoints", mask)
        o.put("trackX", trackAnchorX.toDouble())
        o.put("trackY", trackAnchorY.toDouble())
        o.put("trackSize", trackPatchSize)
        o.put("trackStatus", trackingStatus)
        val trk = JSONArray()
        for (sample in trackSamples) trk.put(JSONArray().put(sample.time.toDouble()).put(sample.x.toDouble()).put(sample.y.toDouble()).put(sample.confidence.toDouble()))
        o.put("trackSamples", trk)
        o.put("fx", effectId)
        o.put("fa", effectAmount.toDouble())
        val fx = JSONArray()
        for (effect in effects) fx.put(JSONArray().put(effect.id).put(effect.amount.toDouble()))
        o.put("fxs", fx)
        o.put("trOn", timeRemapEnabled)
        o.put("tr", timeRemap.js())
        val a = JSONArray()
        for (p in pr) a.put(p.js())
        o.put("pr", a)
        o.put("mi", mid)
        o.put("pc", preCompId)
        o.put("k", kind)
        o.put("s", start.toDouble())
        o.put("of", off.toDouble())
        o.put("ln", len.toDouble())
        o.put("v", vis)
        o.put("lk", lock)
        o.put("mu", mute)
        o.put("so", solo)
        o.put("sh", shy)
        o.put("3d", is3d)
        o.put("sp", spatial)
        o.put("sl", link)
        return o
    }

    companion object {
        fun fromJs(o: JSONObject): Layer {
            val l = Layer(o.getString("n"), o.getLong("c"), 0f, 0f, 1f, 0f)
            l.lcol = o.optLong("lc", l.col)
            val u = o.optString("u", "")
            if (u.isNotEmpty()) l.uid = u
            l.parent = o.optString("pa", "")
            l.role = o.optInt("ro", 0)
            l.isSolid = o.optBoolean("solid", false)
            val pts = o.optJSONArray("path")
            if (pts != null) for (i in 0 until pts.length()) {
                val pt = pts.optJSONArray(i) ?: continue
                if (pt.length() >= 2) l.pathPoints.add(PathPoint(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat()))
            }
            l.pathClosed = o.optBoolean("pathClosed", true)
            l.maskEnabled = o.optBoolean("maskEnabled", false)
            l.maskFeather = o.optDouble("maskFeather", 0.0).toFloat().coerceIn(0f, 100f)
            l.maskOpacity = o.optDouble("maskOpacity", 1.0).toFloat().coerceIn(0f, 1f)
            l.maskInverted = o.optBoolean("maskInverted", false)
            val mask = o.optJSONArray("maskPoints")
            if (mask != null) for (i in 0 until minOf(mask.length(), 512)) {
                val pt = mask.optJSONArray(i) ?: continue
                if (pt.length() >= 2) l.maskPoints.add(PathPoint(pt.optDouble(0).toFloat().coerceIn(0f, 1f), pt.optDouble(1).toFloat().coerceIn(0f, 1f)))
            }
            l.trackAnchorX = o.optDouble("trackX", 0.5).toFloat().coerceIn(0f, 1f)
            l.trackAnchorY = o.optDouble("trackY", 0.5).toFloat().coerceIn(0f, 1f)
            l.trackPatchSize = o.optInt("trackSize", 15).coerceIn(7, 41)
            l.trackingStatus = o.optString("trackStatus", "Not tracked")
            val trk = o.optJSONArray("trackSamples")
            if (trk != null) for (i in 0 until minOf(trk.length(), 20000)) {
                val q = trk.optJSONArray(i) ?: continue
                if (q.length() >= 4) l.trackSamples.add(TrackSample(q.optDouble(0).toFloat(), q.optDouble(1).toFloat().coerceIn(0f, 1f), q.optDouble(2).toFloat().coerceIn(0f, 1f), q.optDouble(3).toFloat().coerceIn(-1f, 1f)))
            }
            l.effectId = o.optInt("fx", 0).coerceIn(0, 16)
            l.effectAmount = o.optDouble("fa", 0.5).toFloat().coerceIn(0f, 1f)
            l.timeRemapEnabled = o.optBoolean("trOn", false)
            o.optJSONObject("tr")?.let { l.timeRemap.load(it) }
            val fxs = o.optJSONArray("fxs")
            if (fxs != null) for (i in 0 until minOf(fxs.length(), MAX_LAYER_EFFECTS)) {
                val pair = fxs.optJSONArray(i) ?: continue
                val id = pair.optInt(0, 0)
                if (pair.length() >= 2 && id in 1..16) {
                    l.effects.add(LayerEffect(id, pair.optDouble(1, 0.5).toFloat().coerceIn(0f, 1f)))
                }
            }
            val pa = o.optJSONArray("pr")
            if (pa != null) {
                for (i in 0 until minOf(pa.length(), NCH)) l.pr[i].load(pa.getJSONObject(i))
            } else {
                // Old format: 4 channels (PosX, PosY, Scale, Rotation) of [t, v] keys, smoothstep-eased.
                val a = o.getJSONArray("p")
                val map = intArrayOf(PX, PY, SX, RT)
                for (i in 0 until minOf(4, a.length())) {
                    val b = a.getJSONArray(i)
                    val ks = ArrayList<Kf>()
                    for (j in 0 until b.length()) {
                        val k = b.getJSONArray(j)
                        ks.add(Kf(k.getDouble(0).toFloat(), k.getDouble(1).toFloat(), 1, 1f / 3f, 0f, 1f / 3f, 0f))
                    }
                    val targets = if (i == 2) intArrayOf(SX, SY) else intArrayOf(map[i])
                    for (ch in targets) {
                        val p = l.pr[ch]
                        p.k.clear()
                        if (ks.size == 1 && ks[0].t <= 0.0001f) p.sv = ks[0].v else p.k.addAll(ks)
                    }
                }
            }
            l.mid = o.optString("mi", "")
            l.preCompId = o.optString("pc", "")
            l.kind = o.optInt("k", -1)
            l.start = o.optDouble("s", 0.0).toFloat()
            l.off = o.optDouble("of", 0.0).toFloat()
            l.len = o.optDouble("ln", 1e6).toFloat()
            l.vis = o.optBoolean("v", true)
            l.lock = o.optBoolean("lk", false)
            l.mute = o.optBoolean("mu", false)
            l.solo = o.optBoolean("so", false)
            l.shy = o.optBoolean("sh", false)
            l.is3d = o.optBoolean("3d", false)
            l.spatial = o.optBoolean("sp", false)
            l.link = o.optBoolean("sl", true)
            return l
        }
    }
}

// Frame item stride: m(a,b,c,d) t(x,y) rgb alpha e(x,y) kind(0 draw, 1 adjustment)
const val MAX_LAYER_EFFECTS = 8
const val STR = 14 + MAX_LAYER_EFFECTS * 2

class PRef(val id: String, val name: String, val p: Prop)
class KSnap(val p: Prop, val orig: List<Kf>)
class KEntry(val id: String, val rel: Float, val kf: Kf)

object KClip {
    var items: List<KEntry> = emptyList()
}

data class CompMarker(val time: Float, val label: String, val color: Long = 0xFFFFC857)
data class PathPoint(val x: Float, val y: Float)
data class TrackSample(val time: Float, val x: Float, val y: Float, val confidence: Float)
data class MaskShape(val points: List<PathPoint>, val feather: Float = 0f, val opacity: Float = 1f, val inverted: Boolean = false, val enabled: Boolean = true)

class Comp(n: String, val w: Int, val h: Int, val fps: Int, val dur: Float) {
    var id = UUID.randomUUID().toString()
    var name by mutableStateOf(n)
    val layers = mutableStateListOf<Layer>()
    val markers = mutableStateListOf<CompMarker>()
    var lib: List<Media> = emptyList()
    var proj: Project? = null
    var onOpenComp: ((String) -> Unit)? = null
    private var renderRevision = 0L
    private val pausedFrames = LinkedHashMap<Int, Pair<Long, Frame>>(4, 0.75f, true)
    var playing by mutableStateOf(false)
    private var t0 by mutableStateOf(0f)

    // Slow-ticking copy of the playhead for value readouts: updated every frame when paused, ~10x/s while playing.
    var uiTime by mutableStateOf(0f)
    var time: Float
        get() = t0
        set(v) {
            t0 = v
            if (!playing) uiTime = v
        }
    var loop by mutableStateOf(true)
    var sel by mutableStateOf(0)

    fun mark() {
        renderRevision++
        pausedFrames.clear()
        proj?.mark()
    }

    fun invalidateFrames() {
        renderRevision++
        pausedFrames.clear()
    }

    fun eps(): Float = 0.5f / fps

    // ---- display units (what the user sees/edits) vs stored units ----
    fun toD(ch: Int, v: Float): Float = when (ch) {
        PX -> (v + 1f) / 2f * w
        PY -> (1f - v) / 2f * h
        SX, SY, OP -> v * 100f
        AX, PZ -> v * h / 2f
        AY -> -v * h / 2f
        else -> v
    }

    fun fromD(ch: Int, d: Float): Float = when (ch) {
        PX -> d / w * 2f - 1f
        PY -> 1f - 2f * d / h
        SX, SY, OP -> d / 100f
        AX, PZ -> d * 2f / h
        AY -> -d * 2f / h
        else -> d
    }

    fun setD(l: Layer, ch: Int, d: Float) {
        val v = fromD(ch, d)
        val t = time
        val e = eps()
        if (l.link && (ch == SX || ch == SY)) {
            val o = if (ch == SX) SY else SX
            val cur = l.pr[ch].get(t)
            val oc = l.pr[o].get(t)
            l.pr[o].set(t, if (abs(cur) > 1e-4f) oc * v / cur else v, e)
        }
        l.pr[ch].set(t, v, e)
    }

    fun resetD(l: Layer, ch: Int) {
        val e = eps()
        l.pr[ch].set(time, PDEF[ch], e)
        if (l.link && (ch == SX || ch == SY)) l.pr[if (ch == SX) SY else SX].set(time, 1f, e)
    }

    // ---- layer creation ----
    private fun insertAt(): Int = if (layers.isEmpty()) 0 else sel.coerceIn(0, layers.size)

    private fun addLayer(l: Layer) {
        mark()
        l.len = dur
        val at = insertAt()
        layers.add(at, l)
        pickLayer(at, false)
    }

    fun add() {
        val n = layers.count { it.role == 0 && it.kind < 0 } + 1
        addLayer(Layer("Shape $n", COLS[n % COLS.size], 0f, 0f, 1f, 0f))
    }

    fun addNull() {
        val n = layers.count { it.role == 1 } + 1
        addLayer(Layer("Null $n", 0xFFE2574A, 0f, 0f, 1f, 0f).also { it.role = 1 })
    }

    fun addSolid(name: String, color: Long) {
        val n = layers.count { it.role == 0 && it.kind < 0 } + 1
        addLayer(Layer(name.ifBlank { "Solid $n" }, color, 0f, 0f, 1f, 0f).also { it.isSolid = true })
    }

    fun addAdjustment() {
        val n = layers.count { it.role == 2 } + 1
        addLayer(Layer("Adjustment Layer $n", 0xFFFFFFFF, 0f, 0f, 1f, 0f).also { it.role = 2 })
    }

    fun applyEffect(effectId: Int) {
        if (effectId !in 1..16) return
        val targets = selected().filter { it.role != 1 && it.role != 2 }
        if (targets.isEmpty()) return
        mark()
        targets.forEach { it.addEffect(effectId, 0.5f) }
    }

    fun adjustEffectAmount(delta: Float) {
        val targets = selected().filter { it.effectId != 0 }
        if (targets.isEmpty()) return
        mark()
        targets.forEach { layer ->
            if (layer.effects.isEmpty() && layer.effectId in 1..16) layer.effects.add(LayerEffect(layer.effectId, layer.effectAmount))
            val last = layer.effects.lastOrNull()
            if (last != null) {
                val updated = last.copy(amount = (last.amount + delta).coerceIn(0f, 1f))
                layer.effects[layer.effects.lastIndex] = updated
                layer.effectId = updated.id
                layer.effectAmount = updated.amount
            }
        }
    }

    fun clearEffect() {
        val targets = selected().filter { it.effectId != 0 }
        if (targets.isEmpty()) return
        mark()
        targets.forEach { it.effects.clear(); it.effectId = 0; it.effectAmount = 0.5f }
    }

    fun addPenPath(points: List<PathPoint>, color: Long = 0xFF4A90E2, closed: Boolean = true) {
        if (points.size < 2) return
        val n = layers.count { it.pathPoints.isNotEmpty() } + 1
        val l = Layer("Path $n", color, 0f, 0f, 1f, 0f)
        l.pathPoints.addAll(points)
        l.pathClosed = closed
        l.pathBitmap = makePathBitmap(points, closed, color)
        addLayer(l)
    }

    fun addMedia(m: Media) {
        mark()
        val l = Layer(m.name.substringBeforeLast('.'), MCOL[m.kind], 0f, 0f, 1f, 0f)
        l.mid = m.id
        l.kind = m.kind
        val st = if (time >= dur - 0.1f) 0f else time
        val room = dur - st
        l.start = st
        l.len = if (m.kind == 0 || m.dur <= 0f) room else minOf(m.dur, room)
        layers.add(0, l)
        pickLayer(0, false)
    }

    fun dup(i: Int) {
        if (i in layers.indices) {
            mark()
            layers.add(i, layers[i].copy())
            pickLayer(i, false)
        }
    }

    fun del(i: Int) {
        val l = layers.getOrNull(i) ?: return
        mark()
        // children of the deleted layer are re-parented to its own parent
        for (c in layers) if (c.parent == l.uid) c.parent = l.parent
        layers.removeAt(i)
        sel = sel.coerceIn(0, maxOf(layers.size - 1, 0))
    }

    fun reorder(i: Int, to: Int) {
        if (i !in layers.indices) return
        val j = to.coerceIn(0, layers.size - 1)
        if (i == j) return
        mark()
        val l = layers.removeAt(i)
        layers.add(j, l)
        pickLayer(j, false)
    }

    fun split(i: Int) {
        val l = layers.getOrNull(i) ?: return
        val e = l.end(dur)
        if (time <= l.start + 0.05f || time >= e - 0.05f) return
        mark()
        val n = l.copy(l.name)
        n.off = l.off + (time - l.start)
        n.start = time
        n.len = e - time
        l.len = time - l.start
        layers.add(i, n)
        pickLayer(i, false)
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

    // ---- selection (multi-select layers, keyframe selection) ----
    fun pickLayer(i: Int, multi: Boolean) {
        val l = layers.getOrNull(i) ?: return
        if (multi) {
            l.pick = !l.pick
            if (l.pick) {
                sel = i
            } else if (sel == i) {
                val j = layers.indexOfFirst { it.pick }
                if (j >= 0) sel = j
            }
        } else {
            for (q in layers) q.pick = false
            l.pick = true
            sel = i
        }
    }

    fun isSel(i: Int): Boolean {
        val l = layers.getOrNull(i) ?: return false
        return l.pick || (i == sel && layers.none { it.pick })
    }

    fun selected(): List<Layer> {
        val p = layers.filter { it.pick }
        return if (p.isNotEmpty()) p else listOfNotNull(layers.getOrNull(sel))
    }

    fun selectAllLayers() {
        for (q in layers) q.pick = true
    }

    fun delSel() {
        val s = selected()
        if (s.isEmpty()) return
        mark()
        for (l in s) for (c in layers) if (c.parent == l.uid) c.parent = l.parent
        layers.removeAll { x -> s.any { it === x } }
        fixParents()
        sel = sel.coerceIn(0, maxOf(layers.size - 1, 0))
        layers.getOrNull(sel)?.pick = true
    }

    fun dupSel() {
        val s = selected()
        if (s.isEmpty()) return
        mark()
        val idx = s.map { layers.indexOf(it) }.filter { it >= 0 }.sortedDescending()
        for (i in idx) {
            val o = layers[i]
            val n = o.copy()
            layers.add(i, n)
            o.pick = false
            n.pick = true
            sel = i
        }
    }

    fun parentSel(p: Layer?) {
        val s = selected()
        var did = false
        for (l in s) if (canParent(l, p)) {
            if (!did) mark()
            did = true
            l.parent = p?.uid ?: ""
        }
    }

    fun moveBars(ls: List<Layer>, dt: Float) {
        var lo = -Float.MAX_VALUE
        var hi = Float.MAX_VALUE
        for (l in ls) {
            lo = maxOf(lo, -l.start)
            hi = minOf(hi, dur - l.end(dur))
        }
        if (lo > hi) return
        val d = dt.coerceIn(lo, hi)
        for (l in ls) l.start += d
    }

    fun addMarker() {
        val t = (Math.round(time * fps) / fps.toFloat()).coerceIn(0f, dur)
        if (markers.any { abs(it.time - t) < 0.5f / fps }) return
        mark()
        markers.add(CompMarker(t, "Marker ${markers.size + 1}"))
        markers.sortBy { it.time }
    }

    fun refs(l: Layer): List<PRef> {
        val r = ArrayList<PRef>()
        for (i in 0 until NCH) r.add(PRef("p$i", PN[i], l.pr[i]))
        return r
    }

    fun clearKeys() {
        for (l in layers) for (r in refs(l)) r.p.selNone()
    }

    fun selectAllKeys() {
        for (l in selected()) for (r in refs(l)) r.p.selAll()
    }

    fun nSelKeys(): Int {
        var n = 0
        for (l in layers) for (r in refs(l)) n += r.p.k.count { it.sel }
        return n
    }

    fun delKeys() {
        if (nSelKeys() == 0) return
        mark()
        for (l in layers) for (r in refs(l)) r.p.delSel()
    }

    fun easeKeys(m: Int) {
        if (nSelKeys() == 0) return
        mark()
        for (l in layers) for (r in refs(l)) {
            val p = r.p
            for (i in 0 until p.k.size) if (p.k[i].sel) p.interp(i, m)
        }
    }

    fun copyKeys(): Int {
        val es = ArrayList<Pair<String, Kf>>()
        for (l in layers) for (r in refs(l)) for (f in r.p.k) if (f.sel) es.add(r.id to f)
        if (es.isEmpty()) return 0
        val t0 = es.minOf { it.second.t }
        KClip.items = es.map { KEntry(it.first, it.second.t - t0, it.second.copy(sel = false)) }
        return es.size
    }

    // Pastes at the playhead onto the matching properties of the selected layers.
    fun pasteKeys(): Int {
        val items = KClip.items
        val ts = selected()
        if (items.isEmpty() || ts.isEmpty()) return 0
        mark()
        clearKeys()
        val e = eps()
        var n = 0
        for (l in ts) {
            val rs = refs(l)
            for (en in items) {
                val r = rs.firstOrNull { it.id == en.id } ?: continue
                val t = time + en.rel
                if (t > dur + e) continue
                r.p.put(en.kf.copy(t = t, sel = true), e)
                n++
            }
        }
        return n
    }

    fun beginKeyMove(): List<KSnap> {
        val r = ArrayList<KSnap>()
        for (l in layers) for (x in refs(l)) if (x.p.hasSel()) r.add(KSnap(x.p, x.p.k.toList()))
        return r
    }

    // Moves the selected keyframes of every snapshot by dt seconds (frame-snapped) and dv(prop) in stored units.
    fun applyKeyMove(s: List<KSnap>, dt: Float, dv: (Prop) -> Float) {
        val e = eps()
        for (q in s) {
            val moved = ArrayList<Kf>()
            val keep = ArrayList<Kf>()
            val d = dv(q.p)
            for (f in q.orig) {
                if (f.sel) {
                    val nt = (Math.round((f.t + dt) * fps) / fps.toFloat()).coerceIn(0f, dur)
                    moved.add(f.copy(t = nt, v = f.v + d))
                } else {
                    keep.add(f)
                }
            }
            keep.removeAll { u -> moved.any { abs(it.t - u.t) <= e } }
            val all = ArrayList<Kf>()
            all.addAll(keep)
            all.addAll(moved)
            all.sortBy { it.t }
            q.p.k.clear()
            q.p.k.addAll(all)
        }
    }

    fun togglePlay() {
        if (!playing && time >= dur - 0.001f) time = 0f
        playing = !playing
    }

    fun stepFrames(n: Int) {
        time = ((time * fps).let { Math.round(it) } + n).coerceIn(0, (dur * fps).toInt()) / fps.toFloat()
    }

    // ---- parenting ----
    fun layerOf(uid: String): Layer? = if (uid.isEmpty()) null else layers.firstOrNull { it.uid == uid }

    fun canParent(c: Layer, p: Layer?): Boolean {
        if (p == null) return true
        if (layers.none { it === p }) return false
        var q: Layer? = p
        var n = 0
        while (q != null && n < 10000) {
            if (q === c) return false
            q = layerOf(q.parent)
            n++
        }
        return n < 10000
    }

    fun setParent(c: Layer, p: Layer?): Boolean {
        if (!canParent(c, p)) return false
        mark()
        c.parent = p?.uid ?: ""
        return true
    }

    // new null object that becomes the parent of layer i
    fun nullParent(i: Int) {
        val l = layers.getOrNull(i) ?: return
        mark()
        val n = layers.count { it.role == 1 } + 1
        val nl = Layer("Null $n", 0xFFE2574A, 0f, 0f, 1f, 0f)
        nl.role = 1
        nl.len = dur
        nl.parent = l.parent
        layers.add(i, nl)
        l.parent = nl.uid
        pickLayer(i + 1, false)
    }

    // drop references to missing layers and break cycles (after load)
    fun fixParents() {
        for (l in layers) if (l.parent.isNotEmpty() && layerOf(l.parent) == null) l.parent = ""
        for (l in layers) {
            var q: Layer? = l
            var n = 0
            while (q != null && n <= layers.size) {
                q = layerOf(q.parent)
                n++
            }
            if (q != null) l.parent = ""
        }
    }

    private fun world(l: Layer, t: Float, asp: Float, memo: HashMap<Layer, Aff>, depth: Int): Aff? {
        val h = memo[l]
        if (h != null) return h
        if (depth > 64) return null
        val par = layerOf(l.parent)
        var pw: Aff? = null
        if (par != null && par !== l) {
            pw = world(par, t, asp, memo, depth + 1)
            if (pw == null) return null
        }
        val position = l.position(t)
        var tx = position.first * asp
        var ty = position.second
        var sx = l.gv(SX, t)
        var sy = l.gv(SY, t)
        val r = Math.toRadians(l.gv(RT, t).toDouble())
        val cs = cos(r).toFloat()
        val sn = sin(r).toFloat()
        val rx = Math.toRadians(l.gv(RX, t).toDouble())
        val ry = Math.toRadians(l.gv(RY, t).toDouble())
        var a = sx
        var b = 0f
        var c = 0f
        var d = sy
        if (l.is3d) {
            // Orthographic projection of an Euler-rotated layer plane with depth perspective.
            val depth = 1f + l.gv(PZ, t)
            if (depth <= 0.05f) return null
            val persp = 1f / depth
            val cx = cos(rx).toFloat()
            val sxr = sin(rx).toFloat()
            val cy = cos(ry).toFloat()
            val syr = sin(ry).toFloat()
            a = sx * cy * persp
            b = sx * sxr * syr * persp
            c = -sy * sxr * syr * persp
            d = sy * cx * persp
            tx *= persp
            ty *= persp
        }
        val loc = Aff(1f, 0f, 0f, 1f, tx, ty)
            .mul(Aff(cs, sn, -sn, cs, 0f, 0f))
            .mul(Aff(a, b, c, d, 0f, 0f))
            .mul(Aff(1f, 0f, 0f, 1f, -l.gv(AX, t), -l.gv(AY, t)))
        val m = if (pw != null) pw.mul(loc) else loc
        memo[l] = m
        return m
    }

    fun frame(): Frame {
        val currentTime = time
        if (playing || layers.any { it.preCompId.isNotEmpty() }) {
            return frameAt(currentTime, 0, emptySet(), playing)
        }
        val key = java.lang.Float.floatToIntBits(currentTime)
        val cached = pausedFrames[key]
        if (cached != null && cached.first == renderRevision) return cached.second
        val rendered = frameAt(currentTime, 0, emptySet(), false)
        pausedFrames[key] = renderRevision to rendered
        while (pausedFrames.size > 4) {
            val oldest = pausedFrames.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            } else break
        }
        return rendered
    }

    private fun makePathBitmap(points: List<PathPoint>, closed: Boolean, color: Long): Bitmap {
        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val path = AndroidPath()
        val first = points.first()
        path.moveTo(first.x.coerceIn(0f, 1f) * 1024f, first.y.coerceIn(0f, 1f) * 1024f)
        for (pt in points.drop(1)) path.lineTo(pt.x.coerceIn(0f, 1f) * 1024f, pt.y.coerceIn(0f, 1f) * 1024f)
        if (closed) path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.argb(((color shr 24) and 255L).toInt(), ((color shr 16) and 255L).toInt(), ((color shr 8) and 255L).toInt(), (color and 255L).toInt())
            style = if (closed) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, paint)
        return bmp
    }

    private fun frameAt(t: Float, depth: Int, chain: Set<String>, renderPlaying: Boolean): Frame {
        if (depth > 8 || id in chain) return Frame(FloatArray(0), emptyArray())
        val nextChain = chain + id
        val n = layers.size
        var out = FloatArray(maxOf(n, 4) * STR)
        var bitmaps = arrayOfNulls<Bitmap>(maxOf(n, 4))
        var count = 0
        fun ensureCapacity() {
            if (count >= bitmaps.size) {
                out = out.copyOf(out.size * 2)
                bitmaps = bitmaps.copyOf(bitmaps.size * 2)
            }
        }
        fun append(src: FloatArray, offset: Int, bm: Bitmap?) {
            ensureCapacity()
            System.arraycopy(src, offset, out, count * STR, STR)
            bitmaps[count] = bm
            count++
        }
        fun appendNested(data: FloatArray, offset: Int, fit: Aff, parent: Aff, opacity: Float, bm: Bitmap?) {
            ensureCapacity()
            val childWorld = Aff(
                data[offset], data[offset + 1], data[offset + 2], data[offset + 3],
                data[offset + 4], data[offset + 5]
            )
            val m = parent.mul(fit.mul(childWorld))
            val o = count * STR
            out[o] = m.a
            out[o + 1] = m.b
            out[o + 2] = m.c
            out[o + 3] = m.d
            out[o + 4] = m.tx
            out[o + 5] = m.ty
            out[o + 6] = data[offset + 6]
            out[o + 7] = data[offset + 7]
            out[o + 8] = data[offset + 8]
            out[o + 9] = data[offset + 9] * opacity
            out[o + 10] = data[offset + 10]
            out[o + 11] = data[offset + 11]
            out[o + 12] = data[offset + 12]
            out[o + 13] = data[offset + 13]
            for (k in 14 until STR) out[o + k] = data[offset + k]
            bitmaps[count] = bm
            count++
        }

        val asp = w.toFloat() / h
        val memo = HashMap<Layer, Aff>()
        val solo = layers.any { it.solo }
        for (i in layers.size - 1 downTo 0) {
            val l = layers[i]
            if (l.role == 1 || l.role == 2 || !l.vis || l.kind == 2 || t < l.start || t > l.end(dur)) continue
            if (solo && !l.solo) continue
            val op = l.gv(OP, t).coerceIn(0f, 1f)
            val parentWorld = world(l, t, asp, memo, 0) ?: continue

            // A pre-comp is rendered by flattening its child frame into this frame.
            // No intermediate bitmap is required, so child footage remains GPU-textured.
            if (l.preCompId.isNotEmpty()) {
                val nested = proj?.comps?.firstOrNull { it.id == l.preCompId } ?: continue
                if (nested.id in nextChain) continue
                val nestedTime = if (l.timeRemapEnabled) l.timeRemap.get(t) else t - l.start + l.off
                if (nestedTime < 0f || nestedTime > nested.dur) continue
                val child = nested.frameAt(nestedTime, depth + 1, nextChain, renderPlaying)
                val ratio = asp / (nested.w.toFloat() / nested.h)
                val fit = Aff(ratio, 0f, 0f, 1f, 0f, 0f)
                for (j in child.b.indices) {
                    appendNested(child.d, j * STR, fit, parentWorld, op, child.b[j])
                }
                continue
            }

            var ex = 0.3f
            var ey = 0.3f
            var bm: Bitmap? = null
            if (l.pathPoints.isNotEmpty()) {
                bm = l.pathBitmap ?: makePathBitmap(l.pathPoints.toList(), l.pathClosed, l.col).also { l.pathBitmap = it }
                ex = 1f
                ey = 1f
            } else if (l.isSolid) {
                ex = 1f
                ey = 1f
            } else if (l.kind >= 0) {
                val media = lib.firstOrNull { it.id == l.mid } ?: continue
                val sourceTime = if (l.timeRemapEnabled) l.timeRemap.get(t) else t - l.start + l.off
                bm = Frames.get(media, sourceTime, renderPlaying) ?: continue
                val ma = bm.width.toFloat() / bm.height
                if (ma >= asp) {
                    ex = asp
                    ey = asp / ma
                } else {
                    ex = ma
                    ey = 1f
                }
            }
            if (bm != null && l.maskEnabled) bm = l.applyMask(bm)
            val item = FloatArray(STR)
            item[0] = parentWorld.a
            item[1] = parentWorld.b
            item[2] = parentWorld.c
            item[3] = parentWorld.d
            item[4] = parentWorld.tx
            item[5] = parentWorld.ty
            if (bm != null) {
                item[6] = 1f
                item[7] = 1f
                item[8] = 1f
            } else {
                item[6] = ((l.col shr 16) and 255L) / 255f
                item[7] = ((l.col shr 8) and 255L) / 255f
                item[8] = (l.col and 255L) / 255f
            }
            item[9] = op
            item[10] = ex
            item[11] = ey
            val stack = l.renderEffects()
            for (j in 0 until MAX_LAYER_EFFECTS) {
                val effect = stack.getOrNull(j)
                item[14 + j] = effect?.id?.toFloat() ?: 0f
                item[14 + MAX_LAYER_EFFECTS + j] = effect?.amount ?: 0f
            }
            item[12] = stack.firstOrNull()?.id?.toFloat() ?: 0f
            item[13] = stack.firstOrNull()?.amount ?: 0f
            append(item, 0, bm)
        }
        return Frame(out.copyOf(count * STR), bitmaps.copyOf(count))
    }

    // Move selected layers into a new composition and replace them with one pre-comp layer.
    // Refuse cross-boundary parenting rather than silently changing the animation.
    fun precomposeSelected(name: String): Boolean {
        val p = proj ?: return false
        val chosen = selected().filter { layers.contains(it) }
        if (chosen.isEmpty()) return false
        val chosenIds = chosen.map { it.uid }.toSet()
        if (chosen.any { it.parent.isNotEmpty() && it.parent !in chosenIds }) return false
        if (layers.any { it.uid !in chosenIds && it.parent in chosenIds }) return false

        val at = chosen.map { layers.indexOf(it) }.minOrNull() ?: return false
        mark()
        val child = Comp(name, w, h, fps, dur)
        child.layers.addAll(chosen)
        for (l in chosen) layers.remove(l)
        child.fixParents()
        p.addComp(child)

        val pre = Layer(name, 0xFFB0B0B0, 0f, 0f, 1f, 0f)
        pre.preCompId = child.id
        pre.len = dur
        layers.add(at.coerceIn(0, layers.size), pre)
        pickLayer(layers.indexOf(pre), false)
        return true
    }

    fun js(): JSONObject {
        val o = JSONObject()
        o.put("ci", id)
        o.put("n", name)
        o.put("w", w)
        o.put("h", h)
        o.put("f", fps)
        o.put("d", dur.toDouble())
        val a = JSONArray()
        for (l in layers) a.put(l.js())
        o.put("l", a)
        val ma = JSONArray()
        for (marker in markers) {
            ma.put(JSONObject().put("t", marker.time.toDouble()).put("n", marker.label).put("c", marker.color))
        }
        o.put("mk", ma)
        return o
    }

    companion object {
        fun fromJs(o: JSONObject): Comp {
            val c = Comp(o.getString("n"), o.getInt("w"), o.getInt("h"), o.getInt("f"), o.getDouble("d").toFloat())
            c.id = o.optString("ci", c.id)
            val ma = o.optJSONArray("mk")
            if (ma != null) for (i in 0 until ma.length()) {
                val marker = ma.getJSONObject(i)
                c.markers.add(CompMarker(marker.optDouble("t", 0.0).toFloat(), marker.optString("n", "Marker"), marker.optLong("c", 0xFFFFC857)))
            }
            val a = o.getJSONArray("l")
            for (i in 0 until a.length()) {
                val l = Layer.fromJs(a.getJSONObject(i))
                l.len = minOf(l.len, c.dur)
                c.layers.add(l)
            }
            c.fixParents()
            return c
        }
    }
}

class Project(
    val id: String,
    var name: String,
    var mod: Long,
    val comps: SnapshotStateList<Comp> = mutableStateListOf(),
    val media: SnapshotStateList<Media> = mutableStateListOf()
) {
    private val ust = ArrayList<String>()
    private val rst = ArrayList<String>()

    init {
        comps.forEach {
            it.lib = media
            it.proj = this
        }
    }

    fun addComp(c: Comp) {
        c.lib = media
        c.proj = this
        comps.add(c)
    }

    fun snap(): String {
        val a = JSONArray()
        for (c in comps) a.put(c.js())
        return a.toString()
    }

    fun mark() {
        val s = snap()
        if (ust.isEmpty() || ust[ust.size - 1] != s) {
            ust.add(s)
            if (ust.size > 50) ust.removeAt(0)
        }
        rst.clear()
    }

    fun clearUndo() {
        ust.clear()
        rst.clear()
    }

    private fun restore(s: String) {
        val a = JSONArray(s)
        val old = comps.toList()
        val n = ArrayList<Comp>()
        for (i in 0 until a.length()) {
            val c = Comp.fromJs(a.getJSONObject(i))
            val o = old.firstOrNull { it.id == c.id }
            if (o != null) {
                c.time = o.time.coerceIn(0f, c.dur)
                c.sel = o.sel.coerceIn(0, maxOf(c.layers.size - 1, 0))
                c.loop = o.loop
            }
            c.lib = media
            c.proj = this
            n.add(c)
        }
        old.forEach { it.playing = false }
        comps.clear()
        comps.addAll(n)
    }

    fun undo(): Boolean {
        if (ust.isEmpty()) return false
        rst.add(snap())
        restore(ust.removeAt(ust.size - 1))
        return true
    }

    fun redo(): Boolean {
        if (rst.isEmpty()) return false
        ust.add(snap())
        restore(rst.removeAt(rst.size - 1))
        return true
    }

    fun rm(m: Media) {
        comps.forEach { c ->
            c.layers.removeAll { it.mid == m.id }
            c.sel = c.sel.coerceIn(0, maxOf(c.layers.size - 1, 0))
        }
        media.remove(m)
        clearUndo() // the file is deleted, so earlier states can no longer be restored
        Frames.drop(m.id)
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
            val l = mutableStateListOf<Comp>()
            val a = o.getJSONArray("c")
            for (i in 0 until a.length()) l.add(Comp.fromJs(a.getJSONObject(i)))
            val ms = mutableStateListOf<Media>()
            val b = o.optJSONArray("md")
            if (b != null) for (i in 0 until b.length()) ms.add(Media.fromJs(b.getJSONObject(i)))
            return Project(o.getString("id"), o.getString("n"), o.getLong("m"), l, ms)
        }

        // A new project is completely empty: no comps, layers, media, keyframes or effects.
        fun empty(name: String = "Untitled Project") = Project(UUID.randomUUID().toString(), name, System.currentTimeMillis())
    }
}
