import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.kotlinx.kover)
}

val npmVersion = providers.gradleProperty("npmVersion")
    .getOrElse(version.toString().replace("-SNAPSHOT", "-next.0"))

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    js(IR) {
        nodejs()
        binaries.library()
        compilations["main"].packageJson {
            customField("name", "@zenwave360/manifest-core")
            customField("version", npmVersion)
            customField("description", "ZenWave architecture manifest parser for JavaScript/TypeScript")
            customField("publishConfig", mapOf(
                "access" to "public",
                "registry" to "https://registry.npmjs.org/",
                "tag" to if (npmVersion.contains("-")) "next" else "latest"
            ))
            customField("homepage", "https://github.com/ZenWave360/zenwave-manifest")
            customField("repository", mapOf(
                "type" to "git",
                "url" to "https://github.com/ZenWave360/zenwave-manifest"
            ))
            customField("license", "MIT")
        }
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
        name.set("ZenWave Manifest Core")
        description.set("Shared manifest parsing and loading library for ZenWave workspace tooling")
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
