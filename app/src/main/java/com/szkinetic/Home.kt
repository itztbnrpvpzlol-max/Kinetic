package com.szkinetic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

val PRE = listOf(
    Triple("HD 1080", 1920, 1080),
    Triple("4K UHD", 3840, 2160),
    Triple("Square", 1080, 1080),
    Triple("Vertical 9:16", 1080, 1920),
    Triple("HD 720", 1280, 720)
)
val BT = listOf("Left click", "Right click", "Off")

fun ago(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

@Composable
fun Thumb(p: Project) {
    val cp = p.comps.firstOrNull()
    Canvas(Modifier.size(64.dp, 36.dp).background(Color.Black)) {
        cp?.layers?.forEach { l ->
            if (l.role == 0 && l.kind < 0) {
                val x = l.pr[PX].get(0f)
                val y = l.pr[PY].get(0f)
                val s = l.pr[SX].get(0f)
                val side = 0.3f * s * size.height
                val cx = (x + 1f) / 2f * size.width
                val cy = (1f - (y + 1f) / 2f) * size.height
                drawRect(Color(l.col), Offset(cx - side / 2, cy - side / 2), Size(side, side))
            }
        }
    }
}

@Composable
fun Crd(t: String, k: Int, c: Cur, f: () -> Unit) {
    Column(
        Modifier.size(128.dp).background(Color(0xFF1B1B1B), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(4.dp)).cur(c) { f() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(72.dp, 54.dp)) {
            val g = Color(0xFF9A9AB0)
            val sw = Stroke(2.dp.toPx())
            val cr = CornerRadius(3.dp.toPx())
            val sz = Size(58.dp.toPx(), 42.dp.toPx())
            if (k == 1) drawRoundRect(g.copy(alpha = 0.5f), Offset(12.dp.toPx(), 0f), sz, cr, sw)
            val tl = Offset(0f, if (k == 1) 10.dp.toPx() else 6.dp.toPx())
            drawRoundRect(Color(0xFF1B1B1B), tl, sz, cr)
            drawRoundRect(g, tl, sz, cr, sw)
            val cx = tl.x + sz.width * 0.5f
            val cy = tl.y + sz.height * 0.5f
            val tp = Path().apply {
                moveTo(cx - 16.dp.toPx(), cy + 10.dp.toPx())
                lineTo(cx - 6.dp.toPx(), cy - 10.dp.toPx())
                lineTo(cx + 2.dp.toPx(), cy + 10.dp.toPx())
                close()
            }
            drawPath(tp, g, style = sw)
            drawCircle(g, 7.dp.toPx(), Offset(cx + 10.dp.toPx(), cy - 4.dp.toPx()), style = sw)
        }
        Spacer(Modifier.height(8.dp))
        Text(t, color = Color(0xFFBDBDBD), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Home(
    c: Cur,
    ps: List<Project>,
    onOpen: (Project) -> Unit,
    onNew: () -> Unit,
    onFoot: () -> Unit,
    onImp: () -> Unit,
    onPlugin: () -> Unit,
    onSet: () -> Unit,
    onDel: (Project) -> Unit,
    onDup: (Project) -> Unit,
    onExp: (Project) -> Unit
) {
    Column(Modifier.fillMaxSize().background(BG)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF171717)).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(36.dp).background(LAV, RoundedCornerShape(10.dp)), Alignment.Center) {
                Text("K", color = Color(0xFF171717), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("Kinetic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            Mn("☰", c, "Settings" to onSet, "New Composition" to onNew)
        }

        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().vscr(c, scroll).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 150.dp).background(Color(0xFF292B3B), RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFF41445B), RoundedCornerShape(22.dp)).cur(c) { onNew() }
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New Composition", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Start a fresh motion design project", color = Color(0xFFB8BCD0), fontSize = 13.sp)
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.background(AC, RoundedCornerShape(9.dp)).padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text("Create composition  →", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(1f).height(54.dp).background(AC, RoundedCornerShape(12.dp))
                        .cur(c) { onImp() }, Alignment.Center
                ) {
                    Text("Import Project File", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.weight(1f).height(54.dp).background(Color(0xFF2476C8), RoundedCornerShape(12.dp))
                        .cur(c) { onPlugin() }, Alignment.Center
                ) {
                    Text("Install Plugin", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your Projects", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${ps.size} projects", color = GR, fontSize = 11.sp)
            }
            if (ps.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().background(Color(0xFF242424), RoundedCornerShape(14.dp)).padding(22.dp),
                    Alignment.Center
                ) {
                    Text("Your projects will appear here.", color = GR, fontSize = 13.sp)
                }
            } else {
                ps.forEach { p ->
                    val cp = p.comps.firstOrNull()
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF252525), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF353535), RoundedCornerShape(12.dp))
                            .cur(c, rc = mnu(c, "Open" to { onOpen(p) }, "Duplicate" to { onDup(p) }, "Export Project" to { onExp(p) }, "Delete" to { onDel(p) })) { onOpen(p) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.background(Color.Black, RoundedCornerShape(7.dp)).padding(5.dp)) { Thumb(p) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (cp == null) "Empty project" else "${cp.w} × ${cp.h}  •  ${cp.fps} fps  •  ${cp.dur.toInt()} s",
                                color = GR, fontSize = 11.sp
                            )
                        }
                        Text(ago(p.mod), color = GR, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun Modal(t: String, c: Cur, foot: @Composable RowScope.() -> Unit = {}, body: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalZ provides 1) {
        Box(Modifier.fillMaxSize().background(Color(0xB3000000)).cur(c) {}, Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).fillMaxHeight(0.92f)
                    .background(P2, RoundedCornerShape(6.dp)).border(1.dp, Color(0xFF444444), RoundedCornerShape(6.dp))
            ) {
                Hdr(t)
                val rs = rememberScrollState()
                Column(
                    Modifier.weight(1f).fillMaxWidth().vscr(c, rs).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = body
                )
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = foot)
            }
        }
    }
}

