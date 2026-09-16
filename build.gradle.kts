plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

group = "io.zenwave360.manifest"
version = "1.0.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        mavenLocal()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    // Karma launches Chrome through CHROME_BIN. When it is not set, fall back to a locally
    // installed Chromium-based browser (Chrome, Chromium or Microsoft Edge).
    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>()
        .matching { it.name == "jsBrowserTest" }
        .configureEach {
            if (System.getenv("CHROME_BIN").isNullOrBlank()) {
                val candidates = listOf(
                    "C:/Program Files/Google/Chrome/Application/chrome.exe",
                    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                    "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                    "/usr/bin/google-chrome",
                    "/usr/bin/chromium",
                    "/usr/bin/chromium-browser",
                    "/usr/bin/microsoft-edge",
                )
                candidates.firstOrNull { file(it).exists() }?.let { environment("CHROME_BIN", it) }
            }
        }
}
