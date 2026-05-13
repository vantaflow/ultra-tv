package com.ultratv.tv.nativeapp.data.recording

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI

/**
 * Records a single live HLS stream segment-by-segment into a flat .ts file.
 * MPEG-TS segments can be concatenated raw, so we just append each downloaded
 * segment's bytes to the output. Re-polls the manifest on a cadence derived
 * from `#EXT-X-TARGETDURATION`.
 *
 * Stop conditions, in order:
 *  - [shouldStop] returns true (caller cancellation)
 *  - max duration reached
 *  - manifest stops producing new segments for ~3× target duration (silence)
 *
 * The output file is written incrementally; partial recordings remain
 * playable.
 */
class HlsRecorder(
    private val ok: OkHttpClient,
    private val sourceM3u8: String,
    private val outFile: File,
    private val maxDurationMs: Long,
    private val shouldStop: () -> Boolean,
    private val onProgress: (downloadedBytes: Long, elapsedMs: Long) -> Unit,
) {
    suspend fun run(): Boolean {
        outFile.parentFile?.mkdirs()
        val seenSegments = mutableSetOf<String>()
        var totalBytes = 0L
        val startMs = System.currentTimeMillis()
        var lastNewSegmentMs = startMs
        var targetSec = 6.0   // sensible default before we see #EXT-X-TARGETDURATION

        outFile.outputStream().use { sink ->
            while (true) {
                if (shouldStop()) return false
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed >= maxDurationMs) return true

                val (segments, td) = try {
                    fetchManifest(sourceM3u8)
                } catch (_: Throwable) {
                    delay((targetSec * 1000).toLong())
                    continue
                }
                if (td > 0) targetSec = td

                var sawNew = false
                for (seg in segments) {
                    if (shouldStop() || System.currentTimeMillis() - startMs >= maxDurationMs) {
                        return true
                    }
                    if (seg in seenSegments) continue
                    seenSegments.add(seg)
                    sawNew = true
                    lastNewSegmentMs = System.currentTimeMillis()
                    runCatching {
                        ok.newCall(Request.Builder().url(seg).build()).execute().use { resp ->
                            if (!resp.isSuccessful) return@use
                            resp.body?.byteStream()?.use { input ->
                                val buf = ByteArray(64 * 1024)
                                var n: Int
                                while (input.read(buf).also { n = it } > 0) {
                                    sink.write(buf, 0, n)
                                    totalBytes += n
                                }
                            }
                        }
                    }
                    onProgress(totalBytes, System.currentTimeMillis() - startMs)
                }

                // Manifest silent for too long: provider stopped publishing
                // segments. Bail out gracefully.
                if (!sawNew && System.currentTimeMillis() - lastNewSegmentMs > (targetSec * 3000).toLong()) {
                    return true
                }
                // Sleep half a target-duration before re-polling.
                delay((targetSec * 500).toLong())
            }
        }
    }

    private fun fetchManifest(url: String): Pair<List<String>, Double> {
        val req = Request.Builder().url(url).build()
        val text = ok.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("manifest HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val segments = mutableListOf<String>()
        var targetDuration = 0.0
        val base = URI.create(url)
        var lastWasExtinf = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-TARGETDURATION")) {
                targetDuration = line.substringAfter(":").toDoubleOrNull() ?: targetDuration
            } else if (line.startsWith("#EXTINF")) {
                lastWasExtinf = true
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                if (lastWasExtinf) {
                    val abs = if (line.startsWith("http")) line else base.resolve(line).toString()
                    segments.add(abs)
                    lastWasExtinf = false
                }
            }
        }
        return segments to targetDuration
    }
}
