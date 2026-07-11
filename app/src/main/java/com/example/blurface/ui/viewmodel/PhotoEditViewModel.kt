package com.example.blurface.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.data.facedetection.MlKitFaceDetectionRepository
import com.example.blurface.domain.model.DetectedFace
import com.example.blurface.domain.model.FaceEffect
import com.example.blurface.domain.model.ExportFormat
import com.example.blurface.domain.usecase.DetectFacesInImageUseCase
import com.example.blurface.utils.EffectProcessor
import com.example.blurface.utils.ImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DetectionState {
    object Idle : DetectionState()
    object Detecting : DetectionState()
    object Success : DetectionState()
    data class Error(val message: String) : DetectionState()
}

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val uri: Uri) : SaveState()
    data class Error(val message: String) : SaveState()
}

class PhotoEditViewModel(application: Application) : AndroidViewModel(application) {

    // TODO: replace manual wiring with Hilt injection once DI is set up
    private val repository = MlKitFaceDetectionRepository()
    private val detectFacesUseCase = DetectFacesInImageUseCase(repository)

    var imageUri: Uri? = null
        private set

    private val _faces = MutableStateFlow<List<DetectedFace>>(emptyList())
    val faces: StateFlow<List<DetectedFace>> = _faces.asStateFlow()

    private val _detectionState = MutableStateFlow<DetectionState>(DetectionState.Idle)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    // ── Blur Editor state ──
    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _currentEffect = MutableStateFlow(FaceEffect.BLUR)
    val currentEffect: StateFlow<FaceEffect> = _currentEffect.asStateFlow()

    private val _intensityPercent = MutableStateFlow(60f)
    val intensityPercent: StateFlow<Float> = _intensityPercent.asStateFlow()

    private val _selectedEmoji = MutableStateFlow("😀")
    val selectedEmoji: StateFlow<String> = _selectedEmoji.asStateFlow()

    private val _brushMask = MutableStateFlow<Bitmap?>(null)
    val brushMask: StateFlow<Bitmap?> = _brushMask.asStateFlow()

    private val _selectedColor = MutableStateFlow(Color.BLACK)
    val selectedColor: StateFlow<Int> = _selectedColor.asStateFlow()

    private val _editedBitmap = MutableStateFlow<Bitmap?>(null)
    val editedBitmap: StateFlow<Bitmap?> = _editedBitmap.asStateFlow()

    init {
        viewModelScope.launch {
            val baseFlow = combine(
                _sourceBitmap,
                _faces,
                _currentEffect,
                _intensityPercent,
                _selectedEmoji
            ) { bitmap, faces, effect, intensity, emoji ->
                RenderParams(bitmap, faces, effect, intensity, emoji)
            }

            combine(baseFlow, _brushMask, _selectedColor) { params, mask, color ->
                if (params.bitmap == null || (params.faces.isEmpty() && mask == null)) return@combine null
                withContext(Dispatchers.Default) {
                    EffectProcessor.applyEffect(
                        context = getApplication(),
                        original = params.bitmap,
                        boxes = params.faces.map { it.boundingBox },
                        selectedIndices = params.faces.filter { it.isSelected }.map { it.id }.toSet(),
                        effect = params.effect,
                        intensityPercent = params.intensity,
                        emoji = params.emoji,
                        fillColor = color,
                        brushMask = mask
                    )
                }
            }.collect { result -> _editedBitmap.value = result }
        }
    }

    private data class RenderParams(
        val bitmap: Bitmap?,
        val faces: List<DetectedFace>,
        val effect: FaceEffect,
        val intensity: Float,
        val emoji: String
    )

    fun detectFaces(uri: Uri) {
        imageUri = uri
        viewModelScope.launch {
            _detectionState.value = DetectionState.Detecting
            try {
                val result = detectFacesUseCase(getApplication(), uri)
                _faces.value = result
                _detectionState.value = DetectionState.Success
            } catch (e: Exception) {
                _detectionState.value = DetectionState.Error(e.message ?: "Face detection failed")
            }
        }
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        _sourceBitmap.value = bitmap
    }

    fun setBrushMask(mask: Bitmap) {
        _brushMask.value = mask
    }

    fun toggleFaceSelection(faceId: Int) {
        _faces.value = _faces.value.map { face ->
            if (face.id == faceId) face.copy(isSelected = !face.isSelected) else face
        }
    }

    fun setAllSelected(selected: Boolean) {
        _faces.value = _faces.value.map { it.copy(isSelected = selected) }
    }

    /**
     * Brings back one previously-removed face at a time, in original detection order,
     * so tapping "Add Face" repeatedly reveals hidden faces one by one until every
     * detected face is selected again. No-ops once nothing is left to restore.
     */
    fun addBackNextFace() {
        val nextHidden = _faces.value.firstOrNull { !it.isSelected } ?: return
        _faces.value = _faces.value.map { face ->
            if (face.id == nextHidden.id) face.copy(isSelected = true) else face
        }
    }

    fun selectedFaces(): List<DetectedFace> = _faces.value.filter { it.isSelected }

    fun setEffect(effect: FaceEffect) {
        _currentEffect.value = effect
    }

    fun setIntensity(percent: Float) {
        _intensityPercent.value = percent.coerceIn(0f, 100f)
    }

    fun resetIntensity() {
        _intensityPercent.value = 60f
    }

    fun setEmoji(emoji: String) {
        _selectedEmoji.value = emoji
    }

    fun setSelectedColor(color: Int) {
        _selectedColor.value = color
    }

    // ── Export ──
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun saveExport(format: ExportFormat) {
        val bitmap = _editedBitmap.value ?: run {
            _saveState.value = SaveState.Error("Nothing to save yet")
            return
        }
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val uri = withContext(Dispatchers.IO) {
                    ImageSaver.saveToGallery(getApplication(), bitmap, format)
                }
                _saveState.value = if (uri != null) {
                    SaveState.Success(uri)
                } else {
                    SaveState.Error("Could not save image")
                }
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Save failed")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}