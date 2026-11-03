package com.szkinetic

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val cp = p.comps[0]
    Canvas(Modifier.size(64.dp, 36.dp).background(Color.Black)) {
        cp.layers.forEach { l ->
            val x = l.p[0].firstOrNull()?.v ?: 0f
            val y = l.p[1].firstOrNull()?.v ?: 0f
            val s = l.p[2].firstOrNull()?.v ?: 1f
            val side = 0.3f * s * size.height
            val cx = (x + 1f) / 2f * size.width
            val cy = (1f - (y + 1f) / 2f) * size.height
            drawRect(Color(l.col), Offset(cx - side / 2, cy - side / 2), Size(side, side))
        }
    }
}

@Composable
fun Home(
    c: Cur,
    ps: List<Project>,
    onOpen: (Project) -> Unit,
    onNew: () -> Unit,
    onImp: () -> Unit,
    onSet: () -> Unit,
    onDel: (Project) -> Unit,
    onDup: (Project) -> Unit,
    onExp: (Project) -> Unit
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(150.dp).fillMaxHeight().background(Color(0xFF181818)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).background(LAV, RoundedCornerShape(5.dp)), Alignment.Center) {
                    Text("Ki", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text("Kinetic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().background(P3, RoundedCornerShape(4.dp)).padding(8.dp)) {
                Text("Home", color = Color.White, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth().cur(c) { onSet() }.padding(8.dp)) {
                Text("Preferences", color = GR, fontSize = 13.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Home", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("New composition", c, true, true) { onNew() }
                Btn("Import project", c, false, true) { onImp() }
            }
            Spacer(Modifier.height(18.dp))
            Text("Recent projects", color = GR, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(P3))
            val rs = rememberScrollState()
            Column(Modifier.weight(1f).fillMaxWidth().scr(c) { rs.dispatchRawDelta(-it) }.verticalScroll(rs)) {
                if (ps.isEmpty()) {
                    Text(
                        "No projects yet. Create a composition or import a project.",
                        color = GR,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                ps.forEach { p ->
                    val cp = p.comps[0]
                    Row(
                        Modifier.fillMaxWidth()
                            .cur(c, rc = mnu(c, "Open" to { onOpen(p) }, "Duplicate" to { onDup(p) }, "Export" to { onExp(p) }, "Delete" to { onDel(p) })) { onOpen(p) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Thumb(p)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontSize = 14.sp)
                            Text("${cp.w} x ${cp.h}, ${cp.fps} fps, ${cp.dur.toInt()} s", color = GR, fontSize = 11.sp)
                        }
                        Text(ago(p.mod), color = GR, fontSize = 11.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2A)))
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
                    Modifier.weight(1f).fillMaxWidth().scr(c) { rs.dispatchRawDelta(-it) }.verticalScroll(rs).padding(14.dp),
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
fun NewComp(c: Cur, onClose: () -> Unit, onOk: (Comp) -> Unit) {
    var nm by remember { mutableStateOf("Comp 1") }
    var w by remember { mutableStateOf("1920") }
    var h by remember { mutableStateOf("1080") }
    var fp by remember { mutableStateOf("30") }
    var du by remember { mutableStateOf("6") }
    Modal("Composition settings", c, foot = {
        Btn("Cancel", c) { onClose() }
        Btn("Create", c, true) {
            val cm = Comp(
                nm.ifBlank { "Comp 1" },
                (w.toIntOrNull() ?: 1920).coerceIn(16, 8192),
                (h.toIntOrNull() ?: 1080).coerceIn(16, 8192),
                (fp.toIntOrNull() ?: 30).coerceIn(1, 120),
                (du.toFloatOrNull() ?: 6f).coerceIn(1f, 600f)
            )
            cm.seed()
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
        Text("Cursor", color = LAV, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Opt("Virtual cursor") { Tgl(st.cursor, c) { st.cursor = it; st.save() } }
        Opt("Cursor speed") { Sld(st.speed, 0.5f, 4f, c) { st.speed = it; st.save() } }
        Text("Volume buttons", color = LAV, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Opt("Use as mouse buttons") { Tgl(st.vol, c) { st.vol = it; st.save() } }
        Opt("Volume up") { Seg(BT, st.up, c) { st.up = it; st.save() } }
        Opt("Volume down") { Seg(BT, st.down, c) { st.down = it; st.save() } }
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
            val x = m.pos.x.coerceIn(0f, maxOf(c.sz.width - 450f, 0f))
            val y = m.pos.y.coerceIn(0f, maxOf(c.sz.height - 130f * m.items.size, 0f))
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

@Composable
fun Root(st: Settings, c: Cur) {
    val ctx = LocalContext.current
    var ps by remember { mutableStateOf(Store.list(ctx)) }
    var open by remember { mutableStateOf<Project?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showSet by remember { mutableStateOf(false) }
    var expP by remember { mutableStateOf<Project?>(null) }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) {
            val p = Store.imp(ctx, u)
            if (p == null) {
                Toast.makeText(ctx, "Not a Kinetic project file", Toast.LENGTH_SHORT).show()
            } else {
                ps = Store.list(ctx)
                open = p
            }
        }
    }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u: Uri? ->
        val p = expP
        if (u != null && p != null) {
            try {
                ctx.contentResolver.openOutputStream(u)?.use { it.write(p.js().toString().toByteArray()) }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun close() {
        open?.let { Store.save(ctx, it) }
        open = null
        ps = Store.list(ctx)
    }
    BackHandler(open != null || showSet || showNew || c.menu != null) {
        when {
            c.menu != null -> c.menu = null
            showSet -> showSet = false
            showNew -> showNew = false
            else -> close()
        }
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(BG).onSizeChanged { c.sz = Size(it.width.toFloat(), it.height.toFloat()) }) {
            val o = open
            if (o == null) {
                Home(
                    c, ps,
                    onOpen = { open = it },
                    onNew = { showNew = true },
                    onImp = { imp.launch(arrayOf("*/*")) },
                    onSet = { showSet = true },
                    onDel = {
                        Store.del(ctx, it)
                        ps = Store.list(ctx)
                    },
                    onDup = {
                        Store.dup(ctx, it)
                        ps = Store.list(ctx)
                    },
                    onExp = {
                        expP = it
                        exp.launch(it.name + ".kin")
                    }
                )
            } else {
                Editor(o, c, onBack = { close() }, onSet = { showSet = true })
            }
            if (showNew) NewComp(c, { showNew = false }) { cm ->
                showNew = false
                val p = Project.create(cm)
                Store.save(ctx, p)
                ps = Store.list(ctx)
                open = p
            }
            if (showSet) Prefs(st, c) { showSet = false }
            MenuView(c)
            if (c.on) CursorLayer(c)
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
    CompositionLocalProvider(LocalDensity provides Density(d.density * m, d.fontScale)) { Root(st, c) }
}
