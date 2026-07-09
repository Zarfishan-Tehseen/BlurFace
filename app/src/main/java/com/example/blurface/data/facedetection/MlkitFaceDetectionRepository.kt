package com.example.blurface.data.facedetection

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.example.blurface.domain.model.DetectedFace
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitFaceDetectionRepository : FaceDetectionRepository {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setMinFaceSize(0.08f)
        .build()

    override suspend fun detectFaces(context: Context, imageUri: Uri): List<DetectedFace> =
        suspendCancellableCoroutine { continuation ->
            val detector = FaceDetection.getClient(options)

            val image = try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
                return@suspendCancellableCoroutine
            }

            detector.process(image)
                .addOnSuccessListener { faces ->
                    val result = faces.mapIndexed { index, face ->
                        DetectedFace(
                            id = index,
                            boundingBox = RectF(face.boundingBox),
                            isSelected = true
                        )
                    }
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
                .addOnCompleteListener {
                    detector.close()
                }

            continuation.invokeOnCancellation { detector.close() }
        }
}