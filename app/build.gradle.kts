plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.imagestopdf"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.example.imagestopdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }
}

// Run after generating a signed APK in Android Studio. Override signedApkPath if
// the signing wizard saved the APK somewhere other than app/release/app-release.apk.
val signedApkPath = providers.gradleProperty("signedApkPath")
val appVersionName = android.defaultConfig.versionName
    ?: throw GradleException("Set android.defaultConfig.versionName before naming the APK.")
tasks.register("copyNamedSignedApk") {
    group = "build"
    description = "Copies the signed release APK with the app name and version."
    doLast {
        val source = signedApkPath.orNull?.let { project.file(it) }
            ?: layout.projectDirectory.file("release/app-release.apk").asFile
        if (!source.isFile) {
            throw GradleException("Signed APK not found at ${source.absolutePath}. Set -PsignedApkPath=/path/to/signed.apk.")
        }
        val destination = source.resolveSibling("ImageToPDF-$appVersionName.apk")
        source.copyTo(destination, overwrite = true)
        logger.lifecycle("Created ${destination.absolutePath}")
    }
}