@Composable
fun Lbl(t: String) {
    Text(t, color = GR, fontSize = 11.sp)
}

@Composable
fun Opt(l: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(l, color = Color.White, fontSize = 13.sp)
        content()
    }
}

@Composable
fun NewComp(c: Cur, defName: String, onClose: () -> Unit, onOk: (Comp) -> Unit) {
    var nm by remember { mutableStateOf(defName) }
    var w by remember { mutableStateOf("1920") }
    var h by remember { mutableStateOf("1080") }
    var fp by remember { mutableStateOf("30") }
    var du by remember { mutableStateOf("6") }
    Modal("Composition settings", c, foot = {
        Btn("Cancel", c) { onClose() }
        Btn("Create", c, true) {
            val cm = Comp(
                nm.ifBlank { defName },
                (w.toIntOrNull() ?: 1920).coerceIn(16, 8192),
                (h.toIntOrNull() ?: 1080).coerceIn(16, 8192),
                (fp.toIntOrNull() ?: 30).coerceIn(1, 120),
                (du.toFloatOrNull() ?: 6f).coerceIn(1f, 600f)
            )
            onOk(cm)
        }
    }) {
        Lbl("Composition name")
        Fld(nm, c, 220.dp, false) { nm = it }
        Lbl("Preset")
        PRE.chunked(3).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { (n, pw, ph) ->
                    Btn(n, c) {
                        w = pw.toString()
                        h = ph.toString()
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Lbl("Width px")
                Fld(w, c, 100.dp, true) { w = it }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Lbl("Height px")
                Fld(h, c, 100.dp, true) { h = it }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Lbl("Frame rate")
                Fld(fp, c, 100.dp, true) { fp = it }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Lbl("Duration s")
                Fld(du, c, 100.dp, true) { du = it }
            }
        }
    }
}

@Composable
fun Prefs(st: Settings, c: Cur, onClose: () -> Unit) {
    Modal("Preferences", c, foot = { Btn("Done", c, true) { onClose() } }) {
        Text("Display", color = LAV, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Opt("UI size") { Seg(listOf("System", "Fit screen", "Custom"), st.ui, c) { st.ui = it; st.save() } }
        if (st.ui == 2) Opt("UI scale") { Sld(st.scale, 0.7f, 1.6f, c) { st.scale = it; st.save() } }
        Text(
            "System follows Android's Display size and Font size settings. Fit screen scales the interface to your screen width.",
            color = GR,
            fontSize = 11.sp
        )
    }
}

@Composable
fun MenuView(c: Cur) {
    val m = c.menu ?: return
    CompositionLocalProvider(LocalZ provides 2) {
        Box(Modifier.fillMaxSize().cur(c) { c.menu = null }) {
            val dn = LocalDensity.current
            val x = m.pos.x.coerceIn(0f, maxOf(c.sz.width - with(dn) { 150.dp.toPx() }, 0f))
            val y = m.pos.y.coerceIn(0f, maxOf(c.sz.height - with(dn) { 36.dp.toPx() } * m.items.size, 0f))
            Column(
                Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.width(150.dp)
                    .background(Color(0xFF2E2E2E), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(4.dp))
            ) {
                m.items.forEach { (t, a) ->
                    Box(Modifier.fillMaxWidth().cur(c) { c.menu = null; a() }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(t, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private val DK = darkColorScheme()

@Composable
fun Root(st: Settings, c: Cur) {
    val ctx = LocalContext.current
    var ps by remember { mutableStateOf<List<Project>>(emptyList()) }
    var open by remember { mutableStateOf<Project?>(null) }
    var showSet by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf<String?>(null) }
    var expP by remember { mutableStateOf<Project?>(null) }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch { ps = withContext(Dispatchers.IO) { Store.list(ctx) } }
    }
    LaunchedEffect(Unit) {
        ps = withContext(Dispatchers.IO) { Store.list(ctx) }
        crash = withContext(Dispatchers.IO) {
            val f = File(ctx.filesDir, "crash.txt")
            if (f.exists()) f.readText().take(8000) else null
        }
    }
    val foot = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us: List<Uri> ->
        if (us.isNotEmpty()) scope.launch {
            val pid = UUID.randomUUID().toString()
            val ms = withContext(Dispatchers.IO) { us.mapNotNull { Store.importMedia(ctx, pid, it) } }
            if (ms.isEmpty()) {
                Toast.makeText(ctx, "Nothing could be imported", Toast.LENGTH_SHORT).show()
            } else {
                val f = ms.first()
                val vis = ms.firstOrNull { it.kind != 2 }
                val du = when (f.kind) {
                    0 -> 5f
                    else -> f.dur
                }
                val cm = Comp(
                    f.name.substringBeforeLast('.'),
                    (vis?.w ?: 1920).coerceIn(16, 8192),
                    (vis?.h ?: 1080).coerceIn(16, 8192),
                    30,
                    du.coerceIn(1f, 600f)
                )
                val p = Project(pid, cm.name, 0L)
                p.media.addAll(ms)
                p.addComp(cm)
                ms.forEach { cm.addMedia(it) }
                cm.sel = 0
                p.clearUndo()
                open = p
                Store.saveA(ctx, p)
                reload()
            }
        }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) scope.launch {
            val p = withContext(Dispatchers.IO) { Store.imp(ctx, u) }
            if (p == null) {
                Toast.makeText(ctx, "Not a Kinetic project file", Toast.LENGTH_SHORT).show()
            } else {
                open = p
                reload()
            }
        }
    }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u: Uri? ->
        val p = expP
        if (u != null && p != null) {
            val s = p.js().toString()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openOutputStream(u)?.use { it.write(s.toByteArray()) }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!ok) Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun close() {
        val q = open
        open = null
        if (q != null) scope.launch {
            Store.saveA(ctx, q)
            reload()
        }
    }
    BackHandler(open != null || showSet || c.menu != null) {
        when {
            c.menu != null -> c.menu = null
            showSet -> showSet = false
            else -> close()
        }
    }
    MaterialTheme(colorScheme = DK) {
        Box(Modifier.fillMaxSize().background(BG).onSizeChanged { c.sz = Size(it.width.toFloat(), it.height.toFloat()) }) {
            val o = open
            if (o == null) {
                Home(
                    c, ps,
                    onOpen = { open = it },
                    onNew = {
                        val q = Project.empty()
                        open = q
                        scope.launch {
                            Store.saveA(ctx, q)
                            reload()
                        }
                    },
                    onFoot = { foot.launch(arrayOf("video/*", "audio/*", "image/*")) },
                    onImp = { imp.launch(arrayOf("*/*")) },
                    onSet = { showSet = true },
                    onDel = { p ->
                        scope.launch {
                            withContext(Dispatchers.IO) { Store.del(ctx, p) }
                            reload()
                        }
                    },
                    onDup = { p ->
                        scope.launch {
                            withContext(Dispatchers.IO) { Store.dup(ctx, p) }
                            reload()
                        }
                    },
                    onExp = {
                        expP = it
                        exp.launch(it.name + ".kin")
                    }
                )
            } else {
                key(o.id) { Editor(o, c, onBack = { close() }, onSet = { showSet = true }) }
            }
            if (showSet) Prefs(st, c) { showSet = false }
            val cr = crash
            if (cr != null) Modal("Last crash", c, foot = {
                Btn("Copy", c) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", cr))
                }
                Btn("Dismiss", c, true) {
                    File(ctx.filesDir, "crash.txt").delete()
                    crash = null
                }
            }) { Text(cr, color = GR, fontSize = 9.sp) }
            MenuView(c)
        }
    }
}

@Composable
fun App(st: Settings, c: Cur) {
    val d = LocalDensity.current
    val cfg = LocalConfiguration.current
    val m = when (st.ui) {
        0 -> 1f
        1 -> (cfg.screenWidthDp / 900f).coerceIn(0.7f, 1.8f)
        else -> st.scale
    }
    val dn = remember(d.density, d.fontScale, m) { Density(d.density * m, d.fontScale) }
    CompositionLocalProvider(LocalDensity provides dn) { Root(st, c) }
}
