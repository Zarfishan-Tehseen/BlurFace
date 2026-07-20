package com.example.blurface.ui.manualselection

import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.blurface.databinding.FragmentManualSelectionBinding
import com.example.blurface.domain.model.DetectedFace
import com.example.blurface.ui.blureditor.SelectedFaceChipAdapter
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import com.example.blurface.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.SeekBar
import kotlinx.coroutines.withContext

class ManualSelectionFragment : Fragment() {

    private var _binding: FragmentManualSelectionBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    private lateinit var chipAdapter: SelectedFaceChipAdapter
    private var isLayoutReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Standard Button Actions
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnContinue.setOnClickListener { findNavController().navigate(R.id.blurEditorFragment) }
        binding.btnClearAll.setOnClickListener { sharedViewModel.setAllSelected(false) }
        binding.btnAddFace.setOnClickListener { sharedViewModel.addBackNextFace() }

        // Mode and UI Component Initializers
        setUpBrushModeButtons()
        setUpBrushSizeSlider()
        setUpSelectedFacesStrip()

        // Link the layers together so they can talk to each other
        binding.faceHighlight.brushMaskView = binding.brushMask

        // Setup Box interaction callbacks on our Upgraded Interactive View
        binding.faceHighlight.onFaceTapped = { faceId ->
            sharedViewModel.toggleFaceSelection(faceId)
        }

        binding.faceHighlight.onFaceBoundsChanged = { faceId, newBounds ->
            sharedViewModel.updateFaceBounds(faceId, newBounds)
        }

        binding.faceHighlight.onMatrixChanged = { updatedMatrix ->
            // Keeps the background image tracked with the zoom/pan matrix transformations
            binding.ivPreview.imageMatrix = updatedMatrix
        }

        // ADDED LOGIC: Toggles the brush mode active state on/off when tapping the brush pill
        binding.btnBrush.setOnClickListener {
            val currentMode = sharedViewModel.isBrushModeActive.value
            sharedViewModel.setBrushModeActive(!currentMode)
        }

        val bitmap = sharedViewModel.sourceBitmap.value
        if (bitmap == null) {
            findNavController().navigateUp()
            return
        }

        binding.ivPreview.setImageBitmap(bitmap)
        binding.ivPreview.post {
            binding.faceHighlight.initDefaultTransform(bitmap.width, bitmap.height)
            binding.brushMask.initLayers(bitmap.width, bitmap.height, sharedViewModel.brushMask.value)
            isLayoutReady = true
            observeStateFlows(bitmap)
        }

        binding.brushMask.onMaskStrokeFinished = { maskCopy ->
            sharedViewModel.setBrushMask(maskCopy)
        }
    }

    private fun observeStateFlows(bitmap: android.graphics.Bitmap) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Flow A: Collect face selections/modifications & update the box UI + thumbnails
                launch {
                    sharedViewModel.faces.collect { faces ->
                        if (!isLayoutReady) return@collect

                        binding.faceHighlight.setFaces(faces, bitmap.width, bitmap.height)

                        val selected = faces.filter { it.isSelected }
                        binding.tvSelectedCount.text = getString(R.string.selected_faces_count, selected.size)

                        val hasRemovedFaces = faces.any { !it.isSelected }
                        binding.btnAddFace.isEnabled = hasRemovedFaces
                        binding.btnAddFace.alpha = if (hasRemovedFaces) 1f else 0.4f

                        val items = withContext(Dispatchers.Default) {
                            selected.map { face ->
                                SelectedFaceChipAdapter.Item(
                                    faceId = face.id,
                                    thumbnail = com.example.blurface.utils.BitmapUtils.cropFace(bitmap, face.boundingBox)
                                )
                            }
                        }
                        chipAdapter.submitList(items)
                    }
                }

                // Flow B: Listen for Brush Mode toggle changes to enable/disable drawings vs box moves
                launch {
                    sharedViewModel.isBrushModeActive.collect { isBrushActive ->
                        binding.faceHighlight.isBrushModeEnabled = isBrushActive
                        binding.brushMask.isActive = isBrushActive   // NEW

                        // Updates the visual selected state of your brush toggle layout pill
                        binding.btnBrush.isSelected = isBrushActive
                    }
                }
            }
        }
    }

    private fun setUpBrushModeButtons() {
        binding.btnAddMode.isSelected = true
        binding.btnAddMode.setOnClickListener {
            binding.brushMask.currentTool = BrushTool.PAINT
            binding.btnAddMode.isSelected = true
            binding.btnRemoveMode.isSelected = false
        }
        binding.btnRemoveMode.setOnClickListener {
            binding.brushMask.currentTool = BrushTool.ERASE
            binding.btnAddMode.isSelected = false
            binding.btnRemoveMode.isSelected = true
        }
    }

    private fun setUpBrushSizeSlider() {
        val initialValue = 60
        binding.sliderBrushSize.max = 90
        binding.sliderBrushSize.progress = initialValue - 10
        binding.tvBrushSizeValue.text = "${initialValue}%"
        binding.brushMask.brushRadiusBitmapPx = initialValue * 3f

        binding.sliderBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 10
                binding.tvBrushSizeValue.text = "${value}%"
                binding.brushMask.brushRadiusBitmapPx = value * 3f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setUpSelectedFacesStrip() {
        chipAdapter = SelectedFaceChipAdapter(
        onRemove = { faceId -> sharedViewModel.toggleFaceSelection(faceId) }
        )
        binding.rvSelectedFaces.adapter = chipAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}