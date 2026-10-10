package com.example.imagestopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException

class ImageProcessor(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver

    fun loadThumbnail(uri: Uri): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = 720f / maxOf(info.size.width, info.size.height)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setMutableRequired(true)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("Unable to read image")
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 720)
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inMutable = true
                })
            } ?: throw IOException("Unable to decode image")
        }
    }

    fun loadBitmap(uri: Uri, maxDimension: Int): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longestSide = maxOf(info.size.width, info.size.height)
                if (longestSide > maxDimension) {
                    val scale = maxDimension.toFloat() / longestSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setMutableRequired(true)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("Unable to read image")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inMutable = true
                })
            } ?: throw IOException("Unable to decode image")
        }
    }

    fun applyFilter(bitmap: Bitmap, filter: ImageFilter) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        when (filter) {
            ImageFilter.NONE -> return
            ImageFilter.GRAYSCALE -> for (i in pixels.indices) {
                val gray = luminance(pixels[i])
                pixels[i] = Color.rgb(gray, gray, gray)
            }
            ImageFilter.BLACK_AND_WHITE -> for (i in pixels.indices) {
                val value = if (luminance(pixels[i]) >= 160) 255 else 0
                pixels[i] = Color.rgb(value, value, value)
            }
            ImageFilter.AUTO_SCAN -> applyColorScan(pixels, width, height)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: uri.lastPathSegment ?: "Image"
    } catch (_: Exception) {
        uri.lastPathSegment ?: "Image"
    }

    private fun applyColorScan(pixels: IntArray, width: Int, height: Int) {
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                val i = y * width + x
                rowSum += luminance(pixels[i])
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
            }
        }

        val radius = (minOf(width, height) / 40).coerceAtLeast(12)
        for (y in 0 until height) for (x in 0 until width) {
            val left = (x - radius).coerceAtLeast(0)
            val right = (x + radius + 1).coerceAtMost(width)
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius + 1).coerceAtMost(height)
            val localSum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            val localMean = localSum / ((right - left) * (bottom - top))
            val i = y * width + x
            val color = pixels[i]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            val gray = luminance(color)
            val saturation = maxOf(red, green, blue) - minOf(red, green, blue)
            val colorProtection = 1f - 0.8f * saturation / 255f
            val scanGray = (255f + (gray - localMean.toFloat()) * 1.25f).coerceIn(0f, 255f)
            val adjustedGray = (gray + (scanGray - gray) * colorProtection).toInt()
            val shift = adjustedGray - gray
            pixels[i] = Color.rgb(
                (red + shift).coerceIn(0, 255),
                (green + shift).coerceIn(0, 255),
                (blue + shift).coerceIn(0, 255)
            )
        }
    }

    private fun luminance(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
}
