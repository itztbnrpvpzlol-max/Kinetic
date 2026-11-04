package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

fun lab(t: Float): String {
    val s = t.toInt()
    return if (t == s.toFloat()) (if (s >= 60) "%d:%02ds".format(s / 60, s % 60) else "%02ds".format(s)) else "%.1fs".format(t)
}

private val RS = TextStyle(color = Color(0xFF9A9A9A), fontSize = 9.sp)
private val LS = TextStyle(color = Color(0xFFF0F0F0), fontSize = 10.sp)
private val GTS = TextStyle(color = Color(0xFF9A9A9A), fontSize = 8.sp)
private val STP = floatArrayOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f, 600f)

val RH = 24.dp
val LEFT = 400.dp
private val VALW = 172.dp

class Lt(private val tm: TextMeasurer) {
    private val m = HashMap<String, TextLayoutResult>()

    fun get(k: String, t: String, s: TextStyle): TextLayoutResult {
        var r = m[k]
        if (r == null) {
            if (m.size > 256) m.clear()
            r = tm.measure(AnnotatedString(t), s)
            m[k] = r
        }
        return r
    }
}

// ---------------------------------------------------------------- flat row model (left and right panes share it)
sealed class TR {
    abstract val l: Layer
}

class TLayer(override val l: Layer, val i: Int) : TR()
class TGroup(override val l: Layer, val title: String, val kind: Int, val ind: Int = 1) : TR()
class TProp(
    override val l: Layer, val name: String, val ps: List<Prop>, val unit: String, val sens: Float, val lo: Float, val hi: Float,
    val toD: (Int, Float) -> Float, val set: (Int, Float) -> Unit, val reset: (Int) -> Unit, val rot: Boolean, val ind: Int
) : TR()

private fun tp(comp: Comp, l: Layer, d: PDef, ind: Int): TProp = TProp(
    l, d.name, d.ch.map { l.pr[it] }, d.unit, d.sens, d.lo, d.hi,
    { i, v -> comp.toD(d.ch[i], v) }, { i, dv -> comp.setD(l, d.ch[i], dv) }, { i -> comp.resetD(l, d.ch[i]) }, d === D_ROT, ind
)

fun buildRows(comp: Comp, shy: Boolean, q: String): List<TR> {
    val r = ArrayList<TR>()
    comp.layers.forEachIndexed { i, l ->
        if (shy && l.shy) return@forEachIndexed
        if (q.isNotBlank() && !l.name.contains(q, true)) return@forEachIndexed
        r.add(TLayer(l, i))
        if (!l.open) return@forEachIndexed
        val defs = tdefs(l)
        if (l.animOnly) {
            for (d in defs) if (d.ch.any { l.pr[it].anim }) r.add(tp(comp, l, d, 1))
        } else {
            r.add(TGroup(l, "Transform", 0))
            if (l.tOpen) for (d in defs) r.add(tp(comp, l, d, 2))
        }
    }
    return r
}

fun parentItems(comp: Comp, l: Layer): List<Pair<String, () -> Unit>> {
    val items = ArrayList<Pair<String, () -> Unit>>()
    items.add("None" to { comp.setParent(l, null); Unit })
    comp.layers.forEachIndexed { j, q ->
        if (q !== l && comp.canParent(l, q)) items.add("${j + 1}. ${q.name}" to { comp.setParent(l, q); Unit })
    }
    return items
}

private fun rotTxt(v: Float): String {
    val rev = (v / 360f).toInt()
    val rem = v - rev * 360f
    return "%dx%+.1f\u00B0".format(rev, rem)
}

private fun Modifier.rowLine(): Modifier = this.drawBehind {
    drawLine(Color(0xFF1A1A1A), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
}

// ---------------------------------------------------------------- left side
@Composable
fun LLayer(comp: Comp, l: Layer, i: Int, c: Cur, modes: Boolean, multi: Boolean, onFoc: () -> Unit) {
    var rn by remember(l) { mutableStateOf(false) }
    val s = comp.isSel(i)
    val menu = mnu(
        c,
        "Rename" to { rn = true },
        "Open Pre-comp" to { if (l.preCompId.isNotEmpty()) comp.onOpenComp?.invoke(l.preCompId) },
        "Duplicate" to { comp.dupSel() },
        "Split at playhead" to { comp.split(i) },
        "Move up" to { comp.reorder(i, i - 1) },
        "Move down" to { comp.reorder(i, i + 1) },
        "Bring to front" to { comp.reorder(i, 0) },
        "Send to back" to { comp.reorder(i, comp.layers.size - 1) },
        "Parent to new null" to { comp.nullParent(i) },
        "Select all layers" to { comp.selectAllLayers() },
        "Delete" to { comp.delSel() }
    )
    Row(
        Modifier.fillMaxWidth().height(RH).background(if (s) SR else Color.Transparent).rowLine()
            .cur(c, rc = { p ->
                if (!l.pick) comp.pickLayer(i, false)
                menu(p)
            }) {
                comp.pickLayer(i, multi)
                onFoc()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(17.dp)) { if (l.kind != 2) Ix(0, l.vis, c, 17.dp) { l.vis = !l.vis } }
        Box(Modifier.width(17.dp)) { if (l.kind >= 1) Ix(1, !l.mute, c, 17.dp) { l.mute = !l.mute } }
        Ix(2, l.solo, c, 17.dp) { l.solo = !l.solo }
        Ix(3, l.lock, c, 17.dp) { l.lock = !l.lock }
        Box(
            Modifier.width(14.dp).fillMaxHeight().cur(c) {
                val ix = LABELS.indexOf(l.lcol)
                l.lcol = LABELS[(ix + 1) % LABELS.size]
            },
            Alignment.Center
        ) { Box(Modifier.size(9.dp).background(Color(l.lcol))) }
        Text((i + 1).toString(), color = DIM, fontSize = AE_FONT, modifier = Modifier.width(16.dp))
        Ix(if (l.open) 9 else 8, true, c, 14.dp) {
            l.open = !l.open
            if (l.open) l.animOnly = false
        }
        Canvas(Modifier.size(14.dp, 14.dp)) {
            val u = 1.dp.toPx()
            val col = Color(0xFFC8C8C8)
            when {
                l.role == 1 -> drawRect(col, Offset(2 * u, 2 * u), Size(10 * u, 10 * u), style = Stroke(1.2f * u))
                l.preCompId.isNotEmpty() -> {
                    drawRect(col, Offset(1 * u, 3 * u), Size(12 * u, 9 * u), style = Stroke(1.1f * u))
                    drawRect(col, Offset(4 * u, 1 * u), Size(8 * u, 8 * u), style = Stroke(1.1f * u))
                }
                l.kind >= 0 -> {
                    drawRect(Color(l.col), Offset(1 * u, 3 * u), Size(12 * u, 8 * u))
                    drawRect(col, Offset(1 * u, 3 * u), Size(12 * u, 8 * u), style = Stroke(1f * u))
                }
                else -> drawPath(starPath(7 * u, 7.5f * u, 6 * u, 2.6f * u), col)
            }
        }
        Spacer(Modifier.width(3.dp))
        if (rn) {
            Edt(l.name, Modifier.weight(1f).background(Color(0xFF1A1A1A)).padding(2.dp), false) { s2 ->
                rn = false
                val n = s2.trim()
                if (n.isNotEmpty() && n != l.name) {
                    comp.mark()
                    l.name = n
                }
            }
        } else {
            Text(l.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(Modifier.width(88.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!modes) {
                Ix(4, l.shy, c, 22.dp) { l.shy = !l.shy }
                if (l.role != 2) Box(
                    Modifier.size(22.dp, 24.dp).cur(c) {
                        comp.mark()
                        l.is3d = !l.is3d
                    },
                    Alignment.Center
                ) { Text("3D", color = if (l.is3d) VB else Color(0xFF555555), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            } else {
                Text("Normal", color = TXT, fontSize = 9.sp, modifier = Modifier.width(44.dp), maxLines = 1)
                Text("T", color = DIM, fontSize = 9.sp, modifier = Modifier.width(14.dp))
                Text("None", color = TXT, fontSize = 9.sp, maxLines = 1)
            }
        }
        Row(Modifier.width(84.dp).padding(end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Ix(5, true, c, 16.dp) {}
            Dd(comp.layerOf(l.parent)?.name ?: "None", c, Modifier.weight(1f)) { parentItems(comp, l) }
        }
    }
}

@Composable
fun LGroup(comp: Comp, r: TGroup, c: Cur) {
    val l = r.l
    Row(Modifier.fillMaxWidth().height(RH).rowLine(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(68.dp))
        Spacer(Modifier.width((r.ind * 10).dp))
        Ix(if (l.tOpen) 9 else 8, true, c, 14.dp) { l.tOpen = !l.tOpen }
        Text(r.title, color = TXT, fontSize = AE_FONT, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.width(VALW).padding(start = 2.dp)) {
            Text(
                "Reset", color = VB, fontSize = AE_FONT,
                modifier = Modifier.cur(c) {
                    comp.mark()
                    for (d in tdefs(l)) for (ch in d.ch) comp.resetD(l, ch)
                }.padding(end = 6.dp)
            )
        }
    }
}

@Composable
fun LProp(comp: Comp, r: TProp, c: Cur, graph: Boolean, multi: Boolean) {
    val t = comp.uiTime
    val e = comp.eps()
    val ps = r.ps
    val anim = ps.all { it.anim }
    val has = ps.any { it.idx(t, e) >= 0 }
    val fm: ((Float) -> String)? = if (r.rot) ::rotTxt else null
    Row(Modifier.fillMaxWidth().height(RH).rowLine(), verticalAlignment = Alignment.CenterVertically) {
        // keyframe navigation, as in After Effects
        Row(Modifier.width(68.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ps.any { it.anim }) {
                Ix(10, true, c, 20.dp) {
                    val x = ps.mapNotNull { it.prev(t, e) }.maxOrNull()
                    if (x != null) comp.time = x
                }
                Canvas(
                    Modifier.size(22.dp, 24.dp).cur(c) {
                        comp.mark()
                        ps.forEach {
                            val i = it.idx(t, e)
                            if ((has && i >= 0) || (!has && i < 0)) it.toggle(t, e)
                        }
                    }
                ) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val rr = 4.dp.toPx()
                    if (has) {
                        kfDraw(cx, cy, 0, false, rr)
                    } else {
                        rotate(45f, Offset(cx, cy)) {
                            drawRect(Color(0xFF8A8A8A), Offset(cx - rr * 0.72f, cy - rr * 0.72f), Size(rr * 1.44f, rr * 1.44f), style = Stroke(1.2f))
                        }
                    }
                }
                Ix(11, true, c, 20.dp) {
                    val x = ps.mapNotNull { it.next(t, e) }.minOrNull()
                    if (x != null) comp.time = x
                }
            }
        }
        Spacer(Modifier.width((r.ind * 10).dp))
        if (graph && anim) Ix(7, ps.all { it.gr }, c, 14.dp) {
            val b = !ps.all { it.gr }
            ps.forEach { it.gr = b }
        }
        Stw(anim, c) {
            comp.mark()
            ps.forEach { if (it.anim == anim) it.watch(t) }
        }
        Text(
            r.name, color = TXT, fontSize = AE_FONT, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).cur(c) {
                if (!multi) comp.clearKeys()
                ps.forEach { it.selAll() }
            }
        )
        if (r.name == "Scale") {
            Canvas(
                Modifier.size(22.dp, 24.dp).cur(c) {
                    comp.mark()
                    r.l.link = !r.l.link
                }
            ) {
                val col = if (r.l.link) VB else Color(0xFF777777)
                val w = 8.dp.toPx()
                val h = 5.dp.toPx()
                rotate(-45f, Offset(size.width / 2, size.height / 2)) {
                    drawRoundRect(
                        col,
                        Offset(size.width / 2 - w * 0.9f, size.height / 2 - h / 2),
                        Size(w, h),
                        androidx.compose.ui.geometry.CornerRadius(h / 2),
                        style = Stroke(1.4.dp.toPx())
                    )
                    drawRoundRect(
                        col,
                        Offset(size.width / 2 - w * 0.1f, size.height / 2 - h / 2),
                        Size(w, h),
                        androidx.compose.ui.geometry.CornerRadius(h / 2),
                        style = Stroke(1.4.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.width(VALW), verticalAlignment = Alignment.CenterVertically) {
            ps.forEachIndexed { i, p ->
                if (i > 0) Text(",", color = VB, fontSize = AE_FONT)
                NumField(
                    c, r.toD(i, p.get(t)), if (i == ps.size - 1) r.unit else "", r.sens, r.lo, r.hi, Modifier,
                    onBegin = { comp.mark() }, onSet = { d -> r.set(i, d) }, onReset = { r.reset(i) }, fmt = fm
                )
            }
        }
    }
}

// ---------------------------------------------------------------- right side (tracks)
@Composable
fun TrackLayer(
    comp: Comp, l: Layer, i: Int, c: Cur, multi: Boolean,
    xp: (Float) -> Float, xt: (Float) -> Float, lt: Lt, onFoc: () -> Unit
) {
    val d = comp.dur
    val gs = remember(l) { floatArrayOf(-1f, 0f, 0f) }
    val sl = comp.isSel(i)
    val grab = { o: Offset ->
        if (multi) comp.pickLayer(i, true) else if (!l.pick) comp.pickLayer(i, false) else comp.sel = i
        onFoc()
        val a = xp(l.start)
        val b = xp(l.end(d))
        gs[0] = when {
            l.lock -> -1f
            abs(o.x - a) < 16f -> 1f
            abs(o.x - b) < 16f -> 2f
            o.x >= a && o.x <= b -> 0f
            else -> -1f
        }
        gs[1] = o.x - a
        gs[2] = 0f
        Unit
    }
    Canvas(
        Modifier.fillMaxWidth().height(RH).background(if (sl) SR.copy(alpha = 0.35f) else Color.Transparent).rowLine()
            .cur(
                c,
                drag = { o ->
                    if (gs[0] >= 0f && gs[2] == 0f) {
                        comp.mark()
                        gs[2] = 1f
                    }
                    when (gs[0].toInt()) {
                        0 -> {
                            val ns = xt(o.x - gs[1])
                            val grp = if (l.pick) comp.layers.filter { it.pick } else listOf(l)
                            comp.moveBars(grp, ns - l.start)
                        }
                        1 -> comp.trim(l, 1, xt(o.x))
                        2 -> comp.trim(l, 2, xt(o.x))
                        else -> {}
                    }
                },
                rc = mnu(
                    c,
                    "Duplicate" to { comp.dupSel() },
                    "Split at playhead" to { comp.split(i) },
                    "Delete" to { comp.delSel() }
                ),
                ds = grab,
                gate = { gs[0] >= 0f }
            ) { o -> grab(o) }
    ) {
        val a = xp(l.start)
        val b = xp(l.end(d))
        val ca = maxOf(a, -8f)
        val cb = minOf(b, size.width + 8f)
        if (cb > ca) {
            val tp = Offset(ca, 2.dp.toPx())
            val sz = Size(maxOf(cb - ca, 2f), size.height - 4.dp.toPx())
            val base = Color(l.lcol)
            drawRoundRect(base.copy(alpha = if (l.lock) 0.3f else if (l.role == 1) 0.35f else 0.6f), tp, sz, CornerRadius(2.dp.toPx()))
            drawRoundRect(base.copy(alpha = 0.95f), tp, sz, CornerRadius(2.dp.toPx()), Stroke(1f))
            if (sl) drawRoundRect(Color.White, tp, sz, CornerRadius(2.dp.toPx()), Stroke(1.5f))
            val l0 = maxOf(a, 0f) + 6f
            val r0 = minOf(b, size.width) - 6f
            if (r0 - l0 > 24f) {
                val tl = lt.get("l" + l.name, l.name, LS)
                clipRect(l0, 0f, r0, size.height) { drawText(tl, topLeft = Offset(l0, 5.dp.toPx())) }
            }
        }
    }
}

@Composable
fun TrackGroup() {
    Box(Modifier.fillMaxWidth().height(RH).rowLine())
}

@Composable
fun TrackProp(
    comp: Comp, r: TProp, c: Cur, multi: Boolean, pps: Float,
    xp: (Float) -> Float, xt: (Float) -> Float
) {
    val st = remember(r.l) { GSt() }
    val grab = { o: Offset ->
        var bp = -1
        var bi = -1
        var bd = 12f
        for (pi in r.ps.indices) for (ki in r.ps[pi].k.indices) {
            val dd = abs(o.x - xp(r.ps[pi].k[ki].t))
            if (dd < bd) {
                bd = dd
                bp = pi
                bi = ki
            }
        }
        st.moved = false
        st.snap = null
        st.sx = o.x
        if (bp >= 0) {
            val p = r.ps[bp]
            if (multi && p.k[bi].sel) {
                p.setSel(bi, false)
                st.mode = -1
            } else {
                if (!p.k[bi].sel && !multi) comp.clearKeys()
                p.setSel(bi, true)
                st.mode = 1
            }
        } else {
            if (!multi) comp.clearKeys()
            st.mode = 2
        }
        Unit
    }
    Canvas(
        Modifier.fillMaxWidth().height(RH).rowLine()
            .cur(
                c,
                drag = { o ->
                    if (st.mode == 1) {
                        if (!st.moved && abs(o.x - st.sx) > 3f) {
                            st.moved = true
                            comp.mark()
                            st.snap = comp.beginKeyMove()
                        }
                        val sn = st.snap
                        if (sn != null) comp.applyKeyMove(sn, (o.x - st.sx) / pps) { 0f }
                    } else if (st.mode == 2) {
                        val x0 = minOf(st.sx, o.x)
                        val x1 = maxOf(st.sx, o.x)
                        comp.clearKeys()
                        for (p in r.ps) for (ki in p.k.indices) {
                            val x = xp(p.k[ki].t)
                            if (x >= x0 && x <= x1) p.setSel(ki, true)
                        }
                    }
                },
                ds = grab,
                gate = { st.mode >= 0 }
            ) { o -> grab(o) }
    ) {
        val y = size.height / 2
        drawLine(Color(0xFF2E2E2E), Offset(0f, y), Offset(size.width, y), 1f)
        for (p in r.ps) {
            for (f in p.k) {
                val x = xp(f.t)
                if (x >= -8f && x <= size.width + 8f) kfDraw(x, y, f.ip, f.sel, 4.5.dp.toPx())
            }
        }
    }
}

class GSt {
    var mode = -1
    var g = 0
    var i = 0
    var side = 0
    var sx = 0f
    var sy = 0f
    var moved = false
    var snap: List<KSnap>? = null
}

// ---------------------------------------------------------------- Graph Editor (value graph)
class GRow(val name: String, val p: Prop, val toD: (Float) -> Float, val fromD: (Float) -> Float, val col: Color)
class GHit(val kind: Int, val g: Int, val i: Int, val side: Int)

private val GPAL = listOf(
    Color(0xFFE0605A), Color(0xFF5FC25F), Color(0xFF5A90E0), Color(0xFFE0C050), Color(0xFFB070E0), Color(0xFF50C8C8)
)

@Composable
fun GraphView(
    comp: Comp, c: Cur, xp: (Float) -> Float, xt: (Float) -> Float, pps: Float, multi: Boolean, lt: Lt, m: Modifier
) {
    val rows = ArrayList<GRow>()
    var ci = 0
    for (l in comp.selected()) {
        val defs = tdefs(l)
        for (d in defs) for (j in d.ch.indices) {
            val ch = d.ch[j]
            val p = l.pr[ch]
            if (p.anim && p.gr) {
                val nm = l.name + "  " + d.name + (if (d.ch.size > 1) " " + d.lb[j] else "")
                rows.add(GRow(nm, p, { v -> comp.toD(ch, v) }, { x -> comp.fromD(ch, x) }, GPAL[ci % GPAL.size]))
                ci++
            }
        }
    }
    var lo = Float.MAX_VALUE
    var hi = -Float.MAX_VALUE
    for (g in rows) for (s in 0..120) {
        val v = g.toD(g.p.get(comp.dur * s / 120f))
        lo = minOf(lo, v)
        hi = maxOf(hi, v)
    }
    if (rows.isEmpty()) {
        lo = 0f
        hi = 1f
    }
    if (hi - lo < 1e-3f) {
        lo -= 1f
        hi += 1f
    }
    val byProp = HashMap<Prop, GRow>()
    for (g in rows) byProp[g.p] = g
    var hh by remember { mutableStateOf(1f) }
    var mq by remember { mutableStateOf<Rect?>(null) }
    var speedMode by remember { mutableStateOf(false) }
    val pad = 10f
    val st = remember { GSt() }
    fun speedAt(g: GRow, t: Float): Float {
        val dt = (1f / comp.fps).coerceAtLeast(0.001f)
        val ta = (t - dt).coerceAtLeast(0f)
        val tb = (t + dt).coerceAtMost(comp.dur)
        if (tb - ta <= 1e-6f) return 0f
        return (g.toD(g.p.get(tb)) - g.toD(g.p.get(ta))) / (tb - ta)
    }
    if (speedMode) {
        lo = Float.MAX_VALUE
        hi = -Float.MAX_VALUE
        for (g in rows) for (sample in 0..160) {
            val v = speedAt(g, comp.dur * sample / 160f)
            lo = minOf(lo, v)
            hi = maxOf(hi, v)
        }
        if (rows.isEmpty()) { lo = -1f; hi = 1f }
        if (hi - lo < 1e-3f) { lo -= 1f; hi += 1f }
    }
    val padv = (hi - lo) * 0.1f
    val gmin = lo - padv
    val gmax = hi + padv
    fun graphValue(g: GRow, t: Float): Float = if (speedMode) speedAt(g, t) else g.toD(g.p.get(t))
    fun yOf(v: Float): Float = hh - pad - (v - gmin) / (gmax - gmin) * (hh - 2 * pad)
    fun vAt(y: Float): Float = gmin + (hh - pad - y) / (hh - 2 * pad) * (gmax - gmin)

    fun hit(o: Offset): GHit? {
        for (gi in rows.indices.reversed()) {
            val g = rows[gi]
            val k = g.p.k
            for (i in k.indices) {
                val f = k[i]
                if (f.sel && f.ip == 1) {
                    if (i + 1 < k.size) {
                        val dtn = k[i + 1].t - f.t
                        val oi = f.oi.coerceIn(0.001f, 1f)
                        val hx = xp(f.t + oi * dtn)
                        val hy = yOf(g.toD(f.v + f.osp * oi * dtn))
                        if (abs(o.x - hx) < 14f && abs(o.y - hy) < 14f) return GHit(1, gi, i, 1)
                    }
                    if (i > 0) {
                        val dtp = f.t - k[i - 1].t
                        val ii = f.ii.coerceIn(0.001f, 1f)
                        val hx = xp(f.t - ii * dtp)
                        val hy = yOf(g.toD(f.v - f.isp * ii * dtp))
                        if (abs(o.x - hx) < 14f && abs(o.y - hy) < 14f) return GHit(1, gi, i, 0)
                    }
                }
            }
        }
        var best: GHit? = null
        var bd = 18f
        for (gi in rows.indices) {
            val g = rows[gi]
            for (i in g.p.k.indices) {
                val f = g.p.k[i]
                val dx = o.x - xp(f.t)
                val dy = o.y - yOf(g.toD(f.v))
                val dd = sqrt(dx * dx + dy * dy)
                if (dd < bd) {
                    bd = dd
                    best = GHit(0, gi, i, 0)
                }
            }
        }
        return best
    }

    val grab = { o: Offset ->
        val h = hit(o)
        st.moved = false
        st.snap = null
        st.sx = o.x
        st.sy = o.y
        if (h == null) {
            if (!multi) comp.clearKeys()
            st.mode = 3
            mq = Rect(o.x, o.y, o.x, o.y)
        } else if (h.kind == 1) {
            st.mode = 2
            st.g = h.g
            st.i = h.i
            st.side = h.side
        } else {
            val p = rows[h.g].p
            if (multi && p.k[h.i].sel) {
                p.setSel(h.i, false)
                st.mode = -1
            } else {
                if (!p.k[h.i].sel && !multi) comp.clearKeys()
                p.setSel(h.i, true)
                st.mode = 1
            }
        }
        Unit
    }

    Column(m) {
        Canvas(
            Modifier.weight(1f).fillMaxWidth().padding(1.dp).onSizeChanged { hh = maxOf(it.height.toFloat(), 1f) }.then(
                if (speedMode) Modifier else Modifier.cur(
                c,
                drag = { o ->
                    if (st.mode == 1) {
                        if (!st.moved && (abs(o.x - st.sx) > 3f || abs(o.y - st.sy) > 3f)) {
                            st.moved = true
                            comp.mark()
                            st.snap = comp.beginKeyMove()
                        }
                        val sn = st.snap
                        if (sn != null) {
                            val dvd = -(o.y - st.sy) * (gmax - gmin) / (hh - 2 * pad)
                            comp.applyKeyMove(sn, (o.x - st.sx) / pps) { p ->
                                val r = byProp[p]
                                if (r == null) 0f else r.fromD(dvd) - r.fromD(0f)
                            }
                        }
                    } else if (st.mode == 2) {
                        val g = rows.getOrNull(st.g)
                        if (g != null && st.i in g.p.k.indices) {
                            if (!st.moved) {
                                st.moved = true
                                comp.mark()
                            }
                            val p = g.p
                            val f0 = p.k[st.i]
                            val f = if (f0.ip == 1) f0 else f0.copy(ip = 1)
                            val hv = g.fromD(vAt(o.y))
                            if (st.side == 1 && st.i + 1 < p.k.size) {
                                val dtn = p.k[st.i + 1].t - f.t
                                val oi = ((xt(o.x) - f.t) / dtn).coerceIn(0.001f, 1f)
                                p.k[st.i] = f.copy(oi = oi, osp = (hv - f.v) / (oi * dtn))
                            } else if (st.side == 0 && st.i > 0) {
                                val dtp = f.t - p.k[st.i - 1].t
                                val ii = ((f.t - xt(o.x)) / dtp).coerceIn(0.001f, 1f)
                                p.k[st.i] = f.copy(ii = ii, isp = (f.v - hv) / (ii * dtp))
                            }
                        }
                    } else if (st.mode == 3) {
                        val r = Rect(minOf(st.sx, o.x), minOf(st.sy, o.y), maxOf(st.sx, o.x), maxOf(st.sy, o.y))
                        mq = r
                        comp.clearKeys()
                        for (g in rows) for (i in g.p.k.indices) {
                            val f = g.p.k[i]
                            val x = xp(f.t)
                            val y = yOf(g.toD(f.v))
                            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) g.p.setSel(i, true)
                        }
                    }
                },
                ds = grab,
                gate = { st.mode >= 0 },
                end = {
                    mq = null
                    st.mode = -1
                }
            ) { o -> grab(o) }
            )
        ) {
            for (n in 0..4) {
                val y = pad + (size.height - 2 * pad) * n / 4f
                drawLine(Color(0xFF2E2E2E), Offset(0f, y), Offset(size.width, y), 1f)
                val v = gmax - (gmax - gmin) * ((y - pad) / (size.height - 2 * pad))
                val tx = "%.0f".format(v)
                drawText(lt.get("gy$tx", tx, GTS), topLeft = Offset(3f, y + 1f))
            }
            val x0 = xp(0f)
            val x1 = xp(comp.dur)
            drawLine(Color(0xFF3A3A3A), Offset(x0, 0f), Offset(x0, size.height), 1f)
            drawLine(Color(0xFF3A3A3A), Offset(x1, 0f), Offset(x1, size.height), 1f)
            rows.forEachIndexed { gi, g ->
                val path = Path()
                var x = 0f
                var first = true
                while (x <= size.width) {
                    val y = yOf(graphValue(g, xt(x)))
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += 3f
                }
                drawPath(path, g.col, style = Stroke(1.6f))
                drawCircle(g.col, 3f, Offset(46f, 14f + gi * 12f))
                drawText(lt.get("gn" + g.name, g.name, GTS), topLeft = Offset(54f, 8f + gi * 12f))
                val k = g.p.k
                for (i in k.indices) {
                    val f = k[i]
                    val kx = xp(f.t)
                    val ky = yOf(if (speedMode) speedAt(g, f.t) else g.toD(f.v))
                    if (!speedMode && f.sel && f.ip == 1) {
                        if (i + 1 < k.size) {
                            val dtn = k[i + 1].t - f.t
                            val oi = f.oi.coerceIn(0.001f, 1f)
                            val hx = xp(f.t + oi * dtn)
                            val hy = yOf(g.toD(f.v + f.osp * oi * dtn))
                            drawLine(Color(0xFFCCCCCC), Offset(kx, ky), Offset(hx, hy), 1f)
                            drawCircle(Color.White, 5f, Offset(hx, hy))
                        }
                        if (i > 0) {
                            val dtp = f.t - k[i - 1].t
                            val ii = f.ii.coerceIn(0.001f, 1f)
                            val hx = xp(f.t - ii * dtp)
                            val hy = yOf(g.toD(f.v - f.isp * ii * dtp))
                            drawLine(Color(0xFFCCCCCC), Offset(kx, ky), Offset(hx, hy), 1f)
                            drawCircle(Color.White, 5f, Offset(hx, hy))
                        }
                    }
                    kfDraw(kx, ky, f.ip, f.sel, 5.5.dp.toPx())
                }
            }
            val r = mq
            if (r != null) {
                drawRect(Color(0x552D8CEB), r.topLeft, r.size)
                drawRect(Color(0xFF2D8CEB), r.topLeft, r.size, style = Stroke(1f))
            }
            if (rows.isEmpty()) {
                val msg = "Select a layer with animated properties"
                val tl = lt.get("gm", msg, GTS)
                drawText(tl, topLeft = Offset((size.width - tl.size.width) / 2f, size.height / 2f))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(24.dp).background(CH).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlatBtn("Value", c, !speedMode) { speedMode = false }
            FlatBtn("Speed", c, speedMode) { speedMode = true }
            Dd("Ease", c) {
                listOf(
                    "Easy Ease" to { comp.easeKeys(3) },
                    "Easy Ease In" to { comp.easeKeys(4) },
                    "Easy Ease Out" to { comp.easeKeys(5) },
                    "Auto Bezier" to { comp.easeKeys(1) },
                    "Linear" to { comp.easeKeys(0) },
                    "Hold" to { comp.easeKeys(2) }
                )
            }
            FlatBtn("Add key", c) {
                comp.mark()
                val e = comp.eps()
                for (g in rows) if (g.p.idx(comp.time, e) < 0) g.p.put(Kf(comp.time, g.p.get(comp.time)), e)
            }
            FlatBtn("Delete", c) { comp.delKeys() }
            FlatBtn("Copy", c) { comp.copyKeys() }
            FlatBtn("Paste", c) { comp.pasteKeys() }
            FlatBtn("All", c) { for (g in rows) g.p.selAll() }
        }
    }
}

// ---------------------------------------------------------------- Timeline panel
@Composable
fun TimeBox(comp: Comp) {
    val f by remember(comp) { derivedStateOf { (comp.time * comp.fps).toInt() } }
    Column(Modifier.width(96.dp).padding(start = 6.dp)) {
        Text(aeTc(f, comp.fps), color = VB, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("%05d (%s fps)".format(f, fpsTxt(comp.fps)), color = VB, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
fun FrameTime() {
    Text("Frame Render Time ", color = DIM, fontSize = 9.sp, maxLines = 1)
    Text("${FrameMs.toInt()}ms", color = Color(0xFF3BA55D), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
fun TimePanel(
    comp: Comp, c: Cur, foc: Boolean, onFoc: () -> Unit,
    graph: Boolean, onGraph: (Boolean) -> Unit, multi: Boolean, onMulti: (Boolean) -> Unit, m: Modifier
) {
    var w by remember { mutableStateOf(1f) }
    var zoom by remember { mutableStateOf(1f) }
    var vs by remember { mutableStateOf(0f) }
    var shyOn by remember { mutableStateOf(false) }
    var modes by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var tw by remember { mutableStateOf(0f) }
    var leftWidth by remember(comp.id) { mutableFloatStateOf(LEFT.value) }
    val dn = LocalDensity.current.density
    val maxLeftWidth = if (tw > 0f) maxOf(220f, tw / dn - 190f) else 720f
    val lw = leftWidth.coerceIn(220f, maxLeftWidth).dp
    val tm = rememberTextMeasurer()
    val lt = remember(tm) { Lt(tm) }
    val d = comp.dur
    val pps = w * zoom / d
    val vsc = vs.coerceIn(0f, maxOf(d - d / zoom, 0f))
    val xp = { t: Float -> (t - vsc) * pps }
    val xt = { x: Float -> (vsc + x / pps).coerceIn(0f, d) }
    val seek = { x: Float -> comp.time = xt(x) }
    LaunchedEffect(comp.playing, zoom) {
        while (comp.playing && zoom > 1.05f) {
            withFrameNanos { it }
            val vw = d / zoom
            if (comp.time < vs || comp.time > vs + vw) vs = (comp.time - vw * 0.1f).coerceIn(0f, maxOf(d - vw, 0f))
        }
    }
    val rows = buildRows(comp, shyOn, q)
    Column(m.onSizeChanged { tw = it.width.toFloat() }) {
        val rs = rememberScrollState()
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // ---- left: timecode, search, columns, layer + property rows
            Column(Modifier.width(lw).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(32.dp).background(PB),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeBox(comp)
                    Ix(6, true, c, 16.dp) {}
                    Fld(q, c, 90.dp, false) { q = it }
                    Spacer(Modifier.weight(1f))
                    FlatBtn("Multi", c, multi) { onMulti(!multi) }
                    Spacer(Modifier.width(4.dp))
                    FlatBtn("Shy", c, shyOn) { shyOn = !shyOn }
                    Ix(7, graph, c, 24.dp) { onGraph(!graph) }
                }
                Row(Modifier.fillMaxWidth().height(16.dp).background(CH), verticalAlignment = Alignment.CenterVertically) {
                    Ix(0, true, c, 17.dp, 16.dp) {}
                    Ix(1, true, c, 17.dp, 16.dp) {}
                    Ix(2, true, c, 17.dp, 16.dp) {}
                    Ix(3, true, c, 17.dp, 16.dp) {}
                    Spacer(Modifier.width(14.dp))
                    Text("#", color = DIM, fontSize = 9.sp, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(28.dp))
                    Text("Source Name", color = DIM, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (modes) "Mode   T   TrkMat" else "Switches", color = DIM, fontSize = 9.sp, modifier = Modifier.width(88.dp), maxLines = 1)
                    Text("Parent & Link", color = DIM, fontSize = 9.sp, modifier = Modifier.width(84.dp), maxLines = 1)
                }
                Column(Modifier.weight(1f).fillMaxWidth().background(PB).vscr(c, rs)) {
                    rows.forEach { r ->
                        when (r) {
                            is TLayer -> LLayer(comp, r.l, r.i, c, modes, multi, onFoc)
                            is TGroup -> LGroup(comp, r, c)
                            is TProp -> LProp(comp, r, c, graph, multi)
                        }
                    }
                }
            }
            // Draggable divider: resize only the layer/property side of the timeline.
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF111111))
                    .pointerInput(comp.id, tw) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            leftWidth = (leftWidth + dragAmount.x / dn)
                                .coerceIn(220f, maxLeftWidth)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight(0.22f).background(Color(0xFF666666)))
            }
            // ---- right: ruler, tracks or graph
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { w = maxOf(it.width.toFloat(), 1f) }) {
                Column(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxWidth().height(48.dp).background(PB).cur(c, { seek(it.x) }) { seek(it.x) }) {
                        val x0 = xp(0f)
                        val x1 = xp(d)
                        val y0 = 26.dp.toPx()
                        val y1 = size.height - 3.dp.toPx()
                        val ym = 38.dp.toPx()
                        val ty = 12.dp.toPx()
                        drawRect(Color(0xFF5E6B8C), Offset(x0, 3.dp.toPx()), Size(x1 - x0, 6.dp.toPx()))
                        drawRect(Color(0xFF3BA55D), Offset(x0, y1), Size(x1 - x0, 3.dp.toPx()))
                        var stp = 600f
                        for (qq in STP) {
                            if (qq * pps >= 64f) {
                                stp = qq
                                break
                            }
                        }
                        val i0 = floor(vsc / stp).toInt()
                        val i1 = ceil((vsc + size.width / pps) / stp).toInt()
                        for (k in i0..minOf(i1, i0 + 400)) {
                            val t = k * stp
                            if (t < 0f || t > d + 0.001f) continue
                            val x = xp(t)
                            drawLine(Color(0xFF8A8A8A), Offset(x, y0), Offset(x, y1), 1.2f)
                            val lb = lab(t)
                            drawText(lt.get("r$lb", lb, RS), topLeft = Offset(x + 3f, ty))
                            for (j in 1..3) {
                                val mt = t + stp * j / 4f
                                if (mt <= d) {
                                    val mx = xp(mt)
                                    drawLine(Color(0xFF5E5E5E), Offset(mx, ym), Offset(mx, y1), 1f)
                                }
                            }
                        }
                        for (marker in comp.markers) {
                            val mx = xp(marker.time)
                            if (mx >= -8f && mx <= size.width + 8f) {
                                val mc = Color(marker.color)
                                drawLine(mc.copy(alpha = 0.75f), Offset(mx, 0f), Offset(mx, size.height), 1.2f)
                                val pin = Path().apply {
                                    moveTo(mx - 5.dp.toPx(), 0f)
                                    lineTo(mx + 5.dp.toPx(), 0f)
                                    lineTo(mx, 8.dp.toPx())
                                    close()
                                }
                                drawPath(pin, mc)
                                drawText(lt.get("marker_" + marker.label, marker.label, GTS), topLeft = Offset(mx + 6.dp.toPx(), 1.dp.toPx()))
                            }
                        }
                    }
                    if (graph) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E)).scr(c) { o ->
                                if (zoom > 1.05f) vs = (vsc - o.x / pps).coerceIn(0f, maxOf(d - d / zoom, 0f))
                            }
                        ) { GraphView(comp, c, xp, xt, pps, multi, lt, Modifier.fillMaxSize()) }
                    } else {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().background(PB).scr(c) { o ->
                                if (zoom > 1.05f && abs(o.x) > abs(o.y)) vs = (vsc - o.x / pps).coerceIn(0f, maxOf(d - d / zoom, 0f)) else rs.dispatchRawDelta(-o.y)
                            }.verticalScroll(rs)
                        ) {
                            rows.forEach { r ->
                                when (r) {
                                    is TLayer -> TrackLayer(comp, r.l, r.i, c, multi, xp, xt, lt, onFoc)
                                    is TGroup -> TrackGroup()
                                    is TProp -> TrackProp(comp, r, c, multi, pps, xp, xt)
                                }
                            }
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val x = xp(comp.time)
                    if (x >= 0f && x <= size.width) {
                        drawLine(VB, Offset(x, 0f), Offset(x, size.height), 1.5f)
                        val p = Path().apply {
                            moveTo(x - 6.dp.toPx(), 0f)
                            lineTo(x + 6.dp.toPx(), 0f)
                            lineTo(x + 6.dp.toPx(), 9.dp.toPx())
                            lineTo(x, 14.dp.toPx())
                            lineTo(x - 6.dp.toPx(), 9.dp.toPx())
                            close()
                        }
                        drawPath(p, VB)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(22.dp).background(CH).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FrameTime()
            FlatBtn("+ Marker", c) { comp.addMarker() }
            Spacer(Modifier.weight(1f))
            FlatBtn("Toggle Switches / Modes", c) { modes = !modes }
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.size(12.dp, 10.dp)) {
                val p = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width / 2, size.height * 0.3f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(p, Color(0xFF8A8A8A))
            }
            Sld(zoom, 1f, 12f, c, 90.dp) {
                zoom = it
                vs = vs.coerceIn(0f, maxOf(d - d / it, 0f))
            }
            Canvas(Modifier.size(16.dp, 12.dp)) {
                val p = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width * 0.35f, size.height * 0.25f)
                    lineTo(size.width * 0.6f, size.height * 0.6f)
                    lineTo(size.width * 0.8f, size.height * 0.1f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(p, Color(0xFF8A8A8A))
            }
        }
    }
}
