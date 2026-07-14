package com.example.blurface.ui.video.exportprocess

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.domain.model.BlurSettings
import com.example.blurface.domain.model.Person
import com.example.blurface.domain.model.VideoResolution
import com.example.blurface.utils.VideoExportProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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

    // Set once export finishes successfully. Read by ExportProcessFragment to hand
    // off the real rendered file to BlurredVideoResultFragment.
    var outputPath: String? = null
        private set

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

    /**
     * Runs the real render/encode pipeline.
     *
     * @param sourcePath resolved path of the originally uploaded video
     *   (FaceClusterViewModel.videoPath)
     * @param people the clustered people, with shouldBlur flags as set on
     *   DetectedFacesFragment (FaceClusterViewModel.selectedPeopleForBlur())
     * @param blurSettings the effect config from VideoBlurEditorFragment
     *   (FaceClusterViewModel.blurSettings.value)
     * @param analysisFps the fps the pipeline originally analyzed frames at
     *   (FaceClusterViewModel.fps) - needed to map export-frame timestamps back to
     *   the analyzed frame each face box came from
     */
    fun startExport(
        context: Context,
        sourcePath: String,
        people: List<Person>,
        blurSettings: BlurSettings,
        analysisFps: Int
    ) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportState.value = ExportState.Exporting(0)

            val destination = File(
                context.cacheDir,
                "blurface_export_${System.currentTimeMillis()}.mp4"
            ).absolutePath

            val result = VideoExportProcessor.export(
                context = context.applicationContext,
                sourcePath = sourcePath,
                outputPath = destination,
                people = people,
                blurSettings = blurSettings,
                analysisFps = analysisFps,
                resolution = _resolution.value,
                frameRate = _frameRate.value,
                onProgress = { progress -> _exportState.value = ExportState.Exporting(progress) }
            )

            _exportState.value = if (result.success && result.outputPath != null) {
                outputPath = result.outputPath
                ExportState.Done
            } else {
                ExportState.Error(result.error ?: "Export failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        exportJob?.cancel()
    }
}