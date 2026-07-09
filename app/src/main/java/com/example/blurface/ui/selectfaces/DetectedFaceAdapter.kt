package com.example.blurface.ui.selectfaces

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemDetectedFaceBinding
import com.example.blurface.domain.model.DetectedFace

class DetectedFaceAdapter(
    private val onFaceClicked: (DetectedFace) -> Unit,
    private val onCheckboxClicked: (DetectedFace) -> Unit
) : ListAdapter<DetectedFaceAdapter.Item, DetectedFaceAdapter.ViewHolder>(DiffCallback) {

    data class Item(val face: DetectedFace, val thumbnail: Bitmap?)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetectedFaceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDetectedFaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            val face = item.face
            binding.tvFaceLabel.text = "Face ${face.id + 1}"
            binding.cbSelected.isChecked = face.isSelected
            item.thumbnail?.let { binding.ivFaceThumb.setImageBitmap(it) }

            binding.root.setOnClickListener { onFaceClicked(face) }
            binding.cbSelected.setOnClickListener { onCheckboxClicked(face) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) =
            oldItem.face.id == newItem.face.id

        override fun areContentsTheSame(oldItem: Item, newItem: Item) =
            oldItem.face.isSelected == newItem.face.isSelected
    }
}