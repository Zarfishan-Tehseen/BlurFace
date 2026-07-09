package com.example.blurface.domain.model

import android.graphics.RectF

data class DetectedFace(
    val id: Int,
    val boundingBox: RectF,
    val isSelected: Boolean = true
)