package com.example.blurface.domain.model

data class PremiumPlan(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val isPopular: Boolean = false,
    val iconResId: Int
)