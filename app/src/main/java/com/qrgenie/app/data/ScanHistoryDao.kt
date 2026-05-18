package com.qrgenie.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Simple file-backed history storage used instead of Room to avoid annotation-processing issues.
 * Stores entries in JSON under filesDir/scan_history.json and exposes a StateFlow for observation.
 */
object HistoryStorage {
    private const val FILE_NAME = "scan_history.json"
    private val _state = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val state = _state.asStateFlow()

    suspend fun load(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (!file.exists()) {
                    _state.value = emptyList()
                    return@withContext
                }
                val txt = file.readText()
                val arr = JSONArray(txt)
                val list = mutableListOf<ScanHistoryItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optLong("id", i.toLong())
                    val content = o.optString("content", "")
                    val ts = o.optLong("timestamp", 0L)
                    val source = o.optString("source", "scanned")
                    list.add(ScanHistoryItem(id = id, content = content, timestamp = ts, source = source))
                }
                _state.value = list
            } catch (_: Exception) {
                _state.value = emptyList()
            }
        }
    }

    private suspend fun persist(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                val arr = JSONArray()
                _state.value.forEach { e ->
                    val o = JSONObject()
                    o.put("id", e.id)
                    o.put("content", e.content)
                    o.put("timestamp", e.timestamp)
                    o.put("source", e.source)
                    arr.put(o)
                }
                file.writeText(arr.toString())
            } catch (_: Exception) {}
        }
    }

    suspend fun add(context: Context, content: String, source: String = "scanned") {
        withContext(Dispatchers.IO) {
            val list = _state.value.toMutableList()
            // Deduplicate generated entries: skip if the most recent entry is also generated and identical
            if (source == "generated" && list.isNotEmpty()) {
                val first = list.first()
                if (first.source == "generated" && first.content == content) {
                    // skip adding duplicate generated entry
                    return@withContext
                }
            }
            val id = if (list.isEmpty()) 1L else (list.maxOf { it.id } + 1L)
            val entry = ScanHistoryItem(id = id, content = content, timestamp = System.currentTimeMillis(), source = source)
            list.add(0, entry)
            _state.value = list
            persist(context)
        }
    }

    suspend fun replace(context: Context, newList: List<ScanHistoryItem>) {
        withContext(Dispatchers.IO) {
            _state.value = newList
            persist(context)
        }
    }

    suspend fun delete(context: Context, entry: ScanHistoryItem) {
        withContext(Dispatchers.IO) {
            val list = _state.value.toMutableList()
            list.removeAll { it.id == entry.id }
            _state.value = list
            persist(context)
        }
    }

    suspend fun clear(context: Context) {
        withContext(Dispatchers.IO) {
            _state.value = emptyList()
            persist(context)
        }
    }
}

