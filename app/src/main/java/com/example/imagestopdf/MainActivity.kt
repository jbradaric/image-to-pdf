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
import android.widget.FrameLayout
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
    private lateinit var shareButton: Button
    private var lastCreatedPdf: Uri? = null
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
            setPadding(dp(16), dp(20), dp(16), dp(12))
            setBackgroundColor(0xFFF7F8FC.toInt())
        }
        page.addView(TextView(this).apply {
            text = "Image to PDF"
            textSize = 28f
            setTextColor(0xFF172033.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = "Turn your pictures into a PDF, in the order you choose."
            textSize = 14f
            setTextColor(0xFF60697A.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })
        page.addView(Button(this).apply {
            text = "Select images"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, REQUEST_PICK_IMAGES)
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        selectionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        val previewScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(selectionList)
        }
        page.addView(previewScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        progress = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF60697A.toInt())
            setPadding(0, dp(4), 0, dp(4))
        }
        page.addView(progress)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
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
        actions.addView(createButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        shareButton = Button(this).apply {
            text = "Share"
            isEnabled = false
            setOnClickListener { lastCreatedPdf?.let { sharePdf(it) } }
        }
        val shareParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) }
        actions.addView(shareButton, shareParams)
        page.addView(actions)
        return page
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
                textSize = 14f
                setTextColor(0xFF30394A.toInt())
                setPadding(0, 0, 0, dp(8))
            })
            selectedImages.forEachIndexed { index, uri ->
                val column = index % 3
                if (column == 0) {
                    selectionList.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        tag = "preview-row"
                    }, LinearLayout.LayoutParams(-1, dp(112)).apply { bottomMargin = dp(8) })
                }
                val row = selectionList.getChildAt(selectionList.childCount - 1) as LinearLayout
                val previewFrame = FrameLayout(this)
                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFFE2E6EF.toInt())
                    contentDescription = "Selected image ${index + 1}"
                }
                previewFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))
                previewFrame.addView(TextView(this).apply {
                    text = "×"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(0xCC172033.toInt())
                    contentDescription = "Remove image ${index + 1}"
                    setOnClickListener {
                        selectedImages.remove(uri)
                        refreshSelection()
                    }
                }, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
                row.addView(previewFrame, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    if (column > 0) marginStart = dp(8)
                })
                executor.execute {
                    val thumbnail = try { loadThumbnail(uri) } catch (_: Exception) { null }
                    runOnUiThread {
                        if (thumbnail != null && preview.parent != null) preview.setImageBitmap(thumbnail)
                    }
                }
            }
        }
        createButton.isEnabled = selectedImages.isNotEmpty()
        shareButton.isEnabled = lastCreatedPdf != null
    }

    private fun loadThumbnail(uri: Uri): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = 720f / maxOf(info.size.width, info.size.height)
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IOException("Unable to read image")
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 720)
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
        shareButton.isEnabled = false
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
                    lastCreatedPdf = destination
                    shareButton.isEnabled = true
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

    private fun sharePdf(uri: Uri) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "Share PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app available to share this PDF", Toast.LENGTH_SHORT).show()
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
