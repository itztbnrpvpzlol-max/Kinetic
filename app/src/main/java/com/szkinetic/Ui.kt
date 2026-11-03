package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val BG = Color(0xFF1D1D1D)
val P2 = Color(0xFF262626)
val P3 = Color(0xFF383838)
val AC = Color(0xFF2D8CEB)
val LAV = Color(0xFF9999FF)
val GR = Color(0xFFA0A0A0)

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
fun Sld(v: Float, lo: Float, hi: Float, c: Cur, f: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    val set = { o: Offset -> f(lo + (o.x / w).coerceIn(0f, 1f) * (hi - lo)) }
    val fr = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
    Box(Modifier.width(160.dp).height(22.dp).onSizeChanged { w = it.width.toFloat() }.cur(c, set, null, null, set)) {
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
fun Top(p: Project, c: Cur, comp: Comp, onBack: () -> Unit, onSet: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(P2).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Btn("Home", c) { onBack() }
        Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Btn("+ Layer", c) { comp.add() }
        Btn("Preferences", c) { onSet() }
        Btn(if (c.on) "Cursor on" else "Cursor off", c) { c.on = !c.on }
    }
}

@Composable
fun Props(comp: Comp, c: Cur, m: Modifier) {
    Column(m.background(P2)) {
        Hdr("Properties")
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val l = comp.layers.getOrNull(comp.sel)
            if (l != null) {
                Text(l.name, color = AC, fontSize = 12.sp)
                PN.forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(n, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Btn("-", c) { bump(l.p[i], comp.time, -ST[i]) }
                        Text("%.2f".format(ev(l.p[i], comp.time)), color = Color.White, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                        Btn("+", c) { bump(l.p[i], comp.time, ST[i]) }
                    }
                }
            }
        }
    }
}

@Composable
fun Timeline(comp: Comp, c: Cur, m: Modifier) {
    var w by remember { mutableStateOf(1f) }
    val seek = { x: Float -> comp.time = (x / w * comp.dur).coerceIn(0f, comp.dur) }
    Column(m.background(P2)) {
        Hdr("Timeline  " + comp.name)
        Row(
            Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Btn(if (comp.playing) "Pause" else "Play", c) { comp.playing = !comp.playing }
            Btn("Start", c) { comp.time = 0f }
            Text(tc(comp.time, comp.fps), color = Color.White, fontSize = 12.sp)
            Text("of " + tc(comp.dur, comp.fps), color = GR, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(120.dp)) {
                Spacer(Modifier.height(24.dp))
                comp.layers.forEachIndexed { i, l ->
                    Box(
                        Modifier.fillMaxWidth().height(28.dp)
                            .background(if (i == comp.sel) AC.copy(alpha = 0.35f) else Color.Transparent)
                            .cur(c, rc = mnu(c, "Duplicate" to { comp.dup(i) }, "Delete" to { comp.del(i) })) { comp.sel = i }
                            .padding(start = 8.dp),
                        Alignment.CenterStart
                    ) { Text(l.name, color = Color.White, fontSize = 12.sp) }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { w = it.width.toFloat() }) {
                Column {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(P3).cur(c, { seek(it.x) }) { seek(it.x) })
                    comp.layers.forEachIndexed { i, l ->
                        Canvas(
                            Modifier.fillMaxWidth().height(28.dp)
                                .background(if (i == comp.sel) AC.copy(alpha = 0.15f) else Color.Transparent)
                                .cur(c, rc = mnu(c, "Duplicate" to { comp.dup(i) }, "Delete" to { comp.del(i) })) { comp.sel = i }
                        ) {
                            val ts = l.p.flatMap { k -> k.map { it.t } }.distinct()
                            ts.forEach { t ->
                                val x = t / comp.dur * size.width
                                val y = size.height / 2
                                rotate(45f, Offset(x, y)) { drawRect(Color(0xFFE2C84A), Offset(x - 5f, y - 5f), Size(10f, 10f)) }
                            }
                        }
                    }
                }
                Canvas(Modifier.matchParentSize()) {
                    val x = comp.time / comp.dur * size.width
                    drawLine(Color(0xFFE2574A), Offset(x, 0f), Offset(x, size.height), 2f)
                }
            }
        }
    }
}

@Composable
fun Editor(p: Project, c: Cur, onBack: () -> Unit, onSet: () -> Unit) {
    val ctx = LocalContext.current
    val comp = p.comps[0]
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
        }
    }
    Column(Modifier.fillMaxSize()) {
        Top(p, c, comp, onBack, onSet)
        Row(Modifier.fillMaxWidth().weight(1.2f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Hdr("Composition  " + comp.name + "  " + comp.w + " x " + comp.h)
                Viewer(comp, Modifier.weight(1f).fillMaxWidth())
            }
            Props(comp, c, Modifier.width(200.dp).fillMaxHeight())
        }
        Timeline(comp, c, Modifier.fillMaxWidth().weight(1f))
    }
}
