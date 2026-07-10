package com.example.blurface.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FilePathUtil {
    fun getActualPath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        // Dynamic file stream extractor fallback tool
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "input_source_video.mp4")
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}