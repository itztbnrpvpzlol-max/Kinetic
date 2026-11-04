package com.szkinetic

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Settings(c: Context) {
    private val sp = c.getSharedPreferences("kinetic", Context.MODE_PRIVATE)
    private val initialCursor = if (!sp.getBoolean("touch_input_migrated", false)) {
        sp.edit().putBoolean("cursor", false).putBoolean("touch_input_migrated", true).apply()
        false
    } else sp.getBoolean("cursor", false)
    var cursor by mutableStateOf(initialCursor)
    var vol by mutableStateOf(sp.getBoolean("vol", true))
    var up by mutableStateOf(sp.getInt("up", 0))
    var down by mutableStateOf(sp.getInt("down", 1))
    var speed by mutableStateOf(sp.getFloat("speed", 1.6f))
    var csize by mutableStateOf(sp.getFloat("csize", 1f))
    var ui by mutableStateOf(sp.getInt("ui", 0))
    var scale by mutableStateOf(sp.getFloat("scale", 1f))

    fun save() {
        sp.edit()
            .putBoolean("cursor", cursor)
            .putBoolean("vol", vol)
            .putInt("up", up)
            .putInt("down", down)
            .putFloat("speed", speed)
            .putFloat("csize", csize)
            .putInt("ui", ui)
            .putFloat("scale", scale)
            .apply()
    }
}

object Store {
    fun dir(c: Context): File = File(c.filesDir, "projects").also { it.mkdirs() }

    fun mdir(c: Context, id: String): File = File(c.filesDir, "media/$id").also { it.mkdirs() }

    fun fix(c: Context, p: Project) {
        val d = File(c.filesDir, "media/" + p.id)
        p.media.forEach { it.path = File(d, it.fn).path }
    }

    fun list(c: Context): List<Project> =
        (dir(c).listFiles() ?: emptyArray<File>()).filter { it.name.endsWith(".kin") }.mapNotNull {
            try {
                Project.fromJs(JSONObject(it.readText())).also { p -> fix(c, p) }
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.mod }

    fun put(c: Context, id: String, s: String) {
        try {
            val d = dir(c)
            val f = File(d, "$id.kin")
            val t = File(d, "$id.tmp")
            t.writeText(s)
            if (!t.renameTo(f)) {
                f.writeText(s)
                t.delete()
            }
        } catch (e: Exception) {
        }
    }

    fun save(c: Context, p: Project) {
        p.mod = System.currentTimeMillis()
        put(c, p.id, p.js().toString())
    }

    suspend fun saveA(c: Context, p: Project) {
        p.mod = System.currentTimeMillis()
        val s = p.js().toString()
        withContext(Dispatchers.IO) { put(c, p.id, s) }
    }

    fun del(c: Context, p: Project) {
        File(dir(c), p.id + ".kin").delete()
        try {
            File(c.filesDir, "media/" + p.id).deleteRecursively()
        } catch (e: Exception) {
        }
    }

    fun dup(c: Context, p: Project) {
        val q = Project.fromJs(p.js())
        val n = Project(UUID.randomUUID().toString(), p.name + " copy", 0L, q.comps, q.media)
        try {
            File(c.filesDir, "media/" + p.id).copyRecursively(mdir(c, n.id), true)
        } catch (e: Exception) {
        }
        fix(c, n)
        save(c, n)
    }

    fun imp(c: Context, u: Uri): Project? = try {
        val t = c.contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }
        val q = Project.fromJs(JSONObject(t))
        val p = Project(UUID.randomUUID().toString(), q.name, 0L, q.comps, q.media)
        fix(c, p)
        save(c, p)
        p
    } catch (e: Exception) {
        null
    }

    fun importMedia(c: Context, pid: String, u: Uri): Media? = try {
        val cr = c.contentResolver
        val mt = cr.getType(u) ?: ""
        val k = when {
            mt.startsWith("image") -> 0
            mt.startsWith("video") -> 1
            mt.startsWith("audio") -> 2
            else -> -1
        }
        if (k < 0) {
            null
        } else {
            var nm = "media"
            cr.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) nm = it.getString(0) ?: nm
            }
            val ext = nm.substringAfterLast('.', "").filter { it.isLetterOrDigit() }.take(5)
            val id = UUID.randomUUID().toString()
            val f = File(mdir(c, pid), id.take(8) + (if (ext.isEmpty()) "" else ".$ext"))
            cr.openInputStream(u)!!.use { i -> f.outputStream().use { o -> i.copyTo(o, 65536) } }
            var d = 0f
            var w = 0
            var h = 0
            if (k == 0) {
                val o = android.graphics.BitmapFactory.Options()
                o.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                w = o.outWidth
                h = o.outHeight
                val r = rot(f.path)
                if (r == 90 || r == 270) {
                    val x = w
                    w = h
                    h = x
                }
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(f.path)
                    d = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000f
                    if (k == 1) {
                        w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        val rt = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (rt == 90 || rt == 270) {
                            val x = w
                            w = h
                            h = x
                        }
                    }
                } finally {
                    r.release()
                }
            }
            Media(id, nm, k, f.name, d, w, h).also { it.path = f.path }
        }
    } catch (e: Exception) {
        null
    }
}
