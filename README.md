# Image to PDF

A small native Android app that selects one or more images and creates a PDF with one image per page. Images are fitted (without cropping) to portrait A4 pages, centered, in selection order. The system document picker handles both image access and choosing where to save the PDF; no storage permission is needed.

## Build

Open this project in Android Studio, or build with a compatible JDK and Android SDK:

```sh
gradle :app:assembleDebug
```

The app targets Android 8.0 (API 26) and newer.
