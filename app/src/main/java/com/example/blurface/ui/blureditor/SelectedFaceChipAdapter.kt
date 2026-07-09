package com.example.blurface.ui.blureditor

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemSelectedFaceChipBinding

class SelectedFaceChipAdapter(
    private val onRemove: (faceId: Int) -> Unit
) : ListAdapter<SelectedFaceChipAdapter.Item, SelectedFaceChipAdapter.ViewHolder>(DiffCallback) {

    data class Item(val faceId: Int, val thumbnail: Bitmap)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectedFaceChipBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSelectedFaceChipBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.ivFaceChip.setImageBitmap(item.thumbnail)
            binding.btnRemove.setOnClickListener { onRemove(item.faceId) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.faceId == newItem.faceId
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }
}