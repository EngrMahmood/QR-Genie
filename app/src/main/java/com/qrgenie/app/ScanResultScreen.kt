package com.qrgenie.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.qrgenie.app.data.ScanHistoryRepository
import com.qrgenie.app.ui.theme.QRAppTheme
import kotlinx.coroutines.launch

@Composable
private fun BankDetailRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun connectToWifi(
    context: android.content.Context,
    wifi: QrContentType.Wifi,
    connectingMessage: String,
    openedSettingsMessage: String
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        try {
            val suggestionBuilder = android.net.wifi.WifiNetworkSuggestion.Builder()
                .setSsid(wifi.ssid)
            if (!wifi.password.isNullOrEmpty() && !wifi.encryption.equals("nopass", ignoreCase = true)) {
                suggestionBuilder.setWpa2Passphrase(wifi.password)
            }
            val suggestion = suggestionBuilder.build()
            val wifiManager = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            wifiManager.addNetworkSuggestions(listOf(suggestion))
            Toast.makeText(context, connectingMessage, Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) {
            // fall through to settings fallback below
        }
    }
    // Pre-Q, or if the suggestion API failed: copy the password and open Wi-Fi settings
    // so the user can connect manually - programmatic connect isn't reliably available otherwise.
    try {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("wifi_password", wifi.password ?: ""))
    } catch (_: Exception) {}
    try {
        context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        Toast.makeText(context, openedSettingsMessage, Toast.LENGTH_LONG).show()
    } catch (_: Exception) {}
}

class ScanResultActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_QR_CONTENT = "EXTRA_QR_CONTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qrText = intent.getStringExtra(EXTRA_QR_CONTENT) ?: ""
        try {
            lifecycleScope.launch {
                try { ScanHistoryRepository.insert(applicationContext, qrText, "scanned") } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        ReviewManager.maybePromptReview(this)
        setContent { QRAppTheme { ScanResultScreen(qrText) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(qrText: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val contentType = remember(qrText) { QrContentParser.parse(qrText) }
    val isUrl = contentType is QrContentType.Url

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
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label), tint = Color.White)
                    }
                    Text(
                        text = stringResource(R.string.scan_result_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Icon(
                imageVector = when (contentType) {
                    is QrContentType.Url -> Icons.Filled.Language
                    is QrContentType.Wifi -> Icons.Filled.Wifi
                    is QrContentType.Contact -> Icons.Filled.PersonAdd
                    is QrContentType.Email -> Icons.Filled.Email
                    is QrContentType.Phone -> Icons.Filled.Call
                    is QrContentType.Sms -> Icons.Filled.Message
                    is QrContentType.BankAccount -> Icons.Filled.AccountBalance
                    is QrContentType.Binary -> Icons.Filled.BrokenImage
                    is QrContentType.PlainText -> Icons.Filled.TextSnippet
                },
                contentDescription = null,
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
                    if (contentType is QrContentType.BankAccount) {
                        Text(
                            text = stringResource(R.string.bank_account_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!contentType.bankName.isNullOrBlank()) {
                                    BankDetailRow(stringResource(R.string.bank_name_label), contentType.bankName)
                                }
                                if (!contentType.accountTitle.isNullOrBlank()) {
                                    BankDetailRow(stringResource(R.string.bank_account_title_label), contentType.accountTitle)
                                }
                                if (!contentType.accountNumber.isNullOrBlank()) {
                                    BankDetailRow(stringResource(R.string.bank_account_number_label), contentType.accountNumber)
                                }
                                if (!contentType.iban.isNullOrBlank()) {
                                    BankDetailRow(stringResource(R.string.bank_iban_label), contentType.iban)
                                }
                            }
                        }
                    } else if (contentType is QrContentType.Binary) {
                        Text(
                            text = stringResource(R.string.binary_content_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.binary_content_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.decoded_content_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SelectionContainer {
                            Text(
                                text = qrText,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (contentType !is QrContentType.Binary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val copyText = if (contentType is QrContentType.BankAccount) {
                                listOfNotNull(
                                    contentType.bankName?.takeIf { it.isNotBlank() },
                                    contentType.accountTitle?.takeIf { it.isNotBlank() },
                                    contentType.accountNumber?.takeIf { it.isNotBlank() },
                                    contentType.iban?.takeIf { it.isNotBlank() }
                                ).joinToString("\n")
                            } else qrText
                            clipboardManager.setText(AnnotatedString(copyText))
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = copyLabel)
                        Spacer(Modifier.width(8.dp))
                        Text(if (contentType is QrContentType.BankAccount) stringResource(R.string.copy_account_details) else copyLabel)
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
            }

            when (contentType) {
                is QrContentType.Url -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            try {
                                val uri = qrText.toUri()
                                val scheme = uri.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } else {
                                    Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(openMagicLabel)
                    }
                }
                is QrContentType.Wifi -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    val connectingMsg = stringResource(R.string.wifi_connect_requested, contentType.ssid)
                    val openedSettingsMsg = stringResource(R.string.wifi_connect_opened_settings)
                    Button(
                        onClick = { connectToWifi(context, contentType, connectingMsg, openedSettingsMsg) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.connect_wifi))
                    }
                }
                is QrContentType.Contact -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                contentType.name?.let { putExtra(android.provider.ContactsContract.Intents.Insert.NAME, it) }
                                contentType.phone?.let { putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, it) }
                                contentType.email?.let { putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, it) }
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {
                                Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_contact))
                    }
                }
                is QrContentType.Email -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:${contentType.address}".toUri()
                                contentType.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                                contentType.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {
                                Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_email))
                    }
                }
                is QrContentType.Phone -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, "tel:${contentType.number}".toUri())
                            try { context.startActivity(intent) } catch (_: Exception) {
                                Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.call_number, contentType.number))
                    }
                }
                is QrContentType.Sms -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, "smsto:${contentType.number}".toUri()).apply {
                                contentType.body?.let { putExtra("sms_body", it) }
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {
                                Toast.makeText(context, invalidLinkStr, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Filled.Message, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_sms))
                    }
                }
                is QrContentType.BankAccount -> {}
                is QrContentType.Binary -> {}
                is QrContentType.PlainText -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val goToScan = {
                        val intent = Intent(context, ScanActivity::class.java)
                        context.startActivity(intent)
                        if (context is ComponentActivity) context.finish()
                    }
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        AdsManager.maybeShowInterstitial(activity) { goToScan() }
                    } else {
                        goToScan()
                    }
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