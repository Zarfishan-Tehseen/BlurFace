package com.example.blurface.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MediaSizeUtils {
    fun getFileSizeBytes(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrElse {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
        }
    }
}