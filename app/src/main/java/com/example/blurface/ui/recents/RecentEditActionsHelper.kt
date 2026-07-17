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
import com.example.blurface.databinding.PopupRecentEditActionsBinding
import com.example.blurface.domain.model.RecentEdit
import java.io.File

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
        val resolver = context.contentResolver
        val sourceUri = Uri.parse(edit.mediaUri)
        val extension = if (edit.isVideo) "mp4" else "jpg"
        val filename = "BlurFace_${System.currentTimeMillis()}.$extension"

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
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: return null
                destUri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            // API 24 - 28: Use legacy Environment directory
            // NOTE: Make sure your app requests WRITE_EXTERNAL_STORAGE permission on these older versions!
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "BlurFace"
            )
            if (!targetDir.exists() && !targetDir.mkdirs()) return null
            val destFile = File(targetDir, filename)

            try {
                resolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return null
                Uri.fromFile(destFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun share(context: Context, edit: RecentEdit) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (edit.isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(edit.mediaUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share"))
    }
}