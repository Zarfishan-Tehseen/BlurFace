package com.example.blurface.ui.detecting

import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.databinding.FragmentDetectingFacesBinding
import com.example.blurface.ui.viewmodel.DetectionState
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import kotlinx.coroutines.launch

class DetectingFacesFragment : Fragment() {

    private var _binding: FragmentDetectingFacesBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    private var progressAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetectingFacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnTryAgain.setOnClickListener {
            sharedViewModel.retryDetection()
        }
        binding.actionSelectManually.setOnClickListener {
            // TODO: navigate to the manual face-selection screen once it exists.
        }
        binding.actionChooseAnotherPhoto.setOnClickListener {
            // Re-uses whatever picker flow got the user here in the first place.
            findNavController().navigateUp()
        }

        val imageUriString = arguments?.getString("imageUri")
        val imageUri = imageUriString?.let { Uri.parse(it) }

        if (imageUri == null) {
            findNavController().navigateUp()
            return
        }

        binding.ivPreview.setImageURI(imageUri)

        observeDetectionState()

        sharedViewModel.detectFaces(imageUri)
    }

    private fun observeDetectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.detectionState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: DetectionState) {
        when (state) {
            is DetectionState.Idle -> Unit
            is DetectionState.Detecting -> onDetectingStarted()
            is DetectionState.Success -> onDetectionFinished()
            is DetectionState.NoFacesFound -> onNoFacesFound()
            is DetectionState.Error -> onDetectionFailed(state.message)
        }
    }

    /** Also the state Try Again returns to, so this has to reset everything it touches. */
    private fun onDetectingStarted() {
        binding.groupNoFaceOverlay.visibility = View.GONE
        binding.actionSelectManually.visibility = View.GONE
        binding.actionChooseAnotherPhoto.visibility = View.GONE

        binding.scanningIndicatorGroup.visibility = View.VISIBLE
        binding.groupScanningProgress.visibility = View.VISIBLE

        startClimbingProgress()
    }

    private fun startClimbingProgress() {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofInt(0, 90).apply {
            duration = 3000
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.tvPercentage.text = "$value%"
                binding.progressBar.setProgressCompat(value, true)
            }
            start()
        }
    }

    private fun onDetectionFinished() {
        progressAnimator?.cancel()
        binding.tvPercentage.text = "100%"
        binding.progressBar.setProgressCompat(100, true)

        findNavController().navigate(R.id.selectFacesFragment)
    }

    private fun onNoFacesFound() {
        progressAnimator?.cancel()
        binding.scanningIndicatorGroup.visibility = View.GONE
        binding.groupScanningProgress.visibility = View.GONE

        binding.groupNoFaceOverlay.visibility = View.VISIBLE
        binding.actionSelectManually.visibility = View.VISIBLE
        binding.actionChooseAnotherPhoto.visibility = View.VISIBLE
    }

    private fun onDetectionFailed(message: String) {
        progressAnimator?.cancel()
        // Genuine failures (bad file, ML Kit exception) still just bounce back - only
        // a *successful* detection that found zero faces gets the in-place UI above.
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressAnimator?.cancel()
        progressAnimator = null
        _binding = null
    }
}