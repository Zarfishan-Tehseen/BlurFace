package com.example.blurface.domain.model

enum class EditType(val label: String) {
    BLUR_FACES("Blur Faces"),
    BLUR_BACKGROUND("Blur Background")
}

data class RecentEdit(
    val id: String,
    val title: String,
    val editType: EditType,
    val mediaUri: String,
    val isVideo: Boolean,
    val timestampMillis: Long,
    val fileSizeBytes: Long
)