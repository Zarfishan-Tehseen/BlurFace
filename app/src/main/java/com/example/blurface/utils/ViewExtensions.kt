package com.example.blurface.utils

import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorInt

/**
 * Extension property to easily convert Integer DP to Pixels
 */
val Int.toPx: Float
    get() = (this * Resources.getSystem().displayMetrics.density)

/**
 * Extension function to dynamically create a rounded rectangle background drawable for any View.
 * It takes a background color and specific corner radii in pixels.
 */
fun View.setRoundedCorners(
    @ColorInt backgroundColor: Int,
    topLeft: Float = 0f,
    topRight: Float = 0f,
    bottomRight: Float = 0f,
    bottomLeft: Float = 0f
) {
    val drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(backgroundColor)
        // cornerRadii requires an array of 8 values representing [X, Y] radii for each of the 4 corners:
        // [Top-Left, Top-Right, Bottom-Right, Bottom-Left]
        cornerRadii = floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft
        )
    }
    this.background = drawable
}