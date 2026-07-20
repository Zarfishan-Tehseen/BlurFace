package com.example.blurface.ui.manualselection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

enum class BrushTool { PAINT, ERASE }

class BrushMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    private var bitmapW = 0
    private var bitmapH = 0
    private var isInitialized = false

    private var transformMatrix = Matrix()
    private val inverseMatrix = Matrix()

    var brushRadiusBitmapPx = 60f
    var currentTool = BrushTool.PAINT
    var isActive = false

    var isDrawing = false
        private set

    private val maskPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val maskPreviewPaint = Paint().apply {
        colorFilter = PorterDuffColorFilter(Color.argb(120, 255, 196, 0), PorterDuff.Mode.SRC_IN)
    }

    /** Fires after each finished stroke - hand a copy of the mask up for the ViewModel to hold. */
    var onMaskStrokeFinished: ((Bitmap) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f

    fun initLayers(width: Int, height: Int, existingMask: Bitmap?) {
        bitmapW = width
        bitmapH = height

        maskBitmap = existingMask?.copy(Bitmap.Config.ARGB_8888, true)
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        maskCanvas = Canvas(maskBitmap!!)

        isInitialized = true
        invalidate()
    }

    fun getMaskBitmap(): Bitmap? = maskBitmap

    fun updateTransform(matrix: Matrix) {
        transformMatrix = Matrix(matrix)
        transformMatrix.invert(inverseMatrix)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(transformMatrix)
        maskBitmap?.let { canvas.drawBitmap(it, 0f, 0f, maskPreviewPaint) }
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInitialized || !isActive) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = handlePaintTouch(event.x, event.y, event.actionMasked)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    private fun handlePaintTouch(screenX: Float, screenY: Float, action: Int): Boolean {
        val pt = screenPointToBitmap(screenX, screenY)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                lastX = pt.x; lastY = pt.y
                drawMaskPointOrLine(pt.x, pt.y, isDown = true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return false
                drawMaskPointOrLine(pt.x, pt.y, isDown = false)
                lastX = pt.x; lastY = pt.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    isDrawing = false
                    maskBitmap?.let { bmp ->
                        onMaskStrokeFinished?.invoke(bmp.copy(bmp.config, true))
                    }
                }
                return true
            }
        }
        return false
    }

    private fun drawMaskPointOrLine(x: Float, y: Float, isDown: Boolean) {
        val mc = maskCanvas ?: return
        maskPaint.strokeWidth = brushRadiusBitmapPx * 2f
        maskPaint.xfermode = PorterDuffXfermode(
            if (currentTool == BrushTool.ERASE) PorterDuff.Mode.CLEAR else PorterDuff.Mode.SRC
        )

        if (isDown) {
            maskPaint.style = Paint.Style.FILL
            mc.drawCircle(x, y, brushRadiusBitmapPx, maskPaint)
            maskPaint.style = Paint.Style.STROKE
        } else {
            mc.drawLine(lastX, lastY, x, y, maskPaint)
        }
    }

    private fun screenPointToBitmap(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }
}