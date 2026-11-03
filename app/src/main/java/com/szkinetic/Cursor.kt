package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalZ = compositionLocalOf { 0 }

class Menu(val pos: Offset, val items: List<Pair<String, () -> Unit>>)

class Hit(
    var r: Rect,
    var click: (Offset) -> Unit,
    var drag: ((Offset) -> Unit)?,
    var rc: ((Offset) -> Unit)?,
    var sc: ((Float) -> Unit)?,
    val z: Int
)

class Cur(val st: Settings) {
    var on: Boolean
        get() = st.cursor
        set(v) {
            st.cursor = v
            st.save()
        }
    var pos by mutableStateOf(Offset(400f, 300f))
    var slow by mutableStateOf(false)
    var lock by mutableStateOf(false)
    var menu by mutableStateOf<Menu?>(null)
    var sz = Size.Zero
    val hits = mutableListOf<Hit>()
    var grab: Hit? = null

    fun at(): Hit? {
        val a = hits.filter { it.r.contains(pos) }
        val m = a.maxOfOrNull { it.z } ?: return null
        return a.filter { it.z == m }.minByOrNull { it.r.width * it.r.height }
    }

    fun press(b: Int) {
        val h = at()
        if (b == 0) {
            if (h != null) h.click(pos - h.r.topLeft)
            grab = h
        } else {
            menu = null
            h?.rc?.invoke(pos)
        }
    }

    fun release(b: Int) {
        if (b == 0) grab = null
    }

    fun tap() {
        press(0)
        release(0)
    }

    fun move(d: Offset) {
        val f = if (slow) st.speed * 0.2f else st.speed
        pos = Offset((pos.x + d.x * f).coerceIn(0f, sz.width), (pos.y + d.y * f).coerceIn(0f, sz.height))
        grab?.let { g -> g.drag?.invoke(pos - g.r.topLeft) }
    }

    fun scroll(dy: Float) {
        val a = hits.filter { it.sc != null && it.r.contains(pos) }
        val m = a.maxOfOrNull { it.z } ?: return
        a.filter { it.z == m }.minByOrNull { it.r.width * it.r.height }?.sc?.invoke(dy)
    }
}

fun mnu(c: Cur, vararg i: Pair<String, () -> Unit>): (Offset) -> Unit = { p -> c.menu = Menu(p, i.toList()) }

fun Modifier.cur(
    c: Cur,
    drag: ((Offset) -> Unit)? = null,
    rc: ((Offset) -> Unit)? = null,
    sc: ((Float) -> Unit)? = null,
    click: (Offset) -> Unit = {}
): Modifier = composed {
    val z = LocalZ.current
    val h = remember { Hit(Rect.Zero, click, drag, rc, sc, z) }
    h.click = click
    h.drag = drag
    h.rc = rc
    h.sc = sc
    DisposableEffect(h) {
        c.hits.add(h)
        onDispose { c.hits.remove(h) }
    }
    this.onGloballyPositioned { h.r = it.boundsInRoot() }
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = { h.rc?.invoke(h.r.topLeft + it) }, onTap = { h.click(it) })
        }
        .pointerInput(Unit) {
            if (h.drag != null) detectDragGestures { ch, _ ->
                ch.consume()
                h.drag?.invoke(ch.position)
            }
        }
}

fun Modifier.scr(c: Cur, f: (Float) -> Unit): Modifier = composed {
    val z = LocalZ.current
    val h = remember { Hit(Rect.Zero, {}, null, null, f, z) }
    h.sc = f
    DisposableEffect(h) {
        c.hits.add(h)
        onDispose { c.hits.remove(h) }
    }
    this.onGloballyPositioned { h.r = it.boundsInRoot() }
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
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                var tot = 0f
                var multi = false
                do {
                    val e = awaitPointerEvent()
                    val ch = e.changes
                    if (ch.size > 1) {
                        multi = true
                        c.scroll(ch.map { it.positionChange().y }.average().toFloat())
                    } else if (!multi) {
                        val d = ch[0].positionChange()
                        tot += d.getDistance()
                        if (tot > 8f && d != Offset.Zero) c.move(d)
                    }
                    ch.forEach { it.consume() }
                } while (ch.any { it.pressed })
                if (!multi && tot < 12f) c.tap()
            }
        }
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
            if (c.slow) drawCircle(Color(0x662D8CEB), 40f, p)
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Slow", c.slow) { c.slow = !c.slow }
            Chip("Right", false) { c.press(1) }
            Chip("Lock", c.lock) {
                c.lock = !c.lock
                if (c.lock) c.press(0) else c.release(0)
            }
            Chip("Off", false) { c.on = false }
        }
    }
}
