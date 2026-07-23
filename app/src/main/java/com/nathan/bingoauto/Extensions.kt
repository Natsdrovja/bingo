package com.nathan.bingoauto

import android.content.Intent
import android.graphics.Bitmap
import android.media.Image
import android.os.Build
import android.os.Parcelable

/**
 * Converts a MediaProjection RGBA_8888 [Image] to a [Bitmap], cropping the
 * per-row padding that ImageReader may add so pixel coordinates stay aligned
 * with the real screen (and therefore with tap coordinates).
 */
fun Image.toBitmap(targetWidth: Int): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val paddedWidth = width + rowPadding / pixelStride

    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)

    if (paddedWidth == targetWidth) return padded
    val cropped = Bitmap.createBitmap(padded, 0, 0, targetWidth, height)
    padded.recycle()
    return cropped
}

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name) as? T
    }
