package com.example.blurface.domain.model

import android.graphics.Bitmap

class Person(seed: FaceInstance) {
    val id: Int = nextId++
    val instances = mutableListOf<FaceInstance>()

    // Running centroid embedding representation
    var centroid: FloatArray = seed.embedding.copyOf()
        private set

    var shouldBlur: Boolean = true

    init {
        add(seed)
    }

    fun add(inst: FaceInstance) {
        instances.add(inst)
        val n = instances.size
        for (i in centroid.indices) {
            centroid[i] = (centroid[i] * (n - 1) + inst.embedding[i]) / n
        }
    }

    fun representativeCrop(): Bitmap =
        instances.maxByOrNull { it.quality }!!.faceCrop

    companion object {
        private var nextId = 1
    }
}