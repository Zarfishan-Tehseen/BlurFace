package com.example.blurface.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // Rebuilt automatically any time state changes - screen just observes `items`,
    // never touches SettingsScreenBuilder directly.
    val items: StateFlow<List<SettingsItem>> = _state
        .map { SettingsScreenBuilder.build(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsScreenBuilder.build(_state.value))

    init {
        refreshCacheSize()
    }

    fun setAutoFaceDetection(enabled: Boolean) = update { it.copy(autoFaceDetection = enabled) }
        .also { prefs.edit().putBoolean(KEY_AUTO_FACE, enabled).apply() }

    fun setSaveOriginalPhoto(enabled: Boolean) = update { it.copy(saveOriginalPhoto = enabled) }
        .also { prefs.edit().putBoolean(KEY_SAVE_ORIGINAL, enabled).apply() }

    fun setExportQuality(quality: ExportQuality) = update { it.copy(exportQuality = quality) }
        .also { prefs.edit().putString(KEY_EXPORT_QUALITY, quality.name).apply() }

    fun setAppTheme(theme: AppTheme) = update { it.copy(appTheme = theme) }
        .also { prefs.edit().putString(KEY_APP_THEME, theme.name).apply() }

    fun setLanguage(language: String) = update { it.copy(language = language) }
        .also { prefs.edit().putString(KEY_LANGUAGE, language).apply() }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                getApplication<Application>().cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
            }
            refreshCacheSize()
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            val sizeLabel = withContext(Dispatchers.IO) { formatSize(dirSize(getApplication<Application>().cacheDir)) }
            update { it.copy(cacheSizeLabel = sizeLabel) }
        }
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    private fun update(transform: (SettingsUiState) -> SettingsUiState) {
        _state.value = transform(_state.value)
    }

    private fun loadFromPrefs(): SettingsUiState = SettingsUiState(
        autoFaceDetection = prefs.getBoolean(KEY_AUTO_FACE, true),
        saveOriginalPhoto = prefs.getBoolean(KEY_SAVE_ORIGINAL, true),
        exportQuality = prefs.getString(KEY_EXPORT_QUALITY, null)
            ?.let { runCatching { ExportQuality.valueOf(it) }.getOrNull() } ?: ExportQuality.HIGH,
        appTheme = prefs.getString(KEY_APP_THEME, null)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.DARK,
        language = prefs.getString(KEY_LANGUAGE, null) ?: "English"
    )

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val KEY_AUTO_FACE = "auto_face_detection"
        private const val KEY_SAVE_ORIGINAL = "save_original_photo"
        private const val KEY_EXPORT_QUALITY = "export_quality"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_LANGUAGE = "language"
    }
}