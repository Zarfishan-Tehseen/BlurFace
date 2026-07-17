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

        val imageUriString = arguments?.getString("imageUri")
        val imageUri = imageUriString?.let { Uri.parse(it) }

        if (imageUri == null) {
            findNavController().navigateUp()
            return
        }

        binding.ivPreview.setImageURI(imageUri)

        startClimbingProgress()
        observeDetectionState()

        sharedViewModel.detectFaces(imageUri)
    }

    private fun startClimbingProgress() {
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

    private fun observeDetectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.detectionState.collect { state ->
                    when (state) {
                        is DetectionState.Success -> onDetectionFinished()
                        is DetectionState.Error -> onDetectionFailed(state.message)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun onDetectionFinished() {
        progressAnimator?.cancel()
        binding.tvPercentage.text = "100%"
        binding.progressBar.setProgressCompat(100, true)

        findNavController().navigate(R.id.selectFacesFragment)
    }

    private fun onDetectionFailed(message: String) {
        progressAnimator?.cancel()
        // TODO: show a proper error state instead of just going back
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressAnimator?.cancel()
        progressAnimator = null
        _binding = null
    }
}