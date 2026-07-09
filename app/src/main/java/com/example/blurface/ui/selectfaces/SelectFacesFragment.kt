package com.example.blurface.ui.selectfaces

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.databinding.FragmentSelectFacesBinding
import com.example.blurface.domain.model.DetectedFace
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import com.example.blurface.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectFacesFragment : Fragment() {

    private var _binding: FragmentSelectFacesBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    private lateinit var adapter: DetectedFaceAdapter
    private var sourceBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectFacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        adapter = DetectedFaceAdapter(
            onFaceClicked = { /* TODO: tap-to-preview - zoom into that face */ },
            onCheckboxClicked = { face -> sharedViewModel.toggleFaceSelection(face.id) }
        )
        binding.rvFaces.adapter = adapter

        binding.btnSelectAll.setOnClickListener {
            val allSelected = sharedViewModel.faces.value.all { it.isSelected }
            sharedViewModel.setAllSelected(!allSelected)
        }

        binding.btnContinue.setOnClickListener {
            findNavController().navigate(R.id.blurEditorFragment)
        }

        binding.btnManualSelect.setOnClickListener {
            // TODO: hook up manual brush-mask selection (see prototype's BrushMaskView)
        }

        val imageUri = sharedViewModel.imageUri
        if (imageUri != null) {
            binding.ivPreview.setImageURI(imageUri)
            loadBitmapAndObserveFaces(imageUri)
        }
    }

    private fun loadBitmapAndObserveFaces(imageUri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            sourceBitmap = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
            // Share the decoded bitmap with the ViewModel so downstream screens
            // (Blur Editor) don't need to re-decode it from the URI.
            sourceBitmap?.let { sharedViewModel.setSourceBitmap(it) }

            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.faces.collect { faces -> renderFaces(faces) }
            }
        }
    }

    private suspend fun renderFaces(faces: List<DetectedFace>) {
        val bitmap = sourceBitmap ?: return
        if (faces.isEmpty()) return

        binding.faceOverlay.setFaces(faces, bitmap.width, bitmap.height)
        binding.tvSubtitle.text = getString(R.string.found_faces_subtitle, faces.size)
        binding.tvDetectedCount.text = getString(R.string.detected_faces_count, faces.size)

        val allSelected = faces.all { it.isSelected }
        binding.ivSelectAllIcon.alpha = if (allSelected) 1f else 0.4f

        val items = withContext(Dispatchers.Default) {
            faces.map { face ->
                DetectedFaceAdapter.Item(
                    face = face,
                    thumbnail = BitmapUtils.cropFace(bitmap, face.boundingBox)
                )
            }
        }
        adapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}