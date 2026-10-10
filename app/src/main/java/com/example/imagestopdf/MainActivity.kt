package com.example.imagestopdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var selectionList: LinearLayout
    private lateinit var createButton: Button
    private lateinit var progress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(makeContent())
        refreshSelection()
    }

    private fun makeContent(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(0xFFF7F8FC.toInt())
        }
        page.addView(TextView(this).apply {
            text = "Image to PDF"
            textSize = 30f
            setTextColor(0xFF172033.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = "Turn your pictures into a PDF, in the order you choose."
            textSize = 16f
            setTextColor(0xFF60697A.toInt())
            setPadding(0, dp(8), 0, dp(28))
        })
        page.addView(Button(this).apply {
            text = "Choose images"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, REQUEST_PICK_IMAGES)
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        selectionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, dp(16))
        }
        page.addView(selectionList)
        progress = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF60697A.toInt())
        }
        page.addView(progress)
        createButton = Button(this).apply {
            text = "Create PDF"
            isEnabled = false
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, "images.pdf")
                }
                startActivityForResult(intent, REQUEST_CREATE_PDF)
            }
        }
        val createParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(20) }
        page.addView(createButton, createParams)
        val scroll = ScrollView(this)
        scroll.addView(page)
        return scroll
    }

    @Deprecated("Uses the platform document picker result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            REQUEST_PICK_IMAGES -> {
                val picked = mutableListOf<Uri>()
                data?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) picked.add(clip.getItemAt(i).uri)
                } ?: data?.data?.let { picked.add(it) }
                if (picked.isNotEmpty()) {
                    selectedImages.clear()
                    selectedImages.addAll(picked.distinct())
                    refreshSelection()
                }
            }
            REQUEST_CREATE_PDF -> data?.data?.let { if (selectedImages.isNotEmpty()) writePdf(it) }
        }
    }

    private fun refreshSelection() {
        if (!::selectionList.isInitialized) return
        selectionList.removeAllViews()
        if (selectedImages.isEmpty()) {
            selectionList.addView(TextView(this).apply {
                text = "No images selected yet."
                textSize = 16f
                setTextColor(0xFF30394A.toInt())
            })
        } else {
            selectionList.addView(TextView(this).apply {
                text = "${selectedImages.size} image${if (selectedImages.size == 1) "" else "s"} selected"
                textSize = 16f
                setTextColor(0xFF30394A.toInt())
                setPadding(0, 0, 0, dp(12))
            })
            selectedImages.forEachIndexed { index, uri ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFFE2E6EF.toInt())
                    contentDescription = "Selected image ${index + 1}"
                }
                row.addView(preview, LinearLayout.LayoutParams(-1, dp(220)))
                selectionList.addView(row)
                executor.execute {
                    val thumbnail = try { loadThumbnail(uri) } catch (_: Exception) { null }
                    runOnUiThread {
                        if (thumbnail != null && preview.parent != null) preview.setImageBitmap(thumbnail)
                    }
                }
            }
        }
        createButton.isEnabled = selectedImages.isNotEmpty()
    }

    private fun loadThumbnail(uri: Uri): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = 480f / maxOf(info.size.width, info.size.height)
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("Unable to read image")
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 480)
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("Unable to decode image")
        }
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: uri.lastPathSegment ?: "Image"
    } catch (_: Exception) { uri.lastPathSegment ?: "Image" }

    private fun writePdf(destination: Uri) {
        val images = selectedImages.toList()
        createButton.isEnabled = false
        progress.text = "Creating PDF…"
        executor.execute {
            var document: PdfDocument? = null
            try {
                document = PdfDocument()
                images.forEachIndexed { index, uri ->
                    val bitmap = loadBitmap(uri)
                    try {
                        val pageWidth = 595
                        val pageHeight = 842
                        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
                        val scale = minOf(pageWidth.toFloat() / bitmap.width, pageHeight.toFloat() / bitmap.height)
                        val width = (bitmap.width * scale).toInt()
                        val height = (bitmap.height * scale).toInt()
                        val left = (pageWidth - width) / 2
                        val top = (pageHeight - height) / 2
                        page.canvas.drawColor(android.graphics.Color.WHITE)
                        page.canvas.drawBitmap(bitmap, null, Rect(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        document.finishPage(page)
                    } finally {
                        bitmap.recycle()
                    }
                }
                contentResolver.openOutputStream(destination)?.use { document.writeTo(it) }
                    ?: throw IOException("Unable to open the selected destination")
                runOnUiThread {
                    progress.text = "PDF created successfully."
                    Toast.makeText(this, "PDF saved", Toast.LENGTH_SHORT).show()
                    createButton.isEnabled = selectedImages.isNotEmpty()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.text = "Couldn't create PDF: ${error.localizedMessage ?: "unknown error"}"
                    createButton.isEnabled = selectedImages.isNotEmpty()
                }
            } finally {
                document?.close()
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IOException("Unable to read image")
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PICK_IMAGES = 1
        private const val REQUEST_CREATE_PDF = 2
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
