package com.szkinetic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
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
        if (m.kind == 1 && pf) {
            // playback: sequential hardware decode running ahead of the playhead
            val d = VDecs.get(m)
            if (d != null) {
                val f = d.frameAt(t)
                if (f != null) {
                    synchronized(this) { last[m.id] = f }
                    return f
                }
                val l = synchronized(this) { last[m.id] }
                if (l != null) return l
            }
        }
        val b = if (m.kind == 1) maxOf((t * 24f).toInt(), 0) else 0
        val k = m.id + ":" + b
        synchronized(this) {
            val h = cache.get(k)
            if (h != null) {
                last[m.id] = h
                return h
            }
            want[m.id] = b
            if (pend.add(k)) ex.execute { load(m, b, k, false) }
            return last[m.id]
        }
    }

    private fun ahead(m: Media, b: Int) {
        if (b / 24f > m.dur) return
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
                    val f = r.getFrameAtTime(b * 41667L, MediaMetadataRetriever.OPTION_CLOSEST)
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
        VDecs.drop(id)
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
        VDecs.clear()
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

class DF(val pts: Long, val bmp: Bitmap)

// Sequential MediaCodec video decoder. Runs on its own thread, stays about half a second ahead of the
// requested time, drops frames when it falls behind, and shuts itself down when nobody asks for frames.
class VDec(private val m: Media) {
    private val q = ArrayList<DF>()
    @Volatile private var want = 0L
    @Volatile private var lastReq = 0L
    @Volatile var failed = false
    private var th: Thread? = null

    fun kill() {
        lastReq = 0L
    }

    fun frameAt(t: Float): Bitmap? {
        val us = (t * 1e6f).toLong()
        want = us
        lastReq = System.nanoTime()
        synchronized(this) {
            if (th == null && !failed) {
                val x = Thread { run() }
                x.isDaemon = true
                th = x
                x.start()
            }
            if (q.isEmpty()) return null
            var best = q[0]
            for (f in q) {
                if (f.pts <= us + 20_000L) best = f else break
            }
            if (best.pts > us + 500_000L) return null
            while (q.size > 1 && q[0].pts < best.pts - 400_000L) q.removeAt(0)
            return best.bmp
        }
    }

    private fun conv(img: Image, rot: Int): Bitmap? {
        val cr = img.cropRect
        val iw = cr.width()
        val ih = cr.height()
        if (iw < 2 || ih < 2) return null
        val s = maxOf((maxOf(iw, ih) + 719) / 720, 1)
        val ow = iw / s
        val oh = ih / s
        val pl = img.planes
        val yb = pl[0].buffer
        val ub = pl[1].buffer
        val vb = pl[2].buffer
        val yrs = pl[0].rowStride
        val yps = pl[0].pixelStride
        val urs = pl[1].rowStride
        val ups = pl[1].pixelStride
        val vrs = pl[2].rowStride
        val vps = pl[2].pixelStride
        val out = IntArray(ow * oh)
        var o = 0
        for (j in 0 until oh) {
            val y = cr.top + j * s
            val yr = y * yrs
            val cy = y shr 1
            for (i in 0 until ow) {
                val x = cr.left + i * s
                val yy = (yb.get(yr + x * yps).toInt() and 0xFF) - 16
                val uu = (ub.get(cy * urs + (x shr 1) * ups).toInt() and 0xFF) - 128
                val vv = (vb.get(cy * vrs + (x shr 1) * vps).toInt() and 0xFF) - 128
                val y1 = 1192 * (if (yy < 0) 0 else yy)
                var r = (y1 + 1634 * vv) shr 10
                var g = (y1 - 833 * vv - 400 * uu) shr 10
                var b = (y1 + 2066 * uu) shr 10
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255
                out[o++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        val bmp = Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
        if (rot == 0) return bmp
        val mx = Matrix()
        mx.postRotate(rot.toFloat())
        val rb = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
        if (rb !== bmp) bmp.recycle()
        return rb
    }

    private fun run() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_VIDEO)
        } catch (e: Exception) {
        }
        var ex: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            ex = MediaExtractor()
            ex.setDataSource(m.path)
            var tr = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    tr = i
                    fmt = f
                    break
                }
            }
            if (tr < 0 || fmt == null) {
                failed = true
                return
            }
            ex.selectTrack(tr)
            val rot = if (fmt.containsKey(MediaFormat.KEY_ROTATION)) fmt.getInteger(MediaFormat.KEY_ROTATION) else 0
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false
            var decPos = -1L
            var discard = 0L
            var seekTo = want
            var needSeek = true
            var caught = false
            while (!failed) {
                if (System.nanoTime() - lastReq > 2_500_000_000L) break
                val w = want
                var n = 0
                var first = -1L
                synchronized(this) {
                    n = q.size
                    first = if (q.isEmpty()) -1L else q[0].pts
                }
                if (!needSeek) {
                    val back = first >= 0 && w < first - 150_000L
                    val back2 = first < 0 && caught && decPos >= 0 && w < decPos - 300_000L
                    val fwd = caught && decPos >= 0 && w > decPos + 1_500_000L
                    val far = !caught && Math.abs(w - seekTo) > 1_500_000L
                    if (back || back2 || fwd || far) {
                        needSeek = true
                        seekTo = w
                    }
                }
                if (needSeek) {
                    codec.flush()
                    ex.seekTo(seekTo, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    synchronized(this) { q.clear() }
                    inEos = false
                    outEos = false
                    decPos = -1L
                    discard = seekTo - 150_000L
                    caught = false
                    needSeek = false
                    n = 0
                }
                var prog = false
                if (!inEos) {
                    val ii = codec.dequeueInputBuffer(0)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)
                        val sz = if (ib != null) ex.readSampleData(ib, 0) else -1
                        if (sz < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            codec.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0)
                            ex.advance()
                        }
                        prog = true
                    }
                }
                val full = n >= 10 || (caught && decPos >= w + 450_000L)
                if (!full && !outEos) {
                    val oi = codec.dequeueOutputBuffer(info, 0)
                    if (oi >= 0) {
                        val pts = info.presentationTimeUs
                        decPos = pts
                        if (pts >= seekTo - 20_000L) caught = true
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                        // frames that are already late are skipped without the (costly) colour conversion
                        if (info.size > 0 && pts >= discard && pts >= w - 150_000L) {
                            val img = codec.getOutputImage(oi)
                            if (img == null) {
                                failed = true
                            } else {
                                val b = try {
                                    conv(img, rot)
                                } catch (e: Exception) {
                                    null
                                } finally {
                                    img.close()
                                }
                                if (b == null) {
                                    failed = true
                                } else {
                                    synchronized(this) {
                                        q.add(DF(pts, b))
                                        if (q.size > 1 && q[q.size - 2].pts > pts) q.sortBy { it.pts }
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        prog = true
                    }
                }
                if (!prog) Thread.sleep(3)
            }
        } catch (e: Throwable) {
            if (e is OutOfMemoryError || e is Exception) failed = failed || e !is InterruptedException
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
            }
            try {
                codec?.release()
            } catch (e: Exception) {
            }
            try {
                ex?.release()
            } catch (e: Exception) {
            }
            synchronized(this) {
                q.clear()
                th = null
            }
        }
    }
}

