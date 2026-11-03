package com.szkinetic

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

val BG = Color(0xFF1D1D1D)
val P2 = Color(0xFF262626)
val P3 = Color(0xFF383838)
val AC = Color(0xFF2D8CEB)
val LAV = Color(0xFF9999FF)
val GR = Color(0xFFA0A0A0)
val HD = Color(0xFF2B2B2B)
val EDG = Color(0xFF151515)

@Composable
fun Btn(t: String, c: Cur, pri: Boolean = false, big: Boolean = false, f: () -> Unit) {
    Box(
        Modifier.background(if (pri) AC else P3, RoundedCornerShape(4.dp)).cur(c) { f() }
            .padding(horizontal = if (big) 18.dp else 10.dp, vertical = if (big) 11.dp else 5.dp),
        Alignment.Center
    ) { Text(t, color = Color.White, fontSize = if (big) 14.sp else 12.sp) }
}

@Composable
fun Hdr(t: String) {
    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF2E2E2E)).padding(horizontal = 8.dp), Alignment.CenterStart) {
        Text(t, color = Color(0xFFB0B0B0), fontSize = 11.sp)
    }
}

@Composable
fun Tgl(on: Boolean, c: Cur, f: (Boolean) -> Unit) {
    Box(Modifier.width(40.dp).height(22.dp).background(if (on) AC else P3, RoundedCornerShape(11.dp)).cur(c) { f(!on) }) {
        Box(Modifier.padding(2.dp).offset(x = if (on) 18.dp else 0.dp).size(18.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun Seg(opts: List<String>, sel: Int, c: Cur, f: (Int) -> Unit) {
    Row(Modifier.background(P3, RoundedCornerShape(4.dp))) {
        opts.forEachIndexed { i, o ->
            Box(Modifier.background(if (i == sel) AC else Color.Transparent, RoundedCornerShape(4.dp)).cur(c) { f(i) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(o, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Sld(v: Float, lo: Float, hi: Float, c: Cur, wd: Dp = 160.dp, f: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    val set = { o: Offset -> f(lo + (o.x / w).coerceIn(0f, 1f) * (hi - lo)) }
    val fr = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
    Box(Modifier.width(wd).height(22.dp).onSizeChanged { w = it.width.toFloat() }.cur(c, drag = set, ds = set, click = set)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).background(P3))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(fr).height(4.dp).background(AC))
        Box(Modifier.align(Alignment.CenterStart).offset { IntOffset((fr * (w - 12.dp.roundToPx())).toInt(), 0) }.size(12.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun Fld(v: String, c: Cur, w: Dp, num: Boolean, f: (String) -> Unit) {
    val fr = remember { FocusRequester() }
    Box(
        Modifier.width(w).background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp)).border(1.dp, P3, RoundedCornerShape(3.dp))
            .cur(c) { fr.requestFocus() }.padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        BasicTextField(
            value = v,
            onValueChange = f,
            modifier = Modifier.focusRequester(fr),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = if (num) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done)
        )
    }
}

@Composable
fun Mn(t: String, c: Cur, vararg i: Pair<String, () -> Unit>) {
    var r by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.onGloballyPositioned { r = it.boundsInRoot() }
            .cur(c) { c.menu = Menu(Offset(r.left, r.bottom), i.toList()) }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) { Text(t, color = Color(0xFFD8D8D8), fontSize = 12.sp) }
}

@Composable
fun Pn(t: String, f: Boolean, c: Cur, onF: () -> Unit, m: Modifier, body: @Composable ColumnScope.() -> Unit) {
    Column(m.padding(1.dp).background(P2).border(1.dp, if (f) AC else EDG)) {
        Box(Modifier.fillMaxWidth().height(22.dp).background(HD).cur(c) { onF() }.padding(horizontal = 8.dp), Alignment.CenterStart) {
            Text(t, color = if (f) Color.White else GR, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        body()
    }
}

@Composable
fun Tmb(m: Media?, mod: Modifier) {
    val im by produceState<ImageBitmap?>(null, m?.id) {
        value = if (m == null) null else withContext(Dispatchers.IO) { Thumbs.make(m) }
    }
    Box(mod.background(Color.Black), Alignment.Center) {
        val i = im
        if (i != null) {
            Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (m != null && m.kind == 2) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val n = 14
                val g = size.width / n
                for (k in 0 until n) {
                    val hh = size.height * (0.2f + 0.7f * ((m.id.hashCode() ushr (k % 24)) and 7) / 7f)
                    drawRect(Color(MCOL[2]), Offset(k * g + g * 0.2f, (size.height - hh) / 2), Size(g * 0.6f, hh))
                }
            }
        }
    }
}

@Composable
fun Sw(k: Int, on: Boolean, c: Cur, f: () -> Unit) {
    Canvas(Modifier.size(20.dp, 26.dp).cur(c) { f() }) {
        val col = if (on) Color(0xFFD0D0D0) else Color(0xFF494949)
        val cx = size.width / 2
        val cy = size.height / 2
        when (k) {
            0 -> {
                drawOval(col, Offset(cx - 7.dp.toPx(), cy - 4.dp.toPx()), Size(14.dp.toPx(), 8.dp.toPx()), style = Stroke(1.5f))
                drawCircle(col, 2.2.dp.toPx(), Offset(cx, cy))
            }
            1 -> {
                val p = Path().apply {
                    moveTo(cx - 6.dp.toPx(), cy - 2.5.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), cy - 2.5.dp.toPx())
                    lineTo(cx + 3.dp.toPx(), cy - 6.dp.toPx())
                    lineTo(cx + 3.dp.toPx(), cy + 6.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), cy + 2.5.dp.toPx())
                    lineTo(cx - 6.dp.toPx(), cy + 2.5.dp.toPx())
                    close()
                }
                drawPath(p, col)
            }
            else -> {
                drawRect(col, Offset(cx - 5.dp.toPx(), cy - 1.dp.toPx()), Size(10.dp.toPx(), 7.dp.toPx()))
                drawArc(col, 180f, 180f, false, Offset(cx - 3.5.dp.toPx(), cy - 6.dp.toPx()), Size(7.dp.toPx(), 10.dp.toPx()), style = Stroke(1.5f))
            }
        }
    }
}

@Composable
fun Ico(k: Int, on: Boolean, c: Cur, f: () -> Unit) {
    Canvas(Modifier.size(28.dp, 24.dp).cur(c) { f() }) {
        val s = size.height * 0.3f
        val cx = size.width / 2
        val cy = size.height / 2
        val col = Color(0xFFD8D8D8)
        fun tri(x: Float, dir: Float, z: Float) {
            val p = Path().apply {
                moveTo(x + dir * z, cy)
                lineTo(x - dir * z, cy - z)
                lineTo(x - dir * z, cy + z)
                close()
            }
            drawPath(p, col)
        }
        when (k) {
            0 -> {
                drawRect(col, Offset(cx - s * 1.2f, cy - s), Size(2.5f, s * 2))
                tri(cx + s * 0.3f, -1f, s)
            }
            1 -> tri(cx, -1f, s)
            2 -> if (on) {
                drawRect(col, Offset(cx - s * 0.9f, cy - s), Size(s * 0.6f, s * 2))
                drawRect(col, Offset(cx + s * 0.3f, cy - s), Size(s * 0.6f, s * 2))
            } else tri(cx, 1f, s * 1.1f)
            3 -> tri(cx, 1f, s)
            else -> {
                tri(cx - s * 0.3f, 1f, s)
                drawRect(col, Offset(cx + s * 0.9f, cy - s), Size(2.5f, s * 2))
            }
        }
    }
}

@Composable
fun Kv(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = GR, fontSize = 11.sp)
        Text(v, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PRow(col: Long, nm: String, ty: String, du: String, s: Boolean, c: Cur, rc: ((Offset) -> Unit)?, f: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (s) AC.copy(alpha = 0.3f) else Color.Transparent).cur(c, rc = rc) { f() }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).background(Color(col)))
        Text(nm, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(ty, color = GR, fontSize = 10.sp, modifier = Modifier.width(40.dp))
        Text(du, color = GR, fontSize = 10.sp, modifier = Modifier.width(34.dp))
    }
}
