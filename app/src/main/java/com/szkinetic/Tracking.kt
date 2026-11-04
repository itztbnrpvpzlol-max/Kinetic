package com.szkinetic

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Lightweight 2D point tracker based on zero-mean normalized cross correlation.
 * It follows a small grayscale patch in decoded source-video frames. This is
 * feature/patch tracking, not AI, planar, or 3D tracking.
 */
object MotionTracker {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val jobs = ConcurrentHashMap<String, Future<*>>()

    fun cancel(layer: Layer) {
        jobs.remove(layer.uid)?.cancel(true)
    }

    fun start(
        media: Media,
        layer: Layer,
        comp: Comp,
        onProgress: (Int, String) -> Unit,
        onComplete: (List<TrackSample>, String) -> Unit
    ) {
        cancel(layer)
        val uid = layer.uid
        layer.trackingStatus = "Preparing"
        val future = worker.submit {
            val result = ArrayList<TrackSample>()
            var retriever: MediaMetadataRetriever? = null
            var message = "Tracking failed"
            try {
                if (media.kind != 1 || media.path.isBlank()) {
                    message = "Select a video layer with an available source file"
                } else {
                    retriever = MediaMetadataRetriever().also { it.setDataSource(media.path) }
                    val duration = min(layer.end(comp.dur), comp.dur) - layer.start
                    val step = max(1f / comp.fps, 0.15f)
                    if (duration <= 0f) {
                        message = "Layer has no trackable duration"
                    } else {
                        var previous: GrayFrame? = null
                        var px = 0f
                        var py = 0f
                        var patch = IntArray(0)
                        var patchW = 0
                        var patchH = 0
                        var t = 0f
                        var index = 0
                        val total = (duration / step).toInt() + 1
                        while (t <= duration + 0.0001f && !Thread.currentThread().isInterrupted) {
                            val sourceTime = max(layer.off + t, 0f)
                            val raw = retriever.getFrameAtTime((sourceTime * 1_000_000L), MediaMetadataRetriever.OPTION_CLOSEST)
                            if (raw == null) break
                            val bitmap = scaleFrame(raw, 360)
                            if (bitmap !== raw) raw.recycle()
                            val frame = GrayFrame(bitmap.width, bitmap.height, grayPixels(bitmap))
                            bitmap.recycle()
                            if (previous == null) {
                                px = (layer.trackAnchorX * (frame.w - 1)).coerceIn(0f, (frame.w - 1).toFloat())
                                py = (layer.trackAnchorY * (frame.h - 1)).coerceIn(0f, (frame.h - 1).toFloat())
                                val half = (layer.trackPatchSize / 2).coerceIn(3, 20)
                                patchW = half * 2 + 1
                                patchH = patchW
                                patch = extract(frame, px.toInt(), py.toInt(), patchW, patchH)
                                result.add(TrackSample(layer.start, px / max(frame.w - 1, 1), py / max(frame.h - 1, 1), 1f))
                            } else {
                                val found = match(previous, frame, px.toInt(), py.toInt(), patch, patchW, patchH, 26)
                                px = found.first
                                py = found.second
                                result.add(TrackSample(layer.start + t, px / max(frame.w - 1, 1), py / max(frame.h - 1, 1), found.third))
                            }
                            previous = frame
                            index++
                            val pct = (index * 100 / max(total, 1)).coerceIn(0, 100)
                            main.post { onProgress(pct, "Tracking $pct%") }
                            t += step
                        }
                        message = if (result.size >= 2) "Tracked ${result.size} samples" else "Could not track enough frames"
                    }
                }
            } catch (e: Exception) {
                message = if (Thread.currentThread().isInterrupted) "Cancelled" else "Tracking error: ${e.message ?: "unknown"}"
            } finally {
                try { retriever?.release() } catch (_: Exception) {}
                jobs.remove(uid)
                val samples = result.toList()
                main.post { onComplete(samples, message) }
            }
        }
        jobs[uid] = future
    }

    private data class GrayFrame(val w: Int, val h: Int, val pixels: IntArray)

    private fun scaleFrame(src: Bitmap, maxSide: Int): Bitmap {
        val side = max(src.width, src.height)
        if (side <= maxSide) return src
        val scale = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(src, max(1, (src.width * scale).toInt()), max(1, (src.height * scale).toInt()), true)
    }

    private fun grayPixels(bitmap: Bitmap): IntArray {
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in colors.indices) {
            val c = colors[i]
            colors[i] = (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8
        }
        return colors
    }

    private fun extract(f: GrayFrame, cx: Int, cy: Int, w: Int, h: Int): IntArray {
        val hw = w / 2
        val hh = h / 2
        val x0 = (cx - hw).coerceIn(0, max(f.w - w, 0))
        val y0 = (cy - hh).coerceIn(0, max(f.h - h, 0))
        val out = IntArray(w * h)
        for (y in 0 until h) System.arraycopy(f.pixels, (y0 + y) * f.w + x0, out, y * w, w)
        return out
    }

    // Returns best center and normalized correlation score.
    private fun match(
        old: GrayFrame, now: GrayFrame, cx: Int, cy: Int,
        template: IntArray, pw: Int, ph: Int, radius: Int
    ): Triple<Float, Float, Float> {
        val halfW = pw / 2
        val halfH = ph / 2
        var sumT = 0.0
        for (v in template) sumT += v
        val meanT = sumT / template.size
        var varT = 0.0
        for (v in template) { val d = v - meanT; varT += d * d }
        if (varT < 1.0) return Triple(cx.toFloat(), cy.toFloat(), 0f)
        var best = -2.0
        var bx = cx
        var by = cy
        val xStart = max(halfW, cx - radius)
        val xEnd = min(now.w - halfW - 1, cx + radius)
        val yStart = max(halfH, cy - radius)
        val yEnd = min(now.h - halfH - 1, cy + radius)
        for (y in yStart..max(yStart, yEnd) step 2) {
            if (y > yEnd) break
            for (x in xStart..max(xStart, xEnd) step 2) {
                if (x > xEnd) break
                var sum = 0.0
                var sumSq = 0.0
                var cross = 0.0
                var k = 0
                for (j in -halfH..halfH) {
                    val row = (y + j) * now.w + x - halfW
                    for (i in 0 until pw) {
                        val v = now.pixels[row + i].toDouble()
                        sum += v
                        sumSq += v * v
                        cross += v * template[k++]
                    }
                }
                val n = template.size.toDouble()
                val varC = sumSq - sum * sum / n
                if (varC <= 1.0) continue
                val covariance = cross - sum * meanT
                val score = covariance / sqrt(varT * varC)
                if (score > best) { best = score; bx = x; by = y }
            }
        }
        return Triple(bx.toFloat(), by.toFloat(), best.toFloat().coerceIn(-1f, 1f))
    }
}
