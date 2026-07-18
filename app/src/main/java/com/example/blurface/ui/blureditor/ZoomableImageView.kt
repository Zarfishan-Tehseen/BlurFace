package com.example.blurface.ui.blureditor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.blurface.domain.model.DetectedFace

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val imageView: ImageView
    private val matrix = Matrix()
    private var bitmap: Bitmap? = null

    // Store only the faces to draw outlines around them
    private var overlayFaces: List<DetectedFace> = emptyList()

    // Clean outline paint configuration for face bounding boxes
    private val faceOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f              // Thickness of the box border line
        color = Color.parseColor("#6C4DF6")
    }

    private var minScale = 1f
    private val maxScale = 6f
    private var currentScale = 1f

    private var panPointerId = -1
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isScaling = false

    private val scaleDetector: ScaleGestureDetector

    init {
        setWillNotDraw(false)

        imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(imageView)

        scaleDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    return true
                }
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                    val factor = newScale / currentScale
                    currentScale = newScale
                    matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                    constrainMatrix()
                    applyMatrix()
                    return true
                }
                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                }
            })
    }

    /** Update bounding box coordinates dynamically */
    fun setFaceOverlays(faces: List<DetectedFace>) {
        this.overlayFaces = faces
        invalidate()
    }

    /** First image load - resets zoom/pan to fit the view. */
    fun setImageBitmap(bmp: Bitmap) {
        bitmap = bmp
        imageView.setImageBitmap(bmp)
        post { resetMatrixToFit() }
    }

    /** Swap in the effect-applied bitmap while preserving user pan/zoom position */
    fun updateImagePreservingMatrix(bmp: Bitmap) {
        bitmap = bmp
        imageView.setImageBitmap(bmp)
        imageView.imageMatrix = matrix
        invalidate()
    }

    private fun resetMatrixToFit() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        minScale = scale
        currentScale = scale
        val dx = (width - bmp.width * scale) / 2f
        val dy = (height - bmp.height * scale) / 2f
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        applyMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null) resetMatrixToFit()
    }

    private fun applyMatrix() {
        imageView.imageMatrix = matrix
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 1. Draw the actual image underneath (with the merged effects included)
        super.dispatchDraw(canvas)

        // 2. Draw vector outlines tracking alongside the panning/zooming matrix coordinates
        canvas.save()
        canvas.concat(matrix)

        overlayFaces.filter { it.isSelected }.forEach { face ->
            // Draw clean empty rounded corner outlines over selected faces
            canvas.drawRoundRect(face.boundingBox, 16f, 16f, faceOutlinePaint)
        }

        canvas.restore()
    }

    private fun constrainMatrix() {
        val bmp = bitmap ?: return
        val values = FloatArray(9)
        matrix.getValues(values)
        val scaledW = bmp.width * values[Matrix.MSCALE_X]
        val scaledH = bmp.height * values[Matrix.MSCALE_Y]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val dx = when {
            scaledW <= width -> (width - scaledW) / 2f - transX
            transX > 0 -> -transX
            transX < width - scaledW -> (width - scaledW) - transX
            else -> 0f
        }
        val dy = when {
            scaledH <= height -> (height - scaledH) / 2f - transY
            transY > 0 -> -transY
            transY < height - scaledH -> (height - scaledH) - transY
            else -> 0f
        }
        matrix.postTranslate(dx, dy)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                panPointerId = event.getPointerId(0)
                lastPanX = event.x
                lastPanY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isScaling = false
                panPointerId = event.getPointerId(0)
                lastPanX = event.getX(0)
                lastPanY = event.getY(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isScaling) return true
                if (event.pointerCount == 1) {
                    val idx = event.findPointerIndex(panPointerId)
                    if (idx != -1) {
                        val dx = event.getX(idx) - lastPanX
                        val dy = event.getY(idx) - lastPanY
                        matrix.postTranslate(dx, dy)
                        constrainMatrix()
                        applyMatrix()
                        lastPanX = event.getX(idx)
                        lastPanY = event.getY(idx)
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = event.actionIndex
                val liftedId = event.getPointerId(liftedIndex)
                if (liftedId == panPointerId) {
                    val newIndex = if (liftedIndex == 0) 1 else 0
                    panPointerId = event.getPointerId(newIndex)
                    lastPanX = event.getX(newIndex)
                    lastPanY = event.getY(newIndex)
                }
                if (event.pointerCount <= 2) isScaling = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                panPointerId = -1
                isScaling = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}