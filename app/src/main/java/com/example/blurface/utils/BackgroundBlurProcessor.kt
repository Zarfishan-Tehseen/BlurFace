package com.example.blurface.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.example.blurface.domain.model.BackgroundBlurType
import com.example.blurface.domain.model.BackgroundFilter

@Suppress("DEPRECATION")
object BackgroundBlurProcessor {

    fun apply(
        context: Context,
        original: Bitmap,
        mask: Bitmap,
        blurType: BackgroundBlurType,
        intensityPercent: Float,
        filter: BackgroundFilter
    ): Bitmap {
        android.util.Log.d("BgBlurDebug", "COMPOSITE_V2 apply() running - filter=$filter blurType=$blurType")

        // NONE means "blur disabled" - hand back the untouched photo.
        if (filter == BackgroundFilter.NONE) return original

        val radius = (1f + (intensityPercent.coerceIn(0f, 100f) / 100f) * 24f).coerceIn(1f, 25f)
        val normalizedOriginal = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        Canvas(normalizedOriginal).drawBitmap(original, 0f, 0f, null)
        var background = blurBitmap(context, normalizedOriginal, radius)

        if (blurType == BackgroundBlurType.RADIAL) {
            background = applyVignette(background)
        }

        background = applyFilterTint(background, filter)

        // Clip the ORIGINAL (sharp) image down to just the subject using the mask's
        // alpha channel, then draw that on top of the processed background.
        //
        // Built from a fresh Bitmap.createBitmap() + Canvas draw rather than
        // original.copy() - copy() preserves whatever ColorSpace the source photo
        // carries (many phone cameras embed a wide-gamut/Display P3 ICC profile in
        // JPEGs), and compositing a non-default-ColorSpace bitmap's fully-transparent
        // pixels onto a default-ColorSpace canvas is what was wiping this layer out.
        // background doesn't have this problem because blurBitmap() round-trips it
        // through a RenderScript Allocation, which drops that ColorSpace tagging as a
        // side effect - drawing onto a fresh bitmap here does the same normalization
        // explicitly instead of relying on that RenderScript side effect.
        val subject = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        Canvas(subject).drawBitmap(original, 0f, 0f, null)
        Canvas(subject).drawBitmap(
            mask, 0f, 0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        )
        logSample("subject after mask", subject)

        val result = Bitmap.createBitmap(background.width, background.height, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        logSample("result: fresh blank", result)

        resultCanvas.drawBitmap(background, 0f, 0f, null) // layer 1: full blurred/tinted background
        logSample("result: after background layer", result)

        resultCanvas.drawBitmap(subject, 0f, 0f, null)    // layer 2: sharp subject on top
        logSample("result: after subject layer", result)

        return result
    }

    private fun logSample(label: String, bmp: Bitmap) {
        fun px(x: Float, y: Float): String {
            val xi = (bmp.width * x).toInt().coerceIn(0, bmp.width - 1)
            val yi = (bmp.height * y).toInt().coerceIn(0, bmp.height - 1)
            val c = bmp.getPixel(xi, yi)
            return "(${(x*100).toInt()}%,${(y*100).toInt()}%)=${Integer.toHexString(c)}"
        }
        android.util.Log.d(
            "BgBlurDebug",
            "$label [${bmp.width}x${bmp.height}, ${bmp.config}] " +
                    "${px(0.5f, 0.5f)} ${px(0.25f, 0.25f)} ${px(0.75f, 0.25f)} ${px(0.25f, 0.75f)} ${px(0.75f, 0.75f)}"
        )
    }

    /** Subtle darkened corners so "Radial" reads as a distinct style from plain Gaussian. */
    private fun applyVignette(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width.toFloat()
        val h = result.height.toFloat()
        val radius = maxOf(w, h) * 0.75f

        val gradient = RadialGradient(
            w / 2f, h / 2f, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb(90, 0, 0, 0)),
            null, Shader.TileMode.CLAMP
        )
        Canvas(result).drawRect(0f, 0f, w, h, Paint().apply { shader = gradient })
        return result
    }

    private fun applyFilterTint(bitmap: Bitmap, filter: BackgroundFilter): Bitmap {
        if (filter == BackgroundFilter.ORIGINAL) return bitmap

        val matrix = ColorMatrix()
        when (filter) {
            BackgroundFilter.WARM -> matrix.set(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 8f,
                    0f, 1.03f, 0f, 0f, 0f,
                    0f, 0f, 0.85f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            BackgroundFilter.COOL -> matrix.set(
                floatArrayOf(
                    0.85f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.15f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            else -> return bitmap
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            bitmap, 0f, 0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        )
        return result
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
}