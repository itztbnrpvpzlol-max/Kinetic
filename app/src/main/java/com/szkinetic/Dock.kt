package com.szkinetic

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

val PANELS = listOf("proj", "comp", "time", "info", "browser", "plugins")

fun panelTitle(id: String): String = when (id) {
    "proj" -> "Project"
    "comp" -> "Composition"
    "time" -> "Timeline"
    "browser" -> "Effects & Presets"
    "plugins" -> "Plugins"
    else -> "Info"
}

var gidN = 1

// ---------------------------------------------------------------- layout tree
sealed class LNode

// A tab group showing one panel at a time.
class LGroup(tabs: List<String>, act: Int = 0, val gid: Int = gidN++) : LNode() {
    val tabs = mutableStateListOf<String>().also { it.addAll(tabs) }
    var act by mutableStateOf(act)
}

// vert = children are stacked top to bottom (the dividers are horizontal bars).
class LSplit(val vert: Boolean, val kids: List<LNode>, ws: List<Float>) : LNode() {
    val ws = mutableStateListOf<Float>().also { it.addAll(ws) }
}

fun defaultTree(): LNode = LSplit(
    true,
    listOf(
        LSplit(
            false,
            listOf(
                LGroup(listOf("proj")),
                LGroup(listOf("comp")),
                LGroup(listOf("info", "browser", "plugins"))
            ),
            listOf(0.22f, 0.56f, 0.22f)
        ),
        LGroup(listOf("time"))
    ),
    listOf(1.1f, 1f)
)

// Builds a split, flattening children that split in the same direction and collapsing single children.
fun mk(vert: Boolean, kids: List<LNode>, ws: List<Float>): LNode {
    val k = ArrayList<LNode>()
    val w = ArrayList<Float>()
    for (i in kids.indices) {
        val x = kids[i]
        if (x is LSplit && x.vert == vert) {
            val s = x.ws.sum()
            for (j in x.kids.indices) {
                k.add(x.kids[j])
                w.add(ws[i] * x.ws[j] / s)
            }
        } else {
            k.add(x)
            w.add(ws[i])
        }
    }
    return if (k.size == 1) k[0] else LSplit(vert, k, w)
}

fun panelsIn(n: LNode, out: MutableList<String> = ArrayList()): List<String> {
    when (n) {
        is LGroup -> out.addAll(n.tabs)
        is LSplit -> for (k in n.kids) panelsIn(k, out)
    }
    return out
}

fun firstGid(n: LNode): Int = when (n) {
    is LGroup -> n.gid
    is LSplit -> firstGid(n.kids[0])
}

fun hasGroup(n: LNode, gid: Int): Boolean = when (n) {
    is LGroup -> n.gid == gid
    is LSplit -> n.kids.any { hasGroup(it, gid) }
}

fun removePanel(n: LNode, id: String): LNode? {
    if (n is LGroup) {
        if (id !in n.tabs) return n
        val t = n.tabs.filter { it != id }
        if (t.isEmpty()) return null
        return LGroup(t, minOf(n.act, t.size - 1), n.gid)
    }
    val sp = n as LSplit
    val nk = ArrayList<LNode>()
    val nw = ArrayList<Float>()
    for (i in sp.kids.indices) {
        val k = removePanel(sp.kids[i], id)
        if (k != null) {
            nk.add(k)
            nw.add(sp.ws[i])
        }
    }
    if (nk.isEmpty()) return null
    return mk(sp.vert, nk, nw)
}

// zone: 0 center (tab), 1 left, 2 right, 3 top, 4 bottom
private fun putAt(n: LNode, gid: Int, zone: Int, id: String): LNode {
    if (n is LGroup) {
        if (n.gid != gid) return n
        if (zone == 0) return LGroup(n.tabs.toList() + id, n.tabs.size, n.gid)
        val ng = LGroup(listOf(id))
        val first = zone == 1 || zone == 3
        return mk(
            zone >= 3,
            if (first) listOf(ng, n) else listOf(n, ng),
            if (first) listOf(0.4f, 0.6f) else listOf(0.6f, 0.4f)
        )
    }
    val sp = n as LSplit
    return mk(sp.vert, sp.kids.map { putAt(it, gid, zone, id) }, sp.ws.toList())
}

fun dockTo(root: LNode, gid: Int, zone: Int, id: String): LNode {
    val base = removePanel(root, id) ?: return root
    if (!hasGroup(base, gid)) return root
    return putAt(base, gid, zone, id)
}

fun addPanel(root: LNode, id: String): LNode {
    if (id in panelsIn(root)) return root
    return putAt(root, firstGid(root), 0, id)
}

// ---------------------------------------------------------------- persistence
fun toJs(n: LNode): JSONObject = when (n) {
    is LGroup -> JSONObject().put("t", "g").put("a", n.act).put("p", JSONArray(n.tabs.toList()))
    is LSplit -> JSONObject().put("t", "s").put("v", n.vert)
        .put("w", JSONArray(n.ws.map { it.toDouble() }))
        .put("k", JSONArray(n.kids.map { toJs(it) }))
}

private fun fromJs(o: JSONObject, seen: MutableSet<String>): LNode? {
    if (o.getString("t") == "g") {
        val a = o.getJSONArray("p")
        val tabs = ArrayList<String>()
        for (i in 0 until a.length()) {
            val id = a.getString(i)
            if (id in PANELS && seen.add(id)) tabs.add(id)
        }
        if (tabs.isEmpty()) return null
        return LGroup(tabs, o.optInt("a", 0).coerceIn(0, tabs.size - 1))
    }
    val k = o.getJSONArray("k")
    val w = o.getJSONArray("w")
    val kids = ArrayList<LNode>()
    val ws = ArrayList<Float>()
    for (i in 0 until k.length()) {
        val x = fromJs(k.getJSONObject(i), seen) ?: continue
        val wt = w.optDouble(i, 1.0).toFloat()
        kids.add(x)
        ws.add(if (wt > 0.01f) wt else 0.01f)
    }
    if (kids.isEmpty()) return null
    return mk(o.getBoolean("v"), kids, ws)
}

object LayoutStore {
    private fun sp(c: Context) = c.getSharedPreferences("kinetic_layouts", Context.MODE_PRIVATE)

    private fun parse(s: String?): LNode? {
        if (s == null) return null
        return try {
            fromJs(JSONObject(s), HashSet())
        } catch (e: Exception) {
            null
        }
    }

    fun saveCurrent(c: Context, n: LNode) {
        sp(c).edit().putString("cur", toJs(n).toString()).apply()
    }

    fun loadCurrent(c: Context): LNode? = parse(sp(c).getString("cur", null))
    fun names(c: Context): List<String> = sp(c).all.keys.filter { it.startsWith("L:") }.map { it.substring(2) }.sorted()

    fun save(c: Context, name: String, n: LNode) {
        sp(c).edit().putString("L:$name", toJs(n).toString()).apply()
    }

    fun load(c: Context, name: String): LNode? = parse(sp(c).getString("L:$name", null))

    fun delete(c: Context, name: String) {
        sp(c).edit().remove("L:$name").apply()
    }
}

// ---------------------------------------------------------------- docking UI
class DockCtx(val c: Cur, val getRoot: () -> LNode, val setRoot: (LNode) -> Unit) {
    var title: (String) -> String = { panelTitle(it) }
    var focus by mutableStateOf("time")
    var dragId by mutableStateOf<String?>(null)
    var dragPos by mutableStateOf(Offset.Zero)
    var dropGid by mutableStateOf(-1)
    var dropZone by mutableStateOf(0)
    val bounds = HashMap<Int, Rect>()

    fun hover(x: Float, y: Float) {
        var best = -1
        var ba = Float.MAX_VALUE
        for ((g, r) in bounds) {
            if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
                val a = r.width * r.height
                if (a < ba) {
                    ba = a
                    best = g
                }
            }
        }
        dropGid = best
        val r = bounds[best]
        if (r != null) {
            val fx = (x - r.left) / r.width
            val fy = (y - r.top) / r.height
            val m = minOf(minOf(fx, 1f - fx), minOf(fy, 1f - fy))
            dropZone = if (m > 0.25f) 0 else when (m) {
                fx -> 1
                1f - fx -> 2
                fy -> 3
                else -> 4
            }
        }
    }

    fun drop() {
        val id = dragId ?: return
        if (dropGid >= 0) setRoot(dockTo(getRoot(), dropGid, dropZone, id))
    }

    fun close(id: String) {
        setRoot(removePanel(getRoot(), id) ?: defaultTree())
    }
}

@Composable
fun DockTab(g: LGroup, i: Int, ctx: DockCtx) {
    val id = g.tabs[i]
    val rb = remember { floatArrayOf(0f, 0f) }
    val st = remember { floatArrayOf(0f, 0f, 0f) } // start x, start y, dragging
    val on = i == g.act
    val begin = { o: Offset ->
        st[0] = rb[0] + o.x
        st[1] = rb[1] + o.y
        st[2] = 0f
        g.act = i
        ctx.focus = id
        Unit
    }
    Box(
        Modifier.fillMaxHeight()
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                rb[0] = b.left
                rb[1] = b.top
            }
            .cur(
                ctx.c,
                drag = { o ->
                    val x = rb[0] + o.x
                    val y = rb[1] + o.y
                    if (st[2] == 0f && abs(x - st[0]) + abs(y - st[1]) > 16f) {
                        st[2] = 1f
                        ctx.dragId = id
                    }
                    if (st[2] == 1f) {
                        ctx.dragPos = Offset(x, y)
                        ctx.hover(x, y)
                    }
                },
                ds = begin,
                end = {
                    if (st[2] == 1f) ctx.drop()
                    st[2] = 0f
                    ctx.dragId = null
                }
            ) { o -> begin(o) }
            .padding(horizontal = 8.dp),
        Alignment.Center
    ) {
        Text(ctx.title(id), color = if (on) Color.White else DIM, fontSize = AE_FONT, maxLines = 1)
        if (on) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(Color(0xFFD0D0D0)))
    }
}

@Composable
fun DockGroup(g: LGroup, ctx: DockCtx, content: @Composable (String, Modifier) -> Unit, m: Modifier) {
    val cur = g.tabs.getOrNull(g.act) ?: g.tabs.firstOrNull()
    DisposableEffect(g.gid) { onDispose { ctx.bounds.remove(g.gid) } }
    val foc = cur != null && ctx.focus == cur
    var mr by remember { mutableStateOf(Rect.Zero) }
    Column(
        m.onGloballyPositioned { ctx.bounds[g.gid] = it.boundsInRoot() }
            .background(PB).border(1.dp, if (foc) AC else EDG)
    ) {
        Row(Modifier.fillMaxWidth().height(22.dp).background(PH), verticalAlignment = Alignment.CenterVertically) {
            for (i in g.tabs.indices) DockTab(g, i, ctx)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxHeight().onGloballyPositioned { mr = it.boundsInRoot() }
                    .cur(ctx.c) {
                        val id = cur
                        if (id != null) ctx.c.menu = Menu(
                            Offset(mr.left - 80f, mr.bottom),
                            listOf("Close panel" to { ctx.close(id) }, "Reset layout" to { ctx.setRoot(defaultTree()) })
                        )
                    }
                    .padding(horizontal = 8.dp),
                Alignment.Center
            ) { Text("\u2261", color = DIM, fontSize = 12.sp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (cur != null) content(cur, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Splitter(vert: Boolean, c: Cur, onDelta: (Float) -> Unit) {
    val rb = remember { floatArrayOf(0f, 0f, 0f) } // left, top, last position
    val pos = { o: Offset -> if (vert) rb[1] + o.y else rb[0] + o.x }
    Box(
        (if (vert) Modifier.fillMaxWidth().height(8.dp) else Modifier.fillMaxHeight().width(8.dp))
            .background(Color(0xFF111111))
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                rb[0] = b.left
                rb[1] = b.top
            }
            .cur(c, drag = { o ->
                val p = pos(o)
                onDelta(p - rb[2])
                rb[2] = p
            }, ds = { o -> rb[2] = pos(o) }) { o -> rb[2] = pos(o) },
        Alignment.Center
    ) {
        Box((if (vert) Modifier.fillMaxWidth().height(2.dp) else Modifier.fillMaxHeight().width(2.dp)).background(Color(0xFF3A3A3A)))
    }
}

@Composable
fun DockSplit(s: LSplit, ctx: DockCtx, content: @Composable (String, Modifier) -> Unit, m: Modifier) {
    var sz by remember { mutableStateOf(1f) }
    val apply = { i: Int, px: Float ->
        if (i + 1 < s.ws.size) {
            val tot = s.ws.sum()
            val mn = tot * 0.06f
            val a = s.ws[i]
            val b = s.ws[i + 1]
            if (b - mn >= mn - a) {
                val d = (px / sz * tot).coerceIn(mn - a, b - mn)
                s.ws[i] = a + d
                s.ws[i + 1] = b - d
            }
        }
        Unit
    }
    if (s.vert) {
        Column(m.onSizeChanged { sz = maxOf(it.height.toFloat(), 1f) }) {
            s.kids.forEachIndexed { i, k ->
                DockNode(k, ctx, content, Modifier.weight(s.ws[i]).fillMaxWidth())
                if (i < s.kids.size - 1) Splitter(true, ctx.c) { apply(i, it) }
            }
        }
    } else {
        Row(m.onSizeChanged { sz = maxOf(it.width.toFloat(), 1f) }) {
            s.kids.forEachIndexed { i, k ->
                DockNode(k, ctx, content, Modifier.weight(s.ws[i]).fillMaxHeight())
                if (i < s.kids.size - 1) Splitter(false, ctx.c) { apply(i, it) }
            }
        }
    }
}

@Composable
fun DockNode(n: LNode, ctx: DockCtx, content: @Composable (String, Modifier) -> Unit, m: Modifier) {
    when (n) {
        is LGroup -> DockGroup(n, ctx, content, m)
        is LSplit -> DockSplit(n, ctx, content, m)
    }
}

// Drop-zone highlight and the dragged tab's label. Must sit at the window origin.
@Composable
fun DockOverlay(ctx: DockCtx) {
    val id = ctx.dragId ?: return
    Canvas(Modifier.fillMaxSize()) {
        val r = ctx.bounds[ctx.dropGid]
        if (r != null) {
            val z = ctx.dropZone
            val x = if (z == 2) r.left + r.width / 2 else r.left
            val y = if (z == 4) r.top + r.height / 2 else r.top
            val w = if (z == 1 || z == 2) r.width / 2 else r.width
            val h = if (z == 3 || z == 4) r.height / 2 else r.height
            drawRect(Color(0x552D8CEB), Offset(x, y), Size(w, h))
            drawRect(Color(0xFF2D8CEB), Offset(x, y), Size(w, h), style = Stroke(2.dp.toPx()))
        }
    }
    Box(
        Modifier.offset { IntOffset(ctx.dragPos.x.toInt() + 12, ctx.dragPos.y.toInt() + 12) }
            .background(Color(0xDD1A1A1A)).border(1.dp, AC).padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(ctx.title(id), color = Color.White, fontSize = 11.sp) }
}
