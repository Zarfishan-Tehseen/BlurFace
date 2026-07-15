package com.example.blurface.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import com.example.blurface.data.segmentation.SegmentationRepository

class SegmentForegroundUseCase(
    private val repository: SegmentationRepository
) {
    suspend operator fun invoke(context: Context, source: Bitmap): Bitmap =
        repository.segmentForeground(context, source)
}