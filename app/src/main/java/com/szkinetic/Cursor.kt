package com.szkinetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
    var sc: ((Offset) -> Unit)?,
    val z: Int
) {
    var ds: ((Offset) -> Unit)? = null
    var gate: (() -> Boolean)? = null
}

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
    val hits = ArrayList<Hit>()
    var grab: Hit? = null
    var sg: Hit? = null
    private var dc: Hit? = null
    private var dp = Offset.Zero
    private var mv = 0f

    private fun top(p: Offset): Int {
        var m = -1
        for (i in hits.indices) {
            val h = hits[i]
            if (h.z > m && h.r.contains(p)) m = h.z
        }
        return m
    }

    private fun pick(p: Offset, sc: Boolean): Hit? {
        val m = top(p)
        if (m < 0) return null
        var b: Hit? = null
        var ba = Float.MAX_VALUE
        for (i in hits.indices) {
            val h = hits[i]
            if (h.z != m || !h.r.contains(p) || (sc && h.sc == null)) continue
            val a = h.r.width * h.r.height
            if (a < ba) {
                ba = a
                b = h
            }
        }
        return b
    }

    fun at(): Hit? = pick(pos, false)

    fun press(b: Int) {
        if (b == 0) {
            val h = at()
            sg = pick(pos, true)
            dc = null
            mv = 0f
            if (h != null && h.drag == null && h.sc == null && sg != null) {
                dc = h
                dp = pos - h.r.topLeft
                grab = null
            } else {
                if (h != null) h.click(pos - h.r.topLeft)
                grab = if (h != null && h.drag != null && (h.gate?.invoke() ?: true)) h else null
            }
        } else {
            menu = null
            at()?.rc?.invoke(pos)
        }
    }

    fun release(b: Int) {
        if (b == 0) {
            val d = dc
            if (d != null && mv < 12f) d.click(dp)
            dc = null
            grab = null
            sg = null
        }
    }

    fun tap() {
        press(0)
        release(0)
    }

    fun move(d: Offset) {
        val f = if (slow) st.speed * 0.2f else st.speed
        val o = pos
        val n = Offset((o.x + d.x * f).coerceIn(0f, sz.width), (o.y + d.y * f).coerceIn(0f, sz.height))
        pos = n
        if (dc != null) mv += (n - o).getDistance()
        val g = grab
        if (g != null) g.drag?.invoke(n - g.r.topLeft) else sg?.sc?.invoke(n - o)
    }

    fun scroll(d: Offset) {
        pick(pos, true)?.sc?.invoke(d)
    }
}

fun mnu(c: Cur, vararg i: Pair<String, () -> Unit>): (Offset) -> Unit = { p -> c.menu = Menu(p, i.toList()) }

fun Modifier.cur(
    c: Cur,
    drag: ((Offset) -> Unit)? = null,
    rc: ((Offset) -> Unit)? = null,
    sc: ((Offset) -> Unit)? = null,
    ds: ((Offset) -> Unit)? = null,
    gate: (() -> Boolean)? = null,
    click: (Offset) -> Unit = {}
): Modifier = composed {
    val z = LocalZ.current
    val h = remember { Hit(Rect.Zero, click, drag, rc, sc, z) }
    h.click = click
    h.drag = drag
    h.rc = rc
    h.sc = sc
    h.ds = ds
    h.gate = gate
    DisposableEffect(h) {
        c.hits.add(h)
        onDispose { c.hits.remove(h) }
    }
    val on = c.on
    this.onGloballyPositioned { h.r = it.boundsInRoot() }
        .pointerInput(on) {
            if (!on) detectTapGestures(onLongPress = { h.rc?.invoke(h.r.topLeft + it) }, onTap = { h.click(it) })
        }
        .pointerInput(on) {
            if (!on && h.drag != null) detectDragGestures(onDragStart = { h.ds?.invoke(it) }) { ch, _ ->
                ch.consume()
                h.drag?.invoke(ch.position)
            }
        }
}

fun Modifier.scr(c: Cur, f: (Offset) -> Unit): Modifier = composed {
    val z = LocalZ.current
    val h = remember { Hit(Rect.Zero, {}, null, null, f, z) }
    h.sc = f
    DisposableEffect(h) {
        c.hits.add(h)
        onDispose { c.hits.remove(h) }
    }
    this.onGloballyPositioned { h.r = it.boundsInRoot() }
}

fun Modifier.vscr(c: Cur, rs: ScrollState): Modifier = scr(c) { rs.dispatchRawDelta(-it.y) }.verticalScroll(rs)

@Composable
fun Chip(t: String, a: Boolean, f: () -> Unit) {
    Box(Modifier.background(if (a) AC else P3, RoundedCornerShape(16.dp)).clickable { f() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(t, color = Color.White, fontSize = 12.sp)
    }
}

private var apK = -1f
private var apP: Path? = null

private fun arrowPath(k: Float): Path {
    val q = apP
    if (q != null && apK == k) return q
    val a = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 34f * k)
        lineTo(9f * k, 26f * k)
        lineTo(15f * k, 40f * k)
        lineTo(21f * k, 37f * k)
        lineTo(15f * k, 24f * k)
        lineTo(26f * k, 24f * k)
        close()
    }
    apP = a
    apK = k
    return a
}

fun DrawScope.arrow(p: Offset, k: Float) {
    val a = arrowPath(k)
    translate(p.x, p.y) {
        drawPath(a, Color.White)
        drawPath(a, Color.Black, style = Stroke(maxOf(2f * k, 1.5f)))
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
                        var sx = 0f
                        var sy = 0f
                        for (i in ch.indices) {
                            val p = ch[i].positionChange()
                            sx += p.x
                            sy += p.y
                        }
                        c.scroll(Offset(sx / ch.size, sy / ch.size))
                    } else if (!multi) {
                        val d = ch[0].positionChange()
                        tot += d.getDistance()
                        if (tot > 8f && d != Offset.Zero) c.move(d)
                    }
                    for (i in ch.indices) ch[i].consume()
                } while (ch.any { it.pressed })
                if (!multi && tot < 12f) c.tap()
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = c.st.csize
            arrow(c.pos, k)
            if (c.slow) drawCircle(Color(0x662D8CEB), 40f * k, c.pos)
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
