package com.example.blurface.ui.video

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.blurface.databinding.FragmentVideoAnalysisBinding
import com.example.blurface.ui.video.PersonClusterAdapter
import com.example.blurface.ui.viewmodel.FaceClusterViewModel
import com.example.blurface.ui.viewmodel.FaceUiState
import com.example.blurface.utils.FilePathUtil
import kotlinx.coroutines.launch
import java.io.File

class VideoAnalysisFragment : Fragment() {

    private var _binding: FragmentVideoAnalysisBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FaceClusterViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val videoRepository =
                    com.example.blurface.domain.repository.VideoRepositoryImpl(requireContext().applicationContext)
                val useCase = com.example.blurface.domain.usecase.ProcessAndClusterVideoUseCase(
                    videoRepository
                )
                return FaceClusterViewModel(useCase) as T
            }
        }
    }
    private lateinit var clusterAdapter: PersonClusterAdapter

    // Modern Activity Results Picker Contract
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedVideo(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, viewTheme: Bundle?) {
        super.onViewCreated(view, viewTheme)

        setupRecyclerView()
        setupListeners()
        observeViewModelState()
    }

    private fun setupRecyclerView() {
        clusterAdapter = PersonClusterAdapter()
        binding.rvFaceClusters.apply {
            // Displays extracted identities in a responsive 3-column structural layout grid
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = clusterAdapter
        }
    }

    private fun setupListeners() {
        binding.btnSelectVideo.setOnClickListener {
            // Limit content picking parameters cleanly to video targets only
            videoPickerLauncher.launch("video/*")
        }
    }

    private fun handleSelectedVideo(videoUri: Uri) {
        val context = requireContext()

        // Resolve absolute hardware filesystem references for FrameExtractor safely
        val inputPath = FilePathUtil.getActualPath(context, videoUri)
        if (inputPath == null) {
            Toast.makeText(context, "Could not resolve file pathway location.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Establish a localized workspace cache layout directory
        val workspaceCacheDir = File(context.cacheDir, "blurshield_frame_cache").absolutePath

        // Run your Clean Architecture execution boundary
        viewModel.runFacePipeline(inputPath, workspaceCacheDir)
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    when (uiState) {
                        is FaceUiState.Idle -> {
                            binding.layoutLoading.visibility = View.GONE
                        }

                        is FaceUiState.Loading -> {
                            binding.layoutLoading.visibility = View.VISIBLE
                            binding.progressBar.progress = uiState.progress

                            if (uiState.totalFrames > 0) {
                                binding.tvStatusMessage.text = "${uiState.logMessage} (${uiState.currentFrame}/${uiState.totalFrames})"
                            } else {
                                binding.tvStatusMessage.text = uiState.logMessage
                            }
                        }
                        is FaceUiState.Success -> {
                            binding.layoutLoading.visibility = View.GONE
                            clusterAdapter.setIdentities(uiState.clusteredPeople)
                            if (uiState.clusteredPeople.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "No stable face tracks detected.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        is FaceUiState.Error -> {
                            binding.layoutLoading.visibility = View.GONE
                            Toast.makeText(context, "Error: ${uiState.message}", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}