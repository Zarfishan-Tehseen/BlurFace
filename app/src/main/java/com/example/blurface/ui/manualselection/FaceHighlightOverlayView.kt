package com.example.blurface.ui.manualselection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.blurface.domain.model.DetectedFace

class FaceHighlightOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faces: List<DetectedFace> = emptyList()
    private var sourceWidth = 0
    private var sourceHeight = 0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 255, 196, 0)
    }

    fun setFaces(faces: List<DetectedFace>, sourceWidth: Int, sourceHeight: Int) {
        this.faces = faces
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        invalidate()
    }

    /** Same fit-inside transform math this screen's ImageView and BrushMaskView use. */
    fun fitMatrix(): android.graphics.Matrix {
        val matrix = android.graphics.Matrix()
        if (sourceWidth == 0 || sourceHeight == 0 || width == 0 || height == 0) return matrix
        val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val dx = (width - sourceWidth * scale) / 2f
        val dy = (height - sourceHeight * scale) / 2f
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        return matrix
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceWidth == 0 || sourceHeight == 0) return

        val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
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
            canvas.drawRoundRect(mapped, 24f, 24f, fillPaint)
        }
    }
}