object VDecs {
    private val m = HashMap<String, VDec>()

    @Synchronized
    fun get(md: Media): VDec? {
        val d = m.getOrPut(md.id) { VDec(md) }
        return if (d.failed) null else d
    }

    @Synchronized
    fun drop(id: String) {
        m.remove(id)?.kill()
    }

    @Synchronized
    fun clear() {
        for (d in m.values) d.kill()
        m.clear()
    }
}

object Aud {
    private data class Track(
        val key: String,
        val layer: Layer,
        val time: Float,
        val active: Boolean,
        val muted: Boolean
    )

    private val ps = HashMap<String, MediaPlayer>()
    private val ok = HashSet<MediaPlayer>()
    private val go = HashSet<MediaPlayer>()
    private val fix = HashMap<MediaPlayer, Long>()

    private fun mk(m: Media): MediaPlayer? = try {
        MediaPlayer().also {
            it.setDataSource(m.path)
            it.setOnPreparedListener { p -> ok.add(p) }
            it.setOnSeekCompleteListener { p ->
                if (go.remove(p)) {
                    try {
                        p.start()
                    } catch (e: Exception) {
                    }
                }
            }
            it.setOnErrorListener { p, _, _ ->
                ok.remove(p)
                go.remove(p)
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
        go.remove(p)
        fix.remove(p)
        try {
            p.release()
        } catch (e: Exception) {
        }
    }

    private fun collect(
        c: Comp,
        time: Float,
        active: Boolean,
        muted: Boolean,
        prefix: String,
        chain: Set<String>,
        out: MutableList<Track>
    ) {
        if (c.id in chain || chain.size > 8) return
        val next = chain + c.id
        val solo = c.layers.any { it.solo }
        for (l in c.layers) {
            val inRange = time >= l.start && time < l.end(c.dur)
            val layerActive = active && inRange && (!solo || l.solo)
            val layerMuted = muted || l.mute
            if (l.preCompId.isNotEmpty()) {
                val nested = c.proj?.comps?.firstOrNull { it.id == l.preCompId } ?: continue
                collect(
                    nested, time - l.start + l.off, layerActive, layerMuted,
                    "$prefix/${l.uid}", next, out
                )
            } else if (l.kind >= 1) {
                out.add(Track("$prefix/${l.uid}", l, time - l.start + l.off, layerActive, layerMuted))
            }
        }
    }

    private fun tracks(c: Comp, time: Float): List<Track> =
        ArrayList<Track>().also { collect(c, time, true, false, c.id, emptySet(), it) }

    private fun prune(keep: Set<String>) {
        val it = ps.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in keep) {
                rel(e.value)
                it.remove()
            }
        }
    }

