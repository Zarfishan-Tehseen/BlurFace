package com.example.blurface.ui.video.detectedfaces

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.databinding.FragmentDetectedFacesBinding
import com.example.blurface.domain.model.Person
import com.example.blurface.domain.repository.VideoRepositoryImpl
import com.example.blurface.domain.usecase.ProcessAndClusterVideoUseCase
import com.example.blurface.ui.video.detectedfaces.PersonSelectionAdapter
import com.example.blurface.ui.viewmodel.FaceClusterViewModel
import com.example.blurface.ui.viewmodel.FaceUiState
import kotlinx.coroutines.launch

class DetectedFacesFragment : Fragment() {

    private var _binding: FragmentDetectedFacesBinding? = null
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

    private lateinit var adapter: PersonSelectionAdapter
    private var currentPeople: List<Person> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetectedFacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnBack) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        adapter = PersonSelectionAdapter(
            fps = viewModel.fps,
            onToggle = { updateSelectAllState() },
            onRowClicked = { /* TODO: tap-to-preview a person's occurrences */ }
        )
        binding.rvFaces.adapter = adapter

        binding.ivSelectAll.setOnClickListener {
            val newState = !isAllSelected()
            currentPeople.forEach { it.shouldBlur = newState }
            adapter.notifyDataSetChanged()
            updateSelectAllState()
        }

        // Pops back to AnalyzingVideoFragment, whose onViewCreated re-runs the
        // pipeline with the same videoUri arg since the fragment instance
        // (and its arguments) are preserved on the back stack.
        binding.btnDetectAgain.setOnClickListener { findNavController().navigateUp() }

        binding.btnContinue.setOnClickListener {
            // currentPeople.filter { it.shouldBlur } lives in the shared
            // FaceClusterViewModel already (same Person instances, mutable
            // shouldBlur flag) - VideoBlurEditorFragment reads it back out
            // via viewModel.selectedPeopleForBlur().
            findNavController().navigate(R.id.videoBlurEditorFragment)
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is FaceUiState.Success -> {
                            currentPeople = state.clusteredPeople
                            adapter.submitList(currentPeople)
                            binding.tvSubtitle.text =
                                "We found ${currentPeople.size} ${if (currentPeople.size == 1) "person" else "people"} in this video."
                            updateSelectAllState()
                        }
                        // Shouldn't normally land here without a completed run
                        // (e.g. process death mid-flow) - bail back safely.
                        is FaceUiState.Idle, is FaceUiState.Error -> findNavController().navigateUp()
                        is FaceUiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun isAllSelected(): Boolean =
        currentPeople.isNotEmpty() && currentPeople.all { it.shouldBlur }

    private fun updateSelectAllState() {
        binding.ivSelectAll.alpha = if (isAllSelected()) 1f else 0.35f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}