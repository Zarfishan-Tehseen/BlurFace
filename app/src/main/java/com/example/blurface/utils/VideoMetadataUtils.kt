package com.example.blurface.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlin.math.roundToInt

object VideoMetadataUtils {

    /**
     * Reads the source video's real capture fps.
     * Tries the video track's MediaFormat first (most reliable), falls back to
     * MediaMetadataRetriever's capture-framerate metadata, then a sane default.
     */
    fun getSourceFps(path: String): Int {
        // 1. MediaExtractor: read KEY_FRAME_RATE off the video track format.
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        val fps = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                        extractor.release()
                        if (fps > 0) return fps
                    }
                }
            }
            extractor.release()
        }

        // 2. MediaMetadataRetriever fallback.
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val fps = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
            retriever.release()
            if (fps != null && fps > 0) return fps.roundToInt()
        }

        // 3. Most phone video is 30fps - reasonable default if metadata is missing.
        return 30
    }

    /**
     * Derives how many fps to run face detection at, by dividing the source
     * video's real fps by [divisor] (e.g. 60fps / 5 = 12fps analysis).
     * Clamped to [minFps]..[maxFps] so very low/high fps sources still get a
     * sensible analysis rate (e.g. a 15fps source won't drop to 3fps analysis,
     * and a 240fps slow-mo clip won't try to analyze 48fps).
     */
    fun deriveAnalysisFps(sourceFps: Int, divisor: Int = 5, minFps: Int = 5, maxFps: Int = 15): Int {
        val divided = (sourceFps.toDouble() / divisor).roundToInt()
        return divided.coerceIn(minFps, maxFps)
    }
}