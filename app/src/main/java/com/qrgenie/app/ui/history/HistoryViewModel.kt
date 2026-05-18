package com.qrgenie.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.qrgenie.app.data.ScanHistoryItem
import com.qrgenie.app.data.ScanHistoryRepository
import com.qrgenie.app.data.HistoryStorage
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    // Observe the storage StateFlow as LiveData for Compose interop
    val items: LiveData<List<ScanHistoryItem>> = HistoryStorage.state.asLiveData()

    fun delete(item: ScanHistoryItem) {
        viewModelScope.launch { ScanHistoryRepository.delete(getApplication(), item) }
    }

    fun clearAll() {
        viewModelScope.launch { ScanHistoryRepository.clearAll(getApplication()) }
    }

    fun refresh() {
        viewModelScope.launch { HistoryStorage.load(getApplication()) }
    }
}

