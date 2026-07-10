package com.example.blurface.ui.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.R
import com.example.blurface.domain.model.Person

class PersonClusterAdapter(
    private var dataList: List<Person> = emptyList()
) : RecyclerView.Adapter<PersonClusterAdapter.ClusterViewHolder>() {

    fun setIdentities(items: List<Person>) {
        this.dataList = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClusterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cluster_person, parent, false)
        return ClusterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClusterViewHolder, position: Int) = holder.bind(dataList[position])

    override fun getItemCount(): Int = dataList.size

    class ClusterViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val thumbnail: ImageView = v.findViewById(R.id.ivPersonIdentity)
        private val details: TextView = v.findViewById(R.id.tvOccurrences)

        fun bind(person: Person) {
            thumbnail.setImageBitmap(person.representativeCrop())
            details.text = "Seen in ${person.instances.size} frames"
        }
    }
}