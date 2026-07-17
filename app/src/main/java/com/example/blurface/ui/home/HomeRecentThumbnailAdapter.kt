package com.example.blurface.ui.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemRecentEditBinding
import com.example.blurface.domain.model.RecentEdit

class HomeRecentThumbnailAdapter(
    private val onMoreClicked: (RecentEdit, View) -> Unit
) : ListAdapter<RecentEdit, HomeRecentThumbnailAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentEditBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(edit: RecentEdit) {
            runCatching { binding.ivThumbnail.setImageURI(Uri.parse(edit.mediaUri)) }
            binding.btnMore.setOnClickListener { onMoreClicked(edit, it) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecentEdit>() {
            override fun areItemsTheSame(oldItem: RecentEdit, newItem: RecentEdit) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: RecentEdit, newItem: RecentEdit) = oldItem == newItem
        }
    }
}