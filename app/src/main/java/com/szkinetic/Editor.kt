package com.szkinetic

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Editor(p: Project, c: Cur, onBack: () -> Unit, onSet: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cid by remember { mutableStateOf(p.comps.firstOrNull()?.id ?: "") }
    var navStack by remember { mutableStateOf(emptyList<String>()) }
    val comp: Comp? = p.comps.firstOrNull { it.id == cid } ?: p.comps.firstOrNull()
    val openNested: (String) -> Unit = { target ->
        if (p.comps.any { it.id == target }) {
            if (cid.isNotEmpty() && cid != target) navStack = navStack + cid
            cid = target
        }
    }
    p.comps.forEach { it.onOpenComp = openNested }
    val backToParent = {
        val prev = navStack.lastOrNull()
        if (prev != null) {
            navStack = navStack.dropLast(1)
            cid = prev
        }
    }
    var root by remember { mutableStateOf(LayoutStore.loadCurrent(ctx) ?: defaultTree()) }
    val rootS = rememberUpdatedState(root)
    val dock = remember { DockCtx(c, { rootS.value }, { root = it }) }
    var tool by remember { mutableStateOf(0) }
    var res by remember { mutableStateOf(1) }
    var graph by remember { mutableStateOf(false) }
    var multi by remember { mutableStateOf(false) }
    var psel by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us: List<Uri> ->
        if (us.isNotEmpty()) scope.launch {
            var n = 0
            for (u in us) {
                val m = withContext(Dispatchers.IO) { Store.importMedia(ctx, p.id, u) }
                if (m != null) {
                    p.media.add(m)
                    psel = m.id
                    n++
                }
            }
            Store.saveA(ctx, p)
            Toast.makeText(ctx, "Imported $n of ${us.size}", Toast.LENGTH_SHORT).show()
        }
    }
    val doImp = { imp.launch(arrayOf("video/*", "audio/*", "image/*")) }
    val lo = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            LayoutStore.saveCurrent(ctx, rootS.value)
        }
    }
    DisposableEffect(Unit) { onDispose { LayoutStore.saveCurrent(ctx, rootS.value) } }
    LaunchedEffect(p) {
        var last = withContext(Dispatchers.Default) { p.js().toString() }
        while (true) {
            delay(4000)
            if (p.comps.any { it.playing }) continue // never serialize during playback
            val s = withContext(Dispatchers.Default) { p.js().toString() }
            if (s != last) {
                p.mod = System.currentTimeMillis()
                val t = withContext(Dispatchers.Default) { p.js().toString() }
                last = t
                withContext(Dispatchers.IO) { Store.put(ctx, p.id, t) }
            }
        }
    }
    val pl = comp?.playing == true
    LaunchedEffect(comp, pl) {
        val cm = comp
        if (cm != null && cm.playing) {
            try {
                var last = 0L
                var lastSync = 0L
                var lastUi = 0L
                Aud.warm(cm)
                Aud.sync(cm, true)
                while (cm.playing) {
                    val n = withFrameNanos { it }
                    if (last != 0L) {
                        var nt = cm.time + (n - last) / 1e9f
                        if (nt >= cm.dur) {
                            if (cm.loop) {
                                nt %= cm.dur
                                lastSync = 0L // re-sync audio after the loop point
                            } else {
                                nt = cm.dur
                                cm.playing = false
                            }
                        }
                        cm.time = nt
                    }
                    last = n
                    if (n - lastSync > 120_000_000L) {
                        lastSync = n
                        Aud.sync(cm, true)
                    }
                    if (n - lastUi > 100_000_000L) {
                        lastUi = n
                        cm.uiTime = cm.time
                    }
                }
            } finally {
                Aud.sync(cm, false)
                cm.uiTime = cm.time
            }
        } else if (cm != null) {
            Aud.sync(cm, false)
            cm.uiTime = cm.time
        }
    }
    // keep audio players for this comp prepared so play starts instantly
    LaunchedEffect(comp, comp?.layers?.size) {
        val cm = comp
        if (cm != null) Aud.warm(cm)
    }
    DisposableEffect(lo, p) {
        val ob = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) {
                p.comps.forEach { it.playing = false }
                Aud.stop()
            }
        }
        lo.lifecycle.addObserver(ob)
        onDispose { lo.lifecycle.removeObserver(ob) }
    }
    DisposableEffect(p) {
        onDispose {
            p.comps.forEach { it.playing = false }
            Aud.stop()
            Frames.clear()
        }
    }
    // switching composition stops playback of the previous one
    LaunchedEffect(comp?.id) {
        p.comps.forEach { if (it.id != comp?.id) it.playing = false }
        Aud.stop()
    }
    val undo = {
        if (p.undo()) Unit else Toast.makeText(ctx, "Nothing to undo", Toast.LENGTH_SHORT).show()
    }
    val redo = {
        if (p.redo()) Unit else Toast.makeText(ctx, "Nothing to redo", Toast.LENGTH_SHORT).show()
    }
    val note = { s: String -> Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() }
    val ease = { m: Int ->
        if (comp != null) {
            if (comp.nSelKeys() == 0) note("Select keyframes first") else comp.easeKeys(m)
        }
    }
    dock.title = { id ->
        if (id == "comp") "Composition  " + (comp?.name ?: "") else panelTitle(id)
    }
    val content: @Composable (String, Modifier) -> Unit = { id, mod ->
        when (id) {
            "proj" -> ProjPanel(p, comp, c, psel, { psel = it }, { openNested(it) }, { showNew = true }, false, {}, { doImp() }, mod)
            "comp" -> if (comp != null) {
                CompPanel(p, comp, c, tool, res, { res = it }, false, {}, { openNested(it) }, mod)
            } else {
                EmptyComp(c, false, {}, { showNew = true }, mod)
            }
            "time" -> if (comp != null) {
                TimePanel(comp, c, false, {}, graph, { graph = it }, multi, { multi = it }, mod)
            } else {
                Box(mod, Alignment.Center) { Text("No composition", color = DIM, fontSize = 11.sp) }
            }
            "info" -> InfoPanel(comp, c, mod)
            "browser" -> EffectsBrowserPanel(comp, c, mod)
            "tracking" -> TrackingPanel(comp, c, mod)
            "masks" -> MasksPanel(comp, c, mod)
            "camera" -> CameraPanel(comp, c, mod)
            "plugins" -> PluginsPanel(comp, c, mod)
            else -> InfoPanel(comp, c, mod)
        }
    }
    val winItems = ArrayList<Pair<String, () -> Unit>>()
    for (id in PANELS) {
        val shown = id in panelsIn(root)
        winItems.add(((if (shown) "\u2713 " else "     ") + panelTitle(id)) to {
            root = if (shown) (removePanel(root, id) ?: defaultTree()) else addPanel(root, id)
        })
    }
    winItems.add("Reset layout" to { root = defaultTree() })
    winItems.add("Save layout" to {
        val nm = "Layout " + (LayoutStore.names(ctx).size + 1)
        LayoutStore.save(ctx, nm, root)
        note("Saved $nm")
    })
    for (nm in LayoutStore.names(ctx)) {
        winItems.add("Load: $nm" to {
            val l = LayoutStore.load(ctx, nm)
            if (l != null) root = l
        })
        winItems.add("Delete: $nm" to { LayoutStore.delete(ctx, nm) })
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BG)) {
            // ---- menu bar
            Row(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF1F1F1F)), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(16.dp).background(LAV, RoundedCornerShape(3.dp)), Alignment.Center) {
                    Text("Ki", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
                Spacer(Modifier.width(2.dp))
                Mn("File", c, "Import media..." to { doImp() }, "Save project" to {
                    scope.launch { Store.saveA(ctx, p) }
                    Unit
                }, "Close project" to { onBack() })
                Mn(
                    "Edit", c,
                    "Undo" to { undo() },
                    "Redo" to { redo() },
                    "Copy keyframes" to {
                        val n = comp?.copyKeys() ?: 0
                        note(if (n == 0) "Select keyframes to copy" else "Copied $n keyframes")
                    },
                    "Paste keyframes" to {
                        val n = comp?.pasteKeys() ?: 0
                        note(if (n == 0) "Nothing to paste on the selected layer(s)" else "Pasted $n keyframes")
                    },
                    "Delete keyframes" to { comp?.delKeys(); Unit },
                    "Select all keyframes" to { comp?.selectAllKeys(); Unit },
                    "Select all layers" to { comp?.selectAllLayers(); Unit },
                    "Deselect all" to {
                        comp?.clearKeys()
                        comp?.layers?.forEach { it.pick = false }
                        Unit
                    },
                    (if (multi) "Multi-select: on" else "Multi-select: off") to { multi = !multi },
                    "Preferences..." to { onSet() }
                )
                Mn(
                    "Composition", c,
                    "New composition..." to { showNew = true },
                    "New shape layer" to { comp?.add(); Unit },
                    "New null object" to { comp?.addNull(); Unit },
                    "Back to Parent" to { backToParent() }
                )
                Mn(
                    "Layer", c,
                    "Pre-compose selected layers" to {
                        val nm = "Pre-comp ${p.comps.size + 1}"
                        val ok = comp?.precomposeSelected(nm) == true
                        if (ok) {
                            psel = p.comps.lastOrNull()?.id ?: ""
                            scope.launch { Store.saveA(ctx, p) }
                            note("Created $nm")
                        } else {
                            note("Cannot pre-compose: check the selected layers and their parenting")
                        }
                    },
                    "Duplicate" to { comp?.dupSel(); Unit },
                    "Split at playhead" to { comp?.split(comp.sel); Unit },
                    "Parent to new null" to { comp?.nullParent(comp.sel); Unit },
                    "Delete" to { comp?.delSel(); Unit }
                )
                Mn(
                    "Animation", c,
                    "Easy Ease" to { ease(3) },
                    "Easy Ease In" to { ease(4) },
                    "Easy Ease Out" to { ease(5) },
                    "Auto Bezier" to { ease(1) },
                    "Linear" to { ease(0) },
                    "Hold" to { ease(2) },
                    "Reveal animated properties" to {
                        comp?.selected()?.forEach {
                            it.open = true
                            it.animOnly = true
                        }
                        Unit
                    },
                    (if (graph) "Hide Graph Editor" else "Show Graph Editor") to { graph = !graph }
                )
                Mn("Window", c, *winItems.toTypedArray())
                Mn("View", c, (if (c.on) "Hide virtual cursor" else "Show virtual cursor") to { c.on = !c.on })
                Spacer(Modifier.weight(1f))
                Text(p.name, color = GR, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                Spacer(Modifier.width(8.dp))
            }
            // ---- tools bar
            Row(Modifier.fillMaxWidth().height(26.dp).background(Color(0xFF2B2B2B)), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(4.dp))
                ToolIc(0, false, c) { onBack() }
                Spacer(Modifier.width(6.dp))
                ToolIc(1, tool == 0, c) { tool = 0 }
                ToolIc(2, tool == 1, c) { tool = 1 }
                ToolIc(3, tool == 2, c) { tool = 2 }
                ToolIc(4, false, c) { note("Rotation tool is not available yet") }
                ToolIc(5, false, c) { note("Camera tool is not available yet") }
                Spacer(Modifier.width(6.dp))
                ToolIc(6, false, c) { comp?.add(); Unit }
                ToolIc(7, tool == 3, c) { tool = 3 }
                ToolIc(8, false, c) { note("Text tool is not available yet") }
                ToolIc(9, false, c) { note("Brush tool is not available yet") }
                ToolIc(10, false, c) { note("Eraser tool is not available yet") }
                ToolIc(11, false, c) { note("Puppet pin tool is not available yet") }
                Spacer(Modifier.width(10.dp))
                Text("Fill:", color = TXT, fontSize = 10.sp)
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(16.dp, 12.dp).background(Color(0xFFD9F02A)))
                Spacer(Modifier.width(8.dp))
                Text("Stroke:", color = TXT, fontSize = 10.sp)
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(16.dp, 12.dp).background(Color.White))
                Spacer(Modifier.width(3.dp))
                Text("2 px", color = TXT, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text("Add: \u25CF", color = TXT, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("Essentials", color = Color.White, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Text("Standard", color = DIM, fontSize = 10.sp)
                Spacer(Modifier.width(10.dp))
            }
            DockNode(root, dock, content, Modifier.fillMaxWidth().weight(1f))
        }
        if (showNew) NewComp(c, "Comp ${p.comps.size + 1}", { showNew = false }) { cm ->
            showNew = false
            p.mark()
            p.addComp(cm)
            cid = cm.id
            navStack = emptyList()
            psel = cm.id
            scope.launch { Store.saveA(ctx, p) }
        }
        DockOverlay(dock)
    }
}
