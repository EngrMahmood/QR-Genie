package com.qrgenie.app.data

import android.content.Context

/**
 * Backwards-compatible repository facade that delegates to the file-backed HistoryStorage.
 */
object ScanHistoryRepository {
    suspend fun insert(context: Context, content: String, source: String = "scanned") = HistoryStorage.add(context, content, source)
    suspend fun delete(context: Context, item: ScanHistoryItem) = HistoryStorage.delete(context, item)
    suspend fun clearAll(context: Context) = HistoryStorage.clear(context)
    suspend fun restore(context: Context, items: List<ScanHistoryItem>) = HistoryStorage.replace(context, items)
    fun stateFlow() = HistoryStorage.state
}

