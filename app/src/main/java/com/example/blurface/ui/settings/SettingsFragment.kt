package com.example.blurface.ui.settings

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
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
import com.example.blurface.databinding.DialogConfirmActionBinding
import com.example.blurface.databinding.DialogSingleChoiceBinding
import com.example.blurface.databinding.FragmentSettingsBinding
import com.webscare.prescriptionscanner.common.Utils.addPressEffect
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

        binding.btnBuyNow.addPressEffect {
            findNavController().navigate(R.id.premiumFragment)
        }

        setupLifecycleObservers()
    }
    private fun setupLifecycleObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { settingsList ->
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

            "export_quality" -> showSingleChoiceDialog(
                title = "Export Quality",
                iconRes = R.drawable.ic_hd,
                options = ExportQuality.entries.map { it.label },
                currentIndex = ExportQuality.entries.indexOfFirst { it.label == item.subtitle }
            ) { index -> viewModel.setExportQuality(ExportQuality.entries[index]) }

            "app_theme" -> showSingleChoiceDialog(
                title = "App Theme",
                iconRes = R.drawable.ic_sliders, // or your theme icon
                options = AppTheme.entries.map { it.label },
                currentIndex = AppTheme.entries.indexOfFirst { it.label == item.subtitle }
            ) { index -> viewModel.setAppTheme(AppTheme.entries[index]) }

            "language" -> showSingleChoiceDialog(
                title = "Language",
                iconRes = R.drawable.ic_language,
                options = LANGUAGES,
                currentIndex = LANGUAGES.indexOf(item.subtitle).coerceAtLeast(0)
            ) { index -> viewModel.setLanguage(LANGUAGES[index]) }

            "privacy_policy" -> openPrivacyPolicy()
            "data_security" -> openUrl("https://example.com/security")
            "clear_cache" -> confirmClearCache()
            "help_faq" -> openEmail("Help & FAQs")
            "contact_us" -> openEmail("FaceBlur Support")
            "rate_us" -> rateApp()
        }
    }

    private fun handlePremiumBannerClicked() {
        // TODO: navigate to the real paywall/upgrade screen once it exists.
    }

    private fun showSingleChoiceDialog(
        title: String,
        iconRes: Int,
        options: List<String>,
        currentIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val dialogBinding = DialogSingleChoiceBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogBinding.tvDialogTitle.text = title
        dialogBinding.ivDialogIcon.setImageResource(iconRes) // Set icon dynamically

        var selectedIndex = currentIndex

        options.forEachIndexed { index, optionText ->
            val itemView = layoutInflater.inflate(R.layout.item_dialog_radio_option, dialogBinding.radioGroupOptions, false)
            val container = itemView.findViewById<View>(R.id.containerOption)
            val radioButton = itemView.findViewById<RadioButton>(R.id.radioButton)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvOptionTitle)

            tvTitle.text = optionText

            val isSelected = index == selectedIndex
            radioButton.isChecked = isSelected
            container.setBackgroundResource(
                if (isSelected) R.drawable.bg_option_card_selected else R.drawable.bg_option_card_unselected
            )

            container.setOnClickListener {
                selectedIndex = index
                for (i in 0 until dialogBinding.radioGroupOptions.childCount) {
                    val child = dialogBinding.radioGroupOptions.getChildAt(i)
                    val rb = child.findViewById<RadioButton>(R.id.radioButton)
                    val isCurrent = i == selectedIndex
                    rb.isChecked = isCurrent
                    child.setBackgroundResource(
                        if (isCurrent) R.drawable.bg_option_card_selected else R.drawable.bg_option_card_unselected
                    )
                }
            }

            dialogBinding.radioGroupOptions.addView(itemView)
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnOk.setOnClickListener {
            if (selectedIndex != -1) {
                onSelected(selectedIndex)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmClearCache() {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogBinding.tvDialogTitle.text = "Clear Cache"
        dialogBinding.tvDialogMessage.text =
            "This removes temporary files created while editing. Your saved photos and videos are not affected."

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            viewModel.clearCache()
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun openEmail(subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("hello@webscare.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }

        try {
            startActivity(Intent.createChooser(intent, "Choose Email App"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No email application found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPrivacyPolicy() {
        try {
            val privacyUrl = "https://your-privacy-policy-url.com" // Replace with your final live domain URL
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open browser link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rateApp() {
        val packageName = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
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
        private val LANGUAGES = listOf("English", "Spanish", "French", "Urdu")
    }
}