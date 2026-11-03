package com.szkinetic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.Executors

fun rot(p: String): Int = try {
    when (ExifInterface(p).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        6 -> 90
        3 -> 180
        8 -> 270
        else -> 0
    }
} catch (e: Exception) {
    0
}

fun dec(p: String, mx: Int): Bitmap? {
    val o = BitmapFactory.Options()
    o.inJustDecodeBounds = true
    BitmapFactory.decodeFile(p, o)
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / s > mx) s *= 2
    val q = BitmapFactory.Options()
    q.inSampleSize = s
    val b = BitmapFactory.decodeFile(p, q) ?: return null
    val r = rot(p)
    if (r == 0) return b
    val m = Matrix()
    m.postRotate(r.toFloat())
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
}

fun fit(b: Bitmap, mx: Int): Bitmap {
    val s = maxOf(b.width, b.height)
    if (s <= mx) return b
    val k = mx.toFloat() / s
    return Bitmap.createScaledBitmap(b, maxOf((b.width * k).toInt(), 1), maxOf((b.height * k).toInt(), 1), true)
}

object Frames {
    var ver by mutableStateOf(0)
    private val cache = LruCache<String, Bitmap>(24)
    private val last = HashMap<String, Bitmap>()
    private val pend = HashSet<String>()
    private val want = HashMap<String, Int>()
    private val rets = HashMap<String, MediaMetadataRetriever>()
    private val ex = Executors.newSingleThreadExecutor()

    fun get(m: Media, t: Float): Bitmap? {
        val v = ver
        val b = if (m.kind == 1) maxOf((t * 8f).toInt(), 0) else 0
        val k = m.id + ":" + b
        synchronized(this) {
            val h = cache.get(k)
            if (h != null) {
                last[m.id] = h
                return h
            }
            want[m.id] = b
            if (pend.add(k)) ex.execute { load(m, b, k) }
            return last[m.id]
        }
    }

    private fun load(m: Media, b: Int, k: String) {
        var bmp: Bitmap? = null
        val skip = synchronized(this) { m.kind == 1 && want[m.id] != b }
        if (!skip) {
            try {
                if (m.kind == 0) {
                    bmp = dec(m.path, 2048)
                } else {
                    val r = synchronized(rets) {
                        rets.getOrPut(m.id) { MediaMetadataRetriever().also { it.setDataSource(m.path) } }
                    }
                    val f = r.getFrameAtTime(b * 125000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (f != null) bmp = fit(f, 1280)
                }
            } catch (e: Exception) {
            }
        }
        synchronized(this) {
            pend.remove(k)
            if (bmp != null) {
                cache.put(k, bmp)
                last[m.id] = bmp
            }
        }
        ver = ver + 1
    }
}

object Thumbs {
    private val c = LruCache<String, ImageBitmap>(64)

    fun make(m: Media): ImageBitmap? {
        c.get(m.id)?.let { return it }
        var b: Bitmap? = null
        try {
            if (m.kind == 0) {
                b = dec(m.path, 192)
            } else if (m.kind == 1) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(m.path)
                    b = r.getFrameAtTime((m.dur * 250000f).toLong())
                } finally {
                    r.release()
                }
            }
        } catch (e: Exception) {
        }
        val x = b?.let { fit(it, 192).asImageBitmap() } ?: return null
        c.put(m.id, x)
        return x
    }
}

object Aud {
    private val ps = HashMap<Layer, MediaPlayer>()

    private fun mk(m: Media): MediaPlayer? = try {
        MediaPlayer().also {
            it.setDataSource(m.path)
            it.prepare()
        }
    } catch (e: Exception) {
        null
    }

    fun sync(c: Comp, on: Boolean) {
        ps.keys.toList().filter { it !in c.layers }.forEach {
            try {
                ps.remove(it)?.release()
            } catch (e: Exception) {
            }
        }
        for (l in c.layers) {
            val m = c.lib.firstOrNull { it.id == l.mid }
            if (m == null || m.kind == 0) continue
            val act = on && !l.mute && c.time >= l.start && c.time < l.end(c.dur)
            val p = ps[l]
            try {
                if (act) {
                    val q = p ?: mk(m)?.also { ps[l] = it } ?: continue
                    val want = ((c.time - l.start + l.off) * 1000f).toInt()
                    if (!q.isPlaying) {
                        q.seekTo(want)
                        q.start()
                    } else if (kotlin.math.abs(q.currentPosition - want) > 300) {
                        q.seekTo(want)
                    }
                } else if (p != null && p.isPlaying) {
                    p.pause()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun stop() {
        ps.values.forEach {
            try {
                it.release()
            } catch (e: Exception) {
            }
        }
        ps.clear()
    }
}
