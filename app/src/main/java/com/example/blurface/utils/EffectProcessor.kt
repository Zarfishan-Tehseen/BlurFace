package com.example.blurface.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.example.blurface.domain.model.FaceEffect

@Suppress("DEPRECATION")
object EffectProcessor {

    private fun Bitmap.toSoftwareMutable(): Bitmap = copy(Bitmap.Config.ARGB_8888, true)
    fun applyEffect(
        context: Context,
        original: Bitmap,
        boxes: List<RectF>,
        selectedIndices: Set<Int>,
        effect: FaceEffect,
        intensityPercent: Float,
        emoji: String = "😀",
        selectedColor: Int = Color.BLACK
    ): Bitmap {
        val result = original.toSoftwareMutable()
        val canvas = Canvas(result)
        val pct = intensityPercent.coerceIn(0f, 100f)

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
                    // 0-100% -> RenderScript blur radius 1-25 (its hard max)
                    val radius = (1f + (pct / 100f) * 24f).coerceIn(1f, 25f)
                    val crop = Bitmap.createBitmap(result, left, top, w, h)
                    val blurred = blurBitmap(context, crop, radius)
                    canvas.drawBitmap(blurred, left.toFloat(), top.toFloat(), null)
                }
                FaceEffect.PIXELATE -> {
                    // 0-100% -> block size 2-40 (higher % = blockier)
                    val blockSize = (2 + (pct / 100f * 38f).toInt()).coerceAtLeast(2)
                    val crop = Bitmap.createBitmap(result, left, top, w, h)
                    val pixelated = pixelateBitmap(crop, blockSize)
                    canvas.drawBitmap(pixelated, left.toFloat(), top.toFloat(), null)
                }
                FaceEffect.COLOR -> {
                    // 2. Extract Alpha from the intensity slider pct
                    val alpha = (pct / 100f * 255f).toInt().coerceIn(0, 255)

                    // 3. Extract RGB components from our dynamic selectedColor
                    val red = Color.red(selectedColor)
                    val green = Color.green(selectedColor)
                    val blue = Color.blue(selectedColor)

                    // 4. Combine them into a single Paint object
                    val paint = Paint().apply {
                        color = Color.argb(alpha, red, green, blue)
                    }
                    canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
                }
                FaceEffect.EMOJI -> {
                    // 0-100% -> emoji scale 0.5x-1.5x of the box-fit size
                    val scale = 0.5f + (pct / 100f)
                    drawEmoji(canvas, emoji, left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), scale)
                }
            }
        }
        return result
    }

    private fun drawEmoji(
        canvas: Canvas,
        emoji: String,
        left: Float, top: Float, right: Float, bottom: Float,
        scale: Float
    ) {
        val boxW = right - left
        val boxH = bottom - top
        val paint = Paint().apply {
            textSize = minOf(boxW, boxH) * 0.9f * scale
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val fm = paint.fontMetrics
        canvas.drawText(
            emoji,
            left + boxW / 2f,
            top + boxH / 2f - (fm.ascent + fm.descent) / 2f,
            paint
        )
    }

    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val safe = if (Build.VERSION.SDK_INT >= 26 && bitmap.config == Bitmap.Config.HARDWARE)
            bitmap.copy(Bitmap.Config.ARGB_8888, true) else bitmap

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
}