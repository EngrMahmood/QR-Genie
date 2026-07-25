package com.qrgenie.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qrgenie.app.data.ScanHistoryRepository
import com.qrgenie.app.ui.theme.QRAppTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class GenerateActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRAppTheme {
                GenerateScreen()
            }
        }
    }
}

private enum class GenerateType { TEXT, WIFI, CONTACT }

private fun escapeWifiField(value: String): String =
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen() {
    var text by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(GenerateType.TEXT) }
    var wifiSsid by rememberSaveable { mutableStateOf("") }
    var wifiPassword by rememberSaveable { mutableStateOf("") }
    var wifiOpenNetwork by rememberSaveable { mutableStateOf(false) }
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            // Modern Floating Emerald Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.primary, // Your Blue
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back_label), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.generate_button_text),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // QR content type picker - horizontally scrollable so chip labels never wrap
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == GenerateType.TEXT,
                    onClick = { selectedType = GenerateType.TEXT },
                    label = { Text(stringResource(R.string.qr_type_text), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = selectedType == GenerateType.WIFI,
                    onClick = { selectedType = GenerateType.WIFI },
                    label = { Text(stringResource(R.string.qr_type_wifi), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Wifi, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = selectedType == GenerateType.CONTACT,
                    onClick = { selectedType = GenerateType.CONTACT },
                    label = { Text(stringResource(R.string.qr_type_contact), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedLabelColor = MaterialTheme.colorScheme.secondary,
                cursorColor = MaterialTheme.colorScheme.secondary
            )

            when (selectedType) {
                GenerateType.TEXT -> {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.enter_text_or_link)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors
                    )
                }
                GenerateType.WIFI -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text(stringResource(R.string.wifi_ssid_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        if (!wifiOpenNetwork) {
                            OutlinedTextField(
                                value = wifiPassword,
                                onValueChange = { wifiPassword = it },
                                label = { Text(stringResource(R.string.wifi_password_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = fieldColors
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = wifiOpenNetwork, onCheckedChange = { wifiOpenNetwork = it })
                            Text(stringResource(R.string.wifi_open_network_label))
                        }
                    }
                }
                GenerateType.CONTACT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            label = { Text(stringResource(R.string.contact_name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            label = { Text(stringResource(R.string.contact_phone_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = contactEmail,
                            onValueChange = { contactEmail = it },
                            label = { Text(stringResource(R.string.contact_email_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Generate Button
            val emptyFieldToast = stringResource(R.string.please_enter_some_text)
            Button(
                onClick = {
                    val content = when (selectedType) {
                        GenerateType.TEXT -> text
                        GenerateType.WIFI -> {
                            if (wifiSsid.isBlank()) "" else {
                                val enc = if (wifiOpenNetwork) "nopass" else "WPA"
                                val passPart = if (wifiOpenNetwork) "" else "P:${escapeWifiField(wifiPassword)};"
                                "WIFI:T:$enc;S:${escapeWifiField(wifiSsid)};$passPart;"
                            }
                        }
                        GenerateType.CONTACT -> {
                            if (contactName.isBlank() && contactPhone.isBlank() && contactEmail.isBlank()) "" else {
                                buildString {
                                    append("BEGIN:VCARD\nVERSION:3.0\n")
                                    if (contactName.isNotBlank()) append("FN:$contactName\n")
                                    if (contactPhone.isNotBlank()) append("TEL:$contactPhone\n")
                                    if (contactEmail.isNotBlank()) append("EMAIL:$contactEmail\n")
                                    append("END:VCARD")
                                }
                            }
                        }
                    }
                    if (content.isNotBlank()) {
                        qrBitmap = generateQRCodeBitmap(content)
                        // save generated content into history (non-blocking)
                        try {
                            coroutineScope.launch {
                                try { ScanHistoryRepository.insert(context, content, "generated") } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    } else {
                        Toast.makeText(context, emptyFieldToast, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.generate_button_text), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Result Display
            qrBitmap?.let { bitmap ->
                ElevatedCard(
                    modifier = Modifier.size(280.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_code_preview),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Share Button
                val shareImageText = stringResource(R.string.share_image_text)
                Button(
                    onClick = {
                        val uri = QRCodeUtils.saveBitmapToCacheAndGetUri(context, bitmap)
                        if (uri != null) {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, shareImageText))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = shareImageText, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Text(shareImageText, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun generateQRCodeBitmap(text: String, size: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    // Ensure QR encodes text using UTF-8 so languages like Urdu/Arabic and other special characters are preserved
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
    )
    val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}