package com.qrgenie.app.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qrgenie.app.ui.theme.QRAppTheme
import com.qrgenie.app.data.HistoryStorage
import androidx.lifecycle.lifecycleScope
import com.qrgenie.app.data.ScanHistoryRepository
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            lifecycleScope.launch { HistoryStorage.load(this@HistoryActivity) }
        } catch (_: Exception) {}
        setContent { QRAppTheme { HistoryScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val itemsState by HistoryStorage.state.collectAsState()
    val items = itemsState
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val showClearConfirm = remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Scan History") },
                actions = {
                    IconButton(onClick = { showClearConfirm.value = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear all")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No history yet")
            }
            return@Scaffold
        }

        if (showClearConfirm.value) {
            AlertDialog(
                onDismissRequest = { showClearConfirm.value = false },
                title = { Text("Clear history") },
                text = { Text("This will permanently delete all history. Are you sure?") },
                confirmButton = {
                    TextButton(onClick = {
                        showClearConfirm.value = false
                        val prev = items.toList()
                        coroutineScope.launch { ScanHistoryRepository.clearAll(context) }
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar("History cleared", actionLabel = "Undo")
                            if (result == SnackbarResult.ActionPerformed) {
                                try { ScanHistoryRepository.restore(context, prev) } catch (_: Exception) {}
                            }
                        }
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm.value = false }) { Text("Cancel") }
                }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(items) { it ->
                HistoryRow(item = it, onOpen = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it.content))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }, onShare = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, it.content)
                    }
                    context.startActivity(Intent.createChooser(send, "Share"))
                }, onDelete = {
                    coroutineScope.launch { ScanHistoryRepository.delete(context, it) }
                })
            }
        }
    }
}

@Composable
fun HistoryRow(
    item: com.qrgenie.app.data.ScanHistoryItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            // source icon
            Icon(
                imageVector = if (item.source == "generated") Icons.Filled.AutoAwesome else Icons.Filled.QrCodeScanner,
                contentDescription = item.source,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 8.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(item.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Text(sdf.format(Date(item.timestamp)), style = MaterialTheme.typography.labelSmall)
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onOpen) { Icon(Icons.Filled.OpenInNew, contentDescription = "Open") }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
    }
}
