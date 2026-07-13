package com.example.blurface.ui.video.analyzingvideo

import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.databinding.FragmentAnalyzingVideoBinding
import com.example.blurface.domain.repository.VideoRepositoryImpl
import com.example.blurface.domain.usecase.ProcessAndClusterVideoUseCase
import com.example.blurface.ui.viewmodel.FaceClusterViewModel
import com.example.blurface.ui.viewmodel.FaceUiState
import com.example.blurface.utils.FilePathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat

class AnalyzingVideoFragment : Fragment() {

    private var _binding: FragmentAnalyzingVideoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FaceClusterViewModel by navGraphViewModels(R.id.nav_graph) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val videoRepository = VideoRepositoryImpl(requireContext().applicationContext)
                val useCase = ProcessAndClusterVideoUseCase(videoRepository)
                return FaceClusterViewModel(useCase) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyzingVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        val videoUriString = arguments?.getString("videoUri")
        val videoUri = videoUriString?.let { Uri.parse(it) }
        if (videoUri == null) {
            findNavController().navigateUp()
            return
        }

        loadThumbnail(videoUri)
        observeState()
        startPipeline(videoUri)
    }

    private fun startPipeline(videoUri: Uri) {
        val context = requireContext()
        val inputPath = FilePathUtil.getActualPath(context, videoUri)
        if (inputPath == null) {
            Toast.makeText(context, "Could not resolve file pathway location.", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        val workspaceCacheDir = File(context.cacheDir, "blurshield_frame_cache").absolutePath
        viewModel.runFacePipeline(inputPath, workspaceCacheDir, fps = 10)
    }

    private fun loadThumbnail(videoUri: Uri) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val thumbnail = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, videoUri)
                    val frame = retriever.getFrameAtTime(0)
                    retriever.release()
                    frame
                }.getOrNull()
            }
            thumbnail?.let { binding.ivVideoThumbnail.setImageBitmap(it) }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is FaceUiState.Loading -> renderLoading(state)
                        is FaceUiState.Success -> findNavController().navigate(R.id.detectedFacesFragment)
                        is FaceUiState.Error -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            findNavController().navigateUp()
                        }
                        is FaceUiState.Idle -> Unit
                    }
                }
            }
        }
    }

    private fun renderLoading(state: FaceUiState.Loading) {
        binding.tvProgressPercent.text = "${state.progress}%"
        binding.progressAnalyzing.setProgressCompat(state.progress, true)

        if (state.totalFrames > 0) {
            binding.tvFrameProgress.text =
                "Analyzing frame ${format(state.currentFrame)} of ${format(state.totalFrames)}"
            binding.tvFramesProcessedValue.text = format(state.currentFrame)
        } else {
            binding.tvFrameProgress.text = state.logMessage
        }
    }

    private fun format(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}