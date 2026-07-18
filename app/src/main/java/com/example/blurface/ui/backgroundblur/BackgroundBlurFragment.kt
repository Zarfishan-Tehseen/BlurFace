package com.example.blurface.ui.backgroundblur

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.data.history.RecentEditsStore
import com.example.blurface.databinding.FragmentBackgroundBlurBinding
import com.example.blurface.domain.model.BackgroundBlurType
import com.example.blurface.domain.model.BackgroundFilter
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import com.example.blurface.utils.MediaSizeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackgroundBlurFragment : Fragment() {

    private var _binding: FragmentBackgroundBlurBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BackgroundBlurViewModel by viewModels()
    private val sharedPhotoViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackgroundBlurBinding.inflate(inflater, container, false)
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
        binding.btnExport.setOnClickListener { exportCurrentImage() }
        setUpMaskPeekGesture()

        setUpBlurTypeChips()
        setUpIntensitySlider()
        setUpFilterThumbnails()
        observeViewModel()

        val imageUri = arguments?.getString("imageUri")?.toUri()
        if (imageUri == null) {
            findNavController().navigateUp()
            return
        }
        viewModel.loadImage(imageUri)
    }
    private fun setUpMaskPeekGesture() {
        binding.ivPreview.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.mask.value?.let { binding.ivPreview.setImageBitmap(it) }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewModel.editedBitmap.value?.let { binding.ivPreview.setImageBitmap(it) }
                    true
                }

                else -> false
            }
        }
    }

    private fun setUpBlurTypeChips() {
        binding.chipRadial.setOnClickListener { viewModel.setBlurType(BackgroundBlurType.RADIAL) }
        binding.chipGaussian.setOnClickListener { viewModel.setBlurType(BackgroundBlurType.GAUSSIAN) }
        binding.btnReset.setOnClickListener { viewModel.resetIntensity() }
    }

    private fun updateBlurTypeSelection(selected: BackgroundBlurType) {
        binding.chipRadial.isSelected = selected == BackgroundBlurType.RADIAL
        binding.chipGaussian.isSelected = selected == BackgroundBlurType.GAUSSIAN
    }

    private fun setUpIntensitySlider() {
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            binding.tvIntensityValue.text = "${value.toInt()}%"
            if (fromUser) viewModel.setIntensity(value)
        }
    }

    private fun setUpFilterThumbnails() {
        binding.filterNone.setOnClickListener { viewModel.setFilter(BackgroundFilter.NONE) }
        binding.filterOriginal.setOnClickListener { viewModel.setFilter(BackgroundFilter.ORIGINAL) }
        binding.filterWarm.setOnClickListener { viewModel.setFilter(BackgroundFilter.WARM) }
        binding.filterCool.setOnClickListener { viewModel.setFilter(BackgroundFilter.COOL) }
    }

    private fun updateFilterSelection(selected: BackgroundFilter) {
        binding.filterNone.isSelected = selected == BackgroundFilter.NONE
        binding.filterOriginal.isSelected = selected == BackgroundFilter.ORIGINAL
        binding.filterWarm.isSelected = selected == BackgroundFilter.WARM
        binding.filterCool.isSelected = selected == BackgroundFilter.COOL
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.blurType.collect { updateBlurTypeSelection(it) }
                }
                launch {
                    viewModel.intensity.collect { percent ->
                        binding.tvIntensityValue.text = "${percent.toInt()}%"
                        if (binding.sliderIntensity.value != percent) {
                            binding.sliderIntensity.value = percent
                        }
                    }
                }
                launch {
                    viewModel.filter.collect { updateFilterSelection(it) }
                }
                launch {
                    viewModel.editedBitmap.collect { bitmap ->
                        bitmap?.let { binding.ivPreview.setImageBitmap(it) }
                    }
                }
                launch {
                    viewModel.sourceBitmap.collect { bitmap ->
                        bitmap ?: return@collect
                        renderFilterThumbnails(bitmap)
                    }
                }
            }
        }
    }

    private suspend fun renderFilterThumbnails(source: Bitmap) {
        val (original, warm, cool) = withContext(Dispatchers.Default) {
            Triple(
                downscale(source, 120),
                tint(downscale(source, 120), warm = true),
                tint(downscale(source, 120), warm = false)
            )
        }
        binding.ivFilterOriginal.setImageBitmap(original)
        binding.ivFilterWarm.setImageBitmap(warm)
        binding.ivFilterCool.setImageBitmap(cool)
    }

    private fun downscale(bitmap: Bitmap, targetSize: Int): Bitmap {
        val scale = targetSize.toFloat() / minOf(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun tint(bitmap: Bitmap, warm: Boolean): Bitmap {
        val matrix = if (warm) {
            ColorMatrix(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 8f,
                    0f, 1.03f, 0f, 0f, 0f,
                    0f, 0f, 0.85f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        } else {
            ColorMatrix(
                floatArrayOf(
                    0.85f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.15f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            bitmap, 0f, 0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        )
        return result
    }

    private fun exportCurrentImage() {
        val bitmap = viewModel.editedBitmap.value
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Send the edited bitmap to the shared nav-scoped ViewModel
        sharedPhotoViewModel.setBitmapToExport(bitmap)

        // 2. Open up your export bottom sheet fragment interface
        val bottomSheet = com.example.blurface.ui.export.ExportBottomSheetFragment()
        bottomSheet.show(childFragmentManager, "ExportBottomSheet")
    }

    private fun saveJpegToGallery(bitmap: Bitmap): Uri? {
        val context = requireContext()
        val resolver = context.contentResolver
        val filename = "BlurFace_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BlurFace")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        val wrote = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        } ?: false

        if (!wrote) {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}