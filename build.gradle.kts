plugins {
    // Centralize plugin versions to avoid multiple Kotlin plugin loads
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
}

allprojects {
    group = "com.ytdownloader"
    version = "1.0.0"
}

val desktopProject = project(":desktop-app")
val cliProject = project(":cli")

fun isMacHost(): Boolean = System.getProperty("os.name")?.lowercase(java.util.Locale.ROOT)?.contains("mac") == true
fun isWindowsHost(): Boolean = System.getProperty("os.name")?.lowercase(java.util.Locale.ROOT)?.contains("win") == true
fun isLinuxHost(): Boolean {
    val os = System.getProperty("os.name")?.lowercase(java.util.Locale.ROOT) ?: return false
    return os.contains("nux") || os.contains("nix")
}

fun registerDesktopReleaseTask(
    taskName: String,
    osLabel: String,
    desktopTaskPath: String,
    artifactPattern: String,
    publishSubDir: String,
    hostCheck: () -> Boolean
) {
    tasks.register(taskName) {
        group = "distribution"
        description = "Packages $osLabel desktop binary plus CLI zip for publishing."
        dependsOn(desktopTaskPath, ":cli:distZip")
        onlyIf { hostCheck() }
        val publishDir = layout.buildDirectory.dir("publish/$publishSubDir")
        doLast {
            val targetDir = publishDir.get().asFile
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            val composeBinariesDir = desktopProject.layout.buildDirectory.dir("compose/binaries").get().asFile
            if (!composeBinariesDir.exists()) {
                logger.warn("Compose binaries directory not found at ${composeBinariesDir.absolutePath}.")
            } else {
                val artifacts = project.fileTree(composeBinariesDir) {
                    include(artifactPattern)
                }
                if (artifacts.isEmpty) {
                    logger.warn("No $osLabel desktop artifacts matching $artifactPattern found under ${composeBinariesDir.absolutePath}.")
                } else {
                    project.copy {
                        from(artifacts)
                        into(targetDir)
                    }
                }
            }

            val cliZip = cliProject.layout.buildDirectory.file("distributions/cli.zip").get().asFile
            if (!cliZip.exists()) {
                logger.warn("CLI distribution ZIP not found at ${cliZip.absolutePath}.")
            } else {
                project.copy {
                    from(cliZip)
                    into(targetDir)
                    rename { "youtube-downloader-cli-${version}.zip" }
                }
            }

            logger.lifecycle("$osLabel release assets staged in ${targetDir.absolutePath}")
        }
    }
}

registerDesktopReleaseTask(
    taskName = "packageMacReleaseArtifacts",
    osLabel = "macOS",
    desktopTaskPath = ":desktop-app:packageDmg",
    artifactPattern = "**/*.dmg",
    publishSubDir = "mac",
    hostCheck = ::isMacHost
)

registerDesktopReleaseTask(
    taskName = "packageWindowsReleaseArtifacts",
    osLabel = "Windows",
    desktopTaskPath = ":desktop-app:packageMsi",
    artifactPattern = "**/*.msi",
    publishSubDir = "windows",
    hostCheck = ::isWindowsHost
)

registerDesktopReleaseTask(
    taskName = "packageLinuxReleaseArtifacts",
    osLabel = "Linux",
    desktopTaskPath = ":desktop-app:packageDeb",
    artifactPattern = "**/*.deb",
    publishSubDir = "linux",
    hostCheck = ::isLinuxHost
)
