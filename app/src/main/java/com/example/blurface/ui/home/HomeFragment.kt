package com.example.blurface.ui.home

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.blurface.R
import com.example.blurface.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { navigateToDetectingFaces(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSelectPhoto.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.cardBlurBackground.setOnClickListener {
            findNavController().navigate(R.id.videoEditorFragment)
        }

        // TODO: wire up Quick Action cards (cardBlurFaces, cardBlurBackground,
        // cardPixelateFaces, cardEmojiCover) once their destination screens exist.
        // TODO: wire up rvRecentEdits adapter once we have a history data source.
    }

    private fun navigateToDetectingFaces(uri: Uri) {
        findNavController().navigate(
            R.id.detectingFacesFragment,
            bundleOf("imageUri" to uri.toString())
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}