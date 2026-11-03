package com.szkinetic

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun Tcode(comp: Comp, sz: TextUnit) {
    val f by remember(comp) { derivedStateOf { (comp.time * comp.fps).toInt() } }
    Text(tf(f, comp.fps), color = AC, fontSize = sz, fontWeight = FontWeight.Bold)
}

// ---------------------------------------------------------------- Project panel
@Composable
fun ProjPanel(
    p: Project, comp: Comp?, c: Cur, sel: String, onSel: (String) -> Unit, onOpen: (String) -> Unit,
    onNew: () -> Unit, foc: Boolean, onFoc: () -> Unit, onImp: () -> Unit, m: Modifier
) {
    val ctx = LocalContext.current
    val cm = p.media.firstOrNull { it.id == sel }
    val cc = p.comps.firstOrNull { it.id == sel }
    Pn("Project", foc, c, onFoc, m) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tmb(cm, Modifier.size(64.dp, 38.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cm?.name ?: cc?.name ?: p.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cm != null) {
                    Text(if (cm.kind == 2) KN[2] else "${cm.w} x ${cm.h}", color = GR, fontSize = 10.sp)
                    Text(if (cm.kind == 0) KN[0] else dl(cm.dur), color = GR, fontSize = 10.sp)
                } else if (cc != null) {
                    Text("${cc.w} x ${cc.h}, ${cc.fps} fps", color = GR, fontSize = 10.sp)
                    Text(dl(cc.dur), color = GR, fontSize = 10.sp)
                } else {
                    Text("${p.comps.size} comps, ${p.media.size} items", color = GR, fontSize = 10.sp)
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
        Column(Modifier.weight(1f).fillMaxWidth().vscr(c, rs)) {
            p.comps.forEach { q ->
                PRow(
                    0xFFB0B0B0, q.name, "Comp", dl(q.dur), sel == q.id, c,
                    mnu(c, "Open" to { onSel(q.id); onOpen(q.id) }, "Delete" to {
                        p.mark()
                        p.comps.remove(q)
                        onSel("")
                    })
                ) {
                    onSel(q.id)
                    onOpen(q.id)
                }
            }
            p.media.forEach { x ->
                PRow(
                    MCOL[x.kind], x.name, KN[x.kind], if (x.kind == 0) "" else dl(x.dur), sel == x.id, c,
                    mnu(c, "Add to timeline" to { comp?.addMedia(x); Unit }, "Delete" to { p.rm(x) })
                ) { onSel(x.id) }
            }
        }
        Row(Modifier.fillMaxWidth().background(HD).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Btn("+Comp", c) { onNew() }
            Btn("Import", c) { onImp() }
            Btn("Add", c) {
                if (cm == null) {
                    Toast.makeText(ctx, "Select a footage item first", Toast.LENGTH_SHORT).show()
                } else if (comp == null) {
                    Toast.makeText(ctx, "Create a composition first", Toast.LENGTH_SHORT).show()
                } else {
                    comp.addMedia(cm)
                }
            }
            Btn("Del", c) {
                if (cm != null) {
                    p.rm(cm)
                    onSel("")
                } else if (cc != null) {
                    p.mark()
                    p.comps.remove(cc)
                    onSel("")
                }
            }
        }
    }
}

@Composable
fun MBtn(t: String, c: Cur, pri: Boolean = false, items: () -> List<Pair<String, () -> Unit>>) {
    var r by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.onGloballyPositioned { r = it.boundsInRoot() }
            .background(if (pri) AC else P3, RoundedCornerShape(4.dp))
            .cur(c) { c.menu = Menu(Offset(r.left, r.bottom), items()) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        Alignment.Center
    ) { Text(t, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun EmptyComp(c: Cur, foc: Boolean, onFoc: () -> Unit, onNew: () -> Unit, m: Modifier) {
    Pn("Composition  (none)", foc, c, onFoc, m) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This project has no compositions.", color = GR, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Btn("New Composition", c, true) { onNew() }
        }
    }
}

// ---------------------------------------------------------------- Composition panel
private val ZS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f, 6f, 8f)

@Composable
fun CompPanel(comp: Comp, c: Cur, foc: Boolean, onFoc: () -> Unit, m: Modifier) {
    var res by remember(comp.id) { mutableStateOf(1) }
    var zm by remember(comp.id) { mutableStateOf(1f) }
    var px by remember(comp.id) { mutableStateOf(0f) }
    var py by remember(comp.id) { mutableStateOf(0f) }
    var vw by remember { mutableStateOf(1f) }
    var vh by remember { mutableStateOf(1f) }
    val lp = remember { floatArrayOf(0f, 0f) }
    Pn("Composition  " + comp.name, foc, c, onFoc, m) {
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged {
            vw = maxOf(it.width.toFloat(), 1f)
            vh = maxOf(it.height.toFloat(), 1f)
        }) {
            Viewer(comp, res, zm, px, py, Modifier.fillMaxSize())
            if (zm > 1.01f) {
                // drag the zoomed view
                Box(
                    Modifier.fillMaxSize().cur(
                        c,
                        drag = { o ->
                            px += (o.x - lp[0]) / vw
                            py += (o.y - lp[1]) / vh
                            lp[0] = o.x
                            lp[1] = o.y
                        },
                        ds = { o ->
                            lp[0] = o.x
                            lp[1] = o.y
                        }
                    ) { o ->
                        lp[0] = o.x
                        lp[1] = o.y
                    }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(28.dp).background(HD).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Ico(0, false, c) { comp.time = 0f }
            Ico(1, false, c) { comp.stepFrames(-1) }
            Ico(2, comp.playing, c) { comp.togglePlay() }
            Ico(3, false, c) { comp.stepFrames(1) }
            Ico(4, false, c) { comp.time = comp.dur }
            Btn("Stop", c) {
                comp.playing = false
                comp.time = 0f
            }
            Btn(if (comp.loop) "Loop: On" else "Loop: Off", c, comp.loop) { comp.loop = !comp.loop }
            Spacer(Modifier.weight(1f))
            Tcode(comp, 13.sp)
            Text(" / " + tc(comp.dur, comp.fps), color = GR, fontSize = 10.sp)
        }
        Row(
            Modifier.fillMaxWidth().height(28.dp).background(HD).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${comp.w}x${comp.h}", color = GR, fontSize = 10.sp)
            Text("Quality", color = GR, fontSize = 10.sp)
            Seg(listOf("Full", "1/2", "1/3", "1/4"), res - 1, c) { res = it + 1 }
            Spacer(Modifier.weight(1f))
            Btn("-", c) {
                zm = ZS.lastOrNull { it < zm - 0.001f } ?: ZS[0]
                if (zm <= 1.01f) {
                    px = 0f
                    py = 0f
                }
            }
            Text("${Math.round(zm * 100)}%", color = Color.White, fontSize = 11.sp)
            Btn("+", c) { zm = ZS.firstOrNull { it > zm + 0.001f } ?: ZS[ZS.size - 1] }
            Btn("Fit", c) {
                zm = 1f
                px = 0f
                py = 0f
            }
        }
    }
}

// ---------------------------------------------------------------- numeric scrubbing
@Composable
fun Edt(init: String, mod: Modifier, num: Boolean, onDone: (String) -> Unit) {
    var tx by remember { mutableStateOf(init) }
    var fin by remember { mutableStateOf(false) }
    var got by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    BasicTextField(
        value = tx,
        onValueChange = { tx = it },
        modifier = mod.focusRequester(fr).onFocusChanged {
            if (it.isFocused) {
                got = true
            } else if (got && !fin) {
                fin = true
                onDone(tx)
            }
        },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (!fin) {
                fin = true
                onDone(tx)
            }
        })
    )
}

