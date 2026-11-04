package com.szkinetic

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private data class UserEffectPreset(
    val name: String,
    val effectId: Int,
    val amount: Float
)

@Composable
fun PluginsPanel(comp: Comp?, c: Cur, m: Modifier) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var presetName by remember { mutableStateOf("My Custom Effect") }
    var effectIndex by remember { mutableIntStateOf(0) }
    var amount by remember { mutableFloatStateOf(0.65f) }
    var solidName by remember { mutableStateOf("Solid 1") }
    var colorText by remember { mutableStateOf("#4A90E2") }
    val presets = remember(ctx) { loadPresets(ctx).toMutableStateList() }
    val effects = remember { RenderEffects.all }

    LaunchedEffect(Unit) { PluginRegistry.bootstrap(); refresh++ }
    val installedPlugins = remember(refresh) { PluginRegistry.all() }

    Column(m.background(PB).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PLUGIN STUDIO", color = DIM, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("API v$KINETIC_PLUGIN_API_VERSION", color = VB, fontSize = 9.sp)
        }
        if (comp == null) {
            Text("Open a composition to run plugin actions and apply effects.", color = TXT, fontSize = 10.sp)
        }

        Text("INSTALLED PLUGINS", color = DIM, fontSize = 9.sp)
        installedPlugins.forEach { entry ->
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF292929), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF414141), RoundedCornerShape(4.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.manifest.name, color = Color.White, fontSize = 12.sp)
                        Text("v${entry.manifest.version}  •  by ${entry.manifest.author}", color = DIM, fontSize = 9.sp)
                    }
                    Text(if (entry.enabled) "ENABLED" else "DISABLED", color = if (entry.enabled) Color(0xFF75C98B) else DIM, fontSize = 8.sp)
                    Spacer(Modifier.width(6.dp))
                    FlatBtn(if (entry.enabled) "Disable" else "Enable", c) {
                        PluginRegistry.setEnabled(entry.manifest.id, !entry.enabled)
                        refresh++
                    }
                }
                Text(entry.manifest.description, color = TXT, fontSize = 10.sp)
                Text("PERMISSIONS  •  " + entry.grantedPermissions.joinToString { it.id }, color = DIM, fontSize = 8.sp)
                entry.actions.forEach { action ->
                    FlatBtn(action.title, c) {
                        try {
                            val context = PluginRegistry.context(comp, entry.grantedPermissions)
                            action.execute(context)
                            Toast.makeText(ctx, "${action.title} complete", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message ?: "Plugin action failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                entry.effects.forEach { effect ->
                    FlatBtn("Apply ${effect.name}", c) {
                        try {
                            PluginRegistry.context(comp, entry.grantedPermissions)
                                .applyEffect(effect.renderEffectId, effect.defaultAmount)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message ?: "Select a layer first", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        Text("CUSTOM EFFECT PRESETS", color = DIM, fontSize = 9.sp)
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF202020), RoundedCornerShape(4.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text("Build a reusable effect recipe from Kinetic's live GPU effects.", color = TXT, fontSize = 9.sp)
            BasicTextField(
                value = presetName,
                onValueChange = { presetName = it.take(48) },
                modifier = Modifier.fillMaxWidth().background(Color(0xFF171717), RoundedCornerShape(3.dp)).padding(7.dp),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 10.sp),
                cursorBrush = SolidColor(Color.White)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlatBtn("‹", c) { effectIndex = (effectIndex - 1).coerceAtLeast(0) }
                Column(Modifier.weight(1f)) {
                    Text(effects[effectIndex].name, color = Color.White, fontSize = 10.sp)
                    Text(effects[effectIndex].description, color = DIM, fontSize = 8.sp)
                }
                FlatBtn("›", c) { effectIndex = (effectIndex + 1).rem(effects.size) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Strength", color = TXT, fontSize = 9.sp)
                Spacer(Modifier.weight(1f))
                FlatBtn("−", c) { amount = (amount - 0.05f).coerceAtLeast(0f) }
                Text("${(amount * 100).toInt()}%", color = VB, fontSize = 10.sp)
                FlatBtn("+", c) { amount = (amount + 0.05f).coerceAtMost(1f) }
            }
            FlatBtn("Save Effect Preset", c) {
                if (presetName.isBlank()) {
                    Toast.makeText(ctx, "Enter a preset name", Toast.LENGTH_SHORT).show()
                } else {
                    presets.add(UserEffectPreset(presetName.trim(), effects[effectIndex].id, amount))
                    savePresets(ctx, presets)
                    Toast.makeText(ctx, "Effect preset saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (presets.isNotEmpty()) {
            Text("MY EFFECT PRESETS", color = DIM, fontSize = 9.sp)
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().vscr(c, rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presets.forEachIndexed { index, preset ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF252525), RoundedCornerShape(3.dp)).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, color = TXT, fontSize = 10.sp)
                            Text("${RenderEffects.byId(preset.effectId)?.name ?: "Effect"} • ${(preset.amount * 100).toInt()}%", color = DIM, fontSize = 8.sp)
                        }
                        FlatBtn("Apply", c) {
                            if (comp == null) Toast.makeText(ctx, "Open a composition first", Toast.LENGTH_SHORT).show()
                            else {
                                comp.applyEffect(preset.effectId)
                                comp.adjustEffectAmount(preset.amount - 0.5f)
                            }
                        }
                        Spacer(Modifier.width(5.dp))
                        FlatBtn("×", c) {
                            presets.removeAt(index)
                            savePresets(ctx, presets)
                        }
                    }
                }
            }
        }

        Text("CREATE SOLID", color = DIM, fontSize = 9.sp)
        BasicTextField(
            value = solidName,
            onValueChange = { solidName = it },
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1B1B), RoundedCornerShape(2.dp)).padding(7.dp),
            singleLine = true,
            textStyle = TextStyle(color = TXT, fontSize = 10.sp),
            cursorBrush = SolidColor(Color.White)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(18.dp).background(parsePluginColor(colorText), RoundedCornerShape(2.dp)).border(1.dp, Color.White))
            BasicTextField(
                value = colorText,
                onValueChange = { colorText = it },
                modifier = Modifier.weight(1f).background(Color(0xFF1B1B1B), RoundedCornerShape(2.dp)).padding(7.dp),
                singleLine = true,
                textStyle = TextStyle(color = TXT, fontSize = 10.sp),
                cursorBrush = SolidColor(Color.White)
            )
            FlatBtn("Add Solid", c) {
                if (comp == null) Toast.makeText(ctx, "Open a composition first", Toast.LENGTH_SHORT).show()
                else comp.addSolid(solidName, parsePluginColorValue(colorText))
            }
        }
        Text(
            "Plugin API v1 supports native Kotlin plugin modules, lifecycle hooks, declared permissions, commands and effect recipes. External APK/package installation and third-party shader execution are not enabled yet.",
            color = DIM, fontSize = 8.sp
        )
    }
}

