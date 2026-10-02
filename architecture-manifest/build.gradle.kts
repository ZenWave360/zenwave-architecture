import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.kotlinx.kover)
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js(IR) {
        nodejs()
        // The browser target makes browser use checkable: jsBrowserTest (part of check) loads this
        // library in headless Chromium, so a Node-only import fails the build here, not in a consumer.
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib-common"))
            implementation(libs.json.schema.ref.parser.kmp)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(kotlin("stdlib-jdk8"))
        }

        jvmTest.dependencies {
            implementation("com.networknt:json-schema-validator:2.0.4")
        }
    }
}

val hasSigningCredentials = sequenceOf(
    "signingInMemoryKey",
    "signingKey",
    "signing.secretKeyRingFile"
).any { !providers.gradleProperty(it).orNull.isNullOrBlank() }

mavenPublishing {
    publishToMavenCentral()
    if (hasSigningCredentials) {
        signAllPublications()
    }
    pom {
        name.set("ZenWave Architecture Manifest")
        description.set("Shared manifest parsing and loading library for ZenWave workspace tooling")
        url.set("https://github.com/ZenWave360/zenwave-architecture")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("ivangsa")
                name.set("Ivan Garcia Sainz-Aja")
                email.set("ivangsa@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/ZenWave360/zenwave-architecture.git")
            developerConnection.set("scm:git:ssh://github.com/ZenWave360/zenwave-architecture.git")
            url.set("https://github.com/ZenWave360/zenwave-architecture")
        }
    }
}
