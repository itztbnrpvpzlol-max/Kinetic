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
import androidx.compose.ui.graphics.Color
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
val D_OPA = PDef("Opacity", intArrayOf(OP), listOf(""), "%", 0.5f, 0f, 100f)
private val TD2 = listOf(D_ANC, D_POS, D_SCL, D_ROT, D_OPA)
private val TD3 = listOf(D_ANC, D_POS3, D_SCL, D_ROT, D_OPA)

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
