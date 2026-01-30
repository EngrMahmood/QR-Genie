@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qrgenie.app

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.qrgenie.app.ui.theme.QRAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors




class ScanActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        permissionLauncher.launch(Manifest.permission.CAMERA)

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

    // For Vibration
    val vibrator = remember {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isScanning by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) } // Flash State

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
                else Toast.makeText(context, "No QR found", Toast.LENGTH_SHORT).show()
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
            .setTargetResolution(android.util.Size(1280, 720)) // Improved detection
            .build()
            .also {
                it.setAnalyzer(executor) { imageProxy ->
                    if (isScanning) {
                        QRCodeUtils.scanImageProxy(imageProxy) { result ->
                            isScanning = false
                            // Vibrate for 100ms
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, 10))
                            coroutineScope.launch(Dispatchers.Main) { onQRCodeDetected(result) }
                        }
                    } else {
                        imageProxy.close()
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
        topBar = { TopAppBar(title = { Text("Scan QR Code") }) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // The Visual Overlay
            ScannerOverlay()

            // Buttons at the bottom
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                    Text("Switch")
                }
                // Flash Button
                Button(onClick = {
                    isFlashOn = !isFlashOn
                    camera?.cameraControl?.enableTorch(isFlashOn)
                }) {
                    Text(if (isFlashOn) "Flash Off" else "Flash On")
                }
                Button(onClick = { galleryLauncher.launch("image/*") }) {
                    Text("Gallery")
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
            color = Color.Red,
            start = Offset(left + 10.dp.toPx(), currentLineY),
            end = Offset(left + rectSize - 10.dp.toPx(), currentLineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}