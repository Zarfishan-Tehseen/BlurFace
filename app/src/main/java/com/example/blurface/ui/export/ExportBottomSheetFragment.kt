package com.example.blurface.ui.export

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    override fun getTheme(): Int = R.style.TransparentBottomSheetDialogTheme

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
        dialog?.window?.let { dialogWindow ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            dialogWindow.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

            dialogWindow.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dialogWindow.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dialogWindow.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }

            val controller = androidx.core.view.WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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

    // 🎯 DYNAMIC WINDOW INSETS FIX: Removes gray bands and locks sheet dimensions
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog

            // 1. Get the topmost parent container and clear its default padding
            val parentContainer = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.container
            )
            parentContainer?.fitsSystemWindows = false
            parentContainer?.setPadding(0, 0, 0, 0) // <-- Clear the pre-applied padding

            // 2. Intercept system insets and set the bottom navigation bar height to 0
            parentContainer?.let { container ->
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                    val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    view.setPadding(0, 0, 0, 0) // Ensure no padding is applied to container

                    // Pass modified insets (with bottom navigation bar inset set to 0) to child views
                    androidx.core.view.WindowInsetsCompat.Builder(insets)
                        .setInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                            androidx.core.graphics.Insets.of(systemBars.left, systemBars.top, systemBars.right, 0)
                        )
                        .build()
                }
            }

            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) as FrameLayout?

            bottomSheet?.let { container ->
                val behavior = BottomSheetBehavior.from(container)

                container.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                container.fitsSystemWindows = false
                container.setPadding(0, 0, 0, 0) // <-- Clear padding on bottom sheet view too

                val params = container.layoutParams
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                container.layoutParams = params

                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true

                (container.parent as? View)?.let { coordinatorLayout ->
                    coordinatorLayout.fitsSystemWindows = false
                    coordinatorLayout.setPadding(0, 0, 0, 0) // <-- Clear padding on coordinator
                    coordinatorLayout.requestLayout()
                }

                container.requestLayout()
            }

            hideDialogSystemUI(bottomSheetDialog)
        }
        return dialog
    }
    private fun hideDialogSystemUI(dialog: Dialog) {
        val window = dialog.window ?: return

        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
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

        val isBackgroundBlur = sharedViewModel.isExternalExport
        RecentEditsStore(context).add(
            RecentEdit(
                id = uri.toString(),
                title = if (isBackgroundBlur) "Background Blur" else "Photo Edit",
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