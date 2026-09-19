package com.example.blurface.ui.recents

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.blurface.R
import com.example.blurface.databinding.FragmentMediaPreviewBinding
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webscare.prescriptionscanner.common.Utils.addPressEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class MediaPreviewFragment : Fragment() {

    private var _binding: FragmentMediaPreviewBinding? = null
    private val binding get() = _binding!!

    private val recentsViewModel: RecentsViewModel by activityViewModels()

    private var mediaUriString: String = ""
    private var isVideo: Boolean = false
    private var title: String = ""
    private var editTypeLabel: String = ""
    private var timestampMillis: Long = 0L
    private var fileSizeBytes: Long = 0L

    private var controlsVisible: Boolean = true

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

        // Apply window insets for edge-to-edge black canvas & status bar padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.topHeaderOverlay) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomActionOverlay) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }

        binding.btnBack.addPressEffect { findNavController().navigateUp() }
        binding.btnDetails.addPressEffect { showDetailsBottomSheet() }

        setupHeaderAndDetails()
        setupMediaView()
        setupGestureDetector()
        setupActions()
    }

    private fun setupHeaderAndDetails() {
        val dateFormatDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateFormatTime = SimpleDateFormat("h:mm a", Locale.getDefault())

        binding.tvHeaderTitle.text = dateFormatDate.format(timestampMillis)
        binding.tvHeaderSubtitle.text = dateFormatTime.format(timestampMillis)
    }

    private fun setupMediaView() {
        if (mediaUriString.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.media_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse(mediaUriString)

        if (isVideo) {
            binding.ivImagePreview.visibility = View.GONE
            binding.videoContainer.visibility = View.VISIBLE
            binding.videoControlsRow.visibility = View.VISIBLE
            setupVideoPlayer(uri)
        } else {
            binding.videoContainer.visibility = View.GONE
            binding.videoControlsRow.visibility = View.GONE
            binding.ivImagePreview.visibility = View.VISIBLE
            runCatching { binding.ivImagePreview.setImageURI(uri) }
        }
    }

    private fun setupVideoPlayer(uri: Uri) {
        binding.videoPlayer.setVideoURI(uri)

        binding.videoPlayer.setOnPreparedListener { player ->
            player.isLooping = true
            binding.seekVideoProgress.max = player.duration
            binding.tvTotalTime.text = formatMs(player.duration)
            binding.tvCurrentTime.text = formatMs(0)
            binding.ivPlayPauseOverlay.visibility = View.VISIBLE
            binding.btnPlayPause.setImageResource(com.example.blurface.R.drawable.ic_play)
        }

        binding.videoPlayer.setOnErrorListener { _, _, _ ->
            Toast.makeText(requireContext(), getString(R.string.could_not_play_video), Toast.LENGTH_SHORT).show()
            true
        }

        binding.ivPlayPauseOverlay.addPressEffect { togglePlayback() }
        binding.btnPlayPause.addPressEffect { togglePlayback() }

        binding.seekVideoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatMs(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                binding.videoPlayer.seekTo(seekBar.progress)
            }
        })

        progressHandler.post(progressTick)
    }

    private fun togglePlayback() {
        if (!isVideo) return
        if (binding.videoPlayer.isPlaying) {
            binding.videoPlayer.pause()
            binding.ivPlayPauseOverlay.setImageResource(com.example.blurface.R.drawable.ic_play)
            binding.btnPlayPause.setImageResource(com.example.blurface.R.drawable.ic_play)
            binding.ivPlayPauseOverlay.visibility = View.VISIBLE
        } else {
            binding.videoPlayer.start()
            binding.ivPlayPauseOverlay.setImageResource(com.example.blurface.R.drawable.ic_pause)
            binding.btnPlayPause.setImageResource(com.example.blurface.R.drawable.ic_pause)
            binding.ivPlayPauseOverlay.visibility = View.GONE
        }
    }

    private fun setupGestureDetector() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControlsVisibility()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && (e1.y - e2.y > 100) && abs(velocityY) > 200) {
                    showDetailsBottomSheet()
                    return true
                }
                return false
            }
        })

        binding.mediaCanvas.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun toggleControlsVisibility() {
        controlsVisible = !controlsVisible
        val targetAlpha = if (controlsVisible) 1f else 0f

        binding.topHeaderOverlay.animate()
            .alpha(targetAlpha)
            .setDuration(250)
            .withStartAction {
                if (controlsVisible) binding.topHeaderOverlay.visibility = View.VISIBLE
            }
            .withEndAction {
                if (!controlsVisible) binding.topHeaderOverlay.visibility = View.GONE
            }
            .start()

        binding.bottomActionOverlay.animate()
            .alpha(targetAlpha)
            .setDuration(250)
            .withStartAction {
                if (controlsVisible) binding.bottomActionOverlay.visibility = View.VISIBLE
            }
            .withEndAction {
                if (!controlsVisible) binding.bottomActionOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun showDetailsBottomSheet() {
        val bottomSheet = MediaDetailsBottomSheetFragment.newInstance(
            title = title,
            editType = editTypeLabel.ifEmpty { if (isVideo) "Video" else "Photo" },
            isVideo = isVideo,
            timestampMillis = timestampMillis,
            fileSizeBytes = fileSizeBytes,
            path = mediaUriString
        )
        bottomSheet.show(childFragmentManager, MediaDetailsBottomSheetFragment.TAG)
    }

    private fun setupActions() {
        val dummyEdit = createRecentEditModel()

        binding.actionShare.addPressEffect {
            RecentEditActionsHelper.share(requireContext(), dummyEdit)
        }

        binding.actionSave.addPressEffect {
            viewLifecycleOwner.lifecycleScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        RecentEditActionsHelper.copyToDownloads(requireContext(), dummyEdit)
                    }.getOrNull()
                }
                val msg = if (saved != null) getString(R.string.saved_to_downloads) else getString(R.string.could_not_download)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.actionDetails.addPressEffect {
            showDetailsBottomSheet()
        }

        binding.actionDelete.addPressEffect {
            showDeleteConfirmationDialog(requireContext()) {
                recentsViewModel.delete(dummyEdit)
                Toast.makeText(requireContext(), getString(R.string.status_deleted), Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun createRecentEditModel(): RecentEdit {
        return RecentEdit(
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
    }

    private fun formatMs(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressTick)
        if (isVideo) binding.videoPlayer.stopPlayback()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun showDeleteConfirmationDialog(context: Context, onConfirmDelete: () -> Unit) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Delete item?")
                .setMessage("Are you sure you want to delete this item? It will be removed from your recent edits.")
                .setPositiveButton("Delete") { _, _ -> onConfirmDelete() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
