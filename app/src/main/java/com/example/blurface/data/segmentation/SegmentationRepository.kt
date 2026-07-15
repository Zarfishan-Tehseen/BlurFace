package com.example.blurface.data.segmentation

import android.content.Context
import android.graphics.Bitmap

interface SegmentationRepository {
    /**
     * Returns a same-size mask bitmap where alpha = confidence the pixel is
     * foreground (subject). White/opaque = subject, transparent = background.
     */
    suspend fun segmentForeground(context: Context, source: Bitmap): Bitmap
}