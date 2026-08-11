package com.qrgenie.app

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
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

private enum class GenerateType { TEXT, WIFI, CONTACT, BANK, EMAIL, SMS, LOCATION, EVENT }

private fun escapeWifiField(value: String): String =
    value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")

// QR foreground color choices. Background stays white for maximum scan reliability across
// devices/lighting - only the module color is customizable.
private val QR_COLOR_SWATCHES = listOf(
    Color(0xFF000000),
    Color(0xFF2962FF),
    Color(0xFF00A15C),
    Color(0xFF9C27B0),
    Color(0xFFE53935),
    Color(0xFFF57C00)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var bankName by rememberSaveable { mutableStateOf("") }
    var bankAccountTitle by rememberSaveable { mutableStateOf("") }
    var bankAccountNumber by rememberSaveable { mutableStateOf("") }
    var bankIban by rememberSaveable { mutableStateOf("") }
    var emailAddress by rememberSaveable { mutableStateOf("") }
    var emailSubject by rememberSaveable { mutableStateOf("") }
    var emailBody by rememberSaveable { mutableStateOf("") }
    var smsPhone by rememberSaveable { mutableStateOf("") }
    var smsMessage by rememberSaveable { mutableStateOf("") }
    var locationLat by rememberSaveable { mutableStateOf("") }
    var locationLng by rememberSaveable { mutableStateOf("") }
    var eventTitle by rememberSaveable { mutableStateOf("") }
    var eventLocation by rememberSaveable { mutableStateOf("") }
    var eventStart by rememberSaveable { mutableStateOf("") }
    var eventEnd by rememberSaveable { mutableStateOf("") }
    var qrColor by rememberSaveable { mutableStateOf(QR_COLOR_SWATCHES.first().toArgb()) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val storagePermissionDeniedMsg = stringResource(R.string.storage_permission_required)
    val savedToGalleryMsg = stringResource(R.string.saved_to_gallery_toast)
    val saveFailedMsg = stringResource(R.string.save_to_gallery_failed)
    val savePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            qrBitmap?.let { bmp ->
                val ok = QRCodeUtils.saveBitmapToGallery(context, bmp)
                Toast.makeText(context, if (ok) savedToGalleryMsg else saveFailedMsg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, storagePermissionDeniedMsg, Toast.LENGTH_SHORT).show()
        }
    }

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

            // QR content type picker - wraps to additional rows so every chip is always
            // fully visible instead of being clipped at the screen edge.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                FilterChip(
                    selected = selectedType == GenerateType.BANK,
                    onClick = { selectedType = GenerateType.BANK },
                    label = { Text(stringResource(R.string.qr_type_bank), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = selectedType == GenerateType.EMAIL,
                    onClick = { selectedType = GenerateType.EMAIL },
                    label = { Text(stringResource(R.string.qr_type_email), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = selectedType == GenerateType.SMS,
                    onClick = { selectedType = GenerateType.SMS },
                    label = { Text(stringResource(R.string.qr_type_sms), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Sms, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = selectedType == GenerateType.LOCATION,
                    onClick = { selectedType = GenerateType.LOCATION },
                    label = { Text(stringResource(R.string.qr_type_location), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = selectedType == GenerateType.EVENT,
                    onClick = { selectedType = GenerateType.EVENT },
                    label = { Text(stringResource(R.string.qr_type_event), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp)) }
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
                        colors = fieldColors,
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { pasted ->
                                    if (pasted.isNotEmpty()) text = pasted
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.paste_label))
                            }
                        }
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
                GenerateType.BANK -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text(stringResource(R.string.bank_name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = bankAccountTitle,
                            onValueChange = { bankAccountTitle = it },
                            label = { Text(stringResource(R.string.bank_account_title_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = bankAccountNumber,
                            onValueChange = { bankAccountNumber = it },
                            label = { Text(stringResource(R.string.bank_account_number_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = bankIban,
                            onValueChange = { bankIban = it },
                            label = { Text(stringResource(R.string.bank_iban_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                    }
                }
                GenerateType.EMAIL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = { emailAddress = it },
                            label = { Text(stringResource(R.string.email_address_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = emailSubject,
                            onValueChange = { emailSubject = it },
                            label = { Text(stringResource(R.string.email_subject_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = emailBody,
                            onValueChange = { emailBody = it },
                            label = { Text(stringResource(R.string.email_body_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors
                        )
                    }
                }
                GenerateType.SMS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = smsPhone,
                            onValueChange = { smsPhone = it },
                            label = { Text(stringResource(R.string.contact_phone_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = smsMessage,
                            onValueChange = { smsMessage = it },
                            label = { Text(stringResource(R.string.sms_message_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                    }
                }
                GenerateType.LOCATION -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = locationLat,
                            onValueChange = { locationLat = it },
                            label = { Text(stringResource(R.string.location_lat_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = locationLng,
                            onValueChange = { locationLng = it },
                            label = { Text(stringResource(R.string.location_lng_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                    }
                }
                GenerateType.EVENT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            label = { Text(stringResource(R.string.event_title_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = eventLocation,
                            onValueChange = { eventLocation = it },
                            label = { Text(stringResource(R.string.event_location_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = eventStart,
                            onValueChange = { eventStart = it },
                            label = { Text(stringResource(R.string.event_start_label)) },
                            placeholder = { Text(stringResource(R.string.event_datetime_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = eventEnd,
                            onValueChange = { eventEnd = it },
                            label = { Text(stringResource(R.string.event_end_label)) },
                            placeholder = { Text(stringResource(R.string.event_datetime_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = fieldColors
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // QR color picker
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.qr_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QR_COLOR_SWATCHES.forEach { swatch ->
                        val isSelected = swatch.toArgb() == qrColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (isSelected)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                    else Modifier
                                )
                                .clickable { qrColor = swatch.toArgb() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
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
                        GenerateType.BANK -> {
                            if (bankName.isBlank() && bankAccountTitle.isBlank() && bankAccountNumber.isBlank() && bankIban.isBlank()) "" else {
                                "BANKACCT:B:${escapeWifiField(bankName)};N:${escapeWifiField(bankAccountTitle)};A:${escapeWifiField(bankAccountNumber)};I:${escapeWifiField(bankIban)};;"
                            }
                        }
                        GenerateType.EMAIL -> {
                            if (emailAddress.isBlank()) "" else {
                                val params = mutableListOf<String>()
                                if (emailSubject.isNotBlank()) params.add("subject=${java.net.URLEncoder.encode(emailSubject, "UTF-8")}")
                                if (emailBody.isNotBlank()) params.add("body=${java.net.URLEncoder.encode(emailBody, "UTF-8")}")
                                val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
                                "mailto:$emailAddress$query"
                            }
                        }
                        GenerateType.SMS -> {
                            if (smsPhone.isBlank()) "" else "SMSTO:$smsPhone:$smsMessage"
                        }
                        GenerateType.LOCATION -> {
                            if (locationLat.isBlank() || locationLng.isBlank()) "" else "geo:$locationLat,$locationLng"
                        }
                        GenerateType.EVENT -> {
                            if (eventTitle.isBlank() || eventStart.isBlank()) "" else {
                                buildString {
                                    append("BEGIN:VEVENT\n")
                                    append("SUMMARY:$eventTitle\n")
                                    if (eventLocation.isNotBlank()) append("LOCATION:$eventLocation\n")
                                    append("DTSTART:$eventStart\n")
                                    if (eventEnd.isNotBlank()) append("DTEND:$eventEnd\n")
                                    append("END:VEVENT")
                                }
                            }
                        }
                    }
                    if (content.isNotBlank()) {
                        qrBitmap = generateQRCodeBitmap(content, foregroundColor = qrColor)
                        // save generated content into history (non-blocking)
                        try {
                            coroutineScope.launch {
                                try { ScanHistoryRepository.insert(context, content, "generated") } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                        (context as? android.app.Activity)?.let {
                            AdsManager.maybeShowInterstitial(it)
                            ReviewManager.maybePromptReview(it)
                        }
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
            if (qrBitmap == null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.generate_placeholder_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.generate_placeholder_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save to Gallery Button
                    val saveLabel = stringResource(R.string.save_to_gallery)
                    Button(
                        onClick = {
                            val needsPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
                                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                            if (needsPermission) {
                                savePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                val ok = QRCodeUtils.saveBitmapToGallery(context, bitmap)
                                Toast.makeText(context, if (ok) savedToGalleryMsg else saveFailedMsg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = saveLabel, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(saveLabel, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

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
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = shareImageText, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(shareImageText, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

fun generateQRCodeBitmap(text: String, size: Int = 512, foregroundColor: Int = android.graphics.Color.BLACK): Bitmap {
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
            bmp.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else android.graphics.Color.WHITE)
        }
    }
    return bmp
}