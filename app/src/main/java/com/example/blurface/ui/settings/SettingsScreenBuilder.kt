package com.example.blurface.ui.settings

import com.example.blurface.R

object SettingsScreenBuilder {

    fun build(state: SettingsUiState): List<SettingsItem> = buildList {
        // --- Group 0: Preferences ---
        add(
            SettingsItem(
                id = "auto_face_detection",
                icon = R.drawable.ic_blur_faces,
                title = "Auto Face Detection",
                categoryHeader = "PREFERENCES",
                isToggleable = true,
                isChecked = state.autoFaceDetection
            )
        )
        add(
            SettingsItem(
                id = "save_original_photo",
                icon = R.drawable.ic_blur_background,
                title = "Save Original Photo",
                isToggleable = true,
                isChecked = state.saveOriginalPhoto
            )
        )
        add(
            SettingsItem(
                id = "export_quality",
                icon = R.drawable.ic_hd,
                title = "Export Quality",
                subtitle = state.exportQuality.label
            )
        )
        add(
            SettingsItem(
                id = "app_theme",
                icon = R.drawable.ic_theme,
                title = "App Theme",
                subtitle = state.appTheme.label
            )
        )
        add(
            SettingsItem(
                id = "language",
                icon = R.drawable.ic_language,
                title = "Language",
                subtitle = state.language,
                isLastInGroup = true
            )
        )

        // --- Group 1: Privacy & Security ---
        add(
            SettingsItem(
                id = "privacy_policy",
                icon = R.drawable.ic_shield,
                title = "Privacy Policy",
                categoryHeader = "PRIVACY & SECURITY"
            )
        )
        add(
            SettingsItem(
                id = "data_security",
                icon = R.drawable.ic_lock,
                title = "Data Security"
            )
        )
        add(
            SettingsItem(
                id = "clear_cache",
                icon = R.drawable.ic_trash,
                title = "Clear Cache",
                trailingText = state.cacheSizeLabel,
                isLastInGroup = true
            )
        )

        // --- Group 2: Support ---
        add(
            SettingsItem(
                id = "help_faq",
                icon = R.drawable.ic_help,
                title = "Help & FAQ",
                categoryHeader = "SUPPORT"
            )
        )
        add(
            SettingsItem(
                id = "contact_us",
                icon = R.drawable.ic_mail,
                title = "Contact Us"
            )
        )
        add(
            SettingsItem(
                id = "rate_us",
                icon = R.drawable.ic_star_outline,
                title = "Rate Us",
                isLastInGroup = true
            )
        )
    }
}