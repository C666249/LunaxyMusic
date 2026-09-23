plugins {
    id("com.android.application")
}

android {
    namespace = "com.xingyu.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xingyu.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 993
        versionName = "92.9.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Modern Android playback engine. No Compose/JetBrains Compose dependency is used.
    implementation("androidx.media3:media3-exoplayer:1.10.1")
}


// Keep AGP's internal APK name intact for Android Studio install/run tasks, then export a
// traceable share-ready copy after assembly. Android Studio remains the supported build path.
tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val apkDir = project.layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val source = apkDir.resolve("app-debug.apk")
        if (source.isFile) source.copyTo(
            apkDir.resolve("LunaxyMusic-Stable-92.9.14-debug.apk"),
            overwrite = true
        )
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        val apkDir = project.layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val source = listOf(apkDir.resolve("app-release.apk"), apkDir.resolve("app-release-unsigned.apk"))
            .firstOrNull { it.isFile }
        if (source != null) source.copyTo(
            apkDir.resolve("LunaxyMusic-Stable-92.9.14-release.apk"),
            overwrite = true
        )
    }
}
