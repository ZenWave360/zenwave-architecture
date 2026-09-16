pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "zenwave-manifest"

// Sibling checkouts are included as composite builds when they exist. The directory defaults to
// ../<repository>; override it (for example to build against a git worktree) with a Gradle property
// or an environment variable:
//   -Pzenwave.local.jsonRefParserDir=../json-schema-ref-parser-kmp-feature   (ZENWAVE_LOCAL_JSON_REF_PARSER_DIR)
//   -Pzenwave.local.dslKotlinDir=../dsl-kotlin-feature                         (ZENWAVE_LOCAL_DSL_KOTLIN_DIR)
fun localBuildDir(property: String, environmentVariable: String, default: String): File =
    file(
        providers.gradleProperty(property).orNull
            ?: providers.environmentVariable(environmentVariable).orNull
            ?: default,
    )

val localJsonRefParser = localBuildDir(
    "zenwave.local.jsonRefParserDir", "ZENWAVE_LOCAL_JSON_REF_PARSER_DIR", "../json-schema-ref-parser-kmp",
)
if (localJsonRefParser.exists()) {
    includeBuild(localJsonRefParser) {
        dependencySubstitution {
            substitute(module("io.zenwave360.jsonrefparser:json-schema-ref-parser-kmp")).using(project(":"))
        }
    }
}

val localDslKotlin = localBuildDir("zenwave.local.dslKotlinDir", "ZENWAVE_LOCAL_DSL_KOTLIN_DIR", "../dsl-kotlin")
if (localDslKotlin.exists()) {
    includeBuild(localDslKotlin) {
        dependencySubstitution {
            substitute(module("io.zenwave360.dsl:dsl-kotlin")).using(project(":"))
        }
    }
}

include("manifest-core", "manifest-graph")
