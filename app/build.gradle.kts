import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tangjin.personalizehyper.theme"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

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

    // 条件签名：仅在 CI / 本地提供 mrli-key 环境变量时启用，否则出未签名包（不阻塞构建）。
    // 环境变量：KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    val keystoreFileEnv = System.getenv("KEYSTORE_FILE")
    val keystorePwEnv = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS")
    val keyPwEnv = System.getenv("KEY_PASSWORD")
    val hasReleaseSigning = keystoreFileEnv != null && keystorePwEnv != null &&
        keyAliasEnv != null && keyPwEnv != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreFileEnv!!)
                storePassword = keystorePwEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPwEnv
            }
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

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // 仅排除 license/notice 等重复文件，绝不能排除 META-INF/xposed/** ——
        // libxposed 的模块入口声明文件 META-INF/xposed/java_init.list 就在这里。
        resources {
            excludes += setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/DEPENDENCIES*",
                "/META-INF/*.kotlin_module",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.version"
            )
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
    // libxposed 102 模块 API（仅参与编译，运行时由 LSPosed 注入）
    compileOnly("io.github.libxposed:api:102.0.0")
    // 经典 XposedBridge.log 编译期引用：运行时由 LSPosed 兼容提供，LogHelper 已做兜底
    compileOnly("de.robv.android.xposed:api:82")
    // MIUI 内部类（miui.drm.*）的桩，运行时由宿主进程提供
    compileOnly(files("libs/miui-framework.jar"))

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
