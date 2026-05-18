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
import com.google.zxing.BarcodeFormat
import java.nio.charset.Charset
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import android.graphics.Matrix
import android.util.Log
import com.google.zxing.PlanarYUVLuminanceSource

@OptIn(ExperimentalGetImage::class)
object QRCodeUtils {

    private const val TAG = "QRCodeUtils"

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
            Log.d(TAG, "Gallery ML Kit result: ${mlResult ?: "<null>"}")
            // If ML Kit returned a result but it contains replacement characters (e.g. "?")
            // try a ZXing fallback decode with UTF-8 to preserve Arabic/Urdu and other special characters.
            if (mlResult != null && mlResult.contains("?")) {
                val zxing = decodeBitmapWithZXing(bitmap)
                if (!zxing.isNullOrEmpty()) {
                    Log.d(TAG, "Gallery ZXing(Bitmap) succeeded: ${toCodePointsString(zxing)}")
                    return@withContext zxing
                }
            }
            mlResult
        } catch (e: Exception) {
            Log.d(TAG, "Gallery ML Kit exception: ${e.message}")
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
                    Log.d(TAG, "ML Kit result: ${result ?: "<null>"}")
                    // If ML Kit provided a possibly-garbled result (contains '?') or no result,
                    // attempt ZXing fallback on the camera frame bitmap to preserve UTF-8 text.
                    if (result != null && !result.contains("?")) {
                        Log.d(TAG, "Using ML Kit result: ${toCodePointsString(result)}")
                        onDetected(result)
                    } else {
                        // try ZXing decode directly from the ImageProxy YUV planes (faster, avoids JPEG conversion)
                        val zFromYuv = decodeImageProxyWithZXing(imageProxy)
                        if (!zFromYuv.isNullOrEmpty()) {
                            Log.d(TAG, "ZXing(YUV) succeeded: ${toCodePointsString(zFromYuv)}")
                            onDetected(zFromYuv)
                        } else {
                            // fallback to bitmap-based ZXing decode (slower) if direct YUV decode failed
                            val bmp = imageProxyToBitmap(imageProxy)
                            if (bmp != null) {
                                val z = decodeBitmapWithZXing(bmp)
                                if (!z.isNullOrEmpty()) {
                                    Log.d(TAG, "ZXing(Bitmap) succeeded: ${toCodePointsString(z)}")
                                    onDetected(z)
                                } else if (result != null) {
                                    Log.d(TAG, "Using ML Kit fallback result (may be garbled): ${result}")
                                    // fallback to ML Kit result even if it contained '?'
                                    onDetected(result)
                                }
                            } else if (result != null) {
                                onDetected(result)
                            }
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

    @OptIn(ExperimentalGetImage::class)
    @ExperimentalGetImage
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
            Log.d(TAG, "imageProxyToBitmap failed: ${e.message}")
            null
        }
    }

    private fun yuv420ToNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val nv21 = ByteArray(width * height * 3 / 2)
        var outPos = 0

        // Copy Y plane row-by-row, respecting rowStride.
        val yBuffer = yPlane.buffer.duplicate()
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            yBuffer.position(rowStart)
            yBuffer.get(nv21, outPos, width)
            outPos += width
        }

        // Copy UV planes as interleaved VU (NV21)
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        for (row in 0 until chromaHeight) {
            val uRowStart = row * uPlane.rowStride
            val vRowStart = row * vPlane.rowStride
            for (col in 0 until chromaWidth) {
                val uIndex = uRowStart + col * uPlane.pixelStride
                val vIndex = vRowStart + col * vPlane.pixelStride
                nv21[outPos++] = vBuffer.get(vIndex)
                nv21[outPos++] = uBuffer.get(uIndex)
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

            // Use explicit mutable hints, prefer QR_CODE format and try harder
            val hints = hashMapOf<DecodeHintType, Any>()
            hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
            hints[DecodeHintType.TRY_HARDER] = true
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)

            val reader = MultiFormatReader()
            reader.setHints(hints)
            val result = reader.decode(binaryBitmap)

            // If ZXing produced text but it contains replacement chars (e.g. '?'),
            // try decoding the raw bytes using UTF-8 and some other likely encodings
            val text = result.text
            if (text != null && !text.contains("?")) return text

            val raw = result.rawBytes
            if (raw != null) {
                val encodings = listOf("UTF-8", "Windows-1256", "ISO-8859-1")
                for (enc in encodings) {
                    try {
                        val s = String(raw, Charset.forName(enc))
                        if (!s.contains("?")) {
                            // prefer a candidate that contains Arabic-script characters
                            if (containsArabicScript(s)) return s
                            // otherwise keep as a fallback
                            return s
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            // final fallback to whatever ZXing produced
            text
        } catch (e: Exception) {
            Log.d(TAG, "decodeBitmapWithZXing failed: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun decodeImageProxyWithZXing(imageProxy: ImageProxy): String? {
        try {
            val image = imageProxy.image ?: return null
            // Use existing NV21 conversion helper
            val nv21 = yuv420ToNV21(image)

            val width = image.width
            val height = image.height

            // Create a PlanarYUVLuminanceSource directly over the NV21 buffer
            val source = PlanarYUVLuminanceSource(nv21, width, height, 0, 0, width, height, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = hashMapOf<DecodeHintType, Any>()
            hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
            hints[DecodeHintType.TRY_HARDER] = true
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)

            val reader = MultiFormatReader()
            reader.setHints(hints)
            val result = reader.decode(binaryBitmap)

            val text = result.text
            if (text != null && !text.contains("?")) return text

            val raw = result.rawBytes
            if (raw != null) {
                val encodings = listOf("UTF-8", "Windows-1256", "ISO-8859-1")
                for (enc in encodings) {
                    try {
                        val s = String(raw, Charset.forName(enc))
                        if (!s.contains("?")) {
                            if (containsArabicScript(s)) return s
                            return s
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            return text
        } catch (e: Exception) {
            Log.d(TAG, "ZXing(YUV) decode exception: ${e.message}")
            return null
        }
    }

    private fun toCodePointsString(s: String): String {
        val codes = ArrayList<String>(s.length)
        for (ch in s) codes.add(ch.code.toString())
        return codes.joinToString(",")
    }

    private fun containsArabicScript(s: String): Boolean {
        for (ch in s) {
            val code = ch.code
            // Arabic and extended Arabic ranges
            if ((code in 0x0600..0x06FF) || (code in 0x0750..0x077F) || (code in 0x08A0..0x08FF) || (code in 0xFB50..0xFDFF) || (code in 0xFE70..0xFEFF)) {
                return true
            }
        }
        return false
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