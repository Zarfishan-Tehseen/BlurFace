package com.example.blurface.ui.manualselection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import kotlin.math.hypot
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.blurface.domain.model.DetectedFace

class InteractiveOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faces: List<DetectedFace> = emptyList()
    private var sourceWidth = 0
    private var sourceHeight = 0

    // Core transforms
    val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()

    // Modes & Callbacks
    var isBrushModeEnabled = false
    var brushMaskView: BrushMaskView? = null
    var onFaceTapped: ((Int) -> Unit)? = null
    var onFaceBoundsChanged: ((Int, RectF) -> Unit)? = null
    var onMatrixChanged: ((Matrix) -> Unit)? = null

    // Box editing states
    private var selectedFaceIdForEdit: Int? = null
    private var currentEditMode = EditMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchTolerance = 40f // px radius around corners to catch resize triggers

    private enum class EditMode { NONE, DRAG, RESIZE_BR, RESIZE_TL }

    // Paints
    private val boxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 255, 196, 0)
    }
    private val boxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.rgb(255, 196, 0)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // Gestures for Zoom & Pan
    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isBrushModeEnabled) return false
                transformMatrix.postScale(
                    detector.scaleFactor,
                    detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )
                notifyMatrixUpdate()
                return true
            }
        })

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isBrushModeEnabled) return false
                val pt = screenToBitmapPoints(e.x, e.y)

                // Check if user tapped inside any face box
                val tappedFace = faces.lastOrNull { it.boundingBox.contains(pt[0], pt[1]) }
                tappedFace?.let {
                    onFaceTapped?.invoke(it.id)
                    return true
                }
                return false
            }
        })

    fun setFaces(faces: List<DetectedFace>, sourceWidth: Int, sourceHeight: Int) {
        this.faces = faces
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        invalidate()
    }

    fun initDefaultTransform(bitmapW: Int, bitmapH: Int) {
        if (bitmapW == 0 || bitmapH == 0 || width == 0 || height == 0) return
        val scale = minOf(width.toFloat() / bitmapW, height.toFloat() / bitmapH)
        val dx = (width - bitmapW * scale) / 2f
        val dy = (height - bitmapH * scale) / 2f
        transformMatrix.reset()
        transformMatrix.postScale(scale, scale)
        transformMatrix.postTranslate(dx, dy)
        notifyMatrixUpdate()
    }

    private fun notifyMatrixUpdate() {
        transformMatrix.invert(inverseMatrix)
        onMatrixChanged?.invoke(transformMatrix)
        brushMaskView?.updateTransform(transformMatrix)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(transformMatrix)

        faces.forEach { face ->
            if (face.isSelected) {
                // Draw selection highlight
                canvas.drawRoundRect(face.boundingBox, 24f, 24f, boxFillPaint)
                canvas.drawRoundRect(face.boundingBox, 24f, 24f, boxBorderPaint)

                // Draw resize handles on the active/tapped box corner points
                canvas.drawCircle(face.boundingBox.left, face.boundingBox.top, 12f, handlePaint)
                canvas.drawCircle(face.boundingBox.right, face.boundingBox.bottom, 12f, handlePaint)
            }
        }
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ROUTE 1: If brush mode is on, completely delegate touch down to BrushMaskView
        if (isBrushModeEnabled) {
            return brushMaskView?.onTouchEvent(event) ?: false
        }

        // ROUTE 2: Handle Box interactions and Adjustments
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pt = screenToBitmapPoints(x, y)
                lastTouchX = x
                lastTouchY = y

                // Determine if we hit a handle or box body of a selected face
                selectedFaceIdForEdit = null
                currentEditMode = EditMode.NONE

                for (face in faces.filter { it.isSelected }) {
                    val bounds = face.boundingBox
                    val mappedTL = bitmapToScreenPoints(bounds.left, bounds.top)
                    val mappedBR = bitmapToScreenPoints(bounds.right, bounds.bottom)

                    if (hypot(x - mappedTL[0], y - mappedTL[1]) < touchTolerance) {
                        selectedFaceIdForEdit = face.id
                        currentEditMode = EditMode.RESIZE_TL
                        break
                    } else if (hypot(x - mappedBR[0], y - mappedBR[1]) < touchTolerance) {
                        selectedFaceIdForEdit = face.id
                        currentEditMode = EditMode.RESIZE_BR
                        break
                    } else if (bounds.contains(pt[0], pt[1])) {
                        selectedFaceIdForEdit = face.id
                        currentEditMode = EditMode.DRAG
                        break
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true

                val dx = x - lastTouchX
                val dy = y - lastTouchY

                val id = selectedFaceIdForEdit
                if (id != null && currentEditMode != EditMode.NONE) {
                    val face = faces.firstOrNull { it.id == id } ?: return false
                    val newBounds = RectF(face.boundingBox)

                    // Convert raw physical screen shifts into background bitmap scale units
                    val bitmapDelta = screenToBitmapVector(dx, dy)

                    when (currentEditMode) {
                        EditMode.DRAG -> {
                            newBounds.offset(bitmapDelta[0], bitmapDelta[1])
                        }

                        EditMode.RESIZE_TL -> {
                            newBounds.left += bitmapDelta[0]
                            newBounds.top += bitmapDelta[1]
                        }

                        EditMode.RESIZE_BR -> {
                            newBounds.right += bitmapDelta[0]
                            newBounds.bottom += bitmapDelta[1]
                        }

                        else -> {}
                    }

                    // Enforce structural rect invariants
                    if (newBounds.left < newBounds.right && newBounds.top < newBounds.bottom) {
                        onFaceBoundsChanged?.invoke(id, newBounds)
                    } else {
                        // NEW: clamp minimum size instead of ignoring the update entirely
                        val minSize = 10f
                        when (currentEditMode) {
                            EditMode.RESIZE_TL -> newBounds.left = newBounds.right - minSize
                            EditMode.RESIZE_BR -> newBounds.right = newBounds.left + minSize
                            else -> {}
                        }
                        onFaceBoundsChanged?.invoke(id, newBounds)
                    }
                } else {
                    // Pan execution when dragging empty space
                    transformMatrix.postTranslate(dx, dy)
                    notifyMatrixUpdate()
                }

                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedFaceIdForEdit = null
                currentEditMode = EditMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)

            }
        }
        return true
    }

    // Matrix conversion utilities
    private fun screenToBitmapPoints(x: Float, y: Float): FloatArray {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        return pts
    }

    private fun bitmapToScreenPoints(x: Float, y: Float): FloatArray {
        val pts = floatArrayOf(x, y)
        transformMatrix.mapPoints(pts)
        return pts
    }

    private fun screenToBitmapVector(dx: Float, dy: Float): FloatArray {
        val vec = floatArrayOf(dx, dy)
        inverseMatrix.mapVectors(vec)
        return vec
    }
}