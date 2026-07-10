package com.example.blurface.data.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {
    private val interpreter: Interpreter
    private val inputSize = 112
    private val embeddingDim = 192

    init {
        val fd = context.assets.openFd("mobile_face_net.tflite")
        val input = fd.createInputStream()
        val channel = input.channel
        val model = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        val opts = Interpreter.Options().apply { numThreads = 4 }
        interpreter = Interpreter(model, opts)
    }

    fun embed(face: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(face, inputSize, inputSize, true)
        val input = convertToBuffer(resized)
        val output = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(input, output)
        return l2Normalize(output[0])
    }

    private fun convertToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            buffer.putFloat((r - 127.5f) / 128f)
            buffer.putFloat((g - 127.5f) / 128f)
            buffer.putFloat((b - 127.5f) / 128f)
        }
        buffer.rewind()
        return buffer
    }
    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm == 0f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    fun close() = interpreter.close()
}