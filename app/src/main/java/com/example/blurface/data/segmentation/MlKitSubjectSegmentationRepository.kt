package com.example.blurface.data.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Uses ML Kit's Subject Segmentation API instead of Selfie Segmentation.
 *
 * SelfieSegmenter is trained specifically to detect human figures (face/torso/limbs)
 * and performs unpredictably on other kinds of photos - held objects, product shots,
 * pets, close-up framing, etc. Subject Segmentation is trained more generally to
 * isolate "the main subject" (person, pet, or object) regardless of framing, which is
 * what BackgroundBlurFragment actually needs.
 *
 * Implements the same SegmentationRepository interface as MlKitSegmentationRepository,
 * so nothing above this layer (SegmentForegroundUseCase, BackgroundBlurViewModel's
 * consumption of it, BackgroundBlurFragment) needs to change - only which concrete
 * repository gets constructed.
 *
 * IMPORTANT: unlike Selfie Segmentation (bundled into the APK, ~4.5MB), this model is
 * downloaded on-demand via Google Play services (~200KB) the first time it's used on a
 * given device. Per Google's docs: "Requests you make before the download has completed
 * produce no results." See the class-level TODO below and the gradle/manifest setup
 * notes that go with this file.
 */
class MlKitSubjectSegmentationRepository : SegmentationRepository {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundConfidenceMask()
        .build()

    override suspend fun segmentForeground(context: Context, source: Bitmap): Bitmap =
        suspendCancellableCoroutine { continuation ->
            val segmenter = SubjectSegmentation.getClient(options)
            val image = InputImage.fromBitmap(source, 0)

            segmenter.process(image)
                .addOnSuccessListener { result ->
                    val mask = result.foregroundConfidenceMask
                    if (mask == null) {
                        // Shouldn't happen given enableForegroundConfidenceMask() is set
                        // above, but the SDK types this as nullable regardless - fail
                        // loudly with a clear cause instead of an NPE further down.
                        continuation.resumeWithException(
                            IllegalStateException(
                                "SubjectSegmentationResult had no foregroundConfidenceMask " +
                                        "- was enableForegroundConfidenceMask() removed from options?"
                            )
                        )
                        return@addOnSuccessListener
                    }
                    continuation.resume(maskToAlphaBitmap(mask, source.width, source.height))
                }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
                .addOnCompleteListener { segmenter.close() }

            continuation.invokeOnCancellation { segmenter.close() }
        }
    private fun maskToAlphaBitmap(mask: FloatBuffer, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val alpha = (mask.get(i) * 255f).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}