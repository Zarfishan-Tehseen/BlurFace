package com.example.blurface.domain.usecase

import com.example.blurface.domain.repository.VideoRepository
import com.example.blurface.domain.repository.PipelineStatus
import kotlinx.coroutines.flow.Flow

class ProcessAndClusterVideoUseCase(private val repository: VideoRepository) {
    operator fun invoke(videoPath: String, outputDir: String, fps: Int = 15): Flow<PipelineStatus> {
        return repository.processAndClusterVideo(videoPath, outputDir, fps)
    }
}