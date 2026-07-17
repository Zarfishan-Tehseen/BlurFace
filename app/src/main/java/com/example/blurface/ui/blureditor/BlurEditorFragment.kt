package com.example.blurface.ui.blureditor

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
import com.example.blurface.databinding.FragmentBlurEditorBinding
import com.example.blurface.domain.model.FaceEffect
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import com.example.blurface.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.flow.combine

class BlurEditorFragment : Fragment() {

    private var _binding: FragmentBlurEditorBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    private lateinit var chipAdapter: SelectedFaceChipAdapter
    private var isFirstBitmapRender = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlurEditorBinding.inflate(inflater, container, false)
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
        binding.btnExport.setOnClickListener {
            com.example.blurface.ui.export.ExportBottomSheetFragment()
                .show(childFragmentManager, "export_sheet")        }

        setUpEffectChips()
        setUpIntensitySlider()
        setUpSelectedFacesStrip()
        setUpColorPalette()
        setUpEmojiPalette()
        observeViewModel()
    }

    private fun setUpEffectChips() {
        val chips = mapOf(
            binding.chipGaussian to FaceEffect.BLUR,
            binding.chipMosaic to FaceEffect.PIXELATE,
            binding.chipColor to FaceEffect.COLOR,
            binding.chipEmoji to FaceEffect.EMOJI
        )
        chips.forEach { (view, effect) ->
            view.setOnClickListener { sharedViewModel.setEffect(effect) }
        }
    }

    private fun updateChipSelection(selected: FaceEffect) {
        binding.chipGaussian.isSelected = selected == FaceEffect.BLUR
        binding.chipMosaic.isSelected = selected == FaceEffect.PIXELATE
        binding.chipColor.isSelected = selected == FaceEffect.COLOR
        binding.chipEmoji.isSelected = selected == FaceEffect.EMOJI

        val isColorSelected = selected == FaceEffect.COLOR
        binding.viewColors.visibility = if (isColorSelected) View.VISIBLE else View.GONE
        binding.layoutColorPalette.visibility = if (isColorSelected) View.VISIBLE else View.GONE

        val isEmojiSelected = selected == FaceEffect.EMOJI
        binding.viewEmoji.visibility = if (isEmojiSelected) View.VISIBLE else View.GONE
        binding.layoutEmojiPalette.visibility = if (isEmojiSelected) View.VISIBLE else View.GONE
    }

    private fun setUpIntensitySlider() {
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            binding.tvIntensityValue.text = "${value.toInt()}%"
            if (fromUser) sharedViewModel.setIntensity(value)
        }
        binding.btnReset.setOnClickListener { sharedViewModel.resetIntensity() }
    }
    private fun setUpColorPalette() {
        binding.colorBlack.setOnClickListener { sharedViewModel.setSelectedColor(Color.BLACK) }
        binding.colorPurple.setOnClickListener { sharedViewModel.setSelectedColor(Color.parseColor("#800080")) }
        binding.colorBlue.setOnClickListener { sharedViewModel.setSelectedColor(Color.BLUE) }
        binding.colorRed.setOnClickListener { sharedViewModel.setSelectedColor(Color.RED) }
        binding.colorYellow.setOnClickListener { sharedViewModel.setSelectedColor(Color.YELLOW) }
        binding.colorWhite.setOnClickListener { sharedViewModel.setSelectedColor(Color.WHITE) }
        binding.colorGreen.setOnClickListener { sharedViewModel.setSelectedColor(Color.GREEN) }
    }

    private fun setUpSelectedFacesStrip() {
        chipAdapter = SelectedFaceChipAdapter(
            onRemove = { faceId -> sharedViewModel.toggleFaceSelection(faceId) }
        )
        binding.rvSelectedFaces.adapter = chipAdapter

        binding.btnClearAll.setOnClickListener { sharedViewModel.setAllSelected(false) }
    }
    private fun setUpEmojiPalette() {
        binding.emojiSmile.setOnClickListener { sharedViewModel.setEmoji("😀") }
        binding.emojiLaugh.setOnClickListener { sharedViewModel.setEmoji("😂") }
        binding.emojiHeartEyes.setOnClickListener { sharedViewModel.setEmoji("😍") }
        binding.emojiCool.setOnClickListener { sharedViewModel.setEmoji("😎") }
        binding.emojiScared.setOnClickListener { sharedViewModel.setEmoji("😱") }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedViewModel.currentEffect.collect { updateChipSelection(it) }
                }
                launch {
                    sharedViewModel.intensityPercent.collect { percent ->
                        binding.tvIntensityValue.text = "${percent.toInt()}%"
                        if (binding.sliderIntensity.value != percent) {
                            binding.sliderIntensity.value = percent
                        }
                    }
                }
                launch {
                    sharedViewModel.editedBitmap.collect { bitmap ->
                        bitmap ?: return@collect
                        if (isFirstBitmapRender) {
                            binding.zoomableImage.setImageBitmap(bitmap)
                            isFirstBitmapRender = false
                        } else {
                            binding.zoomableImage.updateImagePreservingMatrix(bitmap)
                        }
                    }
                }
                launch {
                    sharedViewModel.faces.collect { faces ->
                        val selected = faces.filter { it.isSelected }
                        binding.tvSelectedCount.text =
                            getString(R.string.selected_faces_count, selected.size)

                        val bitmap = sharedViewModel.sourceBitmap.value ?: return@collect
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
                }
                launch {
                    sharedViewModel.faces.collect { faces ->
                        binding.zoomableImage.setFaceOverlays(faces)
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