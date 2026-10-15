package com.jarvis.localai.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.jarvis.localai.inference.ImageInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

data class PreparedImage(val savedFile: File, val input: ImageInput)

object ImagePreprocessor {
    private const val MAX_EDGE = 896

    suspend fun prepare(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not open the selected image.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image." }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bitmap = openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, decoded) }
            ?: error("The selected image could not be decoded.")

        try {
            val orientation = openStream(context, uri)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) } ?: ExifInterface.ORIENTATION_NORMAL
            val transform = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                }
            }
            if (!transform.isIdentity) {
                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
                if (oriented !== bitmap) bitmap.recycle()
                bitmap = oriented
            }

            val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap
            val file = File(context.filesDir, "images/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            file.outputStream().use { resized.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val width = resized.width
            val height = resized.height
            val pixels = IntArray(width * height)
            resized.getPixels(pixels, 0, width, 0, 0, width, height)
            val rgb = ByteArray(pixels.size * 3)
            pixels.forEachIndexed { index, color ->
                val offset = index * 3
                rgb[offset] = (color shr 16).toByte()
                rgb[offset + 1] = (color shr 8).toByte()
                rgb[offset + 2] = color.toByte()
            }
            if (resized !== bitmap) resized.recycle()
            PreparedImage(file, ImageInput(width, height, rgb))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_EDGE * 2) sample *= 2
        return sample
    }

    private fun openStream(context: Context, uri: Uri) =
        if (uri.scheme == "file") uri.path?.let(::File)?.let(::FileInputStream)
        else context.contentResolver.openInputStream(uri)
}