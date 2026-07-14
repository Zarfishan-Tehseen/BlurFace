package com.example.blurface.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.example.blurface.domain.model.BlurSettings
import com.example.blurface.domain.model.BlurShape
import com.example.blurface.domain.model.Person
import com.example.blurface.domain.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Renders the final exported video.
 *
 * For every output frame timestamp (spaced by [frameRate]):
 *  1. Grabs the source frame via MediaMetadataRetriever.
 *  2. Finds the nearest *analyzed* frame's FaceInstance boxes (analysis ran at
 *     [analysisFps], normally much lower than export [frameRate]) for people whose
 *     shouldBlur == true, and composites the chosen [BlurSettings] effect onto those
 *     regions - or over the whole frame if blurEntireVideo is set.
 *  3. Scales the composited frame to [resolution] and feeds it into a MediaCodec AVC
 *     encoder (ByteBuffer input, explicit per-frame presentation timestamps).
 *  4. Passes the original audio track through unmodified via MediaExtractor.
 *
 * Known limitation: because face positions come from the nearest *analyzed* frame
 * rather than per-output-frame detection, fast head movement between analyzed frames
 * can show brief box misalignment on the in-between output frames. Re-running ML Kit
 * at full frameRate would fix this but is far more expensive.
 *
 * Known device caveat: COLOR_FormatYUV420SemiPlanar (NV12) input is widely but not
 * universally supported by hardware encoders. If a specific device throws a
 * MediaCodec.CodecException on configure()/queueInputBuffer(), query
 * MediaCodecInfo.CodecCapabilities.colorFormats for that device's AVC encoder and
 * adjust bitmapToNV12() to match the format it actually supports (e.g. Planar/I420).
 */
object VideoExportProcessor {

    data class ExportResult(
        val success: Boolean,
        val outputPath: String? = null,
        val error: String? = null
    )

    suspend fun export(
        context: Context,
        sourcePath: String,
        outputPath: String,
        people: List<Person>,
        blurSettings: BlurSettings,
        analysisFps: Int,
        resolution: VideoResolution,
        frameRate: Int,
        onProgress: (Int) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {

        val outputFile = File(outputPath).apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }

        val retriever = MediaMetadataRetriever()
        var muxer: MediaMuxer? = null
        var codec: MediaCodec? = null

        try {
            retriever.setDataSource(sourcePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                return@withContext ExportResult(false, error = "Could not read source video duration")
            }

            val totalFrames = ((durationMs / 1000.0) * frameRate).toInt().coerceAtLeast(1)
            val analysisIntervalMs = 1000.0 / analysisFps

            // frameId -> boxes, from selected (shouldBlur == true) people only.
            val frameFaceMap: Map<Int, List<RectF>> = people
                .filter { it.shouldBlur }
                .flatMap { it.instances }
                .groupBy { it.frameId }
                .mapValues { (_, insts) -> insts.map { it.boundingBox } }

            // Locate the source audio track (if any) up front - MediaMuxer requires
            // every track to be added before start(), and the video track's format
            // isn't known until the encoder emits INFO_OUTPUT_FORMAT_CHANGED.
            var sourceAudioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            runCatching {
                val probe = MediaExtractor().apply { setDataSource(sourcePath) }
                for (i in 0 until probe.trackCount) {
                    val f = probe.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        sourceAudioTrackIndex = i
                        audioFormat = f
                        break
                    }
                }
                probe.release()
            }

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bitRate = (resolution.approxBitrateMbps * 1_000_000).toInt()
            val videoFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, resolution.width, resolution.height
            ).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = -1
            var audioTrackIndexMuxer = -1
            var muxerStarted = false

            fun drainEncoder(endOfStream: Boolean) {
                val activeCodec = codec ?: return
                val activeMuxer = muxer ?: return
                while (true) {
                    val outIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrackIndex = activeMuxer.addTrack(activeCodec.outputFormat)
                            audioFormat?.let { audioTrackIndexMuxer = activeMuxer.addTrack(it) }
                            activeMuxer.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val encoded = activeCodec.getOutputBuffer(outIndex)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0 && encoded != null && muxerStarted) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                activeMuxer.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                            }
                            activeCodec.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            for (frameIndex in 0 until totalFrames) {
                val outputTimeUs = frameIndex * 1_000_000L / frameRate

                val sourceFrame = runCatching {
                    retriever.getFrameAtTime(outputTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                }.getOrNull() ?: continue

                val nearestAnalysisFrameId =
                    ((outputTimeUs / 1000.0) / analysisIntervalMs).roundToInt()
                val boxes = frameFaceMap[nearestAnalysisFrameId] ?: emptyList()

                val composited = compositeFrame(context, sourceFrame, boxes, blurSettings)
                if (composited !== sourceFrame) sourceFrame.recycle()

                val scaled = if (composited.width == resolution.width && composited.height == resolution.height) {
                    composited
                } else {
                    Bitmap.createScaledBitmap(composited, resolution.width, resolution.height, true)
                        .also { if (it !== composited) composited.recycle() }
                }

                val nv12 = bitmapToNV12(scaled)
                scaled.recycle()

                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.getInputBuffer(inIndex)?.apply {
                        clear()
                        put(nv12)
                    }
                    codec.queueInputBuffer(inIndex, 0, nv12.size, outputTimeUs, 0)
                }

                drainEncoder(false)

                onProgress((5 + (frameIndex + 1) * 90 / totalFrames).coerceIn(0, 95))
            }

            // Signal end-of-stream on the input side (ByteBuffer mode - NOT
            // signalEndOfInputStream(), which is Surface-input only).
            val eosIndex = codec.dequeueInputBuffer(10_000)
            if (eosIndex >= 0) {
                codec.queueInputBuffer(eosIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainEncoder(true)

            // Copy audio samples through unmodified, now that the muxer is started.
            if (sourceAudioTrackIndex >= 0 && audioTrackIndexMuxer >= 0) {
                val audioExtractor = MediaExtractor().apply {
                    setDataSource(sourcePath)
                    selectTrack(sourceAudioTrackIndex)
                }
                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = audioExtractor.sampleTime
                    info.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndexMuxer, buffer, info)
                    audioExtractor.advance()
                }
                audioExtractor.release()
            }

            onProgress(100)
            ExportResult(success = true, outputPath = outputPath)

        } catch (e: Exception) {
            runCatching { outputFile.delete() }
            ExportResult(success = false, error = e.message ?: "Export failed")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { retriever.release() }
        }
    }

    /**
     * Applies [settings] to [frame]: over the whole frame if blurEntireVideo is set,
     * otherwise cropped to each box in [boxes] and drawn back in place.
     */
    private suspend fun compositeFrame(
        context: Context,
        frame: Bitmap,
        boxes: List<RectF>,
        settings: BlurSettings
    ): Bitmap {
        if (settings.blurEntireVideo) {
            return VideoFaceEffectProcessor.applyPreview(
                context = context,
                faceCrop = frame,
                settings = settings.copy(shape = BlurShape.RECTANGLE)
            )
        }
        if (boxes.isEmpty()) return frame

        val result = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        for (box in boxes) {
            val clamped = Rect(
                box.left.toInt().coerceIn(0, frame.width - 1),
                box.top.toInt().coerceIn(0, frame.height - 1),
                box.right.toInt().coerceIn(1, frame.width),
                box.bottom.toInt().coerceIn(1, frame.height)
            )
            if (clamped.width() <= 0 || clamped.height() <= 0) continue

            val crop = Bitmap.createBitmap(frame, clamped.left, clamped.top, clamped.width(), clamped.height())
            val blurred = VideoFaceEffectProcessor.applyPreview(context, crop, settings)
            canvas.drawBitmap(blurred, clamped.left.toFloat(), clamped.top.toFloat(), null)
            if (blurred !== crop) blurred.recycle()
            crop.recycle()
        }
        return result
    }

    /** Converts an ARGB_8888 bitmap to NV12 (Y plane, then interleaved U,V) bytes. */
    private fun bitmapToNV12(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        var index = 0

        for (j in 0 until height) {
            for (i in 0 until width) {
                val p = argb[index]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
                index++
            }
        }
        return yuv
    }
}