// Drag horizontally to scrub (drag up beyond the field = x10, down = x0.1), double-tap to type, long-press to reset.
@Composable
fun NumField(
    c: Cur, v: Float, unit: String, sens: Float, lo: Float, hi: Float, mod: Modifier,
    onBegin: () -> Unit, onSet: (Float) -> Unit, onReset: () -> Unit
) {
    var ed by remember { mutableStateOf(false) }
    val dn = LocalDensity.current.density
    val st = remember { floatArrayOf(0f, 0f, 0f, 0f) } // lastX, startY, accumulated value, moved
    val lc = remember { longArrayOf(0L) }
    val cur = rememberUpdatedState(v)
    Box(
        mod.height(22.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp)).border(1.dp, P3, RoundedCornerShape(3.dp))
            .cur(
                c,
                drag = { o ->
                    val dx = o.x - st[0]
                    val dy = o.y - st[1]
                    if (st[3] == 0f && abs(dx) >= 4f) {
                        st[3] = 1f
                        st[2] = cur.value
                        onBegin()
                    }
                    if (st[3] == 1f) {
                        val mult = if (dy < -70f) 10f else if (dy > 70f) 0.1f else 1f
                        st[2] = (st[2] + dx / dn * sens * mult).coerceIn(lo, hi)
                        st[0] = o.x
                        onSet(st[2])
                    }
                },
                rc = mnu(c, "Reset to default" to {
                    onBegin()
                    onReset()
                }),
                ds = { o ->
                    st[0] = o.x
                    st[1] = o.y
                    st[3] = 0f
                }
            ) { o ->
                st[0] = o.x
                st[1] = o.y
                st[3] = 0f
                val now = System.currentTimeMillis()
                if (now - lc[0] < 350L) {
                    ed = true
                    lc[0] = 0L
                } else {
                    lc[0] = now
                }
            }
            .padding(horizontal = 4.dp),
        Alignment.CenterStart
    ) {
        if (ed) {
            Edt("%.2f".format(v), Modifier.fillMaxWidth(), true) { s ->
                ed = false
                val x = s.trim().replace(',', '.').toFloatOrNull()
                if (x != null && x.isFinite()) {
                    onBegin()
                    onSet(x.coerceIn(lo, hi))
                }
            }
        } else {
            Text("%.1f".format(v) + unit, color = Color(0xFF8CB4FF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
fun Stw(on: Boolean, c: Cur, f: () -> Unit) {
    Canvas(Modifier.size(22.dp, 24.dp).cur(c) { f() }) {
        val col = if (on) AC else Color(0xFF777777)
        val cx = size.width / 2
        val cy = size.height / 2 + 1.dp.toPx()
        val r = 6.dp.toPx()
        drawCircle(col, r, Offset(cx, cy), style = Stroke(1.6f))
        drawLine(col, Offset(cx, cy), Offset(cx + r * 0.6f, cy - r * 0.5f), 1.6f)
        drawLine(col, Offset(cx - 2.dp.toPx(), cy - r - 1.5.dp.toPx()), Offset(cx + 2.dp.toPx(), cy - r - 1.5.dp.toPx()), 1.6f)
        if (on) drawCircle(col.copy(alpha = 0.35f), r - 1f, Offset(cx, cy))
    }
}

// One property: stopwatch, name, one scrubbable field per component, plus key navigation when animated.
@Composable
fun ParamRow(
    name: String, ps: List<Prop>, lb: List<String>, unit: String, sens: Float, comp: Comp, c: Cur,
    toD: (Int, Float) -> Float, set: (Int, Float) -> Unit, reset: (Int) -> Unit, lo: Float, hi: Float
) {
    val t = comp.time
    val e = comp.eps()
    val anim = ps.all { it.anim }
    val has = ps.any { it.idx(t, e) >= 0 }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Stw(anim, c) {
                comp.mark()
                ps.forEach { if (it.anim == anim) it.watch(t) }
            }
            Text(name, color = Color(0xFFD0D0D0), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(62.dp))
            ps.forEachIndexed { i, p ->
                if (lb[i].isNotEmpty()) Text(lb[i], color = GR, fontSize = 9.sp)
                NumField(
                    c, toD(i, p.get(t)), unit, sens, lo, hi, Modifier.weight(1f),
                    onBegin = { comp.mark() }, onSet = { d -> set(i, d) }, onReset = { reset(i) }
                )
            }
        }
        if (ps.any { it.anim }) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("<", c) {
                    val x = ps.mapNotNull { it.prev(t, e) }.maxOrNull()
                    if (x != null) comp.time = x
                }
                Btn(if (has) "Key *" else "Key +", c, has) {
                    comp.mark()
                    ps.forEach {
                        val i = it.idx(t, e)
                        if ((has && i >= 0) || (!has && i < 0)) it.toggle(t, e)
                    }
                }
                Btn(">", c) {
                    val x = ps.mapNotNull { it.next(t, e) }.minOrNull()
                    if (x != null) comp.time = x
                }
                MBtn("Ease", c) {
                    val modes = listOf("Linear" to 0, "Auto Bezier" to 1, "Hold" to 2, "Easy Ease" to 3, "Ease In" to 4, "Ease Out" to 5)
                    val items = ArrayList<Pair<String, () -> Unit>>()
                    for ((nm, md) in modes) {
                        items.add(nm to {
                            comp.mark()
                            ps.forEach { p ->
                                val i = p.idx(t, e)
                                if (i >= 0) p.interp(i, md)
                            }
                        })
                    }
                    items
                }
            }
        }
    }
}

