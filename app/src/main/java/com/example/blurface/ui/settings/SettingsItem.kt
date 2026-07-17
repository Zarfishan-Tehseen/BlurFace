package com.example.blurface.ui.settings

import androidx.annotation.DrawableRes

data class SettingsItem(
    val id: String,
    @DrawableRes val icon: Int,
    val title: String,
    val subtitle: String? = null,
    val categoryHeader: String? = null,  // Set if this item starts a new card block
    val isToggleable: Boolean = false,   // True for SwitchCompat, False for Chevron
    var isChecked: Boolean = false,
    val isLastInGroup: Boolean = false,  // Helps determine dynamic card rounding
    val trailingText: String? = null     // e.g., for cache size strings
) {
    fun stableId(): String = id
}

// These can stay at the bottom of your file as they are
data class SettingsUiState(
    val autoFaceDetection: Boolean = true,
    val saveOriginalPhoto: Boolean = true,
    val exportQuality: ExportQuality = ExportQuality.HIGH,
    val appTheme: AppTheme = AppTheme.DARK,
    val language: String = "English",
    val cacheSizeLabel: String = "…"
)

enum class ExportQuality(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }
enum class AppTheme(val label: String) { LIGHT("Light"), DARK("Dark"), SYSTEM("System") }