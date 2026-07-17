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

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnContinue.setOnClickListener { findNavController().navigate(R.id.blurEditorFragment) }
        binding.btnClearAll.setOnClickListener { sharedViewModel.setAllSelected(false) }
        binding.btnAddFace.setOnClickListener {
            sharedViewModel.addBackNextFace()
        }

        setUpBrushModeButtons()
        setUpBrushSizeSlider()
        setUpSelectedFacesStrip()

        val bitmap = sharedViewModel.sourceBitmap.value
        if (bitmap == null) {
            // Shouldn't happen in the normal flow (Select Faces always sets it first)
            findNavController().navigateUp()
            return
        }

        binding.ivPreview.setImageBitmap(bitmap)
        binding.ivPreview.post {
            setUpFitTransform(bitmap.width, bitmap.height)
            binding.brushMask.initLayers(bitmap.width, bitmap.height, sharedViewModel.brushMask.value)
            isLayoutReady = true
            observeFaces(bitmap)
        }

        binding.brushMask.onMaskStrokeFinished = { maskCopy ->
            sharedViewModel.setBrushMask(maskCopy)
        }
    }

    private fun setUpFitTransform(bitmapWidth: Int, bitmapHeight: Int) {
        val viewWidth = binding.ivPreview.width.toFloat()
        val viewHeight = binding.ivPreview.height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return

        val scale = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val dx = (viewWidth - bitmapWidth * scale) / 2f
        val dy = (viewHeight - bitmapHeight * scale) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }

        binding.ivPreview.imageMatrix = matrix
        binding.brushMask.updateTransform(matrix)
    }

    private fun setUpBrushModeButtons() {
        binding.btnAddMode.isSelected = true // paint is the default tool
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
        binding.sliderBrushSize.value = 60f
        binding.tvBrushSizeValue.text = "60%"
        // Slider is a 10-100 "percent-ish" control; scale it into an actual bitmap-pixel
        // radius so brush size feels consistent across different photo resolutions.
        binding.brushMask.brushRadiusBitmapPx = 60f * 3f

        binding.sliderBrushSize.addOnChangeListener { _, value, _ ->
            binding.tvBrushSizeValue.text = "${value.toInt()}%"
            binding.brushMask.brushRadiusBitmapPx = value * 3f
        }
    }

    private fun setUpSelectedFacesStrip() {
        chipAdapter = SelectedFaceChipAdapter(
            onRemove = { faceId -> sharedViewModel.toggleFaceSelection(faceId) }
        )
        binding.rvSelectedFaces.adapter = chipAdapter
    }

    private fun observeFaces(bitmap: android.graphics.Bitmap) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.faces.collect { faces -> renderFaces(faces, bitmap) }
            }
        }
    }

    private suspend fun renderFaces(faces: List<DetectedFace>, bitmap: android.graphics.Bitmap) {
        if (!isLayoutReady) return

        binding.faceHighlight.setFaces(faces, bitmap.width, bitmap.height)

        val selected = faces.filter { it.isSelected }
        binding.tvSelectedCount.text = getString(R.string.selected_faces_count, selected.size)

        // Nothing left to bring back once every detected face is already selected
        val hasRemovedFaces = faces.any { !it.isSelected }
        binding.btnAddFace.isEnabled = hasRemovedFaces
        binding.btnAddFace.alpha = if (hasRemovedFaces) 1f else 0.4f

        val items = withContext(Dispatchers.Default) {
            selected.map { face ->
                SelectedFaceChipAdapter.Item(
                    faceId = face.id,
                    thumbnail = BitmapUtils.cropFace(bitmap, face.boundingBox)
                )
            }
        }
        chipAdapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}