class PDef(val name: String, val ch: IntArray, val lb: List<String>, val unit: String, val sens: Float, val lo: Float, val hi: Float)

private val D_ANC = PDef("Anchor Point", intArrayOf(AX, AY), listOf("X", "Y"), "px", 1f, -1e6f, 1e6f)
private val D_POS = PDef("Position", intArrayOf(PX, PY), listOf("X", "Y"), "px", 1f, -1e6f, 1e6f)
private val D_POS3 = PDef("Position", intArrayOf(PX, PY, PZ), listOf("X", "Y", "Z"), "px", 1f, -1e6f, 1e6f)
private val D_SCL = PDef("Scale", intArrayOf(SX, SY), listOf("X", "Y"), "%", 0.5f, -10000f, 10000f)
private val D_ROT = PDef("Rotation", intArrayOf(RT), listOf(""), "°", 0.5f, -36000f, 36000f)
private val D_OPA = PDef("Opacity", intArrayOf(OP), listOf(""), "%", 0.5f, 0f, 100f)
private val TD2 = listOf(D_ANC, D_POS, D_SCL, D_ROT, D_OPA)
private val TD3 = listOf(D_ANC, D_POS3, D_SCL, D_ROT, D_OPA)

fun tdefs(l: Layer): List<PDef> = if (l.is3d) TD3 else TD2

