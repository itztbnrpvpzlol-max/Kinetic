package com.szkinetic

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ---------------------------------------------------------------- text / number editing
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

// Blue After Effects style value. Drag horizontally to scrub (drag up away from it = x10, down = x0.1),
// double-tap to type, long-press for the reset menu.
@Composable
fun NumField(
    c: Cur, v: Float, unit: String, sens: Float, lo: Float, hi: Float, mod: Modifier,
    onBegin: () -> Unit, onSet: (Float) -> Unit, onReset: () -> Unit, fmt: ((Float) -> String)? = null
) {
    var ed by remember { mutableStateOf(false) }
    val dn = LocalDensity.current.density
    val st = remember { floatArrayOf(0f, 0f, 0f, 0f) } // lastX, startY, accumulated value, moved
    val lc = remember { longArrayOf(0L) }
    val cur = rememberUpdatedState(v)
    Box(
        mod.height(20.dp)
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
            .padding(horizontal = 2.dp),
        Alignment.CenterStart
    ) {
        if (ed) {
            Edt("%.2f".format(v), Modifier.widthIn(min = 40.dp).background(Color(0xFF1A1A1A)).padding(2.dp), true) { s ->
                ed = false
                val x = s.trim().replace(',', '.').toFloatOrNull()
                if (x != null && x.isFinite()) {
                    onBegin()
                    onSet(x.coerceIn(lo, hi))
                }
            }
        } else {
            Text(fmt?.invoke(v) ?: ("%.1f".format(v) + unit), color = VB, fontSize = AE_FONT, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
fun Stw(on: Boolean, c: Cur, f: () -> Unit) {
    Canvas(Modifier.size(18.dp, 24.dp).cur(c) { f() }) {
        val col = if (on) VB else Color(0xFF8A8A8A)
        val cx = size.width / 2
        val cy = size.height / 2 + 1.dp.toPx()
        val r = 5.dp.toPx()
        drawCircle(col, r, Offset(cx, cy), style = Stroke(1.4f))
        drawLine(col, Offset(cx, cy), Offset(cx + r * 0.6f, cy - r * 0.5f), 1.4f)
        drawLine(col, Offset(cx - 2.dp.toPx(), cy - r - 1.5.dp.toPx()), Offset(cx + 2.dp.toPx(), cy - r - 1.5.dp.toPx()), 1.4f)
        if (on) drawCircle(col.copy(alpha = 0.35f), r - 1f, Offset(cx, cy))
    }
}

class PDef(val name: String, val ch: IntArray, val lb: List<String>, val unit: String, val sens: Float, val lo: Float, val hi: Float)

val D_ANC = PDef("Anchor Point", intArrayOf(AX, AY), listOf("X", "Y"), "", 1f, -1e6f, 1e6f)
val D_POS = PDef("Position", intArrayOf(PX, PY), listOf("X", "Y"), "", 1f, -1e6f, 1e6f)
val D_POS3 = PDef("Position", intArrayOf(PX, PY, PZ), listOf("X", "Y", "Z"), "", 1f, -1e6f, 1e6f)
val D_SCL = PDef("Scale", intArrayOf(SX, SY), listOf("X", "Y"), "%", 0.5f, -10000f, 10000f)
val D_ROT = PDef("Rotation", intArrayOf(RT), listOf(""), "\u00B0", 0.5f, -36000f, 36000f)
val D_ROT_X = PDef("X Rotation", intArrayOf(RX), listOf(""), "\u00B0", 0.5f, -36000f, 36000f)
val D_ROT_Y = PDef("Y Rotation", intArrayOf(RY), listOf(""), "\u00B0", 0.5f, -36000f, 36000f)
val D_OPA = PDef("Opacity", intArrayOf(OP), listOf(""), "%", 0.5f, 0f, 100f)
private val TD2 = listOf(D_ANC, D_POS, D_SCL, D_ROT, D_OPA)
private val TD3 = listOf(D_ANC, D_POS3, D_SCL, D_ROT, D_ROT_X, D_ROT_Y, D_OPA)

fun tdefs(l: Layer): List<PDef> = if (l.is3d) TD3 else TD2

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
fun FlatBtn(t: String, c: Cur, on: Boolean = false, f: () -> Unit) {
    Box(Modifier.background(if (on) AC else Color(0xFF353535)).cur(c) { f() }.padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(t, color = Color.White, fontSize = AE_FONT, maxLines = 1)
    }
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
    Column(m) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tmb(cm, Modifier.size(64.dp, 38.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(cm?.name ?: cc?.name ?: p.name, color = Color.White, fontSize = AE_FONT, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cm != null) {
                    Text(if (cm.kind == 2) KN[2] else "${cm.w} x ${cm.h}", color = DIM, fontSize = 9.sp)
                    Text(if (cm.kind == 0) KN[0] else dl(cm.dur), color = DIM, fontSize = 9.sp)
                } else if (cc != null) {
                    Text("${cc.w} x ${cc.h} (1.00)", color = DIM, fontSize = 9.sp)
                    Text("\u0394 " + aeTc((cc.dur * cc.fps).toInt(), cc.fps) + ", " + fpsTxt(cc.fps) + " fps", color = DIM, fontSize = 9.sp)
                } else {
                    Text("${p.comps.size} comps, ${p.media.size} items", color = DIM, fontSize = 9.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Ix(6, true, c, 16.dp, 18.dp) {}
            Box(Modifier.weight(1f).height(16.dp).background(Color(0xFF1A1A1A)))
        }
        Row(Modifier.fillMaxWidth().background(CH).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("Name \u25BE", color = DIM, fontSize = 9.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
            Text("Type", color = DIM, fontSize = 9.sp, modifier = Modifier.width(38.dp))
            Text("Dur", color = DIM, fontSize = 9.sp, modifier = Modifier.width(30.dp))
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
        Row(
            Modifier.fillMaxWidth().background(CH).padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text("8 bpc", color = DIM, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            FlatBtn("+Comp", c) { onNew() }
            FlatBtn("Import", c) { onImp() }
            FlatBtn("Add", c) {
                if (cm == null) {
                    Toast.makeText(ctx, "Select a footage item first", Toast.LENGTH_SHORT).show()
                } else if (comp == null) {
                    Toast.makeText(ctx, "Create a composition first", Toast.LENGTH_SHORT).show()
                } else {
                    comp.addMedia(cm)
                }
            }
            FlatBtn("Del", c) {
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
fun EmptyComp(c: Cur, foc: Boolean, onFoc: () -> Unit, onNew: () -> Unit, m: Modifier) {
    Column(m) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This project has no compositions.", color = DIM, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Btn("New Composition", c, true) { onNew() }
        }
    }
}

@Composable
fun TimeText(comp: Comp) {
    val f by remember(comp) { derivedStateOf { (comp.time * comp.fps).toInt() } }
    Text(aeTc(f, comp.fps), color = VB, fontSize = AE_FONT, fontWeight = FontWeight.Bold)
}

// ---------------------------------------------------------------- Composition panel
private val ZS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f, 6f, 8f)
val QN = listOf("Full", "Half", "Third", "Quarter")

@Composable
fun CompPanel(
    p: Project, comp: Comp, c: Cur, tool: Int, res: Int, onRes: (Int) -> Unit,
    foc: Boolean, onFoc: () -> Unit, onOpen: (String) -> Unit, m: Modifier
) {
    var zm by remember(comp.id) { mutableStateOf(1f) }
    var px by remember(comp.id) { mutableStateOf(0f) }
    var py by remember(comp.id) { mutableStateOf(0f) }
    var vw by remember { mutableStateOf(1f) }
    var vh by remember { mutableStateOf(1f) }
    var gridOn by remember(comp.id) { mutableStateOf(false) }
    val penPoints = remember(comp.id) { mutableStateListOf<PathPoint>() }
    var penColor by remember(comp.id) { mutableLongStateOf(0xFF4A90E2) }
    val lp = remember { floatArrayOf(0f, 0f) }
    Column(m) {
        Row(Modifier.fillMaxWidth().height(18.dp).background(PB), verticalAlignment = Alignment.CenterVertically) {
            Dd(comp.name, c, Modifier.padding(start = 6.dp), Color.White) {
                p.comps.map { q -> q.name to { onOpen(q.id) } }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).onSizeChanged {
            vw = maxOf(it.width.toFloat(), 1f)
            vh = maxOf(it.height.toFloat(), 1f)
        }) {
            Viewer(comp, res, zm, px, py, Modifier.fillMaxSize())
            if (gridOn) {
                Canvas(Modifier.fillMaxSize()) {
                    val stepX = size.width / 10f
                    val stepY = size.height / 10f
                    val thin = Color(0x557F9AB8)
                    val axis = Color(0xAA9DB8D8)
                    for (i in 0..10) {
                        val x = i * stepX
                        val y = i * stepY
                        drawLine(if (i == 5) axis else thin, Offset(x, 0f), Offset(x, size.height), if (i == 5) 1.25.dp.toPx() else 0.65.dp.toPx())
                        drawLine(if (i == 5) axis else thin, Offset(0f, y), Offset(size.width, y), if (i == 5) 1.25.dp.toPx() else 0.65.dp.toPx())
                    }
                }
            }
            if (tool == 3) {
                Canvas(
                    Modifier.fillMaxSize().cur(c, click = { o ->
                        val scale = minOf(vw / comp.w.toFloat(), vh / comp.h.toFloat()) * zm
                        val cw = comp.w * scale
                        val ch = comp.h * scale
                        val left = (vw - cw) / 2f + px * vw
                        val top = (vh - ch) / 2f - py * vh
                        if (cw > 0f && ch > 0f && o.x >= left && o.x <= left + cw && o.y >= top && o.y <= top + ch) {
                            penPoints.add(PathPoint(((o.x - left) / cw).coerceIn(0f, 1f), ((o.y - top) / ch).coerceIn(0f, 1f)))
                        }
                    })
                ) {
                    if (penPoints.isNotEmpty()) {
                        val scale = minOf(size.width / comp.w.toFloat(), size.height / comp.h.toFloat()) * zm
                        val cw = comp.w * scale
                        val ch = comp.h * scale
                        val left = (size.width - cw) / 2f + px * size.width
                        val top = (size.height - ch) / 2f - py * size.height
                        val path = Path()
                        penPoints.forEachIndexed { i, pt ->
                            val x = left + pt.x * cw
                            val y = top + pt.y * ch
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        if (penPoints.size > 2) path.lineTo(left + penPoints.first().x * cw, top + penPoints.first().y * ch)
                        drawPath(path, Color(penColor), style = Stroke(width = 2.dp.toPx()))
                        penPoints.forEach { pt ->
                            drawCircle(Color.White, 4.dp.toPx(), Offset(left + pt.x * cw, top + pt.y * ch))
                            drawCircle(Color(penColor), 2.dp.toPx(), Offset(left + pt.x * cw, top + pt.y * ch))
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xDD202020), RoundedCornerShape(3.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PEN  •  ${penPoints.size} points", color = TXT, fontSize = 9.sp)
                    FlatBtn("Finish", c, false) {
                        if (penPoints.size >= 2) comp.addPenPath(penPoints.toList(), penColor, true)
                        penPoints.clear()
                    }
                    FlatBtn("Cancel", c) { penPoints.clear() }
                }
            }
            if (zm > 1.01f || tool == 1 || tool == 2) {
                // hand tool / zoomed view: drag to pan; zoom tool: tap to zoom in
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
                        if (tool == 2) zm = ZS.firstOrNull { it > zm + 0.001f } ?: ZS[ZS.size - 1]
                    }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(28.dp).background(PH).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Ico(0, false, c) { comp.time = 0f }
            Ico(1, false, c) { comp.stepFrames(-1) }
            Ico(2, comp.playing, c) { comp.togglePlay() }
            Ico(3, false, c) { comp.stepFrames(1) }
            Ico(4, false, c) { comp.time = comp.dur }
            Spacer(Modifier.width(8.dp))
            FlatBtn(if (comp.loop) "Loop: On" else "Loop: Off", c, comp.loop) { comp.loop = !comp.loop }
            Spacer(Modifier.weight(1f))
            Text("Frame Rate (" + fpsTxt(comp.fps) + ")", color = DIM, fontSize = AE_FONT, maxLines = 1)
        }
        Row(
            Modifier.fillMaxWidth().height(22.dp).background(CH).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Dd("${Math.round(zm * 100)}%", c) {
                val l = ArrayList<Pair<String, () -> Unit>>()
                l.add("Fit" to {
                    zm = 1f
                    px = 0f
                    py = 0f
                })
                for (z in ZS) l.add("${Math.round(z * 100)}%" to {
                    zm = z
                    if (z <= 1f) {
                        px = 0f
                        py = 0f
                    }
                })
                l
            }
            TimeText(comp)
            Spacer(Modifier.weight(1f))
            Dd("(" + QN[res - 1] + ")", c) { QN.mapIndexed { i, n -> n to { onRes(i + 1) } } }
            FlatBtn("Grid", c, gridOn) { gridOn = !gridOn }
            Text("Active Camera", color = TXT, fontSize = AE_FONT, maxLines = 1)
            Text("1 View", color = TXT, fontSize = AE_FONT, maxLines = 1)
            Text("+0.0", color = TXT, fontSize = AE_FONT, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Info / Effects & Presets panels
@Composable
fun InfoPanel(comp: Comp?, c: Cur, m: Modifier) {
    val rs = rememberScrollState()
    Column(m.vscr(c, rs).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val l = comp?.layers?.getOrNull(comp.sel)
        if (comp == null || l == null) {
            Text("No layer selected", color = DIM, fontSize = AE_FONT)
        } else {
            val md = comp.lib.firstOrNull { it.id == l.mid }
            Text(l.name, color = Color.White, fontSize = AE_FONT, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Kv("Type", when {
                l.preCompId.isNotEmpty() -> "Pre-comp"
                l.role == 1 -> "Null"
                l.kind < 0 -> "Shape"
                else -> KN[l.kind]
            })
            Kv("Parent", comp.layerOf(l.parent)?.name ?: "None")
            Kv("In", aeTc((l.start * comp.fps).toInt(), comp.fps))
            Kv("Out", aeTc((l.end(comp.dur) * comp.fps).toInt(), comp.fps))
            if (md != null && md.kind != 2) Kv("Size", "${md.w} x ${md.h}")
        }
    }
}


// ---------------------------------------------------------------- Effects & Presets browser
@Composable
fun EffectsBrowserPanel(comp: Comp?, c: Cur, m: Modifier) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf("Blur & Sharpen") }
    val q = query.trim()
    val visibleCategories = EffectCatalog.categories.mapNotNull { category ->
        val matches = if (q.isEmpty()) category.effects else category.effects.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
                category.name.contains(q, ignoreCase = true)
        }
        if (matches.isEmpty()) null else category to matches
    }
    Column(m.background(PB).padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(Color(0xFF171717), RoundedCornerShape(3.dp))
                .border(1.dp, EDG, RoundedCornerShape(3.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", color = DIM, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                singleLine = true,
                textStyle = TextStyle(color = TXT, fontSize = 11.sp),
                cursorBrush = SolidColor(AC),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search 500 effects", color = DIM, fontSize = 11.sp)
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Text("×", color = DIM, fontSize = 15.sp, modifier = Modifier.cur(c) { query = "" }.padding(horizontal = 3.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("EFFECTS LIBRARY", color = DIM, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${EffectCatalog.all.size} effects  •  ${EffectCatalog.categories.size} categories", color = VB, fontSize = 9.sp)
        }
        val target = comp?.selected()?.firstOrNull()
        val active = target?.let { RenderEffects.byId(it.effectId) }
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF202020), RoundedCornerShape(3.dp)).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(if (target == null) "NO LAYER SELECTED" else "SELECTED: ${target.name}", color = DIM, fontSize = 9.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(active?.name ?: "No effect applied", color = TXT, fontSize = 10.sp, modifier = Modifier.weight(1f))
                if (active != null && comp != null) {
                    FlatBtn("−", c) { comp.adjustEffectAmount(-0.05f) }
                    Text("${(target!!.effectAmount * 100f).toInt()}%", color = VB, fontSize = 9.sp)
                    FlatBtn("+", c) { comp.adjustEffectAmount(0.05f) }
                    FlatBtn("Remove", c) { comp.clearEffect() }
                }
            }
            Text(
                if (target == null) "Select a layer in the timeline to apply an effect."
                else "Tap a supported effect below to apply it to the selected layer(s).",
                color = DIM, fontSize = 8.sp
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth().then(Modifier.vscr(c, rememberScrollState()))) {
            if (visibleCategories.isEmpty()) {
                Text("No matching effects", color = DIM, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                visibleCategories.forEach { (category, matches) ->
                    val isExpanded = q.isNotEmpty() || expanded == category.name
                    Row(
                        Modifier.fillMaxWidth().height(31.dp)
                            .border(0.5.dp, Color(0xFF343434), RoundedCornerShape(2.dp))
                            .background(if (isExpanded) Color(0xFF343B46) else Color(0xFF292929))
                            .cur(c) { expanded = if (expanded == category.name) "" else category.name }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isExpanded) "▾" else "▸", color = VB, fontSize = 10.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(category.name, color = TXT, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("${matches.size}", color = DIM, fontSize = 9.sp)
                    }
                    if (isExpanded) {
                        matches.forEach { effect ->
                            val renderEffect = RenderEffects.byName(effect.name)
                            Row(
                                Modifier.fillMaxWidth().height(27.dp)
                                    .background(if (renderEffect != null && comp != null) Color(0xFF202020) else Color(0xFF191919))
                                    .cur(c) { if (renderEffect != null) comp?.applyEffect(renderEffect.id) }
                                    .padding(start = 25.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(5.dp).background(if (renderEffect != null) AC else Color(0xFF555555), RoundedCornerShape(1.dp)))
                                Spacer(Modifier.width(8.dp))
                                Text(effect.name, color = if (renderEffect != null) TXT else DIM, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(if (renderEffect != null) "APPLY" else "CATALOG", color = if (renderEffect != null) VB else DIM, fontSize = 7.sp)
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color(0xFF202020), RoundedCornerShape(3.dp)).padding(8.dp)) {
            Text(
                "16 effects have live GPU rendering and adjustable strength. Other catalogue entries remain in the searchable catalogue while their processing implementations are developed.",
                color = DIM,
                fontSize = 9.sp
            )
        }
    }
}


// ---------------------------------------------------------------- 3D lighting and shadows
@Composable
fun LightingPanel(comp: Comp?, c: Cur, m: Modifier) {
    val scene = comp
    var selectedId by remember(scene?.id) { mutableStateOf(scene?.lights?.firstOrNull()?.id ?: "") }
    var ambientText by remember(scene?.id) { mutableStateOf("%.2f".format(scene?.ambientLight ?: 0.3f)) }
    var shadowText by remember(scene?.id) { mutableStateOf("%.2f".format(scene?.shadowOpacity ?: 0.35f)) }
    var sxText by remember(scene?.id) { mutableStateOf("%.2f".format(scene?.shadowOffsetX ?: 0.08f)) }
    var syText by remember(scene?.id) { mutableStateOf("%.2f".format(scene?.shadowOffsetY ?: -0.08f)) }
    val selected = scene?.lights?.firstOrNull { it.id == selectedId }
    var xText by remember(scene?.id, selectedId) { mutableStateOf("%.2f".format(selected?.x ?: 0f)) }
    var yText by remember(scene?.id, selectedId) { mutableStateOf("%.2f".format(selected?.y ?: 0f)) }
    var zText by remember(scene?.id, selectedId) { mutableStateOf("%.2f".format(selected?.z ?: 1f)) }
    var azText by remember(scene?.id, selectedId) { mutableStateOf("%.1f".format(selected?.azimuth ?: -35f)) }
    var elText by remember(scene?.id, selectedId) { mutableStateOf("%.1f".format(selected?.elevation ?: 55f)) }
    var powerText by remember(scene?.id, selectedId) { mutableStateOf("%.2f".format(selected?.intensity ?: 0.85f)) }

    fun updateLight(change: (SceneLight) -> SceneLight) {
        val index = scene?.lights?.indexOfFirst { it.id == selectedId } ?: -1
        if (scene != null && index >= 0) {
            scene.mark()
            scene.lights[index] = change(scene.lights[index])
            scene.invalidateFrames()
        }
    }

    Column(m.background(PB).padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("3D LIGHTING", color = DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (scene == null) {
            Text("Open a composition to configure lighting.", color = DIM, fontSize = 10.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FlatBtn(if (scene.lightingEnabled) "Lighting On" else "Lighting Off", c, scene.lightingEnabled) {
                    scene.mark()
                    scene.lightingEnabled = !scene.lightingEnabled
                    scene.invalidateFrames()
                }
                Text("Affects 3D layers only", color = DIM, fontSize = 9.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FlatBtn("+ Directional", c) {
                    scene.mark()
                    val light = SceneLight(name = "Directional ${scene.lights.count { it.type == 0 } + 1}", type = 0)
                    scene.lights.add(light)
                    selectedId = light.id
                    scene.invalidateFrames()
                }
                FlatBtn("+ Point", c) {
                    scene.mark()
                    val light = SceneLight(name = "Point ${scene.lights.count { it.type == 1 } + 1}", type = 1, x = 0.5f, y = 0.5f, z = 1f)
                    scene.lights.add(light)
                    selectedId = light.id
                    scene.invalidateFrames()
                }
            }
            if (scene.lights.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    scene.lights.forEach { light ->
                        FlatBtn(light.name, c, selectedId == light.id) { selectedId = light.id }
                    }
                    FlatBtn("Remove", c, selected != null) {
                        scene.mark()
                        scene.lights.removeAll { it.id == selectedId }
                        selectedId = scene.lights.firstOrNull()?.id ?: ""
                        scene.invalidateFrames()
                    }
                }
                if (selected != null) {
                    Text("${if (selected.type == 0) "DIRECTIONAL" else "POINT"} LIGHT • ${selected.name}", color = TXT, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Intensity", color = DIM, fontSize = 9.sp)
                        Edt(powerText, Modifier.width(64.dp), true) { v ->
                            powerText = v
                            v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value -> updateLight { it.copy(intensity = value.coerceIn(0f, 1.5f)) } }
                        }
                    }
                    if (selected.type == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Azimuth °", color = DIM, fontSize = 9.sp)
                            Edt(azText, Modifier.width(64.dp), true) { v ->
                                azText = v
                                v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value -> updateLight { it.copy(azimuth = value.coerceIn(-180f, 180f)) } }
                            }
                            Text("Elevation °", color = DIM, fontSize = 9.sp)
                            Edt(elText, Modifier.width(64.dp), true) { v ->
                                elText = v
                                v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value -> updateLight { it.copy(elevation = value.coerceIn(-89f, 89f)) } }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("X", color = DIM, fontSize = 9.sp)
                            Edt(xText, Modifier.width(58.dp), true) { v ->
                                xText = v
                                v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value -> updateLight { it.copy(x = value.coerceIn(-10f, 10f)) } }
                            }
                            Text("Y", color = DIM, fontSize = 9.sp)
                            Edt(yText, Modifier.width(58.dp), true) { v ->
                                yText = v
                                v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value -> updateLight { it.copy(y = value.coerceIn(-10f, 10f)) } }
                            }
                            Text("Z", color = DIM, fontSize = 9.sp)
                            Edt(zText, Modifier.width(58.dp), true) { v ->
                                zText = v
                                v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { value -> updateLight { it.copy(z = value.coerceIn(-10f, 10f)) } }
                            }
                        }
                    }
                }
            } else {
                Text("No scene lights yet. Add a directional light or a point light. Existing projects keep using the legacy directional-light settings until you add a scene light.", color = DIM, fontSize = 9.sp)
            }
            Text("Ambient", color = TXT, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Edt(ambientText, Modifier.width(64.dp), true) { v ->
                    ambientText = v
                    v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { scene.mark(); scene.ambientLight = it.coerceIn(0f, 1f); scene.invalidateFrames() }
                }
            }
            Text("Cast shadow", color = TXT, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Opacity", color = DIM, fontSize = 9.sp)
                Edt(shadowText, Modifier.width(64.dp), true) { v ->
                    shadowText = v
                    v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { scene.mark(); scene.shadowOpacity = it.coerceIn(0f, 1f); scene.invalidateFrames() }
                }
                Text("Offset X", color = DIM, fontSize = 9.sp)
                Edt(sxText, Modifier.width(58.dp), true) { v ->
                    sxText = v
                    v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { scene.mark(); scene.shadowOffsetX = it.coerceIn(-1f, 1f); scene.invalidateFrames() }
                }
                Text("Y", color = DIM, fontSize = 9.sp)
                Edt(syText, Modifier.width(58.dp), true) { v ->
                    syText = v
                    v.toFloatOrNull()?.takeIf { it.isFinite() }?.let { scene.mark(); scene.shadowOffsetY = it.coerceIn(-1f, 1f); scene.invalidateFrames() }
                }
            }
            Text("Point lights use distance-attenuated diffuse shading evaluated at each layer center. Directional lights use the layer's rotated normal. This is still a 2.5D renderer: no specular response, physical light falloff, occlusion or per-light shadow maps.", color = DIM, fontSize = 9.sp)
        }
    }
}

// ---------------------------------------------------------------- 3D camera controls
@Composable
fun CameraPanel(comp: Comp?, c: Cur, m: Modifier) {
    val camera = comp
    var xText by remember(camera?.id) { mutableStateOf("%.3f".format(camera?.cameraX ?: 0f)) }
    var yText by remember(camera?.id) { mutableStateOf("%.3f".format(camera?.cameraY ?: 0f)) }
    var distanceText by remember(camera?.id) { mutableStateOf("%.2f".format(camera?.cameraDistance ?: 3f)) }
    var fovText by remember(camera?.id) { mutableStateOf("%.1f".format(camera?.cameraFov ?: 50f)) }
    var rxText by remember(camera?.id) { mutableStateOf("%.1f".format(camera?.cameraRotX ?: 0f)) }
    var ryText by remember(camera?.id) { mutableStateOf("%.1f".format(camera?.cameraRotY ?: 0f)) }
    var rzText by remember(camera?.id) { mutableStateOf("%.1f".format(camera?.cameraRotZ ?: 0f)) }
    Column(m.background(PB).padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("COMPOSITION CAMERA", color = DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        if (camera == null) {
            Text("Open a composition to configure its camera.", color = DIM, fontSize = 10.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FlatBtn(if (camera.cameraEnabled) "Camera On" else "Camera Off", c, camera.cameraEnabled) {
                    camera.mark()
                    camera.cameraEnabled = !camera.cameraEnabled
                    camera.invalidateFrames()
                }
                Text("Perspective projection for 3D layers", color = DIM, fontSize = 9.sp)
            }
            Text("Camera position", color = TXT, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("X", color = DIM, fontSize = 10.sp)
                Edt(xText, Modifier.width(68.dp), true) { value ->
                    xText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                        camera.mark(); camera.cameraX = it.coerceIn(-10f, 10f); camera.invalidateFrames()
                    }
                }
                Text("Y", color = DIM, fontSize = 10.sp)
                Edt(yText, Modifier.width(68.dp), true) { value ->
                    yText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                        camera.mark(); camera.cameraY = it.coerceIn(-10f, 10f); camera.invalidateFrames()
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Distance", color = DIM, fontSize = 10.sp)
                Edt(distanceText, Modifier.width(68.dp), true) { value ->
                    distanceText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                        camera.mark(); camera.cameraDistance = it.coerceIn(0.25f, 100f); camera.invalidateFrames()
                    }
                }
                Text("FOV °", color = DIM, fontSize = 10.sp)
                Edt(fovText, Modifier.width(58.dp), true) { value ->
                    fovText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                        camera.mark(); camera.cameraFov = it.coerceIn(15f, 100f); camera.invalidateFrames()
                    }
                }
            }
            Text("Camera orientation", color = TXT, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("X", color = DIM, fontSize = 9.sp)
                Edt(rxText, Modifier.width(58.dp), true) { value ->
                    rxText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { camera.mark(); camera.cameraRotX = it.coerceIn(-180f, 180f); camera.invalidateFrames() }
                }
                Text("Y", color = DIM, fontSize = 9.sp)
                Edt(ryText, Modifier.width(58.dp), true) { value ->
                    ryText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { camera.mark(); camera.cameraRotY = it.coerceIn(-180f, 180f); camera.invalidateFrames() }
                }
                Text("Z", color = DIM, fontSize = 9.sp)
                Edt(rzText, Modifier.width(58.dp), true) { value ->
                    rzText = value
                    value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { camera.mark(); camera.cameraRotZ = it.coerceIn(-180f, 180f); camera.invalidateFrames() }
                }
            }
            Text("Camera orientation rotates the 3D view before perspective projection. This remains a 2.5D plane renderer, not a full 3D mesh/depth-buffer renderer.", color = DIM, fontSize = 9.sp)
        }
    }
}

// ---------------------------------------------------------------- Motion tracking & stabilization
@Composable
fun TrackingPanel(comp: Comp?, c: Cur, m: Modifier) {
    val ctx = LocalContext.current
    val layer = comp?.selected()?.firstOrNull()
    val media = if (layer != null) comp?.lib?.firstOrNull { it.id == layer.mid } else null
    var progress by remember(layer?.uid) { mutableIntStateOf(0) }
    var status by remember(layer?.uid) { mutableStateOf(layer?.trackingStatus ?: "Select a video layer") }
    val samples = layer?.trackSamples ?: emptyList()
    Column(m.background(PB).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MOTION TRACKER", color = DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(layer?.name ?: "No layer selected", color = Color.White, fontSize = 12.sp, maxLines = 1)
        Text(
            when {
                layer == null -> "Select a video layer in the timeline."
                media == null || media.kind != 1 -> "Select a video footage layer. Shape, image and audio layers cannot be tracked."
                else -> "2D point tracking • grayscale patch matching"
            },
            color = DIM, fontSize = 10.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Point X", color = DIM, fontSize = 10.sp)
            Edt("%.3f".format(layer?.trackAnchorX ?: 0.5f), Modifier.width(54.dp), true) {
                val v = it.toFloatOrNull()
                if (v != null && layer != null) layer.trackAnchorX = v.coerceIn(0f, 1f)
            }
            Text("Y", color = DIM, fontSize = 10.sp)
            Edt("%.3f".format(layer?.trackAnchorY ?: 0.5f), Modifier.width(54.dp), true) {
                val v = it.toFloatOrNull()
                if (v != null && layer != null) layer.trackAnchorY = v.coerceIn(0f, 1f)
            }
            FlatBtn("Center", c) {
                if (layer != null) { layer.trackAnchorX = 0.5f; layer.trackAnchorY = 0.5f }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Patch", color = DIM, fontSize = 10.sp)
            FlatBtn("−", c) { if (layer != null) layer.trackPatchSize = (layer.trackPatchSize - 2).coerceAtLeast(7) }
            Text("${layer?.trackPatchSize ?: 15}px", color = Color.White, fontSize = 10.sp)
            FlatBtn("+", c) { if (layer != null) layer.trackPatchSize = (layer.trackPatchSize + 2).coerceAtMost(41) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FlatBtn("Track", c, media != null && layer != null) {
                if (layer != null && media != null && comp != null) {
                    comp.mark()
                    progress = 0
                    status = "Preparing tracker"
                    MotionTracker.start(media, layer, comp,
                        { pct, msg -> progress = pct; status = msg; layer.trackingStatus = msg },
                        { found, msg ->
                            layer.trackSamples.clear()
                            layer.trackSamples.addAll(found)
                            layer.trackingStatus = msg
                            progress = if (found.size >= 2) 100 else 0
                            status = msg
                            comp.mark()
                        }
                    )
                }
            }
            FlatBtn("Cancel", c, status.contains("Tracking") || status == "Preparing") {
                if (layer != null) {
                    MotionTracker.cancel(layer)
                    status = "Cancelled"
                    layer.trackingStatus = status
                }
            }
            FlatBtn("Clear", c, samples.isNotEmpty()) {
                if (layer != null && comp != null) {
                    comp.mark()
                    layer.trackSamples.clear()
                    layer.trackingStatus = "Not tracked"
                    status = layer.trackingStatus
                    progress = 0
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF353535), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(AC, RoundedCornerShape(3.dp)))
        }
        Text(status, color = if (samples.isNotEmpty()) Color(0xFF72C58B) else DIM, fontSize = 10.sp)
        Text("${samples.size} track samples", color = Color.White, fontSize = 10.sp)
        FlatBtn("Apply Stabilization", c, samples.size >= 2 && layer != null && comp != null) {
            if (layer != null && comp != null && samples.size >= 2) {
                val first = samples.first()
                comp.mark()
                layer.trackSamples.forEach { sample ->
                    val dx = sample.x - first.x
                    val dy = sample.y - first.y
                    val t = sample.time
                    val x = layer.pr[PX].get(t) - dx * 2f
                    val y = layer.pr[PY].get(t) + dy * 2f
                    layer.pr[PX].put(Kf(t, x), comp.eps())
                    layer.pr[PY].put(Kf(t, y), comp.eps())
                }
                comp.invalidateFrames()
                layer.trackingStatus = "Stabilization keys applied"
                status = layer.trackingStatus
            }
        }
        Text("Tracking uses a fixed image patch and normalized cross-correlation. Stabilization writes inverse Position keyframes; it does not yet correct rotation, scale, rolling shutter, or perspective.", color = DIM, fontSize = 9.sp)
    }
}


// ---------------------------------------------------------------- Layer mask editor
@Composable
fun MasksPanel(comp: Comp?, c: Cur, m: Modifier) {
    val layer = comp?.selected()?.firstOrNull()
    var featherText by remember(layer?.uid) { mutableStateOf("%.1f".format(layer?.maskFeather ?: 0f)) }
    var opacityText by remember(layer?.uid) { mutableStateOf("%.0f".format((layer?.maskOpacity ?: 1f) * 100f)) }
    Column(m.background(PB).padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("MASK EDITOR", color = DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(layer?.name ?: "No layer selected", color = Color.White, fontSize = 12.sp, maxLines = 1)
        if (layer == null) {
            Text("Select a layer to create or edit its mask.", color = DIM, fontSize = 10.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FlatBtn(if (layer.maskEnabled) "Mask On" else "Mask Off", c, layer.maskEnabled) {
                    comp?.mark()
                    layer.maskEnabled = !layer.maskEnabled
                }
                FlatBtn(if (layer.maskInverted) "Inverted" else "Normal", c, layer.maskInverted) {
                    comp?.mark()
                    layer.maskInverted = !layer.maskInverted
                }
                FlatBtn("Clear", c, layer.maskPoints.isNotEmpty()) {
                    comp?.mark()
                    layer.maskPoints.clear()
                    layer.maskEnabled = false
                    comp?.invalidateFrames()
                }
            }
            Canvas(
                Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF111820), RoundedCornerShape(4.dp))
                    .border(1.dp, EDG, RoundedCornerShape(4.dp))
                    .pointerInput(layer.uid) {
                        detectTapGestures { point ->
                            comp?.mark()
                            val x = (point.x / size.width).coerceIn(0f, 1f)
                            val y = (point.y / size.height).coerceIn(0f, 1f)
                            layer.maskPoints.add(PathPoint(x, y))
                            layer.maskEnabled = layer.maskPoints.size >= 3
                            comp?.invalidateFrames()
                        }
                    }
            ) {
                val pts = layer.maskPoints.map { Offset(it.x * size.width, it.y * size.height) }
                for (x in 1..9) drawLine(Color(0x223A9BCE), Offset(size.width * x / 10f, 0f), Offset(size.width * x / 10f, size.height), 1f)
                for (y in 1..5) drawLine(Color(0x223A9BCE), Offset(0f, size.height * y / 6f), Offset(size.width, size.height * y / 6f), 1f)
                if (pts.size >= 2) {
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                        if (pts.size >= 3) close()
                    }
                    drawPath(path, Color(0xAA54B7D9), style = Stroke(width = 2.dp.toPx()))
                }
                pts.forEachIndexed { i, p ->
                    drawCircle(if (i == pts.lastIndex) Color(0xFFFFD166) else Color.White, 5.dp.toPx(), p)
                }
            }
            Text("Tap the preview to add mask vertices. Three or more points create a closed polygon.", color = DIM, fontSize = 9.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Feather", color = DIM, fontSize = 10.sp)
                Edt(featherText, Modifier.width(58.dp), true) {
                    featherText = it
                    it.toFloatOrNull()?.let { v ->
                        comp?.mark()
                        layer.maskFeather = v.coerceIn(0f, 100f)
                        comp?.invalidateFrames()
                    }
                }
                Text("Opacity %", color = DIM, fontSize = 10.sp)
                Edt(opacityText, Modifier.width(48.dp), true) {
                    opacityText = it
                    it.toFloatOrNull()?.let { v ->
                        comp?.mark()
                        layer.maskOpacity = v.coerceIn(0f, 100f) / 100f
                        comp?.invalidateFrames()
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FlatBtn("Rectangle", c) {
                    comp?.mark()
                    layer.maskPoints.clear()
                    layer.maskPoints.addAll(listOf(PathPoint(.15f,.15f),PathPoint(.85f,.15f),PathPoint(.85f,.85f),PathPoint(.15f,.85f)))
                    layer.maskEnabled = true
                    comp?.invalidateFrames()
                }
                FlatBtn("Ellipse", c) {
                    comp?.mark()
                    layer.maskPoints.clear()
                    for (i in 0 until 32) {
                        val a = (i * 6.2831853f / 32f)
                        layer.maskPoints.add(PathPoint(.5f + .38f * kotlin.math.cos(a), .5f + .38f * kotlin.math.sin(a)))
                    }
                    layer.maskEnabled = true
                    comp?.invalidateFrames()
                }
            }
            Text("Mask path vertices and feather are saved in the project. Animated vertex paths and multi-mask compositing are not included in this first mask pass.", color = DIM, fontSize = 9.sp)
        }
    }
}
