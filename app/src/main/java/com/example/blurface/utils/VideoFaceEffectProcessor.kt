package com.example.blurface.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.example.blurface.domain.model.BlurShape
import com.example.blurface.domain.model.BlurSettings
import com.example.blurface.domain.model.BlurType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
object VideoFaceEffectProcessor {
    suspend fun applyPreview(
        context: Context,
        faceCrop: Bitmap,
        settings: BlurSettings
    ): Bitmap = withContext(Dispatchers.Default) {
        val pct = settings.intensity.coerceIn(0, 100).toFloat()

        val effectLayer: Bitmap = when (settings.blurType) {
            BlurType.GAUSSIAN -> {
                val radius = (1f + (pct / 100f) * 24f).coerceIn(1f, 25f)
                blurBitmap(context, faceCrop, radius)
            }

            BlurType.MOSAIC -> {
                val blockSize = (2 + (pct / 100f * 38f).toInt()).coerceAtLeast(2)
                pixelateBitmap(faceCrop, blockSize)
            }

            BlurType.COLOR -> {
                val alpha = (pct / 100f * 255f).toInt().coerceIn(0, 255)
                Bitmap.createBitmap(faceCrop.width, faceCrop.height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).drawColor(
                        Color.argb(
                            alpha,
                            Color.red(settings.fillColor),
                            Color.green(settings.fillColor),
                            Color.blue(settings.fillColor)
                        )
                    )
                }
            }

            BlurType.EMOJI -> {
                Bitmap.createBitmap(faceCrop.width, faceCrop.height, Bitmap.Config.ARGB_8888).also { bmp ->
                    val emojiCanvas = Canvas(bmp)
                    val paint = Paint().apply {
                        textSize = minOf(bmp.width, bmp.height) * 0.85f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        alpha = (pct / 100f * 255f).toInt()
                    }
                    val fm = paint.fontMetrics
                    emojiCanvas.drawText(
                        settings.emoji,
                        bmp.width / 2f,
                        bmp.height / 2f - (fm.ascent + fm.descent) / 2f,
                        paint
                    )
                }
            }
        }

        applyShapeMask(context, faceCrop, effectLayer, settings.shape, settings.feather)
    }

    /**
     * Clips [effect] down to [shape] and composites it over [base], with a feathered
     * (blurred-mask) edge so the effect fades into the original crop instead of a hard
     * cutout. RECTANGLE is full-bleed - no mask, no feather.
     */
    private fun applyShapeMask(
        context: Context,
        base: Bitmap,
        effect: Bitmap,
        shape: BlurShape,
        featherPercent: Int
    ): Bitmap {
        if (shape == BlurShape.RECTANGLE) return effect

        val w = base.width
        val h = base.height

        var maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        when (shape) {
            BlurShape.CIRCLE -> {
                val r = minOf(w, h) / 2f
                maskCanvas.drawCircle(w / 2f, h / 2f, r, maskPaint)
            }
            BlurShape.AUTO_FACE -> {
                // Naturalistic face oval, slightly inset from the crop edges.
                val rect = RectF(w * 0.06f, h * 0.02f, w * 0.94f, h * 0.98f)
                maskCanvas.drawOval(rect, maskPaint)
            }
            BlurShape.RECTANGLE -> Unit // unreachable - handled above
        }

        if (featherPercent > 0) {
            val featherRadius = (featherPercent.coerceIn(0, 100) / 100f * 18f).coerceIn(0.1f, 25f)
            maskBmp = blurBitmap(context, maskBmp, featherRadius)
        }

        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val maskedEffect = effect.copy(Bitmap.Config.ARGB_8888, true)
        val dstInPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        Canvas(maskedEffect).drawBitmap(maskBmp, 0f, 0f, dstInPaint)

        canvas.drawBitmap(maskedEffect, 0f, 0f, null)
        return result
    }

    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        // Always copy - never mutate the caller's bitmap in place (RenderScript writes
        // the blurred result back into whatever Allocation-backed bitmap it's given).
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
        val small = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width / blockSize).coerceAtLeast(1),
            (bitmap.height / blockSize).coerceAtLeast(1),
            false
        )
        return Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, false)
    }
}