package com.szkinetic

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

class Settings(c: Context) {
    private val sp = c.getSharedPreferences("kinetic", Context.MODE_PRIVATE)
    var cursor by mutableStateOf(sp.getBoolean("cursor", true))
    var vol by mutableStateOf(sp.getBoolean("vol", true))
    var up by mutableStateOf(sp.getInt("up", 0))
    var down by mutableStateOf(sp.getInt("down", 1))
    var speed by mutableStateOf(sp.getFloat("speed", 1.6f))
    var ui by mutableStateOf(sp.getInt("ui", 0))
    var scale by mutableStateOf(sp.getFloat("scale", 1f))

    fun save() {
        sp.edit()
            .putBoolean("cursor", cursor)
            .putBoolean("vol", vol)
            .putInt("up", up)
            .putInt("down", down)
            .putFloat("speed", speed)
            .putInt("ui", ui)
            .putFloat("scale", scale)
            .apply()
    }
}

object Store {
    fun dir(c: Context): File = File(c.filesDir, "projects").also { it.mkdirs() }

    fun list(c: Context): List<Project> =
        (dir(c).listFiles() ?: emptyArray<File>()).mapNotNull {
            try {
                Project.fromJs(JSONObject(it.readText()))
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.mod }

    fun save(c: Context, p: Project) {
        p.mod = System.currentTimeMillis()
        File(dir(c), p.id + ".kin").writeText(p.js().toString())
    }

    fun del(c: Context, p: Project) {
        File(dir(c), p.id + ".kin").delete()
    }

    fun dup(c: Context, p: Project) {
        val q = Project.fromJs(p.js())
        save(c, Project(UUID.randomUUID().toString(), p.name + " copy", 0L, q.comps))
    }

    fun imp(c: Context, u: Uri): Project? = try {
        val t = c.contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }
        val q = Project.fromJs(JSONObject(t))
        val p = Project(UUID.randomUUID().toString(), q.name, 0L, q.comps)
        save(c, p)
        p
    } catch (e: Exception) {
        null
    }
}
