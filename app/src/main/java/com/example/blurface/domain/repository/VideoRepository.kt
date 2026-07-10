package com.example.blurface.domain.repository

import com.example.blurface.domain.model.Person
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun processAndClusterVideo(
        videoPath: String,
        outputDir: String,
        fps: Int
    ): Flow<PipelineStatus>
}

sealed class PipelineStatus {
    data class Working(val percentage: Int, val message: String, val currentFrame: Int = 0, val totalFrames: Int = 0) : PipelineStatus()
    data class Completed(val clusteredPeople: List<Person>) : PipelineStatus()
    data class Failed(val error: String) : PipelineStatus()
}