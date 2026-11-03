package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BG = Color(0xFF1B1B1B)
val P2 = Color(0xFF262626)
val P3 = Color(0xFF3A3A3A)
val AC = Color(0xFF4A90E2)

@Composable
fun Btn(t: String, c: Cur, f: () -> Unit) {
    Box(Modifier.background(P3, RoundedCornerShape(4.dp)).cur(c) { f() }.padding(horizontal = 10.dp, vertical = 5.dp), Alignment.Center) {
        Text(t, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun Top(comp: Comp, c: Cur) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(P2).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Kinetic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Btn("+ Layer", c) { comp.add() }
        Btn(if (c.on) "Cursor on" else "Cursor off", c) { c.on = !c.on }
    }
}

@Composable
fun Props(comp: Comp, c: Cur, m: Modifier) {
    Column(m.background(P2).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Properties", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

@Composable
fun Timeline(comp: Comp, c: Cur, m: Modifier) {
    var w by remember { mutableStateOf(1f) }
    val seek = { x: Float -> comp.time = (x / w * comp.dur).coerceIn(0f, comp.dur) }
    Column(m.background(P2)) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Btn(if (comp.playing) "Pause" else "Play", c) { comp.playing = !comp.playing }
            Btn("Start", c) { comp.time = 0f }
            Text("%.2fs / %.0fs".format(comp.time, comp.dur), color = Color.White, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(120.dp)) {
                Spacer(Modifier.height(24.dp))
                comp.layers.forEachIndexed { i, l ->
                    Box(
                        Modifier.fillMaxWidth().height(28.dp)
                            .background(if (i == comp.sel) AC.copy(alpha = 0.35f) else Color.Transparent)
                            .cur(c) { comp.sel = i }
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
                                .cur(c) { comp.sel = i }
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
fun App() {
    val comp = remember { Comp() }
    val c = remember { Cur() }
    LaunchedEffect(comp.playing) {
        var last = 0L
        while (comp.playing) {
            val n = withFrameNanos { it }
            if (last != 0L) comp.time = (comp.time + (n - last) / 1e9f) % comp.dur
            last = n
        }
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(BG)) {
            Column(Modifier.fillMaxSize()) {
                Top(comp, c)
                Row(Modifier.fillMaxWidth().weight(1.2f)) {
                    Viewer(comp, Modifier.weight(1f).fillMaxHeight())
                    Props(comp, c, Modifier.width(200.dp).fillMaxHeight())
                }
                Timeline(comp, c, Modifier.fillMaxWidth().weight(1f))
            }
            if (c.on) CursorLayer(c)
        }
    }
}
