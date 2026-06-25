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
        // Xposed API artifacts (de.robv.android.xposed:api).
        maven("https://api.xposed.info/")
        // libsu (root shell) artifacts (com.github.topjohnwu.libsu).
        maven("https://jitpack.io")
    }
}

rootProject.name = "PlayIntegrityAlert"

include(":app")
