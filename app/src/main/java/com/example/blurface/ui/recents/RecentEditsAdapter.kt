package com.example.blurface.ui.recents

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemRecentEditHeaderBinding
import com.example.blurface.databinding.ItemRecentEditRowBinding
import com.example.blurface.domain.model.RecentEdit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class RecentsListItem {
    data class Header(val label: String) : RecentsListItem()
    data class Row(val edit: RecentEdit) : RecentsListItem()
}

class RecentEditsAdapter(
    private val onMoreClicked: (RecentEdit, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<RecentsListItem> = emptyList()

    fun submitList(edits: List<RecentEdit>) {
        items = buildSectionedList(edits)
        notifyDataSetChanged()
    }

    private fun buildSectionedList(edits: List<RecentEdit>): List<RecentsListItem> {
        val result = mutableListOf<RecentsListItem>()
        var lastLabel: String? = null
        edits.forEach { edit ->
            val label = sectionLabelFor(edit.timestampMillis)
            if (label != lastLabel) {
                result.add(RecentsListItem.Header(label))
                lastLabel = label
            }
            result.add(RecentsListItem.Row(edit))
        }
        return result
    }

    private fun sectionLabelFor(timestampMillis: Long): String {
        val editCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(editCal, today) -> "Today"
            isSameDay(editCal, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(editCal.time)
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is RecentsListItem.Header -> TYPE_HEADER
            is RecentsListItem.Row -> TYPE_ROW
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemRecentEditHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowViewHolder(ItemRecentEditRowBinding.inflate(inflater, parent, false), onMoreClicked)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is RecentsListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is RecentsListItem.Row -> (holder as RowViewHolder).bind(item.edit)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(private val binding: ItemRecentEditHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: RecentsListItem.Header) {
            binding.root.text = header.label
        }
    }

    class RowViewHolder(
        private val binding: ItemRecentEditRowBinding,
        private val onMoreClicked: (RecentEdit, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(edit: RecentEdit) {
            binding.tvTitle.text = edit.title
            binding.tvEditType.text = edit.editType.label

            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(edit.timestampMillis)
            val sizeMb = edit.fileSizeBytes / (1024.0 * 1024.0)
            binding.tvMeta.text = String.format(Locale.US, "%s · %.1f MB", time, sizeMb)

            runCatching { binding.ivThumbnail.setImageURI(android.net.Uri.parse(edit.mediaUri)) }

            binding.btnMore.setOnClickListener { onMoreClicked(edit, it) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}