package com.example.blurface.domain.model

enum class VideoResolution(
    val label: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val approxBitrateMbps: Double
) {
    HD_720("720p", "HD", 1280, 720, 2.5),
    FULL_HD_1080("1080p", "Full HD", 1920, 1080, 5.0),
    UHD_4K("4K", "Ultra HD", 3840, 2160, 15.0)
}