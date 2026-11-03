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
import kotlin.math.abs

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
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / s > mx) s *= 2
    val q = BitmapFactory.Options()
    q.inSampleSize = s
    val b = BitmapFactory.decodeFile(p, q) ?: return null
    val r = rot(p)
    if (r == 0) return b
    val m = Matrix()
    m.postRotate(r.toFloat())
    val x = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    if (x !== b) b.recycle()
    return x
}

fun fit(b: Bitmap, mx: Int): Bitmap {
    val s = maxOf(b.width, b.height)
    if (s <= mx) return b
    val k = mx.toFloat() / s
    val x = Bitmap.createScaledBitmap(b, maxOf((b.width * k).toInt(), 1), maxOf((b.height * k).toInt(), 1), true)
    if (x !== b) b.recycle()
    return x
}

object Frames {
    var ver by mutableStateOf(0)
    private val cache = object : LruCache<String, Bitmap>(36 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val last = HashMap<String, Bitmap>()
    private val pend = HashSet<String>()
    private val want = HashMap<String, Int>()
    private val rets = HashMap<String, MediaMetadataRetriever>()
    private val ex = Executors.newSingleThreadExecutor()

    fun get(m: Media, t: Float, pf: Boolean = false): Bitmap? {
        val v = ver
        val b = if (m.kind == 1) maxOf((t * 8f).toInt(), 0) else 0
        val k = m.id + ":" + b
        synchronized(this) {
            val h = cache.get(k)
            if (h != null) {
                last[m.id] = h
                if (pf && m.kind == 1) ahead(m, b + 1)
                return h
            }
            want[m.id] = b
            if (pend.add(k)) ex.execute { load(m, b, k, false) }
            return last[m.id]
        }
    }

    private fun ahead(m: Media, b: Int) {
        if (b / 8f > m.dur) return
        val k = m.id + ":" + b
        if (cache.get(k) == null && pend.add(k)) ex.execute { load(m, b, k, true) }
    }

    private fun load(m: Media, b: Int, k: String, force: Boolean) {
        var bmp: Bitmap? = null
        val skip = !force && synchronized(this) { m.kind == 1 && want[m.id] != b }
        if (!skip) {
            try {
                if (m.kind == 0) {
                    bmp = dec(m.path, 1600)
                } else {
                    val r = synchronized(rets) {
                        rets.getOrPut(m.id) { MediaMetadataRetriever().also { it.setDataSource(m.path) } }
                    }
                    val f = r.getFrameAtTime(b * 125000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (f != null) bmp = fit(f, 960)
                }
            } catch (e: OutOfMemoryError) {
                synchronized(this) { cache.evictAll() }
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

    fun drop(id: String) {
        synchronized(this) {
            for (k in cache.snapshot().keys) if (k.startsWith("$id:")) cache.remove(k)
            last.remove(id)
            want.remove(id)
        }
        ex.execute {
            synchronized(rets) {
                try {
                    rets.remove(id)?.release()
                } catch (e: Exception) {
                }
            }
        }
    }

    fun clear() {
        synchronized(this) {
            cache.evictAll()
            last.clear()
            pend.clear()
            want.clear()
        }
        ex.execute {
            synchronized(rets) {
                for (r in rets.values) {
                    try {
                        r.release()
                    } catch (e: Exception) {
                    }
                }
                rets.clear()
            }
        }
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
                    b = r.getFrameAtTime((m.dur * 250000f).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    r.release()
                }
            }
        } catch (e: Exception) {
        } catch (e: OutOfMemoryError) {
        }
        val x = b?.let { fit(it, 192).asImageBitmap() } ?: return null
        c.put(m.id, x)
        return x
    }
}

object Aud {
    private val ps = HashMap<Layer, MediaPlayer>()
    private val ok = HashSet<MediaPlayer>()

    private fun mk(m: Media): MediaPlayer? = try {
        MediaPlayer().also {
            it.setDataSource(m.path)
            it.setOnPreparedListener { p -> ok.add(p) }
            it.setOnErrorListener { p, _, _ ->
                ok.remove(p)
                true
            }
            it.prepareAsync()
        }
    } catch (e: Exception) {
        null
    }

    private fun rel(p: MediaPlayer?) {
        if (p == null) return
        ok.remove(p)
        try {
            p.release()
        } catch (e: Exception) {
        }
    }

    fun sync(c: Comp, on: Boolean) {
        if (ps.isEmpty() && !on) return
        if (ps.isNotEmpty()) {
            val ei = ps.entries.iterator()
            while (ei.hasNext()) {
                val e = ei.next()
                if (e.key !in c.layers) {
                    rel(e.value)
                    ei.remove()
                }
            }
        }
        val lib = c.lib
        val ls = c.layers
        for (i in 0 until ls.size) {
            val l = ls[i]
            if (l.kind < 1) continue
            val m = lib.firstOrNull { it.id == l.mid } ?: continue
            val act = on && !l.mute && c.time >= l.start && c.time < l.end(c.dur)
            val p = ps[l]
            try {
                if (act) {
                    val q = p ?: (mk(m)?.also { ps[l] = it } ?: continue)
                    if (!ok.contains(q)) continue
                    val want = ((c.time - l.start + l.off) * 1000f).toInt()
                    if (!q.isPlaying) {
                        q.seekTo(want)
                        q.start()
                    } else if (abs(q.currentPosition - want) > 300) {
                        q.seekTo(want)
                    }
                } else if (p != null && ok.contains(p) && p.isPlaying) {
                    p.pause()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun stop() {
        for (p in ps.values) rel(p)
        ps.clear()
        ok.clear()
    }
}
