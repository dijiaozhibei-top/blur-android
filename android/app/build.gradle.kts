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

        // 只打包 64 位 ARM,控制 APK 体积(现代安卓手机均为 arm64)
        ndk {
            abiFilters += listOf("arm64-v8a")
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
    // FFmpeg(libx264 + 全部内置滤镜,tmix/minterpolate 均为内置滤镜)
    implementation("com.arthenica:ffmpeg-kit-min-gpl:6.0-2")

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
