package com.example.blurface.ui.video.exportresult

import android.Manifest
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.data.history.RecentEditsStore
import com.example.blurface.databinding.FragmentBlurredVideoResultBinding
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import com.example.blurface.ui.viewmodel.FaceClusterViewModel
import com.example.blurface.utils.MediaSizeUtils
import com.example.blurface.utils.VideoSaver
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Final screen of the pipeline: previews the rendered, blurred video
 * (FaceClusterViewModel.exportedVideoPath, set by ExportProcessFragment once
 * VideoExportProcessor finishes) using the custom player UI (play/pause overlay +
 * seekbar, no system MediaController), and lets the user save, share, or copy a
 * reference to the exported file.
 */
class BlurredVideoResultFragment : Fragment() {

    private var _binding: FragmentBlurredVideoResultBinding? = null
    private val binding get() = _binding!!

    // Same graph-scoped instance used throughout the pipeline - by this point it's
    // always already created (AnalyzingVideoFragment creates it first), so no factory
    // is needed here, matching ExportProcessFragment's pattern.
    private val viewModel: FaceClusterViewModel by navGraphViewModels(R.id.nav_graph)

    private var resultPath: String? = null
    private var isSaving = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private val progressTick = object : Runnable {
        override fun run() {
            if (_binding == null) return
            if (!isUserSeeking && binding.videoPlayer.isPlaying) {
                binding.seekVideoProgress.progress = binding.videoPlayer.currentPosition
                binding.tvCurrentTime.text = formatMs(binding.videoPlayer.currentPosition)
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    // Pre-Android-10 devices need WRITE_EXTERNAL_STORAGE to insert into MediaStore.Video;
    // API 29+ uses scoped storage and doesn't need it (see VideoSaver's doc comment).
    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveToGallery() else {
                Toast.makeText(requireContext(), "Storage permission is needed to save the video.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlurredVideoResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val path = viewModel.exportedVideoPath
        if (path == null || !File(path).exists()) {
            Toast.makeText(requireContext(), "Exported video not found.", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }
        resultPath = path

        setUpPlayer(path)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.actionSaveGallery.setOnClickListener { onSaveClicked() }
        binding.actionShare.setOnClickListener { onShareClicked() }
        binding.actionCopyLink.setOnClickListener { onCopyLinkClicked() }
        binding.btnBackToHome.setOnClickListener {
            findNavController().popBackStack(findNavController().graph.startDestinationId, false)
        }
    }

    private fun setUpPlayer(path: String) {
        binding.videoPlayer.setVideoPath(path)

        binding.videoPlayer.setOnPreparedListener { player ->
            player.isLooping = true
            binding.seekVideoProgress.max = player.duration
            binding.tvTotalTime.text = formatMs(player.duration)
            binding.tvCurrentTime.text = formatMs(0)
            // Starts paused, showing the centered play button - matches the layout's
            // overlay-play-button design rather than autoplaying immediately.
        }

        binding.videoPlayer.setOnErrorListener { _, _, _ ->
            Toast.makeText(requireContext(), "Couldn't play the exported video.", Toast.LENGTH_SHORT).show()
            true
        }

        binding.btnPlayPause.setOnClickListener { togglePlayback() }
        // Tapping the video itself also toggles playback, same as most players.
        binding.videoFrame.setOnClickListener { togglePlayback() }

        binding.seekVideoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatMs(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                binding.videoPlayer.seekTo(seekBar.progress)
            }
        })

        binding.btnFullscreen.setOnClickListener { showFullscreenPlayer(path) }

        progressHandler.post(progressTick)
    }

    private fun togglePlayback() {
        if (binding.videoPlayer.isPlaying) {
            binding.videoPlayer.pause()
            binding.btnPlayPause.visibility = View.VISIBLE
        } else {
            binding.videoPlayer.start()
            binding.btnPlayPause.visibility = View.GONE
        }
    }
    private fun showFullscreenPlayer(path: String) {
        val wasPlaying = binding.videoPlayer.isPlaying
        val resumePosition = binding.videoPlayer.currentPosition
        binding.videoPlayer.pause()

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val fullscreenVideoView = android.widget.VideoView(requireContext())
        dialog.setContentView(
            fullscreenVideoView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        fullscreenVideoView.setVideoPath(path)
        fullscreenVideoView.setOnPreparedListener { player ->
            player.isLooping = true
            fullscreenVideoView.seekTo(resumePosition)
            fullscreenVideoView.start()
        }
        // Tap to exit fullscreen, same as most players.
        fullscreenVideoView.setOnClickListener { dialog.dismiss() }

        dialog.setOnDismissListener {
            binding.videoPlayer.seekTo(fullscreenVideoView.currentPosition)
            if (wasPlaying) togglePlayback() else binding.btnPlayPause.visibility = View.VISIBLE
        }

        dialog.show()
    }

    private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun onSaveClicked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveToGallery()
    }

    private fun saveToGallery() {
        val path = resultPath ?: return
        if (isSaving) return
        isSaving = true
        binding.actionSaveGallery.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val uri = VideoSaver.saveToGallery(requireContext(), path)
            isSaving = false
            if (_binding == null) return@launch
            binding.actionSaveGallery.isEnabled = true

            if (uri != null) {
                Toast.makeText(requireContext(), "Saved to gallery.", Toast.LENGTH_SHORT).show()
                recordRecentEdit(uri)
            } else {
                Toast.makeText(requireContext(), "Couldn't save the video.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun recordRecentEdit(uri: Uri) {
        val context = requireContext()
        RecentEditsStore(context).add(
            RecentEdit(
                id = uri.toString(),
                title = "Video",
                editType = EditType.BLUR_FACES,
                mediaUri = uri.toString(),
                isVideo = true,
                timestampMillis = System.currentTimeMillis(),
                fileSizeBytes = MediaSizeUtils.getFileSizeBytes(context, uri)
            )
        )
    }

    private fun onShareClicked() {
        val path = resultPath ?: return
        val file = File(path)

        // Requires a FileProvider declared in AndroidManifest.xml, e.g.:
        //
        // <provider
        //     android:name="androidx.core.content.FileProvider"
        //     android:authorities="${applicationId}.fileprovider"
        //     android:exported="false"
        //     android:grantUriPermissions="true">
        //     <meta-data
        //         android:name="android.support.FILE_PROVIDER_PATHS"
        //         android:resource="@xml/file_paths" />
        // </provider>
        //
        // with res/xml/file_paths.xml exposing your cache dir, e.g.:
        // <paths><cache-path name="exports" path="." /></paths>
        val authority = "${requireContext().packageName}.fileprovider"
        val contentUri: Uri = try {
            FileProvider.getUriForFile(requireContext(), authority, file)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(
                requireContext(),
                "Sharing isn't configured yet - add a FileProvider to the manifest.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share video"))
    }

    /**
     * "Copy link" has no server-hosted URL to copy in this app today - there's no
     * upload/backend step anywhere in the pipeline. This copies the local file path
     * instead, which is only useful for on-device debugging, not for sending to
     * someone else. If you want a real shareable link, that needs a cloud upload step
     * (e.g. to your own storage bucket) that returns a public/signed URL - happy to
     * help wire that up if you want to add it.
     */
    private fun onCopyLinkClicked() {
        val path = resultPath ?: return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Exported video path", path))
        Toast.makeText(requireContext(), "Local file path copied (not a shareable link).", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressTick)
        binding.videoPlayer.stopPlayback()
        super.onDestroyView()
        _binding = null
    }
}