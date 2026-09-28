import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tangjin.personalizehyper.theme"
    // libxposed 102 要求 compileSdk >= 37（见根 build.gradle.kts 注释）
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

    // ── 正式签名（条件启用）──────────────────────────────────────────────
    // 仅当环境变量 KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    // 齐备时才启用签名；否则 release 出「未签名」包，构建不失败。
    // 本地开发 / CI 无密钥时走未签名，便于调试；CI 通过 GitHub Secrets 注入上述变量，
    // 即可产出可安装的、用 mrli-key.jks 签名的 release（V1+V2）。
    // 注意：这段必须放在 buildTypes 之前 —— Kotlin 脚本顶层声明按顺序求值，
    // buildTypes 里要引用 hasReleaseSigning，声明在使用之后会报 Unresolved reference。
    val keystoreFileEnv = System.getenv("KEYSTORE_FILE")
    val keystorePwEnv = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS")
    val keyPwEnv = System.getenv("KEY_PASSWORD")
    val hasReleaseSigning = keystoreFileEnv != null && keystorePwEnv != null &&
        keyAliasEnv != null && keyPwEnv != null

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
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
            // 仅在有签名密钥时挂上 release 签名配置；否则保持未签名（不阻塞构建）
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
        resources {
            // 注意：不能排除 /META-INF/**，否则 libxposed 的模块声明
            // （src/main/resources/META-INF/xposed/{module.prop,scope.list,java_init.list}）
            // 会被从 APK 里剥掉，框架将识别不到模块。
            // 只排除依赖自带的 LICENSE / NOTICE 等可能重复的文件。
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
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
    // libxposed API 102：由框架（LSPosed / Vector）在运行时提供，仅参与编译
    // 注意：不再是 de.robv.android.xposed:api，整模块改用 io.github.libxposed 新版 API
    compileOnly("io.github.libxposed:api:102.0.0")
    // service 提供 getRemotePreferences 等跨进程偏好能力（替代 legacy XSharedPreferences）
    implementation("io.github.libxposed:service:102.0.0")

    // MIUI 内部类（miui.drm.*）的桩，运行时由宿主进程提供
    compileOnly(files("libs/miui-framework.jar"))

    // 方法/字段查找：EzXHelper 仅支持 legacy，已在 api102 分支移除；
    // 这里改用本工程自写的 Xp 反射工具（见 Xp.kt），零额外依赖。
    // 按字符串特征反查被混淆的方法（自带 libdexkit.so，框架无关，api102 下仍可用）
    implementation("org.luckypray:DexKit:1.1.8")

    // Jetpack Compose（现代化 UI）
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
}
