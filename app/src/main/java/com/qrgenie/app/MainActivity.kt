package com.qrgenie.app

import android.app.Activity
// ...existing code...
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
// ...existing code...
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Added missing import
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.qrgenie.app.ui.theme.OnSecondary
import com.qrgenie.app.ui.theme.QRAppTheme
import com.qrgenie.app.ui.theme.Secondary
import com.google.firebase.messaging.FirebaseMessaging
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
    import com.qrgenie.app.ui.history.HistoryActivity
import androidx.compose.material.icons.filled.List

class MainActivity : ComponentActivity() {
    private lateinit var appUpdateManager: AppUpdateManager
    // keep a reference so we can unregister the listener
    private var installStateListener: InstallStateUpdatedListener? = null
    // Activity Result launcher for Play Core intent sender (migrates away from onActivityResult)
    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure any vendor native libraries extracted into app files are loaded via shim
        try {
            NativeShim.ensureLoaded(this)
        } catch (ignored: Exception) {}

        // Initialize Update Manager and check for updates
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()

        // Subscribe to release notifications topic (optional - requires Firebase setup)
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("releases")
        } catch (ignored: Exception) {}

        // Register ActivityResult launcher for the in-app update IntentSender flow.
        // This is the modern Activity Result API replacement for onActivityResult.
        updateLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                // Update flow was cancelled or failed; inform user (non-blocking)
                Toast.makeText(this, "Update was cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            QRAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        onScan = { startActivity(Intent(this, ScanActivity::class.java)) },
                        onGenerate = { startActivity(Intent(this, GenerateActivity::class.java)) }
                    )
                }
            }
        }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Listener to handle the "Downloaded" state for Flexible updates
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                // Once downloaded, notify the user to restart
                Toast.makeText(
                    this,
                    "Update downloaded! Restarting to install...",
                    Toast.LENGTH_LONG
                ).show()
                appUpdateManager.completeUpdate()
            }
        }
        // keep reference for lifecycle cleanup
        installStateListener = listener

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.registerListener(listener)
                try {
                    @Suppress("DEPRECATION")
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.FLEXIBLE,
                        this,
                        1001
                    )
                } catch (_: Exception) {
                }
            }
        }
    }




    // Crucial: Check if an update download finished while the app was in background
    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    // Migrated to Activity Result API; no onActivityResult override needed anymore.

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the listener to avoid leaks
        try {
            installStateListener?.let { appUpdateManager.unregisterListener(it) }
        } catch (ignored: Exception) {
        }
    }
}
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HomeScreen(onScan: () -> Unit, onGenerate: () -> Unit) {
        val context = LocalContext.current
        val shareChooserTitle = stringResource(R.string.share_chooser_title)

        // Version fetch logic
        val appVersion = remember(context) {
            try {
                val pm = context.packageManager
                val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
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
                val context = LocalContext.current // Get context here for the share logic

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 12.dp,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.padding(4.dp).fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "QR GENIE",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "MAGICALLY FAST",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.6f),
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Corrected Share Button with inline logic
                        Row {
                        Surface(
                            onClick = {
                                val shareMessage = """
            Check out QR Genie! 🪄
            
            It's the fastest way to handle QRs.
            Download it here: https://play.google.com/store/apps/details?id=${context.packageName}
        """.trimIndent()

                                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                                    type = "text/plain"
                                }
                                                val shareIntent = Intent.createChooser(sendIntent, shareChooserTitle)
                                                context.startActivity(shareIntent)
                            },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = { context.startActivity(Intent(context, HistoryActivity::class.java)) },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.List, contentDescription = "History", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Your QR Assistant",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan or generate codes instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                MenuCard(
                    title = "Scan QR Code",
                    subtitle = "Point and capture instantly",
                    icon = Icons.Default.QrCodeScanner,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onScan
                )

                MenuCard(
                    title = "Generate QR",
                    subtitle = "Create custom codes in seconds",
                    icon = Icons.Default.AddCircle,
                    containerColor = Secondary,
                    contentColor = OnSecondary,
                    onClick = onGenerate
                )

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }

    @Composable
    fun MenuCard(
        title: String,
        subtitle: String,
        icon: ImageVector,
        containerColor: Color, // Changed type to Color
        contentColor: Color,   // Changed type to Color
        onClick: () -> Unit
    ) {
        // 1. Get the haptic feedback manager
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clickable {
                    // 2. Perform vibration
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    // 3. Execute the click action
                    onClick()
                           },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
