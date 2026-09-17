import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvmToolchain(17)
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    js(IR) {
        nodejs()
        binaries.library()
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":manifest-graph"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.json.schema.ref.parser.kmp)
        }
    }
}

val hasSigningCredentials = sequenceOf(
    "signingInMemoryKey", "signingKey", "signing.secretKeyRingFile",
).any { !providers.gradleProperty(it).orNull.isNullOrBlank() }

mavenPublishing {
    publishToMavenCentral()
    if (hasSigningCredentials) signAllPublications()
    pom {
        name.set("ZenWave Workspace Runtime")
        description.set("Reusable semantic workspace query runtime for MCP, Web, CLI and LSP adapters")
        url.set("https://github.com/ZenWave360/zenwave-manifest")
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
            connection.set("scm:git:git://github.com/ZenWave360/zenwave-manifest.git")
            developerConnection.set("scm:git:ssh://github.com/ZenWave360/zenwave-manifest.git")
            url.set("https://github.com/ZenWave360/zenwave-manifest")
        }
    }
}
