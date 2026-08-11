package com.qrgenie.app.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qrgenie.app.R
import com.qrgenie.app.LocalizedComponentActivity
import com.qrgenie.app.ui.theme.QRAppTheme
import com.qrgenie.app.data.HistoryStorage
import androidx.lifecycle.lifecycleScope
import com.qrgenie.app.data.ScanHistoryRepository
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class HistoryActivity : LocalizedComponentActivity() {
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val historyClearedText = stringResource(R.string.history_cleared)
    val undoText = stringResource(R.string.undo)

    val showClearConfirm = remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }

    val items = itemsState.filter { item ->
        (sourceFilter == null || item.source == sourceFilter) &&
            (!showFavoritesOnly || item.isFavorite) &&
            (searchQuery.isBlank() || item.content.contains(searchQuery, ignoreCase = true))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label), tint = Color.White)
                        }
                        Text(
                            text = stringResource(R.string.history_label).uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                    IconButton(onClick = { showClearConfirm.value = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clear_all), tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        if (itemsState.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_history_yet))
            }
            return@Scaffold
        }

        if (showClearConfirm.value) {
            AlertDialog(
                onDismissRequest = { showClearConfirm.value = false },
                title = { Text(stringResource(R.string.clear_history_title)) },
                text = { Text(stringResource(R.string.clear_history_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearConfirm.value = false
                        val prev = itemsState.toList()
                        coroutineScope.launch { ScanHistoryRepository.clearAll(context) }
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(historyClearedText, actionLabel = undoText)
                            if (result == SnackbarResult.ActionPerformed) {
                                try { ScanHistoryRepository.restore(context, prev) } catch (_: Exception) {}
                            }
                        }
                    }) { Text(stringResource(R.string.clear)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm.value = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_history_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = sourceFilter == null,
                    onClick = { sourceFilter = null },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = sourceFilter == "scanned",
                    onClick = { sourceFilter = "scanned" },
                    label = { Text(stringResource(R.string.source_scanned)) }
                )
                FilterChip(
                    selected = sourceFilter == "generated",
                    onClick = { sourceFilter = "generated" },
                    label = { Text(stringResource(R.string.source_generated)) }
                )
                FilterChip(
                    selected = showFavoritesOnly,
                    onClick = { showFavoritesOnly = !showFavoritesOnly },
                    label = { Text(stringResource(R.string.favorites_label)) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(18.dp)) }
                )
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_matching_history))
                }
                return@Column
            }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_label)))
                }, onDelete = {
                    coroutineScope.launch { ScanHistoryRepository.delete(context, it) }
                }, onToggleFavorite = {
                    coroutineScope.launch { ScanHistoryRepository.toggleFavorite(context, it) }
                })
            }
        }
        }
    }
}

@Composable
fun HistoryRow(
    item: com.qrgenie.app.data.ScanHistoryItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
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
                contentDescription = if (item.source == "generated") stringResource(R.string.source_generated) else stringResource(R.string.source_scanned),
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
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(R.string.favorites_label),
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onOpen) { Icon(Icons.Filled.OpenInNew, contentDescription = stringResource(R.string.open)) }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_label)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
        }
    }
}
