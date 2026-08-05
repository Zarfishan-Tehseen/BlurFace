package com.example.blurface.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Pulls frames out of a video at a target [fps] and saves them as JPEGs.
 *
 * Decodes the video track sequentially with MediaCodec instead of repeatedly
 * seeking with MediaMetadataRetriever.getFrameAtTime(). Seeking per-frame is
 * unreliable at scale - many device decoders start failing/timing out after
 * enough back-to-back seeks, silently truncating extraction partway through
 * the video. A single sequential decode pass reads every frame in order and
 * never has that failure mode.
 *
 * Deliberately decodes to a *buffer*, not a Surface/ImageReader: some vendor
 * decoders (seen in the wild: MediaTek's c2.mtk.avc.decoder) have native bugs
 * in how they hand frames to a Surface-backed ImageReader, causing a hard JNI
 * crash in ImageReader$SurfaceImage.nativeCreatePlanes. Decoding with
 * configure(format, null, ...) and reading frames via codec.getOutputImage()
 * avoids that code path entirely and is far more portable across devices.
 */
object FrameExtractor {

    data class ExtractionResult(
        val success: Boolean,
        val framePaths: List<String>,
        val errorMessage: String? = null
    )

    suspend fun extract(
        videoPath: String,
        outputDir: String,
        fps: Int = 15,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): ExtractionResult = withContext(Dispatchers.IO) {

        val dir = File(outputDir).apply {
            deleteRecursively()
            mkdirs()
        }

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(videoPath)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return@withContext ExtractionResult(false, emptyList(), "No video track found")

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: return@withContext ExtractionResult(false, emptyList(), "Video track has no mime type")

            val rotationDegrees = if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else 0

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            if (durationUs <= 0L) {
                return@withContext ExtractionResult(false, emptyList(), "Could not read video duration")
            }

            // Ask for a flexible YUV420 layout explicitly - some decoders pick a
            // vendor-proprietary color format by default when no color format is
            // requested and no Surface is given, which getOutputImage() can't
            // always decode into a proper Image.
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )

            val intervalUs = 1_000_000L / fps
            val totalFrames = (durationUs / intervalUs).toInt().coerceAtLeast(1)
            val framePaths = mutableListOf<String>()

            onProgress(0, "Extracting 0 / $totalFrames frames...")

            decoder = MediaCodec.createDecoderByType(mime)
            // No Surface (null) - decode to buffers we read ourselves.
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var nextWantedUs = 0L
            var savedCount = 0

            while (!outputDone && savedCount < totalFrames) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val presentationUs = bufferInfo.presentationTimeUs
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val wanted = presentationUs >= nextWantedUs && savedCount < totalFrames

                    if (wanted) {
                        // getOutputImage() must be called BEFORE releaseOutputBuffer -
                        // it wraps the still-owned output buffer, it doesn't copy it.
                        val image = decoder.getOutputImage(outputIndex)
                        if (image != null) {
                            val bitmap = imageToBitmap(image, rotationDegrees)
                            image.close()
                            val frameFile = File(dir, "frame_%04d.jpg".format(savedCount))
                            saveBitmap(bitmap, frameFile)
                            bitmap.recycle()
                            framePaths.add(frameFile.absolutePath)
                            savedCount++
                            nextWantedUs += intervalUs

                            val progress = (savedCount * 50 / totalFrames)
                            onProgress(progress, "Extracting $savedCount / $totalFrames frames...")
                        }
                    }

                    decoder.releaseOutputBuffer(outputIndex, false)

                    if (isEos) outputDone = true
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Expected once at the start of decoding - no action needed,
                    // getOutputImage() reads the negotiated format itself.
                }
            }

            onProgress(totalFrames, "Extraction complete. ${framePaths.size} frames saved.")
            ExtractionResult(success = true, framePaths = framePaths)

        } catch (e: Exception) {
            android.util.Log.e("FrameExtractor", "Extraction failed: ${e.message}", e)
            ExtractionResult(
                success = false,
                framePaths = emptyList(),
                errorMessage = e.message ?: "Unknown error"
            )
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Converts a decoder's YUV_420 [Image] into an upright RGB [Bitmap]. */
    private fun imageToBitmap(image: Image, rotationDegrees: Int): Bitmap {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // Bulk-copy each plane into a plain byte array up front - far fewer,
        // cheaper calls than indexing into the ByteBuffers one pixel at a time.
        val yBytes = ByteArray(yPlane.buffer.remaining()).also { yPlane.buffer.get(it) }
        val uBytes = ByteArray(uPlane.buffer.remaining()).also { uPlane.buffer.get(it) }
        val vBytes = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.get(it) }

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val pixels = IntArray(width * height)

        for (row in 0 until height) {
            val yRowStart = row * yRowStride
            val uvRowStart = (row / 2) * uvRowStride
            for (col in 0 until width) {
                val y = yBytes[yRowStart + col * yPixelStride].toInt() and 0xFF
                val uvCol = (col / 2) * uvPixelStride
                val u = (uBytes[uvRowStart + uvCol].toInt() and 0xFF) - 128
                val v = (vBytes[uvRowStart + uvCol].toInt() and 0xFF) - 128

                val r = (y + 1.370705f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.337633f * u - 0.698001f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.732446f * u).toInt().coerceIn(0, 255)

                pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }
    }
    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }

    fun clearFrames(outputDir: String) {
        File(outputDir).deleteRecursively()
    }
}