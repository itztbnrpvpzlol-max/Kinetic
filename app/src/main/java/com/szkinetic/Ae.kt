package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- After Effects palette ---------------------------------------------------
val PB = Color(0xFF232323)      // panel body
val PH = Color(0xFF1A1A1A)      // tab strip
val CH = Color(0xFF2B2B2B)      // column header row
val VB = Color(0xFF4E9EF0)      // blue values / current time
val SR = Color(0xFF4B4B4B)      // selected row
val DIM = Color(0xFF8A8A8A)
val TXT = Color(0xFFD6D6D6)
val KSEL = Color(0xFFE8C14A)
val KNRM = Color(0xFFB9B9B9)

val AE_FONT = 10.sp

// 0:00:05:10
fun aeTc(f: Int, fps: Int): String {
    val s = f / fps
    return "%d:%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60, f % fps)
}

fun fpsTxt(fps: Int): String = "%.2f".format(fps.toFloat())

// Panel with a tab strip like After Effects: tab labels with a ≡ menu glyph, the active tab underlined.
@Composable
fun AePn(
    tabs: List<String>, act: Int, f: Boolean, c: Cur, onF: () -> Unit, m: Modifier,
    onTab: (Int) -> Unit = {}, body: @Composable ColumnScope.() -> Unit
) {
    Column(m.background(PB).border(1.dp, if (f) AC else EDG)) {
        Row(Modifier.fillMaxWidth().height(22.dp).background(PH), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, t ->
                Box(Modifier.fillMaxHeight().cur(c) { onTab(i); onF() }.padding(horizontal = 8.dp), Alignment.Center) {
                    Text(
                        t + "  \u2261", color = if (i == act) Color.White else DIM, fontSize = AE_FONT,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (i == act) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color(0xFFD0D0D0)))
                }
            }
        }
        body()
    }
}

