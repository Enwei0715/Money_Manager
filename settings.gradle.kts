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
    }
}

rootProject.name = "MoneyManager"
include(":core")
// 沒有 Android SDK 的環境（例如只跑 core 單元測試）可設 CORE_ONLY=1 略過 app 模組
if (System.getenv("CORE_ONLY") == null) include(":app")
