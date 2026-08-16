plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.f0e.blur.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.f0e.blur.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "dev"

        // 打包全部 ABI:arm64 真机、x86_64 模拟器、armeabi-v7a 老设备,
        // 避免在不受支持的架构上加载原生库时闪退
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
        }
    }

    // CI 注入签名参数(-PSTORE_FILE 等)时使用正式签名,否则回退 debug 签名,
    // 保证产出的 release APK 无需额外配置即可直接安装。
    val storeFilePath = project.findProperty("STORE_FILE") as String?
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = project.findProperty("STORE_PASSWORD") as String
                keyAlias = project.findProperty("KEY_ALIAS") as String
                keyPassword = project.findProperty("KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // FFmpeg(libx264 + 全部内置滤镜,tmix/minterpolate 均为内置滤镜)。
    // 原版 com.arthenica 已于 2025-04 从 Maven Central 移除;xch168 fork 是空壳 AAR,
    // 故使用社区维护的 ffmpegkit-maintained(FFmpeg 8.1,保留原包名)。
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min-gpl:8.1.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
