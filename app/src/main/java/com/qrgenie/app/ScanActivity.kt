@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qrgenie.app

import android.Manifest
import android.content.Intent
// ...existing code...
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
// ...existing code...
import com.qrgenie.app.ui.theme.QRAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors




class ScanActivity : LocalizedComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Helper that sets the Compose content (start camera UI)
        fun startCameraUi() {
            setContent {
                QRAppTheme {
                    CameraScanScreen(cameraExecutor) { qrText ->
                        val intent = Intent(this, ScanResultActivity::class.java).apply {
                            putExtra("EXTRA_QR_CONTENT", qrText)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

        // If permission already granted, immediately start the camera UI. Otherwise request it and
        // only start the UI when the user grants permission. This prevents composing the camera
        // preview before permissions are available (which caused a black screen until restart).
        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCameraUi()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraUi()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ...existing code...

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}



@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(executor: ExecutorService, onQRCodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // For Vibration (use Class-based getSystemService to avoid deprecated constant)
    val vibrator = remember {
        context.getSystemService(android.os.Vibrator::class.java)
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isScanning by remember { mutableStateOf(true) }
    // Simple debounce to avoid duplicate detections within short window
    var lastDetectedTime by remember { mutableStateOf(0L) }
    // Hold a pending detected result so we can show a short confirmation before navigating
    var pendingResult by remember { mutableStateOf<String?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) } // Flash State

    // When a QR is detected we show a confirmation overlay briefly then navigate.
    // The ConfirmationOverlay shows for ~650ms and fades for 300ms (total ~950ms).
    LaunchedEffect(showConfirmation) {
        if (showConfirmation && pendingResult != null) {
            // wait for the confirmation animation to finish
            kotlinx.coroutines.delay(950)
            val result = pendingResult
            // reset UI state (best-effort) before navigating
            showConfirmation = false
            pendingResult = null
            isScanning = false
            // call the provided callback to navigate to result screen
            result?.let { onQRCodeDetected(it) }
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch {
                @Suppress("DEPRECATION")
                val bitmap = withContext(Dispatchers.IO) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                val result = QRCodeUtils.scanQRCodeFromBitmap(bitmap)
                if (result != null) onQRCodeDetected(result)
                else Toast.makeText(context, context.getString(R.string.no_qr_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(lensFacing) {
        val cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Set default resolution (leave to system) - removed deprecated setTargetResolution
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    // Respect scanning flag and debounce window (1s)
                    if (isScanning && now - lastDetectedTime > 1000L) {
                        try {
                            QRCodeUtils.scanImageProxy(imageProxy) { result ->
                                if (result != null) {
                                    lastDetectedTime = System.currentTimeMillis()
                                    isScanning = false
                                    pendingResult = result
                                    showConfirmation = true
                                    // Vibrate for 100ms (best-effort)
                                    try {
                                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(100, 10))
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ensure imageProxy is closed on unexpected errors
                            try { imageProxy.close() } catch (_: Exception) { }
                        }
                    } else {
                        // Not scanning or within debounce window
                        try { imageProxy.close() } catch (_: Exception) { }
                    }
                }
            }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                analysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            // Modern Floating Blue Header (Matches Home Screen)
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                color = MaterialTheme.colorScheme.primary, // Brand Blue
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
                        stringResource(R.string.scan_magic_qr),
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Camera Viewport
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // The Visual Overlay (Animated)
            ScannerOverlay()

            // Confirmation overlay when a QR is detected
            if (showConfirmation) {
                ConfirmationOverlay()
            }

            // MODERN FLOATING CONTROLS
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
                color = Color.Black.copy(alpha = 0.6f), // Glassmorphism
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Toggle
                    IconButton(onClick = {
                        isFlashOn = !isFlashOn
                        camera?.cameraControl?.enableTorch(isFlashOn)
                    }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Filled.FlashOn
                            else Icons.Filled.FlashOff,
                            contentDescription = stringResource(R.string.flash_label),
                            tint = if (isFlashOn) Color.Yellow else Color.White
                        )
                    }

                    // Gallery Launcher
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.gallery_label), color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    // Flip Camera
                    IconButton(onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) {
                        Icon(Icons.Filled.FlipCameraAndroid, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerOverlay() {
    // Animation for the red line
    val infiniteTransition = rememberInfiniteTransition(label = "scannerLine")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineAnimation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val rectSize = width * 0.7f
        val left = (width - rectSize) / 2
        val top = (height - rectSize) / 2

        // 1. Draw Focus Rectangle
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(rectSize, rectSize),
            cornerRadius = CornerRadius(12.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // 2. Draw Animated Red Line
        val currentLineY = top + (rectSize * lineOffset)
        drawLine(
            color = Color(0xFF2962FF),
            start = Offset(left + 10.dp.toPx(), currentLineY),
            end = Offset(left + rectSize - 10.dp.toPx(), currentLineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun ConfirmationOverlay() {
    // A simple centered green check that fades out automatically
    val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        // Let it show briefly then fade
        kotlinx.coroutines.delay(650)
        alpha.animateTo(0f, animationSpec = tween(300))
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.95f)),
            modifier = Modifier.size(140.dp).alpha(alpha.value)
        ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.scanned_label), tint = Color.White, modifier = Modifier.size(64.dp))
                }
        }
    }
}

