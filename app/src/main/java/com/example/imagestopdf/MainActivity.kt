package com.example.imagestopdf

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val imageProcessor by lazy { ImageProcessor(applicationContext) }
    private val pdfGenerator by lazy { PdfGenerator(applicationContext, imageProcessor) }
    private val selectedImages = mutableListOf<Uri>()
    private val imageFilters = mutableMapOf<Uri, ImageFilter>()
    private val imageRotations = mutableMapOf<Uri, Int>()
    private val previewVersions = mutableMapOf<Uri, Int>()
    private lateinit var selectionList: LinearLayout
    private lateinit var createButton: Button
    private lateinit var shareButton: Button
    private var lastCreatedPdf: Uri? = null
    private lateinit var progress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(makeContent())
        refreshSelection()
    }

    private fun makeContent(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(12))
            setBackgroundColor(if (isDarkMode()) 0xFF121318.toInt() else 0xFFF7F8FC.toInt())
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_app_logo)
            contentDescription = "Image to PDF logo"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        heading.addView(TextView(this).apply {
            text = "Image to PDF"
            textSize = 28f
            setTextColor(if (isDarkMode()) 0xFFF1F0F7.toInt() else 0xFF172033.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        })
        page.addView(heading)
        page.addView(TextView(this).apply {
            text = "Turn your pictures into a PDF, in the order you choose."
            textSize = 14f
            setTextColor(if (isDarkMode()) 0xFFBFC2CC.toInt() else 0xFF60697A.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })
        val selectButton = Button(this).apply {
            text = "Select images"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(intent, REQUEST_PICK_IMAGES)
            }
        }
        styleButton(selectButton, primary = true)
        page.addView(selectButton, LinearLayout.LayoutParams(-1, dp(48)))
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
            setTextColor(if (isDarkMode()) 0xFFBFC2CC.toInt() else 0xFF60697A.toInt())
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
        styleButton(createButton, primary = true)
        actions.addView(createButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        shareButton = Button(this).apply {
            text = "Share"
            isEnabled = false
            setOnClickListener { lastCreatedPdf?.let { sharePdf(it) } }
        }
        styleButton(shareButton, primary = false)
        val shareParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) }
        actions.addView(shareButton, shareParams)
        page.addView(actions)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            page.setOnApplyWindowInsetsListener { view, insets ->
                val systemInsets = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
                )
                view.setPadding(
                    dp(16) + systemInsets.left,
                    dp(20) + systemInsets.top,
                    dp(16) + systemInsets.right,
                    dp(12) + systemInsets.bottom
                )
                insets
            }
        }
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
                    selectedImages.addAll(picked.distinct().filterNot { it in selectedImages })
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
                setTextColor(if (isDarkMode()) 0xFFE2E2E9.toInt() else 0xFF30394A.toInt())
            })
        } else {
            selectionList.addView(TextView(this).apply {
                text = "${selectedImages.size} image${if (selectedImages.size == 1) "" else "s"} selected"
                textSize = 14f
                setTextColor(if (isDarkMode()) 0xFFE2E2E9.toInt() else 0xFF30394A.toInt())
                setPadding(0, 0, 0, dp(8))
            })
            selectedImages.forEachIndexed { index, uri ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                selectionList.addView(row, LinearLayout.LayoutParams(-1, dp(88)).apply { bottomMargin = dp(8) })
                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(if (isDarkMode()) 0xFF343640.toInt() else 0xFFE2E6EF.toInt())
                    contentDescription = "Selected image ${index + 1}"
                }
                row.addView(preview, LinearLayout.LayoutParams(dp(72), dp(72)))
                val controls = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), 0, 0, 0)
                }
                row.addView(controls, LinearLayout.LayoutParams(0, -1, 1f))
                controls.addView(TextView(this).apply {
                    text = imageProcessor.displayName(uri)
                    textSize = 13f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(if (isDarkMode()) 0xFFE2E2E9.toInt() else 0xFF30394A.toInt())
                })
                controls.addView(Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, ImageFilter.entries.map { it.label })
                    setSelection((imageFilters[uri] ?: ImageFilter.NONE).ordinal)
                    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                            val filter = ImageFilter.entries[position]
                            imageFilters[uri] = filter
                            renderPreview(uri, preview, filter)
                        }
                    }
                })
                renderPreview(uri, preview, imageFilters[uri] ?: ImageFilter.NONE)
                val rotationControl = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 16f
                    setTextColor(if (isDarkMode()) 0xFFE2E2E9.toInt() else 0xFF30394A.toInt())
                }
                fun updateRotationLabel() {
                    val rotation = imageRotations[uri] ?: 0
                    rotationControl.text = "↻\n${rotation}°"
                    preview.rotation = rotation.toFloat()
                    rotationControl.contentDescription = "Rotate image ${index + 1}; currently rotated $rotation degrees"
                }
                updateRotationLabel()
                rotationControl.setOnClickListener {
                    imageRotations[uri] = ((imageRotations[uri] ?: 0) + 90) % 360
                    updateRotationLabel()
                }
                row.addView(rotationControl, LinearLayout.LayoutParams(dp(44), dp(48)))
                row.addView(TextView(this).apply {
                    text = "×"
                    textSize = 26f
                    gravity = Gravity.CENTER
                    contentDescription = "Remove image ${index + 1}"
                    setTextColor(if (isDarkMode()) 0xFFE2E2E9.toInt() else 0xFF30394A.toInt())
                    setOnClickListener {
                        selectedImages.remove(uri)
                        imageFilters.remove(uri)
                        imageRotations.remove(uri)
                        previewVersions[uri] = (previewVersions[uri] ?: 0) + 1
                        refreshSelection()
                    }
                }, LinearLayout.LayoutParams(dp(40), dp(48)))
            }
        }
        createButton.isEnabled = selectedImages.isNotEmpty()
        shareButton.isEnabled = lastCreatedPdf != null
    }

    private fun renderPreview(uri: Uri, preview: ImageView, filter: ImageFilter) {
        val requestVersion = (previewVersions[uri] ?: 0) + 1
        previewVersions[uri] = requestVersion
        executor.execute {
            var thumbnail: Bitmap? = null
            try {
                thumbnail = imageProcessor.loadThumbnail(uri)
                if (filter != ImageFilter.NONE) imageProcessor.applyFilter(thumbnail, filter)
            } catch (_: Exception) {
                thumbnail?.recycle()
                thumbnail = null
            }
            val result = thumbnail
            runOnUiThread {
                if (result != null) {
                    if (previewVersions[uri] == requestVersion && preview.parent != null) {
                        preview.setImageBitmap(result)
                    } else {
                        result.recycle()
                    }
                }
            }
        }
    }

    private fun writePdf(destination: Uri) {
        val images = selectedImages.map {
            PdfImage(it, imageFilters[it] ?: ImageFilter.NONE, imageRotations[it] ?: 0)
        }
        createButton.isEnabled = false
        shareButton.isEnabled = false
        progress.text = "Creating PDF…"
        executor.execute {
            try {
                pdfGenerator.write(destination, images)
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

    private fun styleButton(button: Button, primary: Boolean) {
        val dark = isDarkMode()
        val enabledColor = when {
            primary -> 0xFF315EFB.toInt()
            dark -> 0xFF30323B.toInt()
            else -> 0xFFE8EDFF.toInt()
        }
        val disabledColor = if (dark) 0xFF292A31.toInt() else 0xFFE3E5EB.toInt()
        button.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat() }
        button.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(enabledColor, disabledColor)
        )
        button.setTextColor(when {
            primary -> android.graphics.Color.WHITE
            dark -> 0xFFE5E5EC.toInt()
            else -> 0xFF2448C8.toInt()
        })
        button.isAllCaps = false
        button.textSize = 15f
        button.elevation = dp(2).toFloat()
        button.setPadding(dp(12), 0, dp(12), 0)
    }

    private fun isDarkMode(): Boolean =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

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
