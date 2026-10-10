# Repository Guide

## Project

- Native Android app written in Kotlin. `MainActivity.kt` owns the UI and workflow; `ImageProcessor.kt` handles image decoding and filters; `PdfGenerator.kt` builds the PDF; `ImageModels.kt` contains image/filter models.
- The app targets Android 8.0 (API 26) and newer, with `compileSdk = 35` and Java/Kotlin bytecode target 17.
- It uses Android's system document picker; do not add storage permissions unless the access model changes.
- Each selected image becomes one centered, uncropped portrait A4 PDF page. Preserve image aspect ratio and do not upscale images to fill a page.
- Per-image filter and rotation choices should affect both the preview and generated PDF. Preserve originals; process decoded bitmaps only.

## Build and verification

- Use the Gradle wrapper, not a machine-installed Gradle: `./gradlew :app:assembleDebug`.
- The project currently uses AGP 8.7.3, Kotlin Android plugin 2.0.21, and Gradle 9.6.0. Keep the AGP/Gradle/JDK versions compatible when upgrading; do not upgrade AGP alone.
- Check changes with `git diff --check`. Run the debug build after modifying application or Gradle code.
- There are no repository test suites currently configured.

## Release/versioning

- Keep `versionName` and `versionCode` in `app/build.gradle.kts` current. Increment `versionCode` for each release; it must always increase.
- Never commit keystores, passwords, or signing credentials.
- `copyNamedSignedApk` copies an already signed APK to `ImageToPDF-<versionName>.apk`; it does not sign the APK or run automatically. By default it expects `app/release/app-release.apk`; override with `-PsignedApkPath=/path/to/signed.apk`.

## Code conventions

- Keep the app dependency-light and use Android platform APIs unless a dependency is clearly justified.
- Keep image decoding, filtering, and PDF generation off the UI thread. Bound image-processing memory by downsampling before export filters.
- Preserve per-image settings when updating the selection UI, and avoid applying destructive edits to source files.