    // Prepare audio inside nested pre-comps too, so playback can start without a delay.
    fun warm(c: Comp) {
        val ts = tracks(c, c.time)
        prune(ts.map { it.key }.toSet())
        for (t in ts) {
            if (t.layer.kind < 1 || ps.containsKey(t.key) || ps.size >= 8) continue
            val m = c.lib.firstOrNull { it.id == t.layer.mid } ?: continue
            val p = mk(m) ?: continue
            ps[t.key] = p
        }
    }

    // Called ~8 times a second while playing (and once on stop).
    fun sync(c: Comp, on: Boolean) {
        val ts = tracks(c, c.time)
        prune(ts.map { it.key }.toSet())
        val now = System.nanoTime()
        for (t in ts) {
            val l = t.layer
            if (l.kind < 1) continue
            val act = on && t.active && !t.muted
            var p = ps[t.key]
            try {
                if (act) {
                    if (p == null) {
                        val m = c.lib.firstOrNull { it.id == l.mid }
                        p = if (m != null) mk(m) else null
                        if (p != null) ps[t.key] = p
                    }
                    val q = p ?: continue
                    if (!ok.contains(q)) continue
                    val want = (t.time * 1000f).toInt()
                    if (q.isPlaying) {
                        val d = abs(q.currentPosition - want)
                        if (d > 250 && now - (fix[q] ?: 0L) > 1_000_000_000L) {
                            fix[q] = now
                            q.seekTo(want.toLong(), MediaPlayer.SEEK_CLOSEST)
                        }
                    } else if (!go.contains(q)) {
                        go.add(q)
                        q.seekTo(want.toLong(), MediaPlayer.SEEK_CLOSEST)
                    }
                } else if (p != null && ok.contains(p)) {
                    go.remove(p)
                    if (p.isPlaying) p.pause()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun stop() {
        for (p in ps.values) rel(p)
        ps.clear()
        ok.clear()
        go.clear()
        fix.clear()
    }
}
