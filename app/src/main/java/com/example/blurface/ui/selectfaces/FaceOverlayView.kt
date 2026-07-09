package com.example.blurface.ui.selectfaces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.blurface.R
import com.example.blurface.domain.model.DetectedFace

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faces: List<DetectedFace> = emptyList()
    private var sourceWidth = 0
    private var sourceHeight = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = ContextCompat.getColor(context, R.color.purple_primary)
    }

    /**
     * @param sourceWidth / sourceHeight: intrinsic size of the bitmap shown behind this
     * overlay (this view must be laid out over an ImageView with scaleType="centerCrop").
     */
    fun setFaces(faces: List<DetectedFace>, sourceWidth: Int, sourceHeight: Int) {
        this.faces = faces
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceWidth == 0 || sourceHeight == 0 || width == 0 || height == 0) return

        // Replicates ImageView's centerCrop math so boxes line up with the visible image
        val scale = maxOf(
            width.toFloat() / sourceWidth,
            height.toFloat() / sourceHeight
        )
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f

        faces.filter { it.isSelected }.forEach { face ->
            val box = face.boundingBox
            val mapped = RectF(
                box.left * scale + offsetX,
                box.top * scale + offsetY,
                box.right * scale + offsetX,
                box.bottom * scale + offsetY
            )
            canvas.drawRoundRect(mapped, 20f, 20f, boxPaint)
        }
    }
}