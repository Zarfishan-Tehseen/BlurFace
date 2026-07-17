package com.example.blurface.ui.premium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blurface.R
import com.example.blurface.databinding.FragmentPremiumBinding
import com.example.blurface.domain.model.PremiumPlan

class PremiumFragment : Fragment() {

    private var _binding: FragmentPremiumBinding? = null
    private val binding get() = _binding!!

    private lateinit var plansAdapter: PremiumPlansAdapter
    private var selectedPlanId = "annual" // Keep track of the currently active plan selection

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPremiumBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Edge-to-edge system insets mapping
        ViewCompat.setOnApplyWindowInsetsListener(binding.premiumScrollView) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottomActions) { v, insets ->
            val systemNavigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = systemNavigation.bottom)
            insets
        }

        // 2. Setup plans list and RecyclerView
        val plansList = listOf(
            PremiumPlan(
                id = "annual",
                title = "Annual",
                subtitle = "Billed once a year",
                price = "$19.99",
                isPopular = true,
                iconResId = R.drawable.ic_crown
            ),
            PremiumPlan(
                id = "monthly",
                title = "Monthly",
                subtitle = "Billed every month",
                price = "$4.99",
                iconResId = R.drawable.ic_calendar
            ),
            PremiumPlan(
                id = "weekly",
                title = "Weekly",
                subtitle = "Billed every week",
                price = "$1.99",
                iconResId = R.drawable.ic_clock
            )
        )

        plansAdapter = PremiumPlansAdapter(plansList) { selectedPlan ->
            updateCtaButton(selectedPlan)
        }

        binding.rvPlans.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = plansAdapter
        }

        // 3. Setup click listeners
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAction.setOnClickListener {
            Toast.makeText(requireContext(), "Processing payment for: $selectedPlanId", Toast.LENGTH_SHORT).show()
        }

        binding.btnRestore.setOnClickListener {
            Toast.makeText(requireContext(), "Restoring purchase...", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic CTA texts based on selected plan
    private fun updateCtaButton(plan: PremiumPlan) {
        selectedPlanId = plan.id
        when (plan.id) {
            "annual" -> {
                binding.btnAction.text = "🔒 Start 7-Day Free Trial"
                binding.tvBillingDesc.text = "After 7-days, you'll be charged ${plan.price}/year. Cancel anytime."
            }
            "monthly" -> {
                binding.btnAction.text = "🔒 Get Monthly Access"
                binding.tvBillingDesc.text = "You will be charged ${plan.price}/month. Cancel anytime."
            }
            "weekly" -> {
                binding.btnAction.text = "🔒 Get Weekly Access"
                binding.tvBillingDesc.text = "You will be charged ${plan.price}/week. Cancel anytime."
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}