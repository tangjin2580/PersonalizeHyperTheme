pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Xposed API 官方仓库
        maven("https://api.xposed.info")
    }
}

rootProject.name = "PersonalizeHyperTheme"
include(":app")
