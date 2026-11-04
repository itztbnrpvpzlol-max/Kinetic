package com.szkinetic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String
)

interface KineticPlugin {
    val manifest: PluginManifest
    fun actions(comp: Comp): List<Pair<String, () -> Unit>>
}

class FastLayersPlugin : KineticPlugin {
    override val manifest = PluginManifest(
        "com.szkinetic.fastlayers",
        "Fast Layers",
        "0.1.0",
        "Quickly create null, solid and adjustment layers."
    )

    override fun actions(comp: Comp) = listOf(
        "Create Null" to { comp.addNull() },
        "Create Adjustment" to { comp.addAdjustment() }
    )

    fun createSolid(comp: Comp, name: String, color: Long) = comp.addSolid(name, color)
}

object PluginRegistry {
    val plugins: List<KineticPlugin> = listOf(FastLayersPlugin())
}

@Composable
fun PluginsPanel(comp: Comp?, c: Cur, m: Modifier) {
    var solidName by remember { mutableStateOf("Solid 1") }
    var colorText by remember { mutableStateOf("#4A90E2") }
    Column(m.background(PB).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PLUGIN MANAGER", color = DIM, fontSize = 9.sp)
        if (comp == null) {
            Text("Open a composition to use plugins.", color = TXT, fontSize = 11.sp)
            return@Column
        }
        PluginRegistry.plugins.forEach { plugin ->
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF292929), RoundedCornerShape(3.dp))
                    .border(1.dp, Color(0xFF414141), RoundedCornerShape(3.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(plugin.manifest.name, color = Color.White, fontSize = 12.sp)
                        Text("v${plugin.manifest.version}  •  Testing plugin", color = DIM, fontSize = 9.sp)
                    }
                    Text("ENABLED", color = Color(0xFF75C98B), fontSize = 8.sp)
                }
                Text(plugin.manifest.description, color = TXT, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    plugin.actions(comp).forEach { (label, action) ->
                        FlatBtn(label, c) { action() }
                    }
                }
                Text("CREATE SOLID", color = DIM, fontSize = 9.sp)
                BasicTextField(
                    value = solidName,
                    onValueChange = { solidName = it },
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1B1B), RoundedCornerShape(2.dp)).padding(7.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = TXT, fontSize = 10.sp),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        Box {
                            if (solidName.isEmpty()) Text("Layer name", color = DIM, fontSize = 10.sp)
                            inner()
                        }
                    }
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
                        val fast = plugin as? FastLayersPlugin
                        fast?.createSolid(comp, solidName, parsePluginColorValue(colorText))
                    }
                }
            }
        }
        Text("Plugins are currently bundled for testing. External plugin installation will be added to the plugin API later.", color = DIM, fontSize = 9.sp)
    }
}

private fun parsePluginColorValue(raw: String): Long {
    return try {
        val h = raw.trim().removePrefix("#")
        val v = h.toLong(16)
        if (h.length == 6) (0xFF000000L or v) else if (h.length == 8) v else 0xFF4A90E2
    } catch (_: Exception) {
        0xFF4A90E2
    }
}

private fun parsePluginColor(raw: String) = Color(parsePluginColorValue(raw))
