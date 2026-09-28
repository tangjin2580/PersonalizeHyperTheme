plugins {
    // 注意：libxposed 102（io.github.libxposed:interface:102.0.0）要求 compileSdk >= 37，
    // 而 AGP 8.7.3 的 compileSdk 上限是 35，因此必须升级 AGP 到支持 37 的版本（8.13.2）。
    // AGP 8.13.2 需要 Gradle 8.13（见 gradle-wrapper.properties）。
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
