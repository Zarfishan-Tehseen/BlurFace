package com.example.blurface.ui.video.blureditor

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemSelectedFaceBinding
import com.example.blurface.domain.model.Person

/**
 * Strip shown at the top of VideoBlurEditorFragment - the people the user already
 * chose to blur on DetectedFacesFragment. The person LIST itself never changes on this
 * screen (no toggling here), but what's RENDERED for each face does, live, as the user
 * adjusts blur type/shape/intensity/feather - see updatePreviews().
 */
class SelectedFacesAdapter :
    ListAdapter<Person, SelectedFacesAdapter.ViewHolder>(DIFF) {

    // personId -> current effect-applied preview bitmap. Separate from the ListAdapter's
    // own diffing (which is only about which people are present) since every item can
    // re-render together on a single settings change - notifyDataSetChanged() below is
    // intentional, not a DiffUtil workaround.
    private var previewBitmaps: Map<Int, Bitmap> = emptyMap()

    /** Call whenever VideoFaceEffectProcessor produces a fresh batch of previews. */
    fun updatePreviews(bitmaps: Map<Int, Bitmap>) {
        previewBitmaps = bitmaps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSelectedFaceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), previewBitmaps[getItem(position).id])
    }

    class ViewHolder(private val binding: ItemSelectedFaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(person: Person, previewBitmap: Bitmap?) {
            // Falls back to the raw crop until the first preview batch has rendered.
            binding.ivFaceThumb.setImageBitmap(previewBitmap ?: person.representativeCrop())
            binding.tvFaceLabel.text = "Face ${person.id}"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Person>() {
            override fun areItemsTheSame(oldItem: Person, newItem: Person) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Person, newItem: Person) =
                oldItem.shouldBlur == newItem.shouldBlur
        }
    }
}