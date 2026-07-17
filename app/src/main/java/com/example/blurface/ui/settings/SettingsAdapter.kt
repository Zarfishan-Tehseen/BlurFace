package com.example.blurface.ui.settings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.blurface.utils.toPx
import com.example.blurface.utils.setRoundedCorners
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemSettingsRowBinding

class SettingsAdapter(
    private val onItemClicked: (SettingsItem) -> Unit,
    private val onToggleChanged: (SettingsItem, Boolean) -> Unit
) : ListAdapter<SettingsItem, SettingsAdapter.SettingsViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val binding = ItemSettingsRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SettingsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SettingsViewHolder(
        private val binding: ItemSettingsRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SettingsItem) {
            // 1. Text & Icon Setup
            binding.tvSettingTitle.text = item.title
            binding.ivSettingIcon.setImageResource(item.icon)

            // Subtitle / Trailing Text handling (like Cache size label)
            val subtitleText = item.subtitle ?: item.trailingText
            if (!subtitleText.isNullOrEmpty()) {
                binding.tvSettingSubtitle.visibility = View.VISIBLE
                binding.tvSettingSubtitle.text = subtitleText
            } else {
                binding.tvSettingSubtitle.visibility = View.GONE
            }

            // 2. Section Category Headers Handler
            if (item.categoryHeader != null) {
                binding.tvCategoryHeader.visibility = View.VISIBLE
                binding.tvCategoryHeader.text = item.categoryHeader
            } else {
                binding.tvCategoryHeader.visibility = View.GONE
            }

            // 3. Toggles vs Chevrons Switcher
            if (item.isToggleable) {
                binding.ivChevron.visibility = View.GONE
                binding.switchSetting.visibility = View.VISIBLE

                binding.switchSetting.setOnCheckedChangeListener(null)
                binding.switchSetting.isChecked = item.isChecked

                binding.switchSetting.setOnCheckedChangeListener { _, isChecked ->
                    onToggleChanged(item, isChecked)
                }
            } else {
                binding.switchSetting.visibility = View.GONE
                binding.ivChevron.visibility = View.VISIBLE
            }

            // 4. Click Listener
            // Replace with your standard or custom bounce click helper if available
            binding.root.setOnClickListener {
                onItemClicked(item)
            }

            // 5. Divider Renderer
            if (item.isLastInGroup) {
                binding.settingDivider.visibility = View.GONE
            } else {
                binding.settingDivider.visibility = View.VISIBLE
            }

            // 6. Dynamic Card Background Rounding
            // Ensure you import/implement `setRoundedCorners` and `toPx` extensions in your BlurFace project
            val radius = 16.toPx
            val cardBgColor = Color.WHITE // Adjust according to light/dark themes if necessary

            val isFirstInGroup = item.categoryHeader != null || bindingAdapterPosition == 0

            when {
                isFirstInGroup && item.isLastInGroup -> {
                    binding.cardRowContainer.setRoundedCorners(cardBgColor, radius, radius, radius, radius)
                }
                isFirstInGroup -> {
                    binding.cardRowContainer.setRoundedCorners(cardBgColor, topLeft = radius, topRight = radius)
                }
                item.isLastInGroup -> {
                    binding.cardRowContainer.setRoundedCorners(cardBgColor, bottomLeft = radius, bottomRight = radius)
                }
                else -> {
                    binding.cardRowContainer.setRoundedCorners(cardBgColor, 0f, 0f, 0f, 0f)
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SettingsItem>() {
            override fun areItemsTheSame(oldItem: SettingsItem, newItem: SettingsItem) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SettingsItem, newItem: SettingsItem) =
                oldItem == newItem
        }
    }
}