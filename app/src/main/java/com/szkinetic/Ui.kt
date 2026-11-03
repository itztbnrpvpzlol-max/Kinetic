package com.szkinetic

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

val BG = Color(0xFF1D1D1D)
val P2 = Color(0xFF262626)
val P3 = Color(0xFF383838)
val AC = Color(0xFF2D8CEB)
val LAV = Color(0xFF9999FF)
val GR = Color(0xFFA0A0A0)
val HD = Color(0xFF2B2B2B)
val EDG = Color(0xFF151515)

@Composable
fun Btn(t: String, c: Cur, pri: Boolean = false, big: Boolean = false, f: () -> Unit) {
    Box(
        Modifier.background(if (pri) AC else P3, RoundedCornerShape(4.dp)).cur(c) { f() }
            .padding(horizontal = if (big) 18.dp else 10.dp, vertical = if (big) 11.dp else 5.dp),
        Alignment.Center
    ) { Text(t, color = Color.White, fontSize = if (big) 14.sp else 12.sp) }
}

@Composable
fun Hdr(t: String) {
    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF2E2E2E)).padding(horizontal = 8.dp), Alignment.CenterStart) {
        Text(t, color = Color(0xFFB0B0B0), fontSize = 11.sp)
    }
}

@Composable
fun Tgl(on: Boolean, c: Cur, f: (Boolean) -> Unit) {
    Box(Modifier.width(40.dp).height(22.dp).background(if (on) AC else P3, RoundedCornerShape(11.dp)).cur(c) { f(!on) }) {
        Box(Modifier.padding(2.dp).offset(x = if (on) 18.dp else 0.dp).size(18.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun Seg(opts: List<String>, sel: Int, c: Cur, f: (Int) -> Unit) {
    Row(Modifier.background(P3, RoundedCornerShape(4.dp))) {
        opts.forEachIndexed { i, o ->
            Box(Modifier.background(if (i == sel) AC else Color.Transparent, RoundedCornerShape(4.dp)).cur(c) { f(i) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(o, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Sld(v: Float, lo: Float, hi: Float, c: Cur, wd: Dp = 160.dp, f: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    val set = { o: Offset -> f(lo + (o.x / w).coerceIn(0f, 1f) * (hi - lo)) }
    val fr = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
    Box(Modifier.width(wd).height(22.dp).onSizeChanged { w = it.width.toFloat() }.cur(c, set, null, null, null, set)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).background(P3))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(fr).height(4.dp).background(AC))
        Box(Modifier.align(Alignment.CenterStart).offset { IntOffset((fr * (w - 12.dp.roundToPx())).toInt(), 0) }.size(12.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun Fld(v: String, c: Cur, w: Dp, num: Boolean, f: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    Box(
        Modifier.width(w).background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp)).border(1.dp, P3, RoundedCornerShape(3.dp))
            .cur(c) { fr.requestFocus() }.padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        BasicTextField(
            value = v,
            onValueChange = f,
            modifier = Modifier.focusRequester(fr),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done)
        )
    }
}

@Composable
fun Mn(t: String, c: Cur, vararg i: Pair<String, () -> Unit>) {
    var r by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.onGloballyPositioned { r = it.boundsInRoot() }
            .cur(c) { c.menu = Menu(Offset(r.left, r.bottom), i.toList()) }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) { Text(t, color = Color(0xFFD8D8D8), fontSize = 12.sp) }
}

@Composable
fun Pn(t: String, f: Boolean, c: Cur, onF: () -> Unit, m: Modifier, body: @Composable ColumnScope.() -> Unit) {
    Column(m.padding(1.dp).background(P2).border(1.dp, if (f) AC else EDG)) {
        Box(Modifier.fillMaxWidth().height(22.dp).background(HD).cur(c) { onF() }.padding(horizontal = 8.dp), Alignment.CenterStart) {
            Text(t, color = if (f) Color.White else GR, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        body()
    }
}

@Composable
fun Tmb(m: Media?, mod: Modifier) {
    val im by produceState<ImageBitmap?>(null, m?.id) {
        value = if (m == null) null else withContext(Dispatchers.IO) { Thumbs.make(m) }
    }
    Box(mod.background(Color.Black), Alignment.Center) {
        val i = im
        if (i != null) {
            Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (m != null && m.kind == 2) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val n = 14
                val g = size.width / n
                for (k in 0 until n) {
                    val hh = size.height * (0.2f + 0.7f * ((m.id.hashCode() ushr (k % 24)) and 7) / 7f)
                    drawRect(Color(MCOL[2]), Offset(k * g + g * 0.2f, (size.height - hh) / 2), Size(g * 0.6f, hh))
                }
            }
        }
    }
}

@Composable
fun Sw(k: Int, on: Boolean, c: Cur, f: () -> Unit) {
    Canvas(Modifier.size(20.dp, 26.dp).cur(c) { f() }) {
        val col = if (on) Color(0xFFD0D0D0) else Color(0xFF494949)
        val cx = size.width / 2
        val cy = size.height / 2
        when (k) {
            0 -> {
                drawOval(col, Offset(cx - 7.dp.toPx(), cy - 4.dp.toPx()), Size(14.dp.toPx(), 8.dp.toPx()), style = Stroke(1.5f))
                drawCircle(col, 2.2.dp.toPx(), Offset(cx, cy))
            }
            1 -> {
                val p = Path().apply {
                    moveTo(cx - 6.dp.toPx(), cy - 2.5.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), cy - 2.5.dp.toPx())
                    lineTo(cx + 3.dp.toPx(), cy - 6.dp.toPx())
                    lineTo(cx + 3.dp.toPx(), cy + 6.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), cy + 2.5.dp.toPx())
                    lineTo(cx - 6.dp.toPx(), cy + 2.5.dp.toPx())
                    close()
                }
                drawPath(p, col)
            }
            else -> {
                drawRect(col, Offset(cx - 5.dp.toPx(), cy - 1.dp.toPx()), Size(10.dp.toPx(), 7.dp.toPx()))
                drawArc(col, 180f, 180f, false, Offset(cx - 3.5.dp.toPx(), cy - 6.dp.toPx()), Size(7.dp.toPx(), 10.dp.toPx()), style = Stroke(1.5f))
            }
        }
    }
}

@Composable
fun Ico(k: Int, on: Boolean, c: Cur, f: () -> Unit) {
    Canvas(Modifier.size(28.dp, 24.dp).cur(c) { f() }) {
        val s = size.height * 0.3f
        val cx = size.width / 2
        val cy = size.height / 2
        val col = Color(0xFFD8D8D8)
        fun tri(x: Float, dir: Float, z: Float) {
            val p = Path().apply {
                moveTo(x + dir * z, cy)
                lineTo(x - dir * z, cy - z)
                lineTo(x - dir * z, cy + z)
                close()
            }
            drawPath(p, col)
        }
        when (k) {
            0 -> {
                drawRect(col, Offset(cx - s * 1.2f, cy - s), Size(2.5f, s * 2))
                tri(cx + s * 0.3f, -1f, s)
            }
            1 -> tri(cx, -1f, s)
            2 -> if (on) {
                drawRect(col, Offset(cx - s * 0.9f, cy - s), Size(s * 0.6f, s * 2))
                drawRect(col, Offset(cx + s * 0.3f, cy - s), Size(s * 0.6f, s * 2))
            } else tri(cx, 1f, s * 1.1f)
            3 -> tri(cx, 1f, s)
            else -> {
                tri(cx - s * 0.3f, 1f, s)
                drawRect(col, Offset(cx + s * 0.9f, cy - s), Size(2.5f, s * 2))
            }
        }
    }
}

@Composable
fun Kv(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = GR, fontSize = 11.sp)
        Text(v, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PRow(col: Long, nm: String, ty: String, du: String, s: Boolean, c: Cur, rc: ((Offset) -> Unit)?, f: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (s) AC.copy(alpha = 0.3f) else Color.Transparent).cur(c, rc = rc) { f() }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).background(Color(col)))
        Text(nm, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(ty, color = GR, fontSize = 10.sp, modifier = Modifier.width(40.dp))
        Text(du, color = GR, fontSize = 10.sp, modifier = Modifier.width(34.dp))
    }
}

@Composable
fun ProjPanel(p: Project, comp: Comp, c: Cur, sel: String, onSel: (String) -> Unit, foc: Boolean, onFoc: () -> Unit, onImp: () -> Unit, m: Modifier) {
    val cm = p.media.firstOrNull { it.id == sel }
    Pn("Project", foc, c, onFoc, m) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tmb(cm, Modifier.size(64.dp, 38.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cm?.name ?: comp.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cm == null) {
                    Text("${comp.w} x ${comp.h}, ${comp.fps} fps", color = GR, fontSize = 10.sp)
                    Text(dl(comp.dur), color = GR, fontSize = 10.sp)
                } else {
                    Text(if (cm.kind == 2) KN[2] else "${cm.w} x ${cm.h}", color = GR, fontSize = 10.sp)
                    Text(if (cm.kind == 0) KN[0] else dl(cm.dur), color = GR, fontSize = 10.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(P3))
        Row(Modifier.fillMaxWidth().background(HD).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("Name", color = GR, fontSize = 10.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            Text("Type", color = GR, fontSize = 10.sp, modifier = Modifier.width(46.dp))
            Text("Dur", color = GR, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        }
        val rs = rememberScrollState()
        Column(Modifier.weight(1f).fillMaxWidth().scr(c) { rs.dispatchRawDelta(-it) }.verticalScroll(rs)) {
            PRow(0xFFB0B0B0, comp.name, "Comp", dl(comp.dur), sel == "", c, null) { onSel("") }
            p.media.forEach { m ->
                PRow(
                    MCOL[m.kind], m.name, KN[m.kind], if (m.kind == 0) "" else dl(m.dur), sel == m.id, c,
                    mnu(c, "Add to timeline" to { comp.addMedia(m) }, "Delete" to { p.rm(m) })
                ) { onSel(m.id) }
            }
            if (p.media.isEmpty()) Text("Tap Import to add videos, audio or images.", color = GR, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(HD).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Btn("Import", c) { onImp() }
            Btn("Add to comp", c) { if (cm != null) comp.addMedia(cm) }
            Btn("Delete", c) {
                if (cm != null) {
                    p.rm(cm)
                    onSel("")
                }
            }
        }
    }
}

@Composable
fun CompPanel(comp: Comp, c: Cur, foc: Boolean, onFoc: () -> Unit, m: Modifier) {
    Pn("Composition  " + comp.name, foc, c, onFoc, m) {
        Viewer(comp, Modifier.weight(1f).fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().height(24.dp).background(HD).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${comp.w} x ${comp.h}", color = GR, fontSize = 10.sp)
            Text("${comp.fps} fps", color = GR, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(tc(comp.time, comp.fps), color = AC, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RightPanel(comp: Comp, c: Cur, tab: Int, onTab: (Int) -> Unit, foc: Boolean, onFoc: () -> Unit, m: Modifier) {
    Column(m.padding(1.dp).background(P2).border(1.dp, if (foc) AC else EDG)) {
        Row(Modifier.fillMaxWidth().height(22.dp).background(HD)) {
            listOf("Properties", "Info", "Effects").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(if (i == tab) P2 else Color.Transparent).cur(c) {
                        onTab(i)
                        onFoc()
                    },
                    Alignment.Center
                ) { Text(t, color = if (i == tab) Color.White else GR, fontSize = 11.sp) }
            }
        }
        val rs = rememberScrollState()
        Column(
            Modifier.weight(1f).fillMaxWidth().scr(c) { rs.dispatchRawDelta(-it) }.verticalScroll(rs).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val l = comp.layers.getOrNull(comp.sel)
            if (tab == 2) {
                listOf("Blur & Sharpen", "Color Correction", "Distort", "Generate", "Keying", "Stylize", "Transition").forEach {
                    Text("> $it", color = Color(0xFFC8C8C8), fontSize = 11.sp)
                }
            } else if (l == null) {
                Text("No layer selected", color = GR, fontSize = 11.sp)
            } else if (tab == 1) {
                val md = comp.lib.firstOrNull { it.id == l.mid }
                Text(l.name, color = AC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Kv("Type", if (l.kind < 0) "Shape" else KN[l.kind])
                Kv("Source", md?.name ?: "-")
                Kv("In", tc(l.start, comp.fps))
                Kv("Out", tc(l.end(comp.dur), comp.fps))
                Kv("Duration", tc(l.end(comp.dur) - l.start, comp.fps))
                if (md != null && md.kind != 2) Kv("Size", "${md.w} x ${md.h}")
            } else {
                Text(l.name, color = AC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Transform", color = LAV, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                PN.forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(n, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Btn("-", c) { bump(l.p[i], comp.time, -ST[i]) }
                        Text("%.2f".format(ev(l.p[i], comp.time)), color = Color.White, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                        Btn("+", c) { bump(l.p[i], comp.time, ST[i]) }
                    }
                }
            }
        }
    }
}

fun lab(t: Float): String {
    val s = t.toInt()
    return if (t == s.toFloat()) (if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "${s}s") else "%.1fs".format(t)
}

@Composable
fun TimePanel(comp: Comp, c: Cur, foc: Boolean, onFoc: () -> Unit, m: Modifier) {
    var w by remember { mutableStateOf(1f) }
    var zoom by remember { mutableStateOf(1f) }
    var vs by remember { mutableStateOf(0f) }
    val tm = rememberTextMeasurer()
    val d = comp.dur
    val pps = w * zoom / d
    val vsc = vs.coerceIn(0f, maxOf(d - d / zoom, 0f))
    val xp = { t: Float -> (t - vsc) * pps }
    val xt = { x: Float -> (vsc + x / pps).coerceIn(0f, d) }
    val seek = { x: Float -> comp.time = xt(x) }
    val step = { n: Int -> comp.time = ((comp.time * comp.fps).roundToInt() + n).coerceIn(0, (d * comp.fps).toInt()) / comp.fps.toFloat() }
    LaunchedEffect(comp.playing, zoom) {
        while (comp.playing && zoom > 1.05f) {
            withFrameNanos { it }
            val vw = d / zoom
            if (comp.time < vs || comp.time > vs + vw) vs = (comp.time - vw * 0.1f).coerceIn(0f, maxOf(d - vw, 0f))
        }
    }
    Pn("Timeline  " + comp.name, foc, c, onFoc, m) {
        val rs = rememberScrollState()
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(236.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth().height(46.dp).background(HD).padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
                    Text(tc(comp.time, comp.fps), color = AC, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(comp.fps.toString() + " fps", color = GR, fontSize = 9.sp)
                }
                Column(Modifier.weight(1f).fillMaxWidth().scr(c) { rs.dispatchRawDelta(-it) }.verticalScroll(rs)) {
                    comp.layers.forEachIndexed { i, l ->
                        Row(
                            Modifier.fillMaxWidth().height(26.dp)
                                .background(if (i == comp.sel) AC.copy(alpha = 0.3f) else Color.Transparent)
                                .cur(c, rc = mnu(c, "Duplicate" to { comp.dup(i) }, "Split at playhead" to { comp.split(i) }, "Delete" to { comp.del(i) })) {
                                    comp.sel = i
                                    onFoc()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(20.dp)) { if (l.kind != 2) Sw(0, l.vis, c) { l.vis = !l.vis } }
                            Box(Modifier.width(20.dp)) { if (l.kind >= 1) Sw(1, !l.mute, c) { l.mute = !l.mute } }
                            Sw(2, l.lock, c) { l.lock = !l.lock }
                            Box(Modifier.padding(horizontal = 4.dp).size(10.dp).background(Color(l.col)))
                            Text((i + 1).toString(), color = GR, fontSize = 11.sp, modifier = Modifier.width(18.dp))
                            Text(l.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { w = it.width.toFloat() }) {
                Column(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxWidth().height(46.dp).background(HD).cur(c, { seek(it.x) }) { seek(it.x) }) {
                        val x0 = xp(0f)
                        val x1 = xp(d)
                        drawRect(Color(0xFF5A5A5A), Offset(x0, 2.dp.toPx()), Size(x1 - x0, 5.dp.toPx()))
                        drawRect(Color(0xFF3BA55D), Offset(x0, size.height - 3.dp.toPx()), Size(x1 - x0, 3.dp.toPx()))
                        val cand = floatArrayOf(0.1f, 0.25f, 0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f, 600f)
                        val stp = cand.firstOrNull { it * pps >= 64f } ?: 600f
                        val i0 = floor(vsc / stp).toInt()
                        val i1 = ceil((vsc + size.width / pps) / stp).toInt()
                        for (k in i0..minOf(i1, i0 + 400)) {
                            val t = k * stp
                            if (t < 0f || t > d + 0.001f) continue
                            val x = xp(t)
                            drawLine(Color(0xFF8A8A8A), Offset(x, 22.dp.toPx()), Offset(x, size.height - 3.dp.toPx()), 1.5f)
                            drawText(tm, lab(t), Offset(x + 3f, 11.dp.toPx()), TextStyle(color = GR, fontSize = 9.sp))
                            for (j in 1..3) {
                                val mt = t + stp * j / 4f
                                if (mt <= d) drawLine(Color(0xFF5E5E5E), Offset(xp(mt), 34.dp.toPx()), Offset(xp(mt), size.height - 3.dp.toPx()), 1f)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().scr(c) { dy ->
                        if (zoom > 1.05f) vs = (vsc - dy / pps).coerceIn(0f, maxOf(d - d / zoom, 0f)) else rs.dispatchRawDelta(-dy)
                    }.verticalScroll(rs)) {
                        comp.layers.forEachIndexed { i, l ->
                            val gs = remember(l) { floatArrayOf(-1f, 0f) }
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
                            }
                            Canvas(
                                Modifier.fillMaxWidth().height(26.dp)
                                    .background(if (i == comp.sel) AC.copy(alpha = 0.12f) else Color.Transparent)
                                    .cur(
                                        c,
                                        drag = { o ->
                                            when (gs[0].toInt()) {
                                                0 -> comp.move(l, xt(o.x - gs[1]))
                                                1 -> comp.trim(l, 1, xt(o.x))
                                                2 -> comp.trim(l, 2, xt(o.x))
                                                else -> {}
                                            }
                                        },
                                        rc = mnu(c, "Duplicate" to { comp.dup(i) }, "Split at playhead" to { comp.split(i) }, "Delete" to { comp.del(i) }),
                                        ds = grab
                                    ) { o -> grab(o) }
                            ) {
                                val a = xp(l.start)
                                val b = xp(l.end(d))
                                val tp = Offset(a, 3.dp.toPx())
                                val sz = Size(maxOf(b - a, 2f), size.height - 6.dp.toPx())
                                drawRoundRect(Color(l.col).copy(alpha = if (l.lock) 0.45f else 0.85f), tp, sz, CornerRadius(3.dp.toPx()))
                                if (i == comp.sel) drawRoundRect(Color.White, tp, sz, CornerRadius(3.dp.toPx()), Stroke(1.5f))
                                if (sz.width > 48f) drawText(
                                    tm, l.name, Offset(a + 6f, 6.dp.toPx()), TextStyle(color = Color(0xFF101010), fontSize = 10.sp),
                                    overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, size = Size(sz.width - 12f, 14.dp.toPx())
                                )
                                val ts = l.p.flatMap { k -> k.map { it.t } }.distinct()
                                ts.forEach { t ->
                                    val x = xp(t)
                                    val y = size.height / 2
                                    if (t > 0f) rotate(45f, Offset(x, y)) { drawRect(Color(0xFFE2C84A), Offset(x - 4f, y - 4f), Size(8f, 8f)) }
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
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Ico(0, false, c) { comp.time = 0f }
            Ico(1, false, c) { step(-1) }
            Ico(2, comp.playing, c) { comp.playing = !comp.playing }
            Ico(3, false, c) { step(1) }
            Ico(4, false, c) { comp.time = d }
            Spacer(Modifier.width(8.dp))
            Btn("Split", c) { comp.split(comp.sel) }
            Btn("Delete", c) { comp.del(comp.sel) }
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

@Composable
fun Editor(p: Project, c: Cur, onBack: () -> Unit, onSet: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val comp = p.comps[0]
    var foc by remember { mutableStateOf(2) }
    var psel by remember { mutableStateOf("") }
    var rt by remember { mutableStateOf(0) }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us: List<Uri> ->
        if (us.isNotEmpty()) scope.launch {
            var n = 0
            for (u in us) {
                val m = withContext(Dispatchers.IO) { Store.importMedia(ctx, p.id, u) }
                if (m != null) {
                    p.media.add(m)
                    psel = m.id
                    n++
                }
            }
            Store.save(ctx, p)
            Toast.makeText(ctx, "Imported $n of ${us.size}", Toast.LENGTH_SHORT).show()
        }
    }
    val doImp = { imp.launch(arrayOf("video/*", "audio/*", "image/*")) }
    LaunchedEffect(p) {
        while (true) {
            delay(4000)
            Store.save(ctx, p)
        }
    }
    LaunchedEffect(comp.playing) {
        var last = 0L
        while (comp.playing) {
            val n = withFrameNanos { it }
            if (last != 0L) comp.time = (comp.time + (n - last) / 1e9f) % comp.dur
            last = n
            Aud.sync(comp, true)
        }
        Aud.sync(comp, false)
    }
    DisposableEffect(p) { onDispose { Aud.stop() } }
    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().height(28.dp).background(Color(0xFF1F1F1F)), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(18.dp).background(LAV, RoundedCornerShape(4.dp)), Alignment.Center) {
                Text("Ki", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            Spacer(Modifier.width(4.dp))
            Mn("File", c, "Import media..." to { doImp() }, "Save project" to { Store.save(ctx, p) }, "Close project" to { onBack() })
            Mn("Edit", c, "Preferences..." to { onSet() })
            Mn("Composition", c, "New shape layer" to { comp.add() })
            Mn("Layer", c, "Duplicate" to { comp.dup(comp.sel) }, "Split at playhead" to { comp.split(comp.sel) }, "Delete" to { comp.del(comp.sel) })
            Mn("View", c, (if (c.on) "Hide virtual cursor" else "Show virtual cursor") to { c.on = !c.on })
            Spacer(Modifier.weight(1f))
            Text(p.name, color = GR, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(P3, RoundedCornerShape(3.dp)).cur(c) { onBack() }.padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text("Home", color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Row(Modifier.fillMaxWidth().weight(1.15f)) {
            ProjPanel(p, comp, c, psel, { psel = it }, foc == 0, { foc = 0 }, { doImp() }, Modifier.width(214.dp).fillMaxHeight())
            CompPanel(comp, c, foc == 1, { foc = 1 }, Modifier.weight(1f).fillMaxHeight())
            RightPanel(comp, c, rt, { rt = it }, foc == 3, { foc = 3 }, Modifier.width(190.dp).fillMaxHeight())
        }
        TimePanel(comp, c, foc == 2, { foc = 2 }, Modifier.fillMaxWidth().weight(1f))
    }
}
