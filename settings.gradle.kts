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

// Override sibling locations to build against feature worktrees.
fun localBuildDir(property: String, environmentVariable: String, default: String): File =
    file(
        providers.gradleProperty(property).orNull
            ?: providers.environmentVariable(environmentVariable).orNull
            ?: default,
    )

val localJsonRefParser = localBuildDir(
    "zenwave.local.jsonRefParserDir", "ZENWAVE_LOCAL_JSON_REF_PARSER_DIR", "../json-schema-ref-parser-kmp",
)
if (useLocalDependencies && localJsonRefParser.exists()) {
    includeBuild(localJsonRefParser) {
        dependencySubstitution {
            substitute(module("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp")).using(project(":"))
        }
    }
}

val localDslKotlin = localBuildDir("zenwave.local.dslKotlinDir", "ZENWAVE_LOCAL_DSL_KOTLIN_DIR", "../dsl-kotlin")
if (useLocalDependencies && localDslKotlin.exists()) {
    includeBuild(localDslKotlin) {
        dependencySubstitution {
            substitute(module("io.zenwave360.dsl:dsl-kotlin")).using(project(":"))
        }
    }
}

include("manifest-core", "manifest-graph", "workspace-runtime", "workspace-mcp", "workspace-cli")
