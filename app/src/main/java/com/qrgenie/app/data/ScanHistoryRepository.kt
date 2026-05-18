package com.qrgenie.app.data

import android.content.Context

/**
 * Backwards-compatible repository facade that delegates to the file-backed HistoryStorage.
 */
object ScanHistoryRepository {
    suspend fun insert(context: Context, content: String) = HistoryStorage.add(context, content)
    suspend fun delete(context: Context, item: ScanHistoryItem) = HistoryStorage.delete(context, item)
    suspend fun clearAll(context: Context) = HistoryStorage.clear(context)
    fun stateFlow() = HistoryStorage.state
}

