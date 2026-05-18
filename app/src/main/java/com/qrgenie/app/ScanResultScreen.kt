package com.qrgenie.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.qrgenie.app.data.ScanHistoryRepository
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qrgenie.app.ui.theme.QRAppTheme

class ScanResultActivity : ComponentActivity() {
    companion object {
        const val EXTRA_QR_CONTENT = "EXTRA_QR_CONTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qrText = intent.getStringExtra(EXTRA_QR_CONTENT) ?: ""
        // Record scan into history (minimal: content + timestamp)
        try {
            lifecycleScope.launch {
                try {
                    ScanHistoryRepository.insert(applicationContext, qrText)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        setContent {
            QRAppTheme {
                ScanResultScreen(qrText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(qrText: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isUrl = try {
        val uri = qrText.toUri()
        val scheme = uri.scheme?.lowercase()
        scheme == "http" || scheme == "https"
    } catch (e: Exception) {
        false
    }
    val invalidLinkStr = stringResource(R.string.invalid_link)
    val copyLabel = stringResource(R.string.copy_label)
    val shareLabel = stringResource(R.string.share_label)
    val openMagicLabel = stringResource(R.string.open_magic_link)
    val copiedToast = stringResource(R.string.copied_to_clipboard)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        "SCAN RESULT",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // Added verticalScroll to ensure buttons aren't cut off
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

                    Icon(
                        imageVector = if (isUrl) Icons.Filled.Language else Icons.Filled.TextSnippet,
                        contentDescription = if (isUrl) stringResource(R.string.open_magic_link) else stringResource(R.string.copy_label),
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )

            Spacer(modifier = Modifier.height(32.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "DECODED CONTENT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer { // Allows user to manually select text if they want
                        Text(
                            text = qrText,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(qrText))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                    Spacer(Modifier.width(8.dp))
                    Text(copyLabel)
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, qrText)
                        }
                        context.startActivity(Intent.createChooser(intent, shareLabel))
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = shareLabel)
                    Spacer(Modifier.width(8.dp))
                    Text(shareLabel)
                }
            }

            if (isUrl) {
                Spacer(modifier = Modifier.height(16.dp))
                    Button(
                    onClick = {
                        try {
                            val uri = qrText.toUri()
                            val scheme = uri.scheme?.lowercase()
                            if (scheme == "http" || scheme == "https") {
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)) // Forest Green
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = openMagicLabel)
                    Spacer(Modifier.width(8.dp))
                    Text(openMagicLabel)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            // Offer quick 'Scan Again' so user doesn't have to navigate from home
                Button(
                onClick = {
                    // Relaunch ScanActivity
                    val intent = Intent(context, ScanActivity::class.java)
                    context.startActivity(intent)
                    if (context is ComponentActivity) context.finish()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.scan_again))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_again))
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}