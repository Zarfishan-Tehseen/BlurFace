package com.example.blurface.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.data.facedetection.MlKitFaceDetectionRepository
import com.example.blurface.domain.model.DetectedFace
import com.example.blurface.domain.model.FaceEffect
import com.example.blurface.domain.usecase.DetectFacesInImageUseCase
import com.example.blurface.utils.EffectProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import com.example.blurface.domain.model.ExportFormat
import com.example.blurface.utils.ImageSaver

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

    private val _editedBitmap = MutableStateFlow<Bitmap?>(null)
    val editedBitmap: StateFlow<Bitmap?> = _editedBitmap.asStateFlow()

    private val _selectedColor = MutableStateFlow<Int>(Color.BLACK) // Default color
    val selectedColor: StateFlow<Int> = _selectedColor.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _sourceBitmap,
                _faces,
                _currentEffect,
                _intensityPercent,
                _selectedEmoji,
                _selectedColor
            ) { args: Array<Any?> -> // Accepts the bundled array required for 6+ flows
                val bitmap = args[0] as? Bitmap
                val faces = args[1] as? List<DetectedFace> ?: emptyList()
                val effect = args[2] as FaceEffect
                val intensity = args[3] as Float
                val emoji = args[4] as String
                val color = args[5] as Int

                if (bitmap == null || faces.isEmpty()) return@combine null

                withContext(Dispatchers.Default) {
                    EffectProcessor.applyEffect(
                        context = getApplication(),
                        original = bitmap,
                        boxes = faces.map { it.boundingBox },
                        selectedIndices = faces.filter { it.isSelected }.map { it.id }.toSet(),
                        effect = effect,
                        intensityPercent = intensity,
                        emoji = emoji,
                        selectedColor = color
                    )
                }
            }.collect { result -> _editedBitmap.value = result }
        }
    }

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

    fun toggleFaceSelection(faceId: Int) {
        _faces.value = _faces.value.map { face ->
            if (face.id == faceId) face.copy(isSelected = !face.isSelected) else face
        }
    }

    fun setAllSelected(selected: Boolean) {
        _faces.value = _faces.value.map { it.copy(isSelected = selected) }
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