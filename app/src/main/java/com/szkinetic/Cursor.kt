package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class Hit(var r: Rect, var click: (Offset) -> Unit, var drag: ((Offset) -> Unit)?)

class Cur {
    var on by mutableStateOf(true)
    var pos by mutableStateOf(Offset(400f, 300f))
    var slow by mutableStateOf(false)
    var hold by mutableStateOf(false)
    var sz = Size.Zero
    val hits = mutableListOf<Hit>()
    var grab: Hit? = null

    fun at(): Hit? = hits.lastOrNull { it.r.contains(pos) }

    fun tap() {
        at()?.let { it.click(pos - it.r.topLeft) }
    }

    fun move(d: Offset) {
        val f = if (slow) 0.3f else 1.6f
        pos = Offset((pos.x + d.x * f).coerceIn(0f, sz.width), (pos.y + d.y * f).coerceIn(0f, sz.height))
        grab?.let { g -> g.drag?.invoke(pos - g.r.topLeft) }
    }

    fun setHold(v: Boolean) {
        hold = v
        if (v) {
            val h = at()
            h?.let { it.click(pos - it.r.topLeft) }
            grab = h
        } else grab = null
    }
}

fun Modifier.cur(c: Cur, drag: ((Offset) -> Unit)? = null, click: (Offset) -> Unit): Modifier = composed {
    val h = remember { Hit(Rect.Zero, click, drag) }
    h.click = click
    h.drag = drag
    DisposableEffect(h) {
        c.hits.add(h)
        onDispose { c.hits.remove(h) }
    }
    this.onGloballyPositioned { h.r = it.boundsInRoot() }
        .pointerInput(Unit) { detectTapGestures { h.click(it) } }
        .pointerInput(Unit) {
            if (h.drag != null) detectDragGestures { ch, _ ->
                ch.consume()
                h.drag?.invoke(ch.position)
            }
        }
}

@Composable
fun Chip(t: String, a: Boolean, f: () -> Unit) {
    Box(Modifier.background(if (a) AC else P3, RoundedCornerShape(16.dp)).clickable { f() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(t, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun CursorLayer(c: Cur) {
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { c.sz = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) { detectTapGestures { c.tap() } }
            .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); c.move(d) } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = c.pos
            val a = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x, p.y + 34f)
                lineTo(p.x + 9f, p.y + 26f)
                lineTo(p.x + 15f, p.y + 40f)
                lineTo(p.x + 21f, p.y + 37f)
                lineTo(p.x + 15f, p.y + 24f)
                lineTo(p.x + 26f, p.y + 24f)
                close()
            }
            drawPath(a, Color.White)
            drawPath(a, Color.Black, style = Stroke(2f))
            if (c.slow) drawCircle(Color(0x664A90E2), 40f, p)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Slow", c.slow) { c.slow = !c.slow }
            Chip("Hold", c.hold) { c.setHold(!c.hold) }
            Chip("Exit", false) { c.on = false }
        }
    }
}
