package com.example.blurface.utils

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapUtils {

    /**
     * Crops [box] (in the source bitmap's pixel coordinates) out of [source],
     * clamping to the bitmap bounds so an out-of-range box never crashes.
     */
    fun cropFace(source: Bitmap, box: RectF): Bitmap {
        val left = max(0, box.left.roundToInt())
        val top = max(0, box.top.roundToInt())
        val right = min(source.width, box.right.roundToInt())
        val bottom = min(source.height, box.bottom.roundToInt())

        val width = max(1, right - left)
        val height = max(1, bottom - top)

        return Bitmap.createBitmap(source, left, top, width, height)
    }
}