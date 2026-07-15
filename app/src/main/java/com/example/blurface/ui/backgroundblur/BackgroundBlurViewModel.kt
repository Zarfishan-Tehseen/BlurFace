package com.example.blurface.ui.backgroundblur

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.data.segmentation.MlKitSubjectSegmentationRepository
import com.example.blurface.domain.model.BackgroundBlurType
import com.example.blurface.domain.model.BackgroundFilter
import com.example.blurface.domain.usecase.SegmentForegroundUseCase
import com.example.blurface.utils.BackgroundBlurProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SegmentationState {
    object Idle : SegmentationState()
    object Segmenting : SegmentationState()
    object Success : SegmentationState()
    data class Error(val message: String) : SegmentationState()
}

class BackgroundBlurViewModel(application: Application) : AndroidViewModel(application) {

    // TODO: replace manual wiring with Hilt injection once DI is set up
    // Swapped from MlKitSegmentationRepository (Selfie Segmentation - people only) to
    // MlKitSubjectSegmentationRepository (Subject Segmentation - people/pets/objects).
    // SegmentForegroundUseCase and everything downstream is unaffected by this swap.
    private val repository = MlKitSubjectSegmentationRepository()
    private val segmentForegroundUseCase = SegmentForegroundUseCase(repository)

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _mask = MutableStateFlow<Bitmap?>(null)
    // Exposed read-only so the Fragment can offer a "show what ML Kit segmented" debug
    // view - the fastest way to tell a segmentation-quality problem apart from a
    // compositing bug without instrumenting/guessing blind.
    val mask: StateFlow<Bitmap?> = _mask.asStateFlow()

    private val _blurType = MutableStateFlow(BackgroundBlurType.GAUSSIAN)
    val blurType: StateFlow<BackgroundBlurType> = _blurType.asStateFlow()

    private val _intensity = MutableStateFlow(60f)
    val intensity: StateFlow<Float> = _intensity.asStateFlow()

    private val _filter = MutableStateFlow(BackgroundFilter.ORIGINAL)
    val filter: StateFlow<BackgroundFilter> = _filter.asStateFlow()

    private val _segmentationState = MutableStateFlow<SegmentationState>(SegmentationState.Idle)
    val segmentationState: StateFlow<SegmentationState> = _segmentationState.asStateFlow()

    private val _editedBitmap = MutableStateFlow<Bitmap?>(null)
    val editedBitmap: StateFlow<Bitmap?> = _editedBitmap.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _sourceBitmap, _mask, _blurType, _intensity, _filter
            ) { bitmap, mask, type, intensity, filter ->
                if (bitmap == null || mask == null) return@combine null
                withContext(Dispatchers.Default) {
                    BackgroundBlurProcessor.apply(
                        context = getApplication(),
                        original = bitmap,
                        mask = mask,
                        blurType = type,
                        intensityPercent = intensity,
                        filter = filter
                    )
                }
            }.collect { result -> _editedBitmap.value = result }
        }
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _segmentationState.value = SegmentationState.Segmenting
            val context = getApplication<Application>()

            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }

            if (bitmap == null) {
                _segmentationState.value = SegmentationState.Error("Couldn't load image")
                return@launch
            }
            _sourceBitmap.value = bitmap

            try {
                _mask.value = segmentForegroundUseCase(context, bitmap)
                _segmentationState.value = SegmentationState.Success
            } catch (e: Exception) {
                _segmentationState.value = SegmentationState.Error(e.message ?: "Segmentation failed")
            }
        }
    }

    fun setBlurType(type: BackgroundBlurType) {
        _blurType.value = type
    }

    fun setIntensity(percent: Float) {
        _intensity.value = percent.coerceIn(0f, 100f)
    }

    fun resetIntensity() {
        _intensity.value = 60f
    }

    fun setFilter(filter: BackgroundFilter) {
        _filter.value = filter
    }
}