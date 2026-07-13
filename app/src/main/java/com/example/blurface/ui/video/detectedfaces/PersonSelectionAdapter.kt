package com.example.blurface.ui.video.detectedfaces

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.databinding.ItemDetectedVideoFaceBinding
import com.example.blurface.domain.model.Person

class PersonSelectionAdapter(
    private val fps: Int,
    private val onToggle: (Person) -> Unit,
    private val onRowClicked: (Person) -> Unit
) : RecyclerView.Adapter<PersonSelectionAdapter.ViewHolder>() {

    private var people: List<Person> = emptyList()

    fun submitList(newPeople: List<Person>) {
        people = newPeople
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetectedVideoFaceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(people[position])
    }

    override fun getItemCount(): Int = people.size

    inner class ViewHolder(private val binding: ItemDetectedVideoFaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(person: Person) {
            binding.tvFaceLabel.text = "Face ${person.id}"
            binding.ivFaceThumb.setImageBitmap(person.representativeCrop())

            binding.cbSelected.isChecked = person.shouldBlur

            binding.tvSceneCount.text = "Appears in ${person.instances.size} scenes"

            val frameIds = person.instances.map { it.frameId }
            val startSec = (frameIds.minOrNull() ?: 0) / fps
            val endSec = (frameIds.maxOrNull() ?: 0) / fps
            binding.tvTimeRange.text = "${formatTime(startSec)} - ${formatTime(endSec)}"

            binding.cbSelected.setOnClickListener {
                person.shouldBlur = binding.cbSelected.isChecked
                onToggle(person)
            }
            binding.root.setOnClickListener { onRowClicked(person) }
        }

        private fun formatTime(totalSeconds: Int): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return String.format("%02d:%02d:%02d", h, m, s)
        }
    }
}