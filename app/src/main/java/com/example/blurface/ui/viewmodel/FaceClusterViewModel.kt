package com.example.blurface.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.domain.model.BlurSettings
import com.example.blurface.domain.model.Person
import com.example.blurface.domain.repository.PipelineStatus
import com.example.blurface.domain.usecase.ProcessAndClusterVideoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FaceClusterViewModel(
    private val processAndClusterVideoUseCase: ProcessAndClusterVideoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<FaceUiState>(FaceUiState.Idle)
    val uiState: StateFlow<FaceUiState> = _uiState

    var fps: Int = 10
        private set

    var videoPath: String? = null
    var exportedVideoPath: String? = null

    private val _blurSettings = MutableStateFlow(BlurSettings())
    val blurSettings: StateFlow<BlurSettings> = _blurSettings

    fun updateBlurSettings(settings: BlurSettings) {
        _blurSettings.value = settings
    }

    fun selectedPeopleForBlur(): List<Person> =
        (_uiState.value as? FaceUiState.Success)?.clusteredPeople?.filter { it.shouldBlur }
            ?: emptyList()

    fun runFacePipeline(videoPath: String, cacheDir: String, fps: Int = 10) {
        this.fps = fps

        _uiState.value = FaceUiState.Idle

        viewModelScope.launch {
            processAndClusterVideoUseCase(videoPath, cacheDir, fps = fps).collect { state ->
                when (state) {
                    is PipelineStatus.Working -> {
                        _uiState.value = FaceUiState.Loading(
                            progress = state.percentage,
                            logMessage = state.message,
                            currentFrame = state.currentFrame,
                            totalFrames = state.totalFrames
                        )
                    }

                    is PipelineStatus.Completed -> {
                        _uiState.value = FaceUiState.Success(state.clusteredPeople)
                    }

                    is PipelineStatus.Failed -> {
                        _uiState.value = FaceUiState.Error(state.error)
                    }
                }
            }
        }
    }
}

sealed class FaceUiState {
    object Idle : FaceUiState()
    data class Loading(val progress: Int, val logMessage: String, val currentFrame: Int, val totalFrames: Int) : FaceUiState()
    data class Success(val clusteredPeople: List<Person>) : FaceUiState()
    data class Error(val message: String) : FaceUiState()
}