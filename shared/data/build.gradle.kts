plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    androidTarget()
    // iOS targets intentionally disabled for local build stability
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:domain"))
                api(project(":shared:downloader-engine"))
                implementation(project(":shared:playlist-parser"))
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.okio)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
            }
        }
    }
}

android {
    namespace = "com.ytdownloader.shared.data"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}
