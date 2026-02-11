pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "youtube-downloader"

include(":shared:domain")
include(":shared:data")
include(":shared:downloader-engine")
include(":shared:playlist-parser")
include(":desktop-app")
include(":cli")
include(":android-app")
