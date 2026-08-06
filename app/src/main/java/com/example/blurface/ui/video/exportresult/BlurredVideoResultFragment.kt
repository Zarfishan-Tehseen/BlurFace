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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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

class BlurredVideoResultFragment : Fragment() {

    private var _binding: FragmentBlurredVideoResultBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FaceClusterViewModel by navGraphViewModels(R.id.nav_graph)

    private var exoPlayer: ExoPlayer? = null
    private var resultPath: String? = null
    private var isSaving = false

    private val progressHandler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private val progressTick = object : Runnable {
        override fun run() {
            if (_binding == null) return
            exoPlayer?.let { player ->
                if (!isUserSeeking && player.isPlaying) {
                    val currentPos = player.currentPosition.toInt()
                    binding.seekVideoProgress.progress = currentPos
                    binding.tvCurrentTime.text = formatMs(currentPos)
                }
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    private val writePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) saveToGallery() else {
                Toast.makeText(
                    requireContext(),
                    "Storage permission is needed to save the video.",
                    Toast.LENGTH_LONG
                ).show()
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContent) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        val path = viewModel.exportedVideoPath
        if (path == null || !File(path).exists()) {
            Toast.makeText(requireContext(), "Exported video not found.", Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }
        resultPath = path

        setUpPlayer(path)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.actionSaveGallery.setOnClickListener {
            val savedUri = viewModel.exportedVideoUri
            if (savedUri != null) {
                Toast.makeText(requireContext(), "Video is already saved to your Gallery & Recents", Toast.LENGTH_SHORT).show()
                val bundle = Bundle().apply {
                    putString("mediaUri", savedUri)
                    putBoolean("isVideo", true)
                    putString("title", "Blurred Video")
                    putString("editType", "Video")
                    putLong("timestampMillis", System.currentTimeMillis())
                }
                findNavController().navigate(R.id.mediaPreviewFragment, bundle)
            } else {
                onSaveClicked()
            }
        }
        binding.actionShare.setOnClickListener { onShareClicked() }
        binding.actionCopyLink.setOnClickListener { onCopyLinkClicked() }
        binding.btnBackToHome.setOnClickListener {
            findNavController().popBackStack(findNavController().graph.startDestinationId, false)
        }
    }

    private fun setUpPlayer(path: String) {
        exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }

        binding.videoPlayer.player = exoPlayer

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer?.duration?.toInt() ?: 0
                    binding.seekVideoProgress.max = duration
                    binding.tvTotalTime.text = formatMs(duration)
                    binding.tvCurrentTime.text = formatMs(0)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    requireContext(),
                    "Couldn't play the exported video.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        binding.btnPlayPause.setOnClickListener { togglePlayback() }
        binding.videoFrame.setOnClickListener { togglePlayback() }

        binding.seekVideoProgress.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatMs(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                exoPlayer?.seekTo(seekBar.progress.toLong())
            }
        })

        binding.btnFullscreen.setOnClickListener { showFullscreenPlayer(path) }

        progressHandler.post(progressTick)
    }

    private fun togglePlayback() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                binding.btnPlayPause.visibility = View.VISIBLE
            } else {
                player.play()
                binding.btnPlayPause.visibility = View.GONE
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun showFullscreenPlayer(path: String) {
        val wasPlaying = exoPlayer?.isPlaying == true
        val resumePosition = exoPlayer?.currentPosition ?: 0L
        exoPlayer?.pause()

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val container = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val fullscreenPlayerView = PlayerView(requireContext()).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        container.addView(fullscreenPlayerView)
        dialog.setContentView(container)

        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.updatePadding(top = statusBars.top)
                insets
            }
        }

        val dialogPlayer = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            seekTo(resumePosition)
            play()
        }

        fullscreenPlayerView.player = dialogPlayer

        var lastKnownPosition = resumePosition

        fullscreenPlayerView.setOnClickListener {
            lastKnownPosition = dialogPlayer.currentPosition
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            dialogPlayer.release()
            exoPlayer?.seekTo(lastKnownPosition)
            if (wasPlaying) {
                exoPlayer?.play()
                binding.btnPlayPause.visibility = View.GONE
            } else {
                binding.btnPlayPause.visibility = View.VISIBLE
            }
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
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
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
                Toast.makeText(requireContext(), "Couldn't save the video.", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun recordRecentEdit(uri: Uri) {
        val context = requireContext()
        RecentEditsStore(context).add(
            RecentEdit(
                id = uri.toString(),
                title = "Video",
                editType = EditType.VIDEO,
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

    private fun onCopyLinkClicked() {
        val path = resultPath ?: return
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Exported video path", path))
        Toast.makeText(
            requireContext(),
            "Local file path copied (not a shareable link).",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressTick)
        binding.videoPlayer.player = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroyView()
        _binding = null
    }
}