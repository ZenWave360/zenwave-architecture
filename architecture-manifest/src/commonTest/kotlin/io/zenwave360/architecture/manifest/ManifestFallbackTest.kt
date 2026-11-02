package io.zenwave360.architecture.manifest

import io.zenwave360.jsonrefparser.io.InMemoryLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManifestFallbackTest {
    @Test
    fun selectsFirstNonBlankAlternativeAndPreservesItsValue() {
        val properties = mapOf("null" to null, "empty" to "", "blank" to " \t\n", "a" to " hello ", "b" to "world")
        listOf(
            "a|b" to " hello ",
            "missing|b" to "world",
            "null|b" to "world",
            "empty|b" to "world",
            "blank|b" to "world",
            "missing|null|empty|blank|b" to "world",
            " missing | b " to "world",
            "|missing||b|" to "world",
            "a" to " hello ",
            "empty" to "",
            "blank" to " \t\n",
        ).forEach { (expression, expected) ->
            val result = ManifestVariableInterpolator.interpolate("\${$expression}", properties)
            assertEquals(expected, result.value, expression)
            assertTrue(result.unresolvedVariables.isEmpty(), expression)
        }
        val missing = ManifestVariableInterpolator.interpolate("\${missing|null|empty|blank}", properties)
        assertEquals("\${missing|null|empty|blank}", missing.value)
        assertEquals(listOf("missing|null|empty|blank"), missing.unresolvedVariables)
        val multiple = ManifestVariableInterpolator.interpolate("\${missing|b}/\${a|b}/\${missing|b}", properties)
        assertEquals("world/ hello /world", multiple.value)
        assertEquals("\${|||}", ManifestVariableInterpolator.interpolate("\${|||}", properties).value)
    }

    @Test
    fun groupedArtifactsLoadFromSharedReleaseTagWithoutChangingTypesOrPaths() = runTest {
        val base = "https://github.com/arcadia-editions/orders/raw/release/"
        val urls = listOf(
            "${base}asyncapi-all/v0.1.1/asyncapi.yml",
            "${base}asyncapi-all/v0.1.1/asyncapi-client.yml",
            "${base}openapi/v0.1.0/openapi.yml",
            "${base}zdl/v0.1.0/domain-model.zdl",
        )
        val loader = ZenWaveManifestLoader(urls.mapIndexed { i, url -> InMemoryLoader(url, "content-$i") })
        val manifest = loader.parse("file:///work/architecture.yml", """
            config:
              contentResolution: [git]
              sources:
                git:
                  provider: github
                  contentUrlExpression: "${'$'}{server}/arcadia-editions/${'$'}{owner.repository}/raw/release/${'$'}{artifact.releaseGroup|artifact.type}/v${'$'}{artifact.version}/${'$'}{content.path}"
            domains:
              sales:
                services:
                  orders:
                    repository: orders
                    artifacts:
                      - { type: asyncapi, path: asyncapi.yml, version: 0.1.1, releaseGroup: asyncapi-all }
                      - { type: asyncapi-client, path: asyncapi-client.yml, version: 0.1.1, releaseGroup: asyncapi-all }
                      - { type: openapi, path: openapi.yml, version: 0.1.0 }
                      - { type: zdl, path: domain-model.zdl, version: 0.1.0, releaseGroup: "   " }
        """.trimIndent())
        assertTrue(manifest.diagnostics.isEmpty(), manifest.diagnostics.toString())
        val service = manifest.services.single()
        assertEquals(listOf("asyncapi", "asyncapi-client", "openapi", "zdl"), service.artifacts.map { it.type })
        assertNull(service.artifacts[2].releaseGroup)
        service.artifacts.forEachIndexed { i, artifact ->
            assertEquals(artifact.releaseGroup, artifact.copy().releaseGroup)
            val context = loader.artifactResolutionContext(manifest, service, artifact).variables()
            assertEquals(artifact.releaseGroup, context["artifact.releaseGroup"])
            assertEquals(artifact.type, context["artifact.type"])
            assertEquals(urls[i], loader.buildArtifactCandidates(manifest, service, artifact).single().uri)
            assertEquals("content-$i", loader.loadArtifactText(manifest, service, artifact))
        }
    }

    @Test
    fun runtimeFallbacksSelectBeforeEncodingAndValidateEachDocsLookup() = runTest {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.parse("file:///work/architecture.yml", """
            config:
              properties:
                static: default
                blank: "   "
                artifact.type: forged
                artifact.releaseGroup: forged
              contentResolution: [git]
              sources:
                git:
                  provider: generic
                  contentUrlExpression: "https://git.example.com/${'$'}{blank|artifact.releaseGroup|static}/${'$'}{service.docs[missing]|content.path}"
            domains:
              sales:
                artifacts: [{ type: openapi, path: contracts/api file.yml, version: 1, releaseGroup: " group name " }]
        """.trimIndent())
        assertEquals(2, manifest.diagnostics.size)
        assertTrue(manifest.diagnostics.all { it.code == "reserved-runtime-variable" })
        val owner = manifest.domains.single()
        val artifact = owner.artifacts.single()
        assertEquals("https://git.example.com/%20group%20name%20/contracts/api%20file.yml",
            loader.buildArtifactCandidates(manifest, owner, artifact).single().uri)
        val context = loader.artifactResolutionContext(manifest, owner, artifact)
        assertEquals("default", loader.interpolateRuntime("git", "\${missing|static}", context))
        assertFailsWith<ManifestResolutionException> {
            loader.interpolateRuntime("git", "\${service.docs[bad/key]|artifact.type}", context)
        }
        assertFailsWith<ManifestResolutionException> {
            loader.interpolateRuntime("git", "\${missing|absent}", context)
        }
    }

    @Test
    fun fallbacksWorkInCoordinatesWorkspaceAndMavenRepository() = runTest {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.parse("file:///work/architecture.yml", """
            config:
              groupIdExpression: "${'$'}{service.repository|owner.id}"
              artifactIdExpression: "${'$'}{artifact.releaseGroup|artifact.type}"
              contentResolution: [workspace, git, maven]
              sources:
                workspace:
                  basePathExpression: "${'$'}{service.repository|owner.id}"
                git:
                  provider: generic
                  contentUrlExpression: "https://git.example.com/${'$'}{missing|groupId}/${'$'}{artifactId|artifact.type}/${'$'}{content.path}"
                maven:
                  provider: central
                  repository: "${'$'}{service.repository|owner.id}"
            domains:
              sales:
                services:
                  orders:
                    artifacts: [{ type: openapi, path: api.yml, version: 1 }]
        """.trimIndent())
        assertTrue(manifest.diagnostics.isEmpty(), manifest.diagnostics.toString())
        val service = manifest.services.single()
        val candidates = loader.buildArtifactCandidates(manifest, service, service.artifacts.single())
        assertEquals(listOf(
            "file:///work/orders/api.yml",
            "https://git.example.com/orders/openapi/api.yml",
            "https://repo.maven.apache.org/orders/orders/openapi/1/openapi-1.jar",
        ), candidates.map { it.uri })
        listOf("\${artifact.type|groupId}", "\${groupId|artifact.type}").forEach { expression ->
            val fallbackManifest = manifest.copy(config = manifest.config.copy(
                contentResolution = listOf("git"),
                groupIdExpression = "\${service.repository}",
                sources = manifest.config.sources.copy(git = ManifestGitSource(
                    provider = "generic", contentUrlExpression = "https://git.example.com/$expression",
                )),
            ))
            assertEquals("https://git.example.com/openapi",
                loader.buildArtifactCandidates(fallbackManifest, service, service.artifacts.single()).single().uri)
        }
        val recursive = loader.parse("file:///work/architecture.yml", """
            config:
              groupIdExpression: "${'$'}{owner.id|groupId}"
              artifactIdExpression: "${'$'}{artifact.type|artifactId}"
            domains: {}
        """.trimIndent())
        assertEquals(2, recursive.diagnostics.count { it.code == "recursive-coordinate-expression" })
    }

    @Test
    fun staticPropertiesUseTheSameFallbackWithoutChangingExactNames() = runTest {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.parse("file:///work/architecture.yml", """
            config:
              properties:
                blank: "   "
                path: contracts/api.yml
            domains:
              sales:
                artifacts: [{ type: openapi, path: "${'$'}{missing|blank|path}", version: 1 }]
        """.trimIndent())
        assertTrue(manifest.diagnostics.isEmpty(), manifest.diagnostics.toString())
        assertEquals("contracts/api.yml", manifest.domains.single().artifacts.single().path)
    }
}
