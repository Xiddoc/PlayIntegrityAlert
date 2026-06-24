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
    }
}

rootProject.name = "PlayIntegrityAlert"

include(":app")
