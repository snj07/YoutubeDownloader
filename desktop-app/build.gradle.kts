plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared:data"))
    implementation(project(":shared:domain"))
    implementation(project(":shared:downloader-engine"))
    implementation(libs.coroutines.core)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.ytdownloader.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "YouTubeDownloader"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("src/main/resources/icons/download.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/download.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/download.png"))
            }
        }
    }
}
