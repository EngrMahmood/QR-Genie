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
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.DecodeHintType
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import android.graphics.Matrix

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
            val mlResult = barcodes.firstOrNull()?.rawValue
            // If ML Kit returned a result but it contains replacement characters (e.g. "?")
            // try a ZXing fallback decode with UTF-8 to preserve Arabic/Urdu and other special characters.
            if (mlResult != null && mlResult.contains("?") && bitmap != null) {
                val zxing = decodeBitmapWithZXing(bitmap)
                if (!zxing.isNullOrEmpty()) return@withContext zxing
            }
            mlResult
        } catch (e: Exception) {
            // On error with ML Kit, try ZXing fallback
            decodeBitmapWithZXing(bitmap)
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
                    // If ML Kit provided a possibly-garbled result (contains '?') or no result,
                    // attempt ZXing fallback on the camera frame bitmap to preserve UTF-8 text.
                    if (result != null && !result.contains("?")) {
                        onDetected(result)
                    } else {
                        // try ZXing decode from ImageProxy
                        val bmp = imageProxyToBitmap(imageProxy)
                        if (bmp != null) {
                            val z = decodeBitmapWithZXing(bmp)
                            if (!z.isNullOrEmpty()) {
                                onDetected(z)
                            } else if (result != null) {
                                // fallback to ML Kit result even if it contained '?'
                                onDetected(result)
                            }
                        } else if (result != null) {
                            onDetected(result)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image: Image = imageProxy.image ?: return null
        return try {
            val nv21 = yuv420ToNV21(image)
            val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val yuvBytes = out.toByteArray()
            var bmp = BitmapFactory.decodeByteArray(yuvBytes, 0, yuvBytes.size)
            // rotate bitmap if needed
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun yuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4

        val nv21 = ByteArray(ySize + uvSize * 2)

        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        var rowStride = image.planes[0].rowStride
        var pos = 0
        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            val yRow = ByteArray(rowStride)
            var remaining = ySize
            while (remaining > 0) {
                val toGet = minOf(rowStride, remaining)
                yBuffer.get(yRow, 0, toGet)
                System.arraycopy(yRow, 0, nv21, pos, toGet)
                pos += toGet
                remaining -= toGet
            }
        }

        // Interleave V and U for NV21
        val chromaRowStride = image.planes[2].rowStride
        val chromaPixelStride = image.planes[2].pixelStride
        val vRow = ByteArray(chromaRowStride)
        val uRow = ByteArray(image.planes[1].rowStride)

        val uvHeight = height / 2
        var uvPos = ySize
        for (row in 0 until uvHeight) {
            vBuffer.get(vRow, 0, chromaRowStride)
            uBuffer.get(uRow, 0, image.planes[1].rowStride)
            var col = 0
            while (col < width) {
                val v = vRow[col * chromaPixelStride]
                val u = uRow[col * image.planes[1].pixelStride]
                nv21[uvPos++] = v
                nv21[uvPos++] = u
                col += 2
            }
        }
        return nv21
    }

    private fun decodeBitmapWithZXing(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.TRY_HARDER to true)
            val result = MultiFormatReader().apply { setHints(hints) }.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
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