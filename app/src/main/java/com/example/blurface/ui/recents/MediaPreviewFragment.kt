package com.example.blurface.ui.recents

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.blurface.databinding.FragmentMediaPreviewBinding
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MediaPreviewFragment : Fragment() {

    private var _binding: FragmentMediaPreviewBinding? = null
    private val binding get() = _binding!!

    private var mediaUriString: String = ""
    private var isVideo: Boolean = false
    private var title: String = ""
    private var editTypeLabel: String = ""
    private var timestampMillis: Long = 0L
    private var fileSizeBytes: Long = 0L

    private var exoPlayer: ExoPlayer? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private val progressTick = object : Runnable {
        override fun run() {
            if (_binding == null) return
            val player = exoPlayer
            if (!isUserSeeking && player != null && player.isPlaying) {
                val currentPos = player.currentPosition.toInt()
                binding.seekVideoProgress.progress = currentPos
                binding.tvCurrentTime.text = formatMs(currentPos)
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            mediaUriString = args.getString("mediaUri").orEmpty()
            isVideo = args.getBoolean("isVideo", false)
            title = args.getString("title").orEmpty().ifEmpty { "Media Preview" }
            editTypeLabel = args.getString("editType").orEmpty()
            timestampMillis = args.getLong("timestampMillis", System.currentTimeMillis())
            fileSizeBytes = args.getLong("fileSizeBytes", 0L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        setupHeaderAndDetails()
        setupMediaView()
        setupActions()
    }

    private fun setupHeaderAndDetails() {
        binding.tvHeaderTitle.text = title
        binding.tvHeaderSubtitle.text = if (isVideo) "Video Preview" else "Photo Preview"

        binding.tvEditTypeTag.text = editTypeLabel.ifEmpty { if (isVideo) "Video" else "Photo" }

        val sizeMb = fileSizeBytes / (1024.0 * 1024.0)
        binding.tvFileSizeTag.text = String.format(Locale.US, "%.1f MB", sizeMb)

        val dateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        binding.tvDateTimeTag.text = dateFormat.format(timestampMillis)
    }

    private fun setupMediaView() {
        if (mediaUriString.isEmpty()) {
            Toast.makeText(requireContext(), "Media not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse(mediaUriString)

        if (isVideo) {
            binding.ivImagePreview.visibility = View.GONE
            binding.videoContainer.visibility = View.VISIBLE
            setupVideoPlayer(uri)
        } else {
            binding.videoContainer.visibility = View.GONE
            binding.ivImagePreview.visibility = View.VISIBLE
            runCatching { binding.ivImagePreview.setImageURI(uri) }
        }
    }

    private fun setupVideoPlayer(uri: Uri) {
        val player = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
        exoPlayer = player
        binding.videoPlayer.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = player.duration.toInt().coerceAtLeast(0)
                    binding.seekVideoProgress.max = duration
                    binding.tvTotalTime.text = formatMs(duration)
                    binding.tvCurrentTime.text = formatMs(0)
                    binding.ivPlayPauseOverlay.visibility = View.VISIBLE
                    binding.btnPlayPause.setImageResource(com.example.blurface.R.drawable.ic_play)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(requireContext(), "Could not play video", Toast.LENGTH_SHORT).show()
            }
        })

        binding.ivPlayPauseOverlay.setOnClickListener { togglePlayback() }
        binding.btnPlayPause.setOnClickListener { togglePlayback() }
        binding.cardMediaViewport.setOnClickListener { togglePlayback() }

        binding.seekVideoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

        progressHandler.post(progressTick)
    }

    private fun togglePlayback() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            binding.ivPlayPauseOverlay.setImageResource(com.example.blurface.R.drawable.ic_play)
            binding.btnPlayPause.setImageResource(com.example.blurface.R.drawable.ic_play)
            binding.ivPlayPauseOverlay.visibility = View.VISIBLE
        } else {
            player.play()
            binding.ivPlayPauseOverlay.setImageResource(com.example.blurface.R.drawable.ic_pause)
            binding.btnPlayPause.setImageResource(com.example.blurface.R.drawable.ic_pause)
            binding.ivPlayPauseOverlay.visibility = View.GONE
        }
    }

    private fun setupActions() {
        val dummyEdit = RecentEdit(
            id = mediaUriString,
            title = title,
            editType = when (editTypeLabel) {
                "Blur Background" -> EditType.BLUR_BACKGROUND
                "Video" -> EditType.VIDEO
                else -> EditType.BLUR_FACES
            },
            mediaUri = mediaUriString,
            isVideo = isVideo,
            timestampMillis = timestampMillis,
            fileSizeBytes = fileSizeBytes
        )

        binding.btnDownload.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        RecentEditActionsHelper.copyToDownloads(requireContext(), dummyEdit)
                    }.getOrNull()
                }
                val msg = if (saved != null) "Saved to Downloads" else "Could not save media"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShare.setOnClickListener {
            RecentEditActionsHelper.share(requireContext(), dummyEdit)
        }
    }

    private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressTick)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroyView()
        _binding = null
    }
}
