plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared:data"))
    implementation(project(":shared:domain"))
    implementation(project(":shared:downloader-engine"))
    implementation(libs.coroutines.core)
}

application {
    mainClass.set("com.ytdownloader.cli.MainKt")
}
