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

rootProject.name = "TikTokTrends"

include(":core")

// CORE_ONLY=1 builds just the pure-Kotlin module (useful without an Android SDK).
if (System.getenv("CORE_ONLY") != "1") {
    include(":app")
}
