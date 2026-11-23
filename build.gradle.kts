import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

plugins {
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish) apply false
}

group = "io.zenwave360.architecture"
version = "1.0.0-SNAPSHOT"

val npmVersion = providers.gradleProperty("npmVersion")
    .getOrElse(version.toString().replace("-SNAPSHOT", "-next.0"))
require(npmVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"))) {
    "npmVersion must be a semantic version, got $npmVersion"
}
@Suppress("UNCHECKED_CAST")
val npmPackages = JsonSlurper().parse(file("gradle/npm-packages.json")) as Map<String, String>
val npmPack = tasks.register("npmPack") {
    group = "distribution"
    description = "Build and pack all npm packages into build/npm"
}

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

    if (name in npmPackages) {
        val moduleName = name
        val packageDirectory = rootProject.layout.buildDirectory.dir("js/packages/zenwave-architecture-$moduleName")
        val tarball = rootProject.layout.buildDirectory.file("npm/zenwave360-$moduleName-$npmVersion.tgz")
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
                targets.withType<KotlinJsIrTarget>().configureEach {
                    compilations["main"].packageJson {
                        customField("name", "@zenwave360/$moduleName")
                        customField("version", npmVersion)
                        customField("description", npmPackages.getValue(moduleName))
                        customField("publishConfig", mapOf(
                            "access" to "public",
                            "registry" to "https://registry.npmjs.org/",
                            "tag" to if (npmVersion.contains("-")) "next" else "latest"
                        ))
                        customField("homepage", "https://github.com/ZenWave360/zenwave-architecture")
                        customField("repository", mapOf(
                            "type" to "git",
                            "url" to "git+https://github.com/ZenWave360/zenwave-architecture.git"
                        ))
                        customField("license", "MIT")
                    }
                }
            }
            tasks.matching { it.name == "jsProductionLibraryCompileSync" }.configureEach {
                inputs.files(rootProject.file("README.md"), rootProject.file("LICENSE"))
                    .withPropertyName("npmDocumentation")
                doLast {
                    copy {
                        from(rootProject.file("README.md"), rootProject.file("LICENSE"))
                        into(packageDirectory)
                    }
                }
            }
            val pack = tasks.register<Exec>("npmPack") {
                group = "distribution"
                description = "Build and pack @zenwave360/$moduleName"
                dependsOn("jsProductionLibraryCompileSync", "jsPackageJson")
                inputs.dir(packageDirectory).withPropertyName("npmPackage")
                inputs.property("npmVersion", npmVersion)
                outputs.file(tarball)
                workingDir(rootProject.projectDir)
                val arguments = listOf(
                    "pack", "./build/js/packages/zenwave-architecture-$moduleName",
                    "--ignore-scripts", "--pack-destination", "./build/npm"
                )
                if (System.getProperty("os.name").startsWith("Windows")) {
                    commandLine(listOf("cmd", "/c", "npm") + arguments)
                } else {
                    commandLine(listOf("npm") + arguments)
                }
                doFirst { tarball.get().asFile.parentFile.mkdirs() }
            }
            npmPack.configure { dependsOn(pack) }
            tasks.named("assemble") { dependsOn(pack) }
        }
    }

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

tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }
