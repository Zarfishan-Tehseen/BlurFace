package com.example.blurface.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.blurface.domain.model.ExportFormat
import java.io.File
import java.io.FileOutputStream

object ImageSaver {

    private data class FormatConfig(
        val mimeType: String,
        val compressFormat: Bitmap.CompressFormat,
        val quality: Int,
        val extension: String
    )

    private fun configFor(format: ExportFormat): FormatConfig = when (format) {
        ExportFormat.JPEG_COMPRESSED ->
            FormatConfig("image/jpeg", Bitmap.CompressFormat.JPEG, 80, "jpg")
        ExportFormat.JPEG_LOSSLESS ->
            FormatConfig("image/jpeg", Bitmap.CompressFormat.JPEG, 100, "jpg")
        ExportFormat.PNG ->
            FormatConfig("image/png", Bitmap.CompressFormat.PNG, 100, "png")
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, format: ExportFormat): Uri? {
        val config = configFor(format)
        val filename = "BlurFace_${System.currentTimeMillis()}.${config.extension}"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, filename, config)
        } else {
            saveViaLegacyFile(context, bitmap, filename, config)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        config: FormatConfig
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, config.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BlurFace")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        val wrote = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(config.compressFormat, config.quality, out)
        } ?: false

        if (!wrote) {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveViaLegacyFile(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        config: FormatConfig
    ): Uri? {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "BlurFace").apply { if (!exists()) mkdirs() }
        val file = File(appDir, filename)

        FileOutputStream(file).use { out ->
            bitmap.compress(config.compressFormat, config.quality, out)
        }

        // Make it show up in the gallery app immediately instead of after a reboot
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(config.mimeType), null)

        return Uri.fromFile(file)
    }
}