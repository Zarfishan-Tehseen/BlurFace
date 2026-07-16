package com.example.blurface.data.history

import android.content.Context
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecentEditsStore(context: Context) {

    private val file = File(context.filesDir, "recent_edits.json")

    @Synchronized
    fun getAll(): List<RecentEdit> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i -> array.getJSONObject(i).toRecentEdit() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(edit: RecentEdit) {
        val current = getAll().toMutableList()
        current.add(0, edit) // newest first
        writeAll(current)
    }

    @Synchronized
    fun delete(id: String) {
        val current = getAll().filterNot { it.id == id }
        writeAll(current)
    }

    private fun writeAll(edits: List<RecentEdit>) {
        val array = JSONArray()
        edits.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    private fun RecentEdit.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("editType", editType.name)
        put("mediaUri", mediaUri)
        put("isVideo", isVideo)
        put("timestampMillis", timestampMillis)
        put("fileSizeBytes", fileSizeBytes)
    }

    private fun JSONObject.toRecentEdit(): RecentEdit = RecentEdit(
        id = getString("id"),
        title = getString("title"),
        editType = EditType.valueOf(getString("editType")),
        mediaUri = getString("mediaUri"),
        isVideo = getBoolean("isVideo"),
        timestampMillis = getLong("timestampMillis"),
        fileSizeBytes = getLong("fileSizeBytes")
    )
}