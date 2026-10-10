package com.example.imagestopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.IOException

class PdfGenerator(context: Context, private val imageProcessor: ImageProcessor) {
    private val contentResolver = context.applicationContext.contentResolver

    fun write(destination: Uri, images: List<PdfImage>) {
        var document: PdfDocument? = null
        try {
            document = PdfDocument()
            images.forEachIndexed { index, image ->
                var bitmap = imageProcessor.loadBitmap(image.uri, PDF_RENDER_MAX_DIMENSION)
                try {
                    if (image.filter != ImageFilter.NONE) imageProcessor.applyFilter(bitmap, image.filter)
                    if (image.rotation != 0) {
                        val rotated = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height,
                            Matrix().apply { postRotate(image.rotation.toFloat()) }, true
                        )
                        bitmap.recycle()
                        bitmap = rotated
                    }

                    val pageWidth = 595
                    val pageHeight = 842
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    )
                    val scale = minOf(
                        1f,
                        pageWidth.toFloat() / bitmap.width,
                        pageHeight.toFloat() / bitmap.height
                    )
                    val width = (bitmap.width * scale).toInt()
                    val height = (bitmap.height * scale).toInt()
                    val left = (pageWidth - width) / 2
                    val top = (pageHeight - height) / 2
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        Rect(left, top, left + width, top + height),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            contentResolver.openOutputStream(destination)?.use { document.writeTo(it) }
                ?: throw IOException("Unable to open the selected destination")
        } finally {
            document?.close()
        }
    }

    companion object {
        private const val PDF_RENDER_MAX_DIMENSION = 1684
    }
}
