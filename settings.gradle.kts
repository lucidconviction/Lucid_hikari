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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "hikari"
include(":app")

// SmartTube's YouTube engine (MediaServiceCore, MIT) — the exact engine the
// NuvioPlusIPTV fork uses for the YouTube tab. Android-only library modules.
include(":mediaservicecore:mediaserviceinterfaces")
include(":mediaservicecore:youtubeapi")
include(":mediaservicecore:sharedutils")
include(":mediaservicecore:commons-io")
include(":mediaservicecore:j2v8")
