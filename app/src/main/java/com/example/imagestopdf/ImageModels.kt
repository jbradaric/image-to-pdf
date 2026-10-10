package com.example.imagestopdf

import android.net.Uri

data class PdfImage(
    val uri: Uri,
    val filter: ImageFilter,
    val rotation: Int
)

enum class ImageFilter(val label: String) {
    NONE("No filter"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("Black & white"),
    AUTO_SCAN("Auto scan (color)")
}
