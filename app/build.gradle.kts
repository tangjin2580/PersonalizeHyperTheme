import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tangjin.personalizehyper.theme"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.tangjin.personalizehyper.theme"
        minSdk = 29
        targetSdk = 34
        versionCode = 20
        versionName = "2.0.0"

        // 只出手机端 ABI，x86 没有实际意义却会让体积翻几倍
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 注意：不要引入 proguard-log.pro。
            // 那份配置会 -assumenosideeffects 掉 android.util.Log，
            // 但原版 APK 的 release 是保留 logcat 输出的（LogHelper 靠它双写），
            // 去掉后 adb 调试会完全看不到日志，与原版行为不符。
            setProguardFiles(listOf("proguard-rules.pro"))
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/**"
        }
        // 对应 android:extractNativeLibs=false：
        // so 以页对齐 + STORED 方式打包，否则部分 ROM 安装会报 INSTALL_FAILED...（res=-2）
        jniLibs {
            useLegacyPackaging = false
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "PersonalizeHyperTheme-$versionName-$name.apk"
        }
    }
}

dependencies {
    // Xposed API 由框架提供，仅参与编译
    compileOnly("de.robv.android.xposed:api:82")
    // MIUI 内部类（miui.drm.*）的桩，运行时由宿主进程提供
    compileOnly(files("libs/miui-framework.jar"))

    // 方法/字段查找
    implementation("com.github.kyuubiran:EzXHelper:2.2.1")
    // 按字符串特征反查被混淆的方法（自带 libdexkit.so）
    implementation("org.luckypray:DexKit:1.1.8")

    // Jetpack Compose（现代化 UI）
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
}