// Flat dropdown (parent column, zoom, quality, ...)
@Composable
fun Dd(t: String, c: Cur, mod: Modifier = Modifier, col: Color = TXT, items: () -> List<Pair<String, () -> Unit>>) {
    var r by remember { mutableStateOf(Rect.Zero) }
    Box(
        mod.onGloballyPositioned { r = it.boundsInRoot() }
            .background(Color(0xFF353535))
            .cur(c) { c.menu = Menu(Offset(r.left, r.bottom), items()) }
            .padding(horizontal = 5.dp, vertical = 2.dp),
        Alignment.CenterStart
    ) { Text(t + " \u25BE", color = col, fontSize = AE_FONT, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

fun DrawScope.starPath(cx: Float, cy: Float, ro: Float, ri: Float): Path {
    val p = Path()
    for (i in 0 until 10) {
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val r = if (i % 2 == 0) ro else ri
        val x = cx + (Math.cos(a) * r).toFloat()
        val y = cy + (Math.sin(a) * r).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

// Keyframe glyph: diamond = linear, circle = bezier / easy ease, square = hold.
fun DrawScope.kfDraw(x: Float, y: Float, ip: Int, sel: Boolean, r: Float) {
    val col = if (sel) KSEL else KNRM
    when (ip) {
        1 -> drawCircle(col, r * 0.85f, Offset(x, y))
        2 -> drawRect(col, Offset(x - r * 0.75f, y - r * 0.75f), Size(r * 1.5f, r * 1.5f))
        else -> rotate(45f, Offset(x, y)) {
            drawRect(col, Offset(x - r * 0.72f, y - r * 0.72f), Size(r * 1.44f, r * 1.44f))
        }
    }
}

// Small icons. 0 eye, 1 speaker, 2 solo, 3 lock, 4 shy, 5 pick whip, 6 search, 7 graph editor,
// 8 twirl closed, 9 twirl open, 10 prev key, 11 next key
@Composable
fun Ix(k: Int, on: Boolean, c: Cur, w: Dp = 18.dp, h: Dp = 24.dp, f: () -> Unit) {
    Canvas(Modifier.size(w, h).cur(c) { f() }) {
        val col = if (on) Color(0xFFD0D0D0) else Color(0xFF4A4A4A)
        val cx = size.width / 2
        val cy = size.height / 2
        val u = 1.dp.toPx()
        when (k) {
            0 -> {
                drawOval(col, Offset(cx - 6 * u, cy - 3.5f * u), Size(12 * u, 7 * u), style = Stroke(1.3f * u))
                drawCircle(col, 2 * u, Offset(cx, cy))
            }
            1 -> {
                val p = Path().apply {
                    moveTo(cx - 5 * u, cy - 2 * u)
                    lineTo(cx - 2 * u, cy - 2 * u)
                    lineTo(cx + 2 * u, cy - 5 * u)
                    lineTo(cx + 2 * u, cy + 5 * u)
                    lineTo(cx - 2 * u, cy + 2 * u)
                    lineTo(cx - 5 * u, cy + 2 * u)
                    close()
                }
                drawPath(p, col)
                drawArc(col, -50f, 100f, false, Offset(cx - 1 * u, cy - 4 * u), Size(8 * u, 8 * u), style = Stroke(1.2f * u))
            }
            2 -> {
                drawCircle(col, 5 * u, Offset(cx, cy), style = Stroke(1.3f * u))
                if (on) drawCircle(col, 2.2f * u, Offset(cx, cy))
            }
            3 -> {
                drawRect(col, Offset(cx - 4 * u, cy - 1 * u), Size(8 * u, 6 * u))
                drawArc(col, 180f, 180f, false, Offset(cx - 3 * u, cy - 6 * u), Size(6 * u, 10 * u), style = Stroke(1.3f * u))
            }
            4 -> {
                drawCircle(col, 2 * u, Offset(cx, cy - 3.5f * u))
                drawArc(col, 180f, 180f, true, Offset(cx - 4 * u, cy - 0.5f * u), Size(8 * u, 8 * u))
            }
            5 -> {
                drawCircle(col, 5 * u, Offset(cx, cy), style = Stroke(1.2f * u))
                drawCircle(col, 2.5f * u, Offset(cx, cy), style = Stroke(1.2f * u))
                drawCircle(col, 0.8f * u, Offset(cx, cy))
            }
            6 -> {
                drawCircle(col, 4 * u, Offset(cx - 1 * u, cy - 1 * u), style = Stroke(1.3f * u))
                drawLine(col, Offset(cx + 2 * u, cy + 2 * u), Offset(cx + 5.5f * u, cy + 5.5f * u), 1.5f * u)
            }
            7 -> {
                drawRect(col, Offset(cx - 6 * u, cy - 6 * u), Size(12 * u, 12 * u), style = Stroke(1.1f * u))
                val p = Path().apply {
                    moveTo(cx - 5 * u, cy + 4 * u)
                    cubicTo(cx - 1 * u, cy + 4 * u, cx - 1 * u, cy - 4 * u, cx + 5 * u, cy - 4 * u)
                }
                drawPath(p, col, style = Stroke(1.3f * u))
            }
            8 -> {
                val p = Path().apply {
                    moveTo(cx - 2 * u, cy - 4 * u)
                    lineTo(cx + 3 * u, cy)
                    lineTo(cx - 2 * u, cy + 4 * u)
                    close()
                }
                drawPath(p, col)
            }
            9 -> {
                val p = Path().apply {
                    moveTo(cx - 4 * u, cy - 2 * u)
                    lineTo(cx + 4 * u, cy - 2 * u)
                    lineTo(cx, cy + 3 * u)
                    close()
                }
                drawPath(p, col)
            }
            10 -> {
                val p = Path().apply {
                    moveTo(cx + 3 * u, cy - 4 * u)
                    lineTo(cx - 2 * u, cy)
                    lineTo(cx + 3 * u, cy + 4 * u)
                    close()
                }
                drawPath(p, col)
            }
            else -> {
                val p = Path().apply {
                    moveTo(cx - 3 * u, cy - 4 * u)
                    lineTo(cx + 2 * u, cy)
                    lineTo(cx - 3 * u, cy + 4 * u)
                    close()
                }
                drawPath(p, col)
            }
        }
    }
}

// Toolbar tools. 0 home, 1 selection, 2 hand, 3 zoom, 4 rotate, 5 camera, 6 shape, 7 pen, 8 text, 9 brush, 10 eraser, 11 puppet pin
@Composable
fun ToolIc(k: Int, on: Boolean, c: Cur, f: () -> Unit) {
    Box(
        Modifier.size(26.dp, 24.dp).background(if (on) Color(0xFF4A4A4A) else Color.Transparent).cur(c) { f() },
        Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp, 18.dp)) {
            val col = Color(0xFFD0D0D0)
            val u = 1.dp.toPx()
            val cx = size.width / 2
            val cy = size.height / 2
            val sw = Stroke(1.3f * u)
            when (k) {
                0 -> {
                    val p = Path().apply {
                        moveTo(cx - 7 * u, cy)
                        lineTo(cx, cy - 6 * u)
                        lineTo(cx + 7 * u, cy)
                        lineTo(cx + 5 * u, cy)
                        lineTo(cx + 5 * u, cy + 6 * u)
                        lineTo(cx - 5 * u, cy + 6 * u)
                        lineTo(cx - 5 * u, cy)
                        close()
                    }
                    drawPath(p, col)
                }
                1 -> {
                    val p = Path().apply {
                        moveTo(cx - 4 * u, cy - 7 * u)
                        lineTo(cx - 4 * u, cy + 5 * u)
                        lineTo(cx - 1 * u, cy + 2 * u)
                        lineTo(cx + 2 * u, cy + 8 * u)
                        lineTo(cx + 4 * u, cy + 7 * u)
                        lineTo(cx + 1 * u, cy + 1 * u)
                        lineTo(cx + 5 * u, cy + 1 * u)
                        close()
                    }
                    drawPath(p, col)
                }
                2 -> {
                    drawRoundRect(col, Offset(cx - 5 * u, cy - 1 * u), Size(10 * u, 8 * u), androidx.compose.ui.geometry.CornerRadius(3 * u))
                    for (i in 0 until 4) drawLine(col, Offset(cx - 4 * u + i * 2.7f * u, cy - 1 * u), Offset(cx - 4 * u + i * 2.7f * u, cy - 7 * u), 1.6f * u)
                }
                3 -> {
                    drawCircle(col, 5 * u, Offset(cx - 1 * u, cy - 1 * u), style = sw)
                    drawLine(col, Offset(cx + 3 * u, cy + 3 * u), Offset(cx + 7 * u, cy + 7 * u), 1.8f * u)
                }
                4 -> {
                    drawArc(col, 40f, 280f, false, Offset(cx - 6 * u, cy - 6 * u), Size(12 * u, 12 * u), style = sw)
                    drawLine(col, Offset(cx + 5 * u, cy - 6 * u), Offset(cx + 6 * u, cy - 1 * u), 1.6f * u)
                }
                5 -> {
                    drawRect(col, Offset(cx - 7 * u, cy - 4 * u), Size(11 * u, 8 * u), style = sw)
                    drawLine(col, Offset(cx + 4 * u, cy), Offset(cx + 7 * u, cy - 3 * u), 1.6f * u)
                    drawLine(col, Offset(cx + 4 * u, cy), Offset(cx + 7 * u, cy + 3 * u), 1.6f * u)
                }
                6 -> drawRect(col, Offset(cx - 6 * u, cy - 5 * u), Size(12 * u, 10 * u), style = sw)
                7 -> {
                    val p = Path().apply {
                        moveTo(cx, cy - 7 * u)
                        lineTo(cx + 4 * u, cy + 1 * u)
                        lineTo(cx, cy + 4 * u)
                        lineTo(cx - 4 * u, cy + 1 * u)
                        close()
                    }
                    drawPath(p, col)
                    drawLine(col, Offset(cx, cy + 4 * u), Offset(cx, cy + 8 * u), 1.6f * u)
                }
                8 -> {
                    drawLine(col, Offset(cx - 5 * u, cy - 6 * u), Offset(cx + 5 * u, cy - 6 * u), 1.8f * u)
                    drawLine(col, Offset(cx, cy - 6 * u), Offset(cx, cy + 7 * u), 1.8f * u)
                }
                9 -> {
                    drawLine(col, Offset(cx + 5 * u, cy - 7 * u), Offset(cx - 2 * u, cy + 1 * u), 2.4f * u)
                    drawCircle(col, 3 * u, Offset(cx - 4 * u, cy + 4 * u))
                }
                10 -> rotate(-35f, Offset(cx, cy)) { drawRect(col, Offset(cx - 6 * u, cy - 3 * u), Size(12 * u, 6 * u), style = sw) }
                else -> {
                    drawCircle(col, 3 * u, Offset(cx, cy - 3 * u))
                    drawLine(col, Offset(cx, cy), Offset(cx, cy + 7 * u), 1.6f * u)
                }
            }
        }
    }
}
