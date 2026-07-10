package com.example.blurface.domain.model

import android.graphics.Bitmap
import android.graphics.RectF
data class FaceInstance(
    val frameId: Int,
    val trackingId: Int?,
    val embedding: FloatArray,
    val faceCrop: Bitmap,
    val boundingBox: RectF,
    val quality: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceInstance
        return frameId == other.frameId && trackingId == other.trackingId
    }

    override fun hashCode(): Int {
        var result = frameId
        result = 31 * result + (trackingId ?: 0)
        return result
    }
}