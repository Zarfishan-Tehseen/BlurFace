package com.example.blurface.ui.premium

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.blurface.R
import com.example.blurface.databinding.ItemPremiumPlanBinding
import com.example.blurface.domain.model.PremiumPlan

class PremiumPlansAdapter(
    private val plans: List<PremiumPlan>,
    private val onPlanSelected: (PremiumPlan) -> Unit
) : RecyclerView.Adapter<PremiumPlansAdapter.PlanViewHolder>() {

    private var selectedPosition = 0 // Default selection (Annual)

    inner class PlanViewHolder(val binding: ItemPremiumPlanBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val binding = ItemPremiumPlanBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val plan = plans[position]
        val isSelected = position == selectedPosition
        val context = holder.itemView.context

        holder.binding.apply {
            tvPlanTitle.text = plan.title
            tvPlanSubtitle.text = plan.subtitle
            tvPlanPrice.text = plan.price
            ivPlanIcon.setImageResource(plan.iconResId)

            // Dynamic "Most Popular" visibility
            badgeMostPopular.visibility = if (plan.isPopular) View.VISIBLE else View.GONE

            // Handle Selection Visuals
            rbPlan.isChecked = isSelected
            if (isSelected) {
                cardPlanContainer.setBackgroundResource(R.drawable.bg_premium_card_selected)
                ivPlanIcon.background = ContextCompat.getDrawable(context, R.drawable.bg_icon_circle_purple_gradient)
                ivPlanIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
                tvPlanPrice.setTextColor(ContextCompat.getColor(context, R.color.purple_primary))
            } else {
                cardPlanContainer.setBackgroundResource(R.drawable.bg_premium_card_unselected)
                ivPlanIcon.background = ContextCompat.getDrawable(context, R.drawable.bg_icon_circle_purple)
                ivPlanIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.purple_light))
                tvPlanPrice.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            // Click listener for entire item card
            root.setOnClickListener {
                if (selectedPosition != holder.adapterPosition) {
                    val previousSelected = selectedPosition
                    selectedPosition = holder.adapterPosition
                    notifyItemChanged(previousSelected)
                    notifyItemChanged(selectedPosition)
                    onPlanSelected(plan)
                }
            }
        }
    }

    override fun getItemCount(): Int = plans.size
}