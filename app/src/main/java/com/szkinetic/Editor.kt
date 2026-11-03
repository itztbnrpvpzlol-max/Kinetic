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
    val comp: Comp? = p.comps.firstOrNull { it.id == cid } ?: p.comps.firstOrNull()
    var foc by remember { mutableStateOf(2) }
    var psel by remember { mutableStateOf("") }
    var rt by remember { mutableStateOf(0) }
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
    LaunchedEffect(p) {
        var last = p.js().toString()
        while (true) {
            delay(4000)
            val s = p.js().toString()
            if (s != last) {
                p.mod = System.currentTimeMillis()
                val t = p.js().toString()
                last = t
                withContext(Dispatchers.IO) { Store.put(ctx, p.id, t) }
            }
        }
    }
    val pl = comp?.playing == true
    LaunchedEffect(comp, pl) {
        val cm = comp
        if (cm != null && cm.playing) {
            var last = 0L
            while (cm.playing) {
                val n = withFrameNanos { it }
                if (last != 0L) {
                    var nt = cm.time + (n - last) / 1e9f
                    if (nt >= cm.dur) {
                        if (cm.loop) {
                            nt %= cm.dur
                        } else {
                            nt = cm.dur
                            cm.playing = false
                        }
                    }
                    cm.time = nt
                }
                last = n
                Aud.sync(cm, true)
            }
            Aud.sync(cm, false)
        }
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
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BG)) {
            Row(Modifier.fillMaxWidth().height(28.dp).background(Color(0xFF1F1F1F)), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(18.dp).background(LAV, RoundedCornerShape(4.dp)), Alignment.Center) {
                    Text("Ki", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
                Spacer(Modifier.width(4.dp))
                Mn("File", c, "Import media..." to { doImp() }, "Save project" to {
                    scope.launch { Store.saveA(ctx, p) }
                    Unit
                }, "Close project" to { onBack() })
                Mn("Edit", c, "Undo" to { undo() }, "Redo" to { redo() }, "Preferences..." to { onSet() })
                Mn(
                    "Composition", c,
                    "New composition..." to { showNew = true },
                    "New shape layer" to { comp?.add(); Unit },
                    "New null object" to { comp?.addNull(); Unit },
                    "New adjustment layer" to { comp?.addAdj(); Unit }
                )
                Mn(
                    "Layer", c,
                    "Duplicate" to { comp?.dup(comp.sel); Unit },
                    "Split at playhead" to { comp?.split(comp.sel); Unit },
                    "Parent to new null" to { comp?.nullParent(comp.sel); Unit },
                    "Delete" to { comp?.del(comp.sel); Unit }
                )
                Mn("View", c, (if (c.on) "Hide virtual cursor" else "Show virtual cursor") to { c.on = !c.on })
                Spacer(Modifier.weight(1f))
                Text(p.name, color = GR, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.background(P3, RoundedCornerShape(3.dp)).cur(c) { onBack() }.padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("Home", color = Color.White, fontSize = 11.sp)
                }
                Spacer(Modifier.width(6.dp))
            }
            if (comp == null) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    ProjPanel(
                        p, null, c, psel, { psel = it }, { cid = it }, { showNew = true },
                        foc == 0, { foc = 0 }, { doImp() }, Modifier.width(232.dp).fillMaxHeight()
                    )
                    EmptyComp(c, foc == 1, { foc = 1 }, { showNew = true }, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Row(Modifier.fillMaxWidth().weight(1.15f)) {
                    ProjPanel(
                        p, comp, c, psel, { psel = it }, { cid = it }, { showNew = true },
                        foc == 0, { foc = 0 }, { doImp() }, Modifier.width(232.dp).fillMaxHeight()
                    )
                    CompPanel(comp, c, foc == 1, { foc = 1 }, Modifier.weight(1f).fillMaxHeight())
                    RightPanel(comp, c, rt, { rt = it }, foc == 3, { foc = 3 }, Modifier.width(236.dp).fillMaxHeight())
                }
                TimePanel(comp, c, foc == 2, { foc = 2 }, Modifier.fillMaxWidth().weight(1f))
            }
        }
        if (showNew) NewComp(c, "Comp ${p.comps.size + 1}", { showNew = false }) { cm ->
            showNew = false
            p.mark()
            p.addComp(cm)
            cid = cm.id
            psel = cm.id
            scope.launch { Store.saveA(ctx, p) }
        }
    }
}
