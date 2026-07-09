package com.example.blurface.domain.usecase

import android.content.Context
import android.net.Uri
import com.example.blurface.data.facedetection.FaceDetectionRepository

class DetectFacesInImageUseCase(
    private val repository: FaceDetectionRepository
) {
    suspend operator fun invoke(context: Context, imageUri: Uri) =
        repository.detectFaces(context, imageUri)
}