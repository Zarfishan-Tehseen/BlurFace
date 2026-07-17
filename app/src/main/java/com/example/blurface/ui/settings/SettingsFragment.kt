package com.example.blurface.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blurface.R
import com.example.blurface.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var adapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.tvScreenTitle) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        adapter = SettingsAdapter(
            onItemClicked = { item ->
                if (item.isToggleable) {
                    val newValue = !item.isChecked
                    handleToggleChanged(item.id, newValue)
                } else {
                    handleNavigationOrPickerClicked(item)
                }
            },
            onToggleChanged = { item, isChecked ->
                handleToggleChanged(item.id, isChecked)
            }
        )
        binding.rvSettings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSettings.adapter = adapter

        binding.btnBuyNow.setOnClickListener {
            findNavController().navigate(R.id.premiumFragment)
        }

        // 3. Collect UI State Flow (see step 2 below)
        setupLifecycleObservers()
    }
    private fun setupLifecycleObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { settingsList ->
                    // This sends the fresh category item list to your ListAdapter
                    adapter.submitList(settingsList)
                }
            }
        }
    }

    private fun handleToggleChanged(itemId: String, isChecked: Boolean) {
        when (itemId) {
            "auto_face_detection" -> viewModel.setAutoFaceDetection(isChecked)
            "save_original_photo" -> viewModel.setSaveOriginalPhoto(isChecked)
        }
    }
    private fun handleNavigationOrPickerClicked(item: SettingsItem) {
        when (item.id) {
            "premium_banner" -> handlePremiumBannerClicked()

            // Pickers
            "export_quality" -> showSingleChoiceDialog(
                title = "Export Quality",
                options = ExportQuality.entries.map { it.label },
                currentIndex = ExportQuality.entries.indexOfFirst { it.label == item.subtitle }
            ) { index -> viewModel.setExportQuality(ExportQuality.entries[index]) }

            "app_theme" -> showSingleChoiceDialog(
                title = "App Theme",
                options = AppTheme.entries.map { it.label },
                currentIndex = AppTheme.entries.indexOfFirst { it.label == item.subtitle }
            ) { index -> viewModel.setAppTheme(AppTheme.entries[index]) }

            "language" -> showSingleChoiceDialog(
                title = "Language",
                options = LANGUAGES,
                currentIndex = LANGUAGES.indexOf(item.subtitle).coerceAtLeast(0)
            ) { index -> viewModel.setLanguage(LANGUAGES[index]) }

            // Navigations
            "privacy_policy" -> openUrl("https://example.com/privacy")
            "data_security" -> openUrl("https://example.com/security")
            "clear_cache" -> confirmClearCache()
            "help_faq" -> openUrl("https://example.com/help")
            "contact_us" -> openUrl("mailto:support@example.com")
            "rate_us" -> openPlayStoreListing()
        }
    }

    private fun handlePremiumBannerClicked() {
        // TODO: navigate to the real paywall/upgrade screen once it exists.
    }

    private fun showSingleChoiceDialog(
        title: String,
        options: List<String>,
        currentIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        var selected = currentIndex
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), currentIndex) { _, which -> selected = which }
            .setPositiveButton("OK") { dialog, _ ->
                onSelected(selected)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearCache() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Cache")
            .setMessage("This removes temporary files created while editing. Your saved photos and videos are not affected.")
            .setPositiveButton("Clear") { _, _ -> viewModel.clearCache() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun openPlayStoreListing() {
        val uri = Uri.parse("market://details?id=${requireContext().packageName}")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${requireContext().packageName}")
                    )
                )
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val LANGUAGES = listOf("English", "Spanish", "French", "German", "Hindi", "Urdu")
    }
}