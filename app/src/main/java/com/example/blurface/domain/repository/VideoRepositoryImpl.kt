package com.example.blurface.domain.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import com.example.blurface.data.ml.FaceEmbedder
import com.example.blurface.data.ml.PeopleClusterer
import com.example.blurface.domain.model.FaceInstance
import com.example.blurface.utils.FrameExtractor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2

class VideoRepositoryImpl(private val context: Context) : VideoRepository {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    private val embedder by lazy { FaceEmbedder(context) }

    override fun processAndClusterVideo(
        videoPath: String,
        outputDir: String,
        fps: Int
    ): Flow<PipelineStatus> = channelFlow {

        val allInstances = mutableListOf<FaceInstance>()

        trySend(PipelineStatus.Working(0, "Extracting frames from video source...", 0, 0))

        val extractionResult =
            FrameExtractor.extract(videoPath, outputDir, fps) { progress, message ->
                val uiProgress = (progress * 0.4).toInt()
                trySend(
                    PipelineStatus.Working(
                        percentage = uiProgress,
                        message = message,
                        currentFrame = 0,
                        totalFrames = 0
                    )
                )
            }

        if (!extractionResult.success || extractionResult.framePaths.isEmpty()) {
            trySend(PipelineStatus.Failed(extractionResult.errorMessage ?: "Failed parsing frames"))
            return@channelFlow
        }

        val totalPaths = extractionResult.framePaths.size

        withContext(Dispatchers.IO) {
            extractionResult.framePaths.forEachIndexed { index, path ->
                val currentFrameCount = index + 1
                val bitmap = BitmapFactory.decodeFile(path) ?: return@forEachIndexed
                val image = InputImage.fromBitmap(bitmap, 0)

                val mlKitFaces = suspendCancellableCoroutine<List<Face>> { cont ->
                    faceDetector.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resume(emptyList()) }
                }

                for (face in mlKitFaces) {
                    val box = face.boundingBox

                    // ---- Cheap filters FIRST, before any cropping/embedding ----

                    // Too small -> embeddings are garbage.
                    if (box.width() < bitmap.width * 0.05 || box.width() < 50) continue

                    // Extreme pose -> skip BEFORE paying for the embedding.
                    val yaw = abs(face.headEulerAngleY)
                    val pitch = abs(face.headEulerAngleX)
                    if (yaw > 35f || pitch > 25f) continue

                    // ---- Square crop with modest margin (no aspect distortion) ----
                    // FaceNet expects a tight-ish face crop; 25% padding on each side
                    // drags in background and weakens identity separation. 15% margin
                    // on the LONGER box side, forced square, shift-clamped into bounds.
                    val margin = 0.15f
                    val desired = (maxOf(box.width(), box.height()) * (1f + 2f * margin)).toInt()
                    val side = minOf(desired, bitmap.width, bitmap.height)
                    val left = (box.centerX() - side / 2).coerceIn(0, bitmap.width - side)
                    val top = (box.centerY() - side / 2).coerceIn(0, bitmap.height - side)
                    if (side <= 0) continue

                    var crop = Bitmap.createBitmap(bitmap, left, top, side, side)

                    // ---- Roll alignment: rotate so the eyes are horizontal ----
                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                    if (leftEye != null && rightEye != null) {
                        val dx = rightEye.x - leftEye.x
                        val dy = rightEye.y - leftEye.y
                        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (abs(angle) > 2f) {
                            val m = Matrix().apply { postRotate(-angle) }
                            crop = Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, m, true)
                        }
                    }

                    val embedding = embedder.embed(crop)

                    val quality =
                        (1f - (abs(face.headEulerAngleY) + abs(face.headEulerAngleZ)) / 180f) *
                                box.width()

                    allInstances.add(
                        FaceInstance(
                            frameId = index,
                            trackingId = face.trackingId,
                            embedding = embedding,
                            faceCrop = crop,
                            boundingBox = RectF(box),
                            quality = quality
                        )
                    )
                }

                bitmap.recycle()

                val analysisProgress = 40 + ((currentFrameCount * 60) / totalPaths)
                trySend(
                    PipelineStatus.Working(
                        percentage = analysisProgress,
                        message = "Analyzing frame $currentFrameCount / $totalPaths...",
                        currentFrame = currentFrameCount,
                        totalFrames = totalPaths
                    )
                )
            }
        }

        if (allInstances.isEmpty()) {
            trySend(PipelineStatus.Failed("No faces detected in video"))
            return@channelFlow
        }

        trySend(PipelineStatus.Working(95, "Clustering identities...", 0, 0))

        // Fixed thresholds. For L2-normalized FaceNet-512 embeddings, same-person
        // cosine distance in video is ~0.10-0.30 and different-person is ~0.55+.
        // The previous adaptive p25/p75 heuristic landed around 0.45-0.55, which
        // is exactly why two people were merging. If clusters ever SPLIT (same
        // person appears as two people), raise `threshold` in small steps
        // (0.35 -> 0.38 -> 0.40) using the FaceCluster distance logs as a guide.
        val clusterer = PeopleClusterer(
            threshold = 0.35f,
            mergeThreshold = 0.30f
        )
        clusterer.clusterAll(allInstances)

        android.util.Log.d("FaceCluster", "Total persons found: ${clusterer.people.size}")
        clusterer.people.forEach { p ->
            android.util.Log.d("FaceCluster", "  Person ${p.id}: ${p.instances.size} faces")
        }

        trySend(PipelineStatus.Completed(clusterer.people))
    }
}
