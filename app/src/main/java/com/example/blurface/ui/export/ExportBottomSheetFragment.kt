package com.example.blurface.ui.export

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.blurface.R
import com.example.blurface.data.history.RecentEditsStore
import com.example.blurface.databinding.FragmentExportBottomSheetBinding
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.ExportFormat
import com.example.blurface.domain.model.RecentEdit
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import com.example.blurface.ui.viewmodel.SaveState
import com.example.blurface.utils.MediaSizeUtils
import kotlinx.coroutines.launch

class ExportBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentExportBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    private var selectedFormat: ExportFormat = ExportFormat.JPEG_COMPRESSED

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sharedViewModel.saveExport(selectedFormat)
        } else {
            Toast.makeText(requireContext(), R.string.storage_permission_needed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateFormatSelection()

        binding.rowJpegCompressed.setOnClickListener {
            selectedFormat = ExportFormat.JPEG_COMPRESSED
            updateFormatSelection()
        }
        binding.rowJpegLossless.setOnClickListener {
            selectedFormat = ExportFormat.JPEG_LOSSLESS
            updateFormatSelection()
        }
        binding.rowPng.setOnClickListener {
            selectedFormat = ExportFormat.PNG
            updateFormatSelection()
        }

        binding.btnSavePhoto.setOnClickListener { requestSave() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.saveState.collect { state -> handleSaveState(state) }
            }
        }
    }

    private fun updateFormatSelection() {
        binding.radioJpegCompressed.isChecked = selectedFormat == ExportFormat.JPEG_COMPRESSED
        binding.radioJpegLossless.isChecked = selectedFormat == ExportFormat.JPEG_LOSSLESS
        binding.radioPng.isChecked = selectedFormat == ExportFormat.PNG
    }

    private fun requestSave() {
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED

        if (needsLegacyPermission) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            sharedViewModel.saveExport(selectedFormat)
        }
    }

    private fun handleSaveState(state: SaveState) {
        when (state) {
            is SaveState.Saving -> binding.btnSavePhoto.isEnabled = false
            is SaveState.Success -> {
                binding.btnSavePhoto.isEnabled = true
                Toast.makeText(requireContext(), R.string.saved_to_gallery, Toast.LENGTH_SHORT).show()
                recordRecentEdit(state.uri)
                sharedViewModel.resetSaveState()
                dismiss()
            }
            is SaveState.Error -> {
                binding.btnSavePhoto.isEnabled = true
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                sharedViewModel.resetSaveState()
            }
            is SaveState.Idle -> binding.btnSavePhoto.isEnabled = true
        }
    }
    private fun recordRecentEdit(uri: android.net.Uri) {
        val context = requireContext()
        RecentEditsStore(context).add(
            RecentEdit(
                id = uri.toString(),
                title = "Photo Edit",
                editType = EditType.BLUR_FACES,
                mediaUri = uri.toString(),
                isVideo = false,
                timestampMillis = System.currentTimeMillis(),
                fileSizeBytes = MediaSizeUtils.getFileSizeBytes(context, uri)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}