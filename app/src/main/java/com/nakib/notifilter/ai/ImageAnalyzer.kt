package com.nakib.notifilter.ai

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Extracts a notification image as a small base64 JPEG for the vision check.
 * Uses Notification.EXTRA_PICTURE or the large icon bitmap.
 */
object ImageAnalyzer {

    /** Downscale to at most 512px on the long side, JPEG quality 70. */
    fun toBase64Jpeg(bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        return try {
            val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}
