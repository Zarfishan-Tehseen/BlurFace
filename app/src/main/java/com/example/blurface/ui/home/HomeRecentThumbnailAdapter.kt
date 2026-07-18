package com.example.blurface.ui.home

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemRecentEditBinding
import com.example.blurface.domain.model.RecentEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeRecentThumbnailAdapter(
    private val scope: CoroutineScope,
    private val onMoreClicked: (RecentEdit, View) -> Unit
) : ListAdapter<RecentEdit, HomeRecentThumbnailAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentEditBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, scope, onMoreClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemRecentEditBinding,
        private val scope: CoroutineScope,
        private val onMoreClicked: (RecentEdit, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentJob: Job? = null

        fun bind(edit: RecentEdit) {
            currentJob?.cancel()
            binding.ivThumbnail.setImageBitmap(null)
            val mediaUri = Uri.parse(edit.mediaUri)

            if (edit.isVideo) {
                currentJob = scope.launch {
                    val thumbnail = withContext(Dispatchers.IO) {
                        getVideoThumbnail(itemView.context, mediaUri)
                    }
                    if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
                    if (thumbnail != null) {
                        binding.ivThumbnail.setImageBitmap(thumbnail)
                    } else {
                        binding.ivThumbnail.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            } else {
                runCatching { binding.ivThumbnail.setImageURI(mediaUri) }
            }
            binding.btnMore.setOnClickListener { onMoreClicked(edit, it) }
        }

        private fun getVideoThumbnail(context: android.content.Context, uri: Uri): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecentEdit>() {
            override fun areItemsTheSame(oldItem: RecentEdit, newItem: RecentEdit) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: RecentEdit, newItem: RecentEdit) = oldItem == newItem
        }
    }
}