package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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

fun lab(t: Float): String {
    val s = t.toInt()
    return if (t == s.toFloat()) (if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "${s}s") else "%.1fs".format(t)
}

private val RS = TextStyle(color = GR, fontSize = 9.sp)
private val LS = TextStyle(color = Color(0xFF101010), fontSize = 10.sp)
private val STP = floatArrayOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f, 600f)

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

class SubRow(val name: String, val ps: List<Prop>)

// Animated properties of a layer (like After Effects' "U" view).
fun subRows(l: Layer): List<SubRow> {
    val r = ArrayList<SubRow>()
    for (d in (if (l.role == 2) listOf(PDef("Opacity", intArrayOf(OP), listOf(""), "%", 0.5f, 0f, 100f)) else tdefs(l))) {
        val ps = d.ch.map { l.pr[it] }
        if (ps.any { it.anim }) r.add(SubRow(d.name, ps))
    }
    for (fx in l.fx) {
        val d = FXS[fx.type]
        for (i in d.pn.indices) if (fx.pr[i].anim) r.add(SubRow(d.name + " > " + d.pn[i], listOf(fx.pr[i])))
    }
    return r
}

@Composable
fun Tk(t: String, on: Boolean, c: Cur, f: () -> Unit) {
    Box(Modifier.size(17.dp, 26.dp).cur(c) { f() }, Alignment.Center) {
        Text(t, color = if (on) AC else Color(0xFF555555), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TimePanel(comp: Comp, c: Cur, foc: Boolean, onFoc: () -> Unit, m: Modifier) {
    var w by remember { mutableStateOf(1f) }
    var zoom by remember { mutableStateOf(1f) }
    var vs by remember { mutableStateOf(0f) }
    var shyOn by remember { mutableStateOf(false) }
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
    fun keyJump(dir: Int) {
        val l = comp.layers.getOrNull(comp.sel) ?: return
        val e = comp.eps()
        val all = ArrayList<Prop>()
        all.addAll(l.pr)
        for (f in l.fx) all.addAll(f.pr)
        val x = if (dir < 0) all.mapNotNull { it.prev(comp.time, e) }.maxOrNull() else all.mapNotNull { it.next(comp.time, e) }.minOrNull()
        if (x != null) comp.time = x
    }
    Pn("Timeline  " + comp.name, foc, c, onFoc, m) {
        val rs = rememberScrollState()
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).background(HD).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Layers", color = GR, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Btn("+Null", c) { comp.addNull() }
                    Btn("+Adj", c) { comp.addAdj() }
                }
                Column(Modifier.weight(1f).fillMaxWidth().vscr(c, rs)) {
                    comp.layers.forEachIndexed { i, l ->
                        if (shyOn && l.shy) return@forEachIndexed
                        Row(
                            Modifier.fillMaxWidth().height(26.dp)
                                .background(if (i == comp.sel) AC.copy(alpha = 0.3f) else Color.Transparent)
                                .cur(
                                    c,
                                    rc = mnu(
                                        c,
                                        "Duplicate" to { comp.dup(i) },
                                        "Split at playhead" to { comp.split(i) },
                                        "Move up" to { comp.reorder(i, i - 1) },
                                        "Move down" to { comp.reorder(i, i + 1) },
                                        "Bring to front" to { comp.reorder(i, 0) },
                                        "Send to back" to { comp.reorder(i, comp.layers.size - 1) },
                                        "Parent to new null" to { comp.nullParent(i) },
                                        "Delete" to { comp.del(i) }
                                    )
                                ) {
                                    comp.sel = i
                                    onFoc()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(20.dp)) { if (l.kind != 2) Sw(0, l.vis, c) { l.vis = !l.vis } }
                            Box(Modifier.width(20.dp)) { if (l.kind >= 1) Sw(1, !l.mute, c) { l.mute = !l.mute } }
                            Tk("S", l.solo, c) { l.solo = !l.solo }
                            Sw(2, l.lock, c) { l.lock = !l.lock }
                            Tk("H", l.shy, c) { l.shy = !l.shy }
                            Box(
                                Modifier.padding(horizontal = 3.dp).size(12.dp, 26.dp).cur(c) {
                                    val ix = LABELS.indexOf(l.lcol)
                                    l.lcol = LABELS[(ix + 1) % LABELS.size]
                                },
                                Alignment.Center
                            ) { Box(Modifier.size(10.dp).background(Color(l.lcol))) }
                            Text((i + 1).toString(), color = GR, fontSize = 11.sp, modifier = Modifier.width(18.dp))
                            Tk(if (l.open) "v" else ">", l.open, c) { l.open = !l.open }
                            Text(
                                (if (l.role == 1) "[N] " else if (l.role == 2) "[A] " else "") + l.name,
                                color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val pl = comp.layerOf(l.parent)
                            MBtn(if (pl == null) "-" else "#" + (comp.layers.indexOf(pl) + 1), c) {
                                val items = ArrayList<Pair<String, () -> Unit>>()
                                items.add("None" to { comp.setParent(l, null); Unit })
                                comp.layers.forEachIndexed { j, q ->
                                    if (q !== l && comp.canParent(l, q)) items.add("${j + 1}. ${q.name}" to { comp.setParent(l, q); Unit })
                                }
                                items
                            }
                        }
                        if (l.open) {
                            for (sr in subRows(l)) {
                                Row(Modifier.fillMaxWidth().height(22.dp).padding(start = 96.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(sr.name, color = GR, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { w = it.width.toFloat() }) {
                Column(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxWidth().height(46.dp).background(HD).cur(c, { seek(it.x) }) { seek(it.x) }) {
                        val x0 = xp(0f)
                        val x1 = xp(d)
                        val y0 = 22.dp.toPx()
                        val y1 = size.height - 3.dp.toPx()
                        val ym = 34.dp.toPx()
                        val ty = 11.dp.toPx()
                        drawRect(Color(0xFF5A5A5A), Offset(x0, 2.dp.toPx()), Size(x1 - x0, 5.dp.toPx()))
                        drawRect(Color(0xFF3BA55D), Offset(x0, y1), Size(x1 - x0, 3.dp.toPx()))
                        var stp = 600f
                        for (q in STP) {
                            if (q * pps >= 64f) {
                                stp = q
                                break
                            }
                        }
                        val i0 = floor(vsc / stp).toInt()
                        val i1 = ceil((vsc + size.width / pps) / stp).toInt()
                        for (k in i0..minOf(i1, i0 + 400)) {
                            val t = k * stp
                            if (t < 0f || t > d + 0.001f) continue
                            val x = xp(t)
                            drawLine(Color(0xFF8A8A8A), Offset(x, y0), Offset(x, y1), 1.5f)
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
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().scr(c) { o ->
                        if (zoom > 1.05f && abs(o.x) > abs(o.y)) vs = (vsc - o.x / pps).coerceIn(0f, maxOf(d - d / zoom, 0f)) else rs.dispatchRawDelta(-o.y)
                    }.verticalScroll(rs)) {
                        comp.layers.forEachIndexed { i, l ->
                            if (shyOn && l.shy) return@forEachIndexed
                            val gs = remember(l) { floatArrayOf(-1f, 0f, 0f) }
                            val grab = { o: Offset ->
                                comp.sel = i
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
                            }
                            Canvas(
                                Modifier.fillMaxWidth().height(26.dp)
                                    .background(if (i == comp.sel) AC.copy(alpha = 0.12f) else Color.Transparent)
                                    .cur(
                                        c,
                                        drag = { o ->
                                            if (gs[0] >= 0f && gs[2] == 0f) {
                                                comp.mark()
                                                gs[2] = 1f
                                            }
                                            when (gs[0].toInt()) {
                                                0 -> comp.move(l, xt(o.x - gs[1]))
                                                1 -> comp.trim(l, 1, xt(o.x))
                                                2 -> comp.trim(l, 2, xt(o.x))
                                                else -> {}
                                            }
                                        },
                                        rc = mnu(
                                            c,
                                            "Duplicate" to { comp.dup(i) },
                                            "Split at playhead" to { comp.split(i) },
                                            "Delete" to { comp.del(i) }
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
                                    val tp = Offset(ca, 3.dp.toPx())
                                    val sz = Size(maxOf(cb - ca, 2f), size.height - 6.dp.toPx())
                                    drawRoundRect(Color(l.lcol).copy(alpha = if (l.lock) 0.45f else if (l.role == 1) 0.5f else 0.85f), tp, sz, CornerRadius(3.dp.toPx()))
                                    if (i == comp.sel) drawRoundRect(Color.White, tp, sz, CornerRadius(3.dp.toPx()), Stroke(1.5f))
                                    val l0 = maxOf(a, 0f) + 6f
                                    val r0 = minOf(b, size.width) - 6f
                                    if (r0 - l0 > 24f) {
                                        val tl = lt.get("l" + l.name, l.name, LS)
                                        clipRect(l0, 0f, r0, size.height) { drawText(tl, topLeft = Offset(l0, 6.dp.toPx())) }
                                    }
                                }
                                if (!l.open) {
                                    val y = size.height / 2
                                    for (pi in 0 until NCH) {
                                        val kk = l.pr[pi].k
                                        for (j in 0 until kk.size) {
                                            val x = xp(kk[j].t)
                                            if (x >= -8f && x <= size.width + 8f) rotate(45f, Offset(x, y)) { drawRect(Color(0xFFE2C84A), Offset(x - 4f, y - 4f), Size(8f, 8f)) }
                                        }
                                    }
                                }
                            }
                            if (l.open) {
                                for (sr in subRows(l)) {
                                    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                                        val y = size.height / 2
                                        drawLine(Color(0xFF333333), Offset(0f, y), Offset(size.width, y), 1f)
                                        for (p in sr.ps) {
                                            val kk = p.k
                                            for (j in 0 until kk.size) {
                                                val x = xp(kk[j].t)
                                                if (x >= -8f && x <= size.width + 8f) {
                                                    val col = when (kk[j].ip) {
                                                        2 -> Color(0xFFE2574A)
                                                        1 -> Color(0xFF4AE27B)
                                                        else -> Color(0xFFE2C84A)
                                                    }
                                                    rotate(45f, Offset(x, y)) { drawRect(col, Offset(x - 4f, y - 4f), Size(8f, 8f)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val x = xp(comp.time)
                    if (x >= 0f && x <= size.width) {
                        drawLine(AC, Offset(x, 0f), Offset(x, size.height), 2f)
                        val p = Path().apply {
                            moveTo(x - 6.dp.toPx(), 0f)
                            lineTo(x + 6.dp.toPx(), 0f)
                            lineTo(x, 10.dp.toPx())
                            close()
                        }
                        drawPath(p, AC)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(HD).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Btn("Split", c) { comp.split(comp.sel) }
            Btn("Delete", c) { comp.del(comp.sel) }
            Btn("<K", c) { keyJump(-1) }
            Btn("K>", c) { keyJump(1) }
            Btn(if (shyOn) "Shy: hide" else "Shy: show", c, shyOn) { shyOn = !shyOn }
            Spacer(Modifier.weight(1f))
            if (zoom > 1.05f) {
                Text("Pan", color = GR, fontSize = 10.sp)
                Sld(vsc, 0f, maxOf(d - d / zoom, 0.01f), c, 90.dp) { vs = it }
                Spacer(Modifier.width(8.dp))
            }
            Text("Zoom", color = GR, fontSize = 10.sp)
            Sld(zoom, 1f, 12f, c, 90.dp) {
                zoom = it
                vs = vs.coerceIn(0f, maxOf(d - d / it, 0f))
            }
        }
    }
}
