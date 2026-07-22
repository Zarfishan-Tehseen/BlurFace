package com.example.blurface.ui.recents

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.FileProvider
import com.example.blurface.databinding.PopupRecentEditActionsBinding
import com.example.blurface.domain.model.RecentEdit
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object RecentEditActionsHelper {

    fun showPopup(
        context: Context,
        anchor: View,
        onDownload: () -> Unit,
        onDelete: () -> Unit,
        onShare: () -> Unit
    ) {
        val popupBinding = PopupRecentEditActionsBinding.inflate(LayoutInflater.from(context))
        val popup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply { elevation = 12f }

        popupBinding.rowDownload.setOnClickListener { popup.dismiss(); onDownload() }
        popupBinding.rowDelete.setOnClickListener { popup.dismiss(); onDelete() }
        popupBinding.rowShare.setOnClickListener { popup.dismiss(); onShare() }

        popup.showAsDropDown(anchor, -120, 8)
    }

    /** Does file I/O - call this from a background dispatcher. */
    fun copyToDownloads(context: Context, edit: RecentEdit): Uri? {
        val rawUriString = edit.mediaUri
        val resolver = context.contentResolver
        val extension = if (edit.isVideo) "mp4" else "jpg"
        val filename = "BlurFace_${System.currentTimeMillis()}.$extension"

        // FIX 1: Safely open InputStream regardless of whether mediaUri is content://, file://, or a raw path
        val inputStream: InputStream = try {
            val sourceUri = Uri.parse(rawUriString)
            if (rawUriString.startsWith("content://")) {
                resolver.openInputStream(sourceUri)
            } else {
                val filePath = if (rawUriString.startsWith("file://")) sourceUri.path else rawUriString
                if (filePath != null && File(filePath).exists()) {
                    FileInputStream(File(filePath))
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null // Source file could not be read

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: Use Scoped Storage via MediaStore
            val mimeType = if (edit.isVideo) "video/mp4" else "image/jpeg"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BlurFace")
            }
            val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

            try {
                inputStream.use { input ->
                    resolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                destUri
            } catch (e: Exception) {
                e.printStackTrace()
                // Clean up orphaned entry if writing failed
                resolver.delete(destUri, null, null)
                null
            }
        } else {
            // API 24 - 28: Use legacy Environment directory
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BlurFace"
            )
            if (!targetDir.exists() && !targetDir.mkdirs()) return null
            val destFile = File(targetDir, filename)

            try {
                inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(destFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun share(context: Context, edit: RecentEdit) {
        val uriToShare: Uri = try {
            val parsedUri = Uri.parse(edit.mediaUri)
            if (edit.mediaUri.startsWith("content://")) {
                parsedUri
            } else {
                // FIX 2: Convert local file paths into FileProvider content URIs to prevent FileUriExposedException
                val cleanPath = if (edit.mediaUri.startsWith("file://")) parsedUri.path else edit.mediaUri
                val file = File(cleanPath ?: edit.mediaUri)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Uri.parse(edit.mediaUri)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (edit.isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uriToShare)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share"))
    }
}