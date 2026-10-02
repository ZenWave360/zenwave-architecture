plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":workspace-runtime"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("io.zenwave360.architecture.cli.MainKt")
    applicationName = "zenwave"
}

tasks.test {
    useJUnitPlatform()
}
