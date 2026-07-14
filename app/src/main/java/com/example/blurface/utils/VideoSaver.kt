package com.example.blurface.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a rendered video file into the device's Movies collection.
 * Video-specific counterpart to the image flow's ImageSaver - kept separate
 * since the two features are isolated, even though the MediaStore pattern
 * is conceptually the same (insert a pending row, stream bytes in, clear pending).
 */
object VideoSaver {

    /**
     * Copies [sourcePath] into MediaStore.Video. Returns the resulting content
     * Uri on success, or null if the source file is missing or the write failed.
     *
     * On API 29+ this uses scoped storage (no permission needed). On older
     * APIs, writing to MediaStore.Video.Media.EXTERNAL_CONTENT_URI still
     * requires the caller to hold WRITE_EXTERNAL_STORAGE - that permission
     * request isn't handled here and should happen before calling this.
     */
    suspend fun saveToGallery(
        context: Context,
        sourcePath: String,
        displayName: String = "BlurFace_${System.currentTimeMillis()}.mp4"
    ): Uri? = withContext(Dispatchers.IO) {
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) return@withContext null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BlurFace")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext null

        val copyResult = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("Could not open output stream for $uri")
        }

        if (copyResult.isFailure) {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            runCatching { resolver.update(uri, values, null, null) }
        }

        uri
    }
}