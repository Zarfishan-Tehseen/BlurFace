package com.example.blurface.domain.model

/**
 * Everything the user configures on VideoBlurEditorFragment.
 * Kept as a plain immutable data class so it can be persisted in the
 * shared FaceClusterViewModel and later handed to a use case that
 * actually renders the blur.
 */
data class BlurSettings(
    val blurType: BlurType = BlurType.GAUSSIAN,
    val shape: BlurShape = BlurShape.AUTO_FACE,
    val intensity: Int = 60,
    val feather: Int = 40,
    val blurEntireVideo: Boolean = false,
    val emoji: String = "😀"
)

enum class BlurType { GAUSSIAN, MOSAIC, COLOR, EMOJI }

enum class BlurShape { AUTO_FACE, CIRCLE, RECTANGLE }