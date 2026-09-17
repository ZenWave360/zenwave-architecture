pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "zenwave-manifest"

// CI resolves published Maven dependencies; local development can use siblings.
val useLocalDependencies = providers.gradleProperty("useLocalDependencies")
    .map { it.toBooleanStrict() }.getOrElse(true)

val localJsonRefParser = file("../json-schema-ref-parser-kmp")
if (useLocalDependencies && localJsonRefParser.exists()) {
    includeBuild(localJsonRefParser) {
        dependencySubstitution {
            substitute(module("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp")).using(project(":"))
        }
    }
}

val localDslKotlin = file("../dsl-kotlin")
if (useLocalDependencies && localDslKotlin.exists()) {
    includeBuild(localDslKotlin) {
        dependencySubstitution {
            substitute(module("io.zenwave360.dsl:dsl-kotlin")).using(project(":"))
        }
    }
}

include("manifest-core", "manifest-graph", "workspace-runtime", "workspace-mcp", "workspace-cli")
