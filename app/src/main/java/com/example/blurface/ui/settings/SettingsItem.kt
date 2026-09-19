package com.example.blurface.ui.settings

import androidx.annotation.DrawableRes
import com.example.blurface.R

import java.util.Locale

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
    val language: AppLanguage = AppLanguage.ENGLISH,
    val cacheSizeLabel: String = "…"
)

enum class ExportQuality(val label: String, val stringRes: Int) {
    LOW("Low", R.string.quality_low),
    MEDIUM("Medium", R.string.quality_medium),
    HIGH("High", R.string.quality_high);

    fun getLabel(context: android.content.Context): String = context.getString(stringRes)
}

enum class AppTheme(val label: String, val stringRes: Int) {
    LIGHT("Light", R.string.theme_light),
    DARK("Dark", R.string.theme_dark),
    SYSTEM("System", R.string.theme_system);

    fun getLabel(context: android.content.Context): String = context.getString(stringRes)
}

enum class AppLanguage(val tag: String, val stringRes: Int) {
    ENGLISH("en", R.string.lang_english),
    URDU("ur", R.string.lang_urdu),
    SPANISH("es", R.string.lang_spanish),
    FRENCH("fr", R.string.lang_french);

    fun getLabel(context: android.content.Context): String = context.getString(stringRes)

    companion object {
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrEmpty()) return ENGLISH
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                ?: fromLegacyName(tag)
        }

        fun fromLegacyName(name: String?): AppLanguage {
            return when (name?.lowercase(Locale.ROOT)) {
                "english", "en" -> ENGLISH
                "urdu", "ur" -> URDU
                "spanish", "es" -> SPANISH
                "french", "fr" -> FRENCH
                else -> ENGLISH
            }
        }
    }
}