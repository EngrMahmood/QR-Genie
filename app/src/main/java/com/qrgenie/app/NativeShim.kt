package com.qrgenie.app

import android.content.Context
import android.os.Build

object NativeShim {
    init {
        try {
            System.loadLibrary("native_shim")
        } catch (t: Throwable) {
            // Do not crash if native shim cannot be loaded; we'll log at runtime instead.
        }
    }

    @JvmStatic
    external fun loadOriginal(path: String): Boolean

    /**
     * Ensure original vendor libraries extracted into app files are loaded early.
     * Expects the prepare script to have placed libs under filesDir/native/<abi>/
     */
    @JvmStatic
    fun ensureLoaded(context: Context) {
        try {
            val abi = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS[0] else Build.CPU_ABI
            val targetDir = context.filesDir.resolve("native").resolve(abi)
            if (!targetDir.exists()) targetDir.mkdirs()

            // Attempt to extract known vendor libs from assets/native/<abi>/ to filesDir/native/<abi>/
            val candidates = listOf("libbarhopper_v3.so", "libimage_processing_util_jni.so")
            for (name in candidates) {
                val dest = targetDir.resolve(name)
                if (!dest.exists()) {
                    try {
                        val assetPath = "native/$abi/$name"
                        context.assets.open(assetPath).use { inp ->
                            dest.outputStream().use { out ->
                                inp.copyTo(out)
                            }
                        }
                    } catch (_: Exception) {
                        // asset not present; continue
                    }
                }
                if (dest.exists()) {
                    try {
                        loadOriginal(dest.absolutePath)
                    } catch (_: Throwable) {
                        // ignore - loading may fail on some devices
                    }
                }
            }
        } catch (t: Throwable) {
            // swallow - best effort
        }
    }
}