@Composable
fun Grp(t: String, open: Boolean, onT: () -> Unit, c: Cur, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().background(HD).cur(c) { onT() }.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text((if (open) "v  " else ">  ") + t, color = LAV, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (open) body()
    }
}

@Composable
fun Tog(l: String, on: Boolean, c: Cur, f: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(l, color = Color(0xFFD0D0D0), fontSize = 11.sp)
        Tgl(on, c, f)
    }
}

@Composable
fun RightPanel(comp: Comp, c: Cur, tab: Int, onTab: (Int) -> Unit, foc: Boolean, onFoc: () -> Unit, m: Modifier) {
    var gT by remember { mutableStateOf(true) }
    var gE by remember { mutableStateOf(true) }
    var gA by remember { mutableStateOf(true) }
    var rn by remember { mutableStateOf(false) }
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
            Modifier.weight(1f).fillMaxWidth().vscr(c, rs).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val l = comp.layers.getOrNull(comp.sel)
            if (l == null) {
                Text("No layer selected", color = GR, fontSize = 11.sp)
            } else if (tab == 2) {
                if (l.role == 2) {
                    Text("Apply to \"${l.name}\":", color = GR, fontSize = 11.sp)
                    FXS.forEachIndexed { i, d ->
                        Btn(d.name, c) {
                            comp.mark()
                            l.fx.add(Fx(i))
                            gE = true
                        }
                    }
                } else {
                    Text(
                        "Effects are applied through adjustment layers. Create one from Composition > New adjustment layer, then select it.",
                        color = GR, fontSize = 11.sp
                    )
                }
            } else if (tab == 1) {
                val md = comp.lib.firstOrNull { it.id == l.mid }
                Text(l.name, color = AC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Kv("Type", when (l.role) { 1 -> "Null"; 2 -> "Adjustment"; else -> if (l.kind < 0) "Shape" else KN[l.kind] })
                Kv("Source", md?.name ?: "-")
                Kv("Parent", comp.layerOf(l.parent)?.name ?: "None")
                Kv("In", tc(l.start, comp.fps))
                Kv("Out", tc(l.end(comp.dur), comp.fps))
                Kv("Duration", tc(l.end(comp.dur) - l.start, comp.fps))
                if (md != null && md.kind != 2) Kv("Size", "${md.w} x ${md.h}")
            } else {
                if (rn) {
                    Edt(l.name, Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(4.dp), false) { s ->
                        rn = false
                        val n = s.trim()
                        if (n.isNotEmpty() && n != l.name) {
                            comp.mark()
                            l.name = n
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().cur(c) { rn = true }) {
                        Text(l.name + "   (tap to rename)", color = AC, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Parent", color = Color(0xFFD0D0D0), fontSize = 11.sp)
                    MBtn(comp.layerOf(l.parent)?.name ?: "None", c) {
                        val items = ArrayList<Pair<String, () -> Unit>>()
                        items.add("None" to { comp.setParent(l, null); Unit })
                        comp.layers.forEachIndexed { i, q ->
                            if (q !== l && comp.canParent(l, q)) items.add("${i + 1}. ${q.name}" to { comp.setParent(l, q); Unit })
                        }
                        items
                    }
                }
                if (l.role != 2) Tog("3D layer (Z position)", l.is3d, c) {
                    comp.mark()
                    l.is3d = it
                }
                run {
                    Grp(if (l.role == 2) "Adjustment" else "Transform", gT, { gT = !gT }, c) {
                        for (d in (if (l.role == 2) listOf(D_OPA) else tdefs(l))) {
                            ParamRow(
                                d.name, d.ch.map { l.pr[it] }, d.lb, d.unit, d.sens, comp, c,
                                toD = { i, v -> comp.toD(d.ch[i], v) },
                                set = { i, dv -> comp.setD(l, d.ch[i], dv) },
                                reset = { i -> comp.resetD(l, d.ch[i]) },
                                lo = d.lo, hi = d.hi
                            )
                            if (d === D_SCL) Tog("Link scale", l.link, c) { l.link = it }
                        }
                    }
                }
                if (l.role == 2) {
                    Grp("Effects", gE, { gE = !gE }, c) {
                        if (l.fx.isEmpty()) Text("Open the Effects tab to add effects.", color = GR, fontSize = 11.sp)
                        l.fx.forEachIndexed { fi, fx ->
                            val d = FXS[fx.type]
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tgl(fx.on, c) {
                                    comp.mark()
                                    fx.on = it
                                }
                                Text(d.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Btn("x", c) {
                                    comp.mark()
                                    l.fx.remove(fx)
                                }
                            }
                            d.pn.forEachIndexed { pi, nm ->
                                ParamRow(
                                    nm, listOf(fx.pr[pi]), listOf(""), d.unit[pi], d.sens[pi], comp, c,
                                    toD = { _, v -> v },
                                    set = { _, v -> comp.setProp(fx.pr[pi], v) },
                                    reset = { _ -> comp.setProp(fx.pr[pi], d.def[pi]) },
                                    lo = d.lo[pi], hi = d.hi[pi]
                                )
                            }
                        }
                    }
                }
                if (l.kind >= 1) {
                    Grp("Audio", gA, { gA = !gA }, c) {
                        Tog("Audio on", !l.mute, c) {
                            comp.mark()
                            l.mute = !it
                        }
                    }
                }
            }
        }
    }
}
