package com.example.blurface.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.example.blurface.domain.model.FaceEffect
import kotlin.collections.filter

@Suppress("DEPRECATION")
object EffectProcessor {

    private fun Bitmap.toSoftwareMutable(): Bitmap = copy(Bitmap.Config.ARGB_8888, true)

    /**
     * Applies [effect] to every box in [boxes] whose index is in [selectedIndices].
     * [intensityPercent] is a single 0-100 slider value shared across all effects;
     * it's converted to whatever each effect actually needs below.
     */
    fun applyEffect(
        context: Context,
        original: Bitmap,
        boxes: List<RectF>,
        selectedIndices: Set<Int>,
        effect: FaceEffect,
        intensityPercent: Float,
        emoji: String = "😀",
        fillColor: Int = Color.BLACK,
        brushMask: Bitmap? = null
    ): Bitmap {
        // 1. Create a mutable copy of the original image to serve as our base background
        val result = original.toSoftwareMutable()
        val canvas = Canvas(result)
        val pct = intensityPercent.coerceIn(0f, 100f)

        // 2. Render the effects inside the manual brush stroke areas first
        if (brushMask != null && hasDrawnPixels(brushMask)) {

            // Generate a full-size effect bitmap matching the active configurations
            val fullEffectBmp =
                Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val effectCanvas = Canvas(fullEffectBmp)

            when (effect) {
                FaceEffect.BLUR -> {
                    val radius = (1f + (pct / 100f) * 24f).coerceIn(1f, 25f)
                    val blurred = blurBitmap(context, original, radius)
                    effectCanvas.drawBitmap(blurred, 0f, 0f, null)
                }

                FaceEffect.PIXELATE -> {
                    val blockSize = (2 + (pct / 100f * 38f).toInt()).coerceAtLeast(2)
                    val pixelated = pixelateBitmap(original, blockSize)
                    effectCanvas.drawBitmap(pixelated, 0f, 0f, null)
                }

                FaceEffect.COLOR -> {
                    val alpha = (pct / 100f * 255f).toInt().coerceIn(0, 255)
                    effectCanvas.drawColor(
                        Color.argb(
                            alpha,
                            Color.red(fillColor),
                            Color.green(fillColor),
                            Color.blue(fillColor)
                        )
                    )
                }

                FaceEffect.EMOJI -> {
                    // Tile emoji stamps (overlapping, staggered) across the whole canvas;
                    // the DST_IN masking step below clips them down to exactly the brushed
                    // stroke area, giving a continuous run of emojis wherever painted.
                    drawEmojiPattern(effectCanvas, emoji, result.width, result.height, pct)
                }
            }

            // Isolate the effect area using the brush mask stencil (applies to every
            // effect, including EMOJI, so brushed strokes always show through the mask).
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            val maskedEffectLayer =
                Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val layerCanvas = Canvas(maskedEffectLayer)

            layerCanvas.drawBitmap(fullEffectBmp, 0f, 0f, null)
            layerCanvas.drawBitmap(brushMask, 0f, 0f, maskPaint)

            // Paste the final masked effect over the background layout
            canvas.drawBitmap(maskedEffectLayer, 0f, 0f, null)

            fullEffectBmp.recycle()
            maskedEffectLayer.recycle()
        }

        // 3. Render the effects inside the selected face bounding boxes
        selectedIndices.forEach { i ->
            val box = boxes.getOrNull(i) ?: return@forEach
            val left = box.left.toInt().coerceIn(0, result.width)
            val top = box.top.toInt().coerceIn(0, result.height)
            val right = box.right.toInt().coerceIn(0, result.width)
            val bottom = box.bottom.toInt().coerceIn(0, result.height)
            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) return@forEach

            when (effect) {
                FaceEffect.BLUR -> {
                    val radius = (1f + (pct / 100f) * 24f).coerceIn(1f, 25f)
                    val crop = Bitmap.createBitmap(original, left, top, w, h)
                    val blurred = blurBitmap(context, crop, radius)
                    canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
                }

                FaceEffect.PIXELATE -> {
                    val blockSize = (2 + (pct / 100f * 38f).toInt()).coerceAtLeast(2)
                    val crop = Bitmap.createBitmap(original, left, top, w, h)
                    val pixelated = pixelateBitmap(crop, blockSize)
                    canvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), null)
                }

                FaceEffect.COLOR -> {
                    val alpha = (pct / 100f * 255f).toInt().coerceIn(0, 255)
                    val paint = Paint().apply {
                        this.color = Color.argb(
                            alpha,
                            Color.red(fillColor),
                            Color.green(fillColor),
                            Color.blue(fillColor)
                        )
                    }
                    canvas.drawRect(
                        left.toFloat(),
                        top.toFloat(),
                        right.toFloat(),
                        bottom.toFloat(),
                        paint
                    )
                }

                FaceEffect.EMOJI -> {
                    // Size is fixed — intensity fades/darkens the emoji's own opacity instead.
                    drawEmoji(
                        canvas,
                        emoji,
                        left.toFloat(),
                        top.toFloat(),
                        right.toFloat(),
                        bottom.toFloat(),
                        pct
                    )
                }
            }
        }
        return result
    }

    private fun drawEmoji(
        canvas: Canvas,
        emoji: String,
        left: Float, top: Float, right: Float, bottom: Float,
        intensityPercent: Float
    ) {
        val boxW = right - left
        val boxH = bottom - top
        val paint = Paint().apply {
            textSize = minOf(boxW, boxH) * 0.9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            alpha = (intensityPercent.coerceIn(0f, 100f) / 100f * 255f).toInt()
        }
        val fm = paint.fontMetrics
        canvas.drawText(
            emoji,
            left + boxW / 2f,
            top + boxH / 2f - (fm.ascent + fm.descent) / 2f,
            paint
        )
    }

    /**
     * Fills the canvas with a staggered, overlapping run of emoji stamps at a fixed size.
     * Spacing is deliberately smaller than the stamp size so neighboring emojis overlap
     * into one continuous run rather than sitting in a clean, gapped grid. Meant to be
     * drawn onto the brush-mask effect layer, which then gets clipped down to the
     * painted stroke shape by the caller. [intensityPercent] fades/darkens the same way
     * as the single-stamp version above.
     */
    private fun drawEmojiPattern(
        canvas: Canvas,
        emoji: String,
        width: Int,
        height: Int,
        intensityPercent: Float
    ) {
        val stampSize = 130f
        val spacing = stampSize * 0.90f
        val paint = Paint().apply {
            textSize = stampSize
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            alpha = (intensityPercent.coerceIn(0f, 100f) / 100f * 255f).toInt()
        }
        val fm = paint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f

        var row = 0
        var y = spacing / 2f
        while (y - stampSize / 2f < height) {
            // Stagger alternating rows by half a cell (brick-pattern) for denser overlap
            val rowOffset = if (row % 2 == 0) 0f else spacing / 2f
            var x = spacing / 2f - rowOffset
            while (x - stampSize / 2f < width) {
                canvas.drawText(emoji, x, y + baselineOffset, paint)
                x += spacing
            }
            y += spacing
            row++
        }
    }

    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        // Always copy: ScriptIntrinsicBlur writes back into `safe` via output.copyTo(safe),
        // and if `safe` aliases the caller's bitmap (e.g. the shared `original` source bitmap)
        // that bitmap gets mutated in place, corrupting it for every future render.
        val safe = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, safe)
        val output = Allocation.createTyped(rs, input.type)
        ScriptIntrinsicBlur.create(rs, Element.U8_4(rs)).apply {
            setRadius(radius)
            setInput(input)
            forEach(output)
        }
        output.copyTo(safe)
        rs.destroy()
        return safe
    }

    private fun pixelateBitmap(bitmap: Bitmap, blockSize: Int): Bitmap {
        val safe = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE)
            bitmap.copy(Bitmap.Config.ARGB_8888, true) else bitmap

        val small = Bitmap.createScaledBitmap(
            safe,
            (safe.width / blockSize).coerceAtLeast(1),
            (safe.height / blockSize).coerceAtLeast(1),
            false
        )
        return Bitmap.createScaledBitmap(small, safe.width, safe.height, false)
    }

    private fun hasDrawnPixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            if (Color.alpha(pixel) > 0) {
                return true
            }
        }
        return false
    }
}