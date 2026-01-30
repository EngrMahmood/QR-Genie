package com.qrgenie.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object QRCodeUtils {

    /**
     * Used by ScanActivity for Gallery images
     */
    suspend fun scanQRCodeFromBitmap(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        return@withContext try {
            val barcodes = Tasks.await(scanner.process(image))
            barcodes.firstOrNull()?.rawValue
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Used by ScanActivity for Real-time camera stream
     */
    @OptIn(ExperimentalGetImage::class)
    fun scanImageProxy(imageProxy: ImageProxy, onDetected: (String) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = BarcodeScanning.getClient(options)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val result = barcodes.firstOrNull()?.rawValue
                    if (result != null) {
                        onDetected(result)
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Used by GenerateActivity for Sharing
     */
    fun saveBitmapToCacheAndGetUri(context: Context, bitmap: Bitmap): Uri? {
        val imagesFolder = File(context.cacheDir, "images")
        return try {
            imagesFolder.mkdirs()
            val file = File(imagesFolder, "shared_qr.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}