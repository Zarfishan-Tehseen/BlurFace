package com.example.blurface.ui.video.exportprocess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.domain.model.VideoResolution
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val progress: Int) : ExportState()
    object Done : ExportState()
    data class Error(val message: String) : ExportState()
}

class ExportProcessViewModel : ViewModel() {

    private val _resolution = MutableStateFlow(VideoResolution.FULL_HD_1080)
    val resolution: StateFlow<VideoResolution> = _resolution.asStateFlow()

    private val _frameRate = MutableStateFlow(30)
    val frameRate: StateFlow<Int> = _frameRate.asStateFlow()

    // MediaMuxer only reliably supports MP4 containers, so this isn't user-editable
    // right now - kept as a field anyway so the export step has one place to read it.
    val format: String = "MP4"

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private var exportJob: Job? = null

    fun setResolution(resolution: VideoResolution) {
        _resolution.value = resolution
    }

    fun setFrameRate(fps: Int) {
        _frameRate.value = fps
    }

    fun estimatedSizeMb(durationSeconds: Double): Double {
        if (durationSeconds <= 0) return 0.0
        // Scale the resolution's baseline (30fps) bitrate by the chosen frame rate.
        val bitrateMbps = _resolution.value.approxBitrateMbps * (_frameRate.value / 30.0)
        return (bitrateMbps * durationSeconds) / 8.0
    }

    fun startExport() {
        // TODO: this is a simulated progress standing in for the real pipeline:
        //  1. Iterate every frame of the source video (FaceClusterViewModel.videoPath)
        //  2. Per frame, find FaceInstances whose frameId matches and whose
        //     Person.shouldBlur == true; run VideoFaceEffectProcessor on each box
        //     (or the whole frame if BlurSettings.blurEntireVideo), using the
        //     BlurSettings persisted from VideoBlurEditorFragment
        //  3. Feed processed frames into a MediaCodec encoder at `resolution`/`frameRate`,
        //     mux with MediaMuxer (+ passthrough original audio track if present) into
        //     an MP4, saved via MediaStore (same pattern as ImageSaver, video equivalent)
        //  4. Emit real progress from the encoder's presentation-time position instead
        //     of this fixed timer
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportState.value = ExportState.Exporting(0)
            var progress = 0
            while (progress < 100) {
                progress = (progress + 2).coerceAtMost(100)
                _exportState.value = ExportState.Exporting(progress)
                delay(90)
            }
            _exportState.value = ExportState.Done
        }
    }

    override fun onCleared() {
        super.onCleared()
        exportJob?.cancel()
    }
}