package com.example.blurface.ui.video.exportprocess

import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.databinding.FragmentExportProcessBinding
import com.example.blurface.domain.model.VideoResolution
import com.example.blurface.ui.viewmodel.FaceClusterViewModel
import com.webscare.prescriptionscanner.common.Utils.addPressEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ExportProcessFragment : Fragment() {

    private var _binding: FragmentExportProcessBinding? = null
    private val binding get() = _binding!!

    private val exportViewModel: ExportProcessViewModel by viewModels()

    // Only used to read the resolved video path for the duration/size estimate -
    // reuses the same graph-scoped instance from the analysis/detection screens.
    private val faceClusterViewModel: FaceClusterViewModel by navGraphViewModels(R.id.nav_graph)

    private var videoDurationSeconds: Double = 0.0

    private val frameRateOptions = listOf(24, 30, 60)

    // Guards against navigating twice if the STARTED lifecycle re-collects
    // the same terminal ExportState.Done value (e.g. after a config change).
    private var hasNavigatedToResult = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportProcessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnBack.addPressEffect { findNavController().navigateUp() }

        setUpResolutionCards()
        setUpFrameRateRow()
        setUpFormatRow()

        binding.btnExport.addPressEffect {
            val path = faceClusterViewModel.videoPath
            if (path == null) {
                Toast.makeText(requireContext(), getString(R.string.no_source_video_found), Toast.LENGTH_SHORT).show()
                return@addPressEffect
            }
            exportViewModel.startExport(
                context = requireContext(),
                sourcePath = path,
                people = faceClusterViewModel.selectedPeopleForBlur(),
                blurSettings = faceClusterViewModel.blurSettings.value,
                analysisFps = faceClusterViewModel.fps
            )
        }

        loadVideoDuration()
        observeViewModel()
    }

    private fun setUpResolutionCards() {
        val cards = mapOf(
            binding.card720p to VideoResolution.HD_720,
            binding.card1080p to VideoResolution.FULL_HD_1080,
            binding.card4k to VideoResolution.UHD_4K
        )
        cards.forEach { (view, resolution) ->
            view.addPressEffect {
                exportViewModel.setResolution(resolution)
                updateSizeEstimate()
            }
        }
    }

    private fun updateResolutionSelection(selected: VideoResolution) {
        binding.card720p.isSelected = selected == VideoResolution.HD_720
        binding.card1080p.isSelected = selected == VideoResolution.FULL_HD_1080
        binding.card4k.isSelected = selected == VideoResolution.UHD_4K
    }

    private fun setUpFrameRateRow() {
        binding.rowFrameRate.addPressEffect {
            val current = exportViewModel.frameRate.value
            val currentIndex = frameRateOptions.indexOf(current).coerceAtLeast(0)
            val nextFps = frameRateOptions[(currentIndex + 1) % frameRateOptions.size]
            exportViewModel.setFrameRate(nextFps)
            updateSizeEstimate()
        }
    }

    private fun setUpFormatRow() {
        binding.rowFormat.addPressEffect {
            Toast.makeText(
                requireContext(),
                getString(R.string.format_mp4_only),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadVideoDuration() {
        val path = faceClusterViewModel.videoPath
        if (path == null) {
            updateSizeEstimate()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            videoDurationSeconds = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    retriever.release()
                    durationMs / 1000.0
                }.getOrDefault(0.0)
            }
            updateSizeEstimate()
        }
    }

    private fun updateSizeEstimate() {
        val sizeMb = exportViewModel.estimatedSizeMb(videoDurationSeconds)
        binding.tvEstimatedSize.text = if (sizeMb > 0) {
            String.format(Locale.US, "~ %.0f MB", sizeMb)
        } else {
            "—"
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    exportViewModel.resolution.collect { resolution ->
                        updateResolutionSelection(resolution)
                        updateSizeEstimate()
                    }
                }
                launch {
                    exportViewModel.frameRate.collect { fps ->
                        binding.tvFrameRateValue.text = getString(R.string.fps_unit, fps)
                        updateSizeEstimate()
                    }
                }
                launch {
                    exportViewModel.exportState.collect { state -> renderExportState(state) }
                }
            }
        }
    }

    private fun renderExportState(state: ExportState) {
        when (state) {
            is ExportState.Idle -> {
                binding.exportProgressRing.progress = 0
                binding.tvExportPercent.text = "0%"
                binding.tvExportStatus.text = getString(R.string.ready_to_export)
                binding.btnExport.isEnabled = true
            }
            is ExportState.Exporting -> {
                binding.exportProgressRing.setProgressCompat(state.progress, true)
                binding.tvExportPercent.text = "${state.progress}%"
                binding.tvExportStatus.text = getString(R.string.exporting_status)
                binding.btnExport.isEnabled = false
            }
            is ExportState.Done -> {
                binding.exportProgressRing.setProgressCompat(100, true)
                binding.tvExportPercent.text = "100%"
                binding.tvExportStatus.text = getString(R.string.status_done)
                binding.btnExport.isEnabled = true

                if (!hasNavigatedToResult) {
                    hasNavigatedToResult = true
                    faceClusterViewModel.exportedVideoPath = exportViewModel.outputPath
                    faceClusterViewModel.exportedVideoUri = exportViewModel.savedGalleryUri
                    findNavController().navigate(R.id.blurredVideoResultFragment)
                }
            }
            is ExportState.Error -> {
                binding.tvExportStatus.text = getString(R.string.status_failed)
                binding.btnExport.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}