private fun loadPresets(context: Context): List<UserEffectPreset> {
    val raw = context.getSharedPreferences("kinetic_effect_presets", Context.MODE_PRIVATE)
        .getString("presets", "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optInt("effect", 0)
            if (RenderEffects.byId(id) == null) null
            else UserEffectPreset(o.optString("name", "Custom Effect"), id, o.optDouble("amount", 0.5).toFloat().coerceIn(0f, 1f))
        }
    } catch (_: Exception) { emptyList() }
}

private fun savePresets(context: Context, presets: List<UserEffectPreset>) {
    val arr = JSONArray()
    presets.forEach { p ->
        arr.put(JSONObject().put("name", p.name).put("effect", p.effectId).put("amount", p.amount.toDouble()))
    }
    context.getSharedPreferences("kinetic_effect_presets", Context.MODE_PRIVATE)
        .edit().putString("presets", arr.toString()).apply()
}

private fun parsePluginColorValue(raw: String): Long {
    return try {
        val h = raw.trim().removePrefix("#")
        val v = h.toLong(16)
        if (h.length == 6) (0xFF000000L or v) else if (h.length == 8) v else 0xFF4A90E2
    } catch (_: Exception) { 0xFF4A90E2 }
}

private fun parsePluginColor(raw: String) = Color(parsePluginColorValue(raw))
