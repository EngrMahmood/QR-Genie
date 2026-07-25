package com.qrgenie.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.qrgenie.app.ui.theme.QRAppTheme
import java.util.Locale

private data class SettingsLanguageOption(val tag: String, val label: String)

private val SETTINGS_LANGUAGES = listOf(
    SettingsLanguageOption("en", "English"),
    SettingsLanguageOption("ar", "العربية"),
    SettingsLanguageOption("ur", "اردو"),
    SettingsLanguageOption("hi", "हिंदी"),
    SettingsLanguageOption("bn", "বাংলা"),
    SettingsLanguageOption("fa", "فارسی"),
    SettingsLanguageOption("tr", "Türkçe"),
    SettingsLanguageOption("fr", "Français"),
    SettingsLanguageOption("de", "Deutsch"),
    SettingsLanguageOption("es", "Español"),
    SettingsLanguageOption("zh", "中文")
)

private const val PRIVACY_POLICY_URL = "https://sites.google.com/view/qrgenieprivacypolicy/home"

class SettingsActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QRAppTheme { SettingsScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var currentLocaleTag by remember { mutableStateOf(AppLanguageManager.getCurrentLanguageTag()) }
    var currentThemeMode by remember { mutableStateOf(ThemeManager.getCurrentMode()) }

    val appVersion = remember(context) {
        try {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            "v${info.versionName}"
        } catch (e: Exception) {
            "v1.0.0"
        }
    }

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
                        stringResource(R.string.settings_title),
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
                .fillMaxWidth()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            SettingsSection(title = stringResource(R.string.settings_theme_section)) {
                ThemeOptionRow(ThemeMode.SYSTEM, stringResource(R.string.theme_system), currentThemeMode) {
                    currentThemeMode = it
                    ThemeManager.applyMode(it)
                }
                ThemeOptionRow(ThemeMode.LIGHT, stringResource(R.string.theme_light), currentThemeMode) {
                    currentThemeMode = it
                    ThemeManager.applyMode(it)
                }
                ThemeOptionRow(ThemeMode.DARK, stringResource(R.string.theme_dark), currentThemeMode) {
                    currentThemeMode = it
                    ThemeManager.applyMode(it)
                }
            }

            SettingsSection(title = stringResource(R.string.settings_language_section)) {
                SETTINGS_LANGUAGES.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.tag == currentLocaleTag,
                                onClick = {
                                    AppLanguageManager.applyLanguageTag(option.tag)
                                    currentLocaleTag = option.tag
                                    (context as? ComponentActivity)?.recreate()
                                }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.tag.uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        if (option.tag == currentLocaleTag) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_about_section)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_version_label), style = MaterialTheme.typography.bodyLarge)
                    Text(appVersion, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = false, onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
                            } catch (_: Exception) {}
                        })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_privacy_policy), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = false, onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=${context.packageName}".toUri())
                                )
                            } catch (_: Exception) {}
                        })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.settings_rate_app), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(mode: ThemeMode, label: String, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = mode == current, onClick = { onSelect(mode) })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
