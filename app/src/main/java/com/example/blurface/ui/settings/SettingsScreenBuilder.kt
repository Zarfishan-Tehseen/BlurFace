package com.example.blurface.ui.settings

import android.content.Context
import com.example.blurface.R

object SettingsScreenBuilder {

    fun build(context: Context, state: SettingsUiState): List<SettingsItem> = buildList {
        // --- Group 0: Preferences ---
        add(
            SettingsItem(
                id = "auto_face_detection",
                icon = R.drawable.ic_blur_faces,
                title = context.getString(R.string.auto_face_detection),
                categoryHeader = context.getString(R.string.category_preferences),
                isToggleable = true,
                isChecked = state.autoFaceDetection
            )
        )
        add(
            SettingsItem(
                id = "save_original_photo",
                icon = R.drawable.ic_blur_background,
                title = context.getString(R.string.save_original_photo),
                isToggleable = true,
                isChecked = state.saveOriginalPhoto
            )
        )
        add(
            SettingsItem(
                id = "export_quality",
                icon = R.drawable.ic_hd,
                title = context.getString(R.string.export_quality),
                subtitle = state.exportQuality.getLabel(context)
            )
        )
        add(
            SettingsItem(
                id = "language",
                icon = R.drawable.ic_language,
                title = context.getString(R.string.language),
                subtitle = state.language.getLabel(context),
                isLastInGroup = true
            )
        )

        // --- Group 1: Privacy & Security ---
        add(
            SettingsItem(
                id = "privacy_policy",
                icon = R.drawable.ic_shield_check,
                title = context.getString(R.string.privacy_policy),
                categoryHeader = context.getString(R.string.category_privacy_security)
            )
        )
        add(
            SettingsItem(
                id = "data_security",
                icon = R.drawable.ic_lock_huge,
                title = context.getString(R.string.data_security)
            )
        )
        add(
            SettingsItem(
                id = "clear_cache",
                icon = R.drawable.ic_trash,
                title = context.getString(R.string.clear_cache_title),
                trailingText = state.cacheSizeLabel,
                isLastInGroup = true
            )
        )

        // --- Group 2: Support ---
        add(
            SettingsItem(
                id = "help_faq",
                icon = R.drawable.ic_help,
                title = context.getString(R.string.help_and_faq),
                categoryHeader = context.getString(R.string.category_support)
            )
        )
        add(
            SettingsItem(
                id = "contact_us",
                icon = R.drawable.ic_mail_huge,
                title = context.getString(R.string.contact_us)
            )
        )
        add(
            SettingsItem(
                id = "rate_us",
                icon = R.drawable.ic_star,
                title = context.getString(R.string.rate_us),
                isLastInGroup = true
            )
        )
    }
}