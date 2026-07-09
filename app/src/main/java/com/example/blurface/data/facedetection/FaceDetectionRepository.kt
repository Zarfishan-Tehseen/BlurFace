package com.example.blurface.data.facedetection

import android.content.Context
import android.net.Uri
import com.example.blurface.domain.model.DetectedFace

interface FaceDetectionRepository {
    suspend fun detectFaces(context: Context, imageUri: Uri): List<DetectedFace>
}
