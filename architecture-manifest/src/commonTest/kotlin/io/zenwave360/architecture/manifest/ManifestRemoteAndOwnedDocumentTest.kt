package io.zenwave360.architecture.manifest

import io.zenwave360.jsonrefparser.io.DocumentLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SR-R4 (remote manifest skips `workspace`) and SR-R5 (a path next to a declared artifact). */
class ManifestRemoteAndOwnedDocumentTest {
    private val remote = "https://example.org/zenwave-architecture.yml"
    private val local = "file:///work/architecture/zenwave-architecture.yml"
    private val gitOrders = "https://git.example.org/sales/orders/raw/v1.2.0/openapi.yml"

    private class Recording(private val documents: Map<String, String>) : DocumentLoader {
        val loaded = mutableListOf<String>()
        override fun canLoad(uri: String): Boolean = true
        override suspend fun load(uri: String): String {
            loaded += uri
            return documents[uri] ?: error("not found: $uri")
        }
    }

    private fun manifestText(resolution: String, contentUrl: String = "\${content.path}") = """
        config:
          contentResolution: $resolution
          sources:
            workspace:
              basePathExpression: "../${'$'}{service.id}"
            apicurio:
              server: https://registry.example.org
            maven:
              provider: central
              server: https://repo.example.org
              repository: releases
            git:
              provider: generic
              server: https://git.example.org
              contentUrlExpression: "${'$'}{server}/${'$'}{domain.id}/${'$'}{service.id}/raw/v${'$'}{version}/$contentUrl"
        domains:
          sales:
            services:
              orders:
                version: 1.0.0
                artifacts:
                  - { type: openapi, path: openapi.yml, version: 1.2.0 }
    """.trimIndent()

    @Test
    fun remoteManifestReadsFromGitOnlyWhenWorkspaceIsListedFirst() = runTest {
        val io = Recording(mapOf(gitOrders to "openapi: git"))
        val loader = ZenWaveManifestLoader(listOf(io), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[workspace, git]"))
        val service = manifest.services.single()
        val artifact = service.artifacts.single()
        assertEquals("openapi: git", loader.loadArtifactText(manifest, service, artifact))
        assertEquals(listOf(gitOrders), io.loaded)
        assertEquals(listOf(ManifestSourceName.GIT), loader.buildArtifactCandidates(manifest, service, artifact).map { it.source })
    }

    @Test
    fun localManifestStillUsesWorkspaceFirst() = runTest {
        val loader = ZenWaveManifestLoader(listOf(Recording(emptyMap())), archiveEntryLoader = null)
        val manifest = loader.parse(local, manifestText("[workspace, git]"))
        val service = manifest.services.single()
        val sources = loader.buildArtifactCandidates(manifest, service, service.artifacts.single()).map { it.source }
        assertEquals(listOf(ManifestSourceName.WORKSPACE, ManifestSourceName.GIT), sources)
    }

    @Test
    fun workspaceAloneOnARemoteManifestEndsInTerminalErrorNamingTheSkip() = runTest {
        val io = Recording(emptyMap())
        val loader = ZenWaveManifestLoader(listOf(io), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[workspace]"))
        val service = manifest.services.single()
        val error = assertFailsWith<ManifestResourceLoadException> {
            loader.loadArtifactText(manifest, service, service.artifacts.single())
        }
        assertTrue("'workspace' was skipped because the manifest is remote" in error.message.orEmpty(), error.message)
        assertTrue(io.loaded.isEmpty())
    }

    @Test
    fun preferredWorkspaceOnARemoteManifestIsNotActive() = runTest {
        val loader = ZenWaveManifestLoader(listOf(Recording(emptyMap())), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[workspace, git]"))
        val service = manifest.services.single()
        val error = assertFailsWith<ManifestResolutionException> {
            loader.buildArtifactCandidates(
                manifest, service, service.artifacts.single(),
                ManifestLoadOptions(preferredSource = ManifestSourceName.WORKSPACE),
            )
        }
        assertTrue("Preferred source 'workspace' is not active" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aConfiguredWorkspaceBasePathExpressionIsNotAnErrorOnARemoteManifest() = runTest {
        val loader = ZenWaveManifestLoader(listOf(Recording(emptyMap())), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[workspace, git]"))
        assertEquals("../\${service.id}", manifest.config.sources.workspace.basePathExpression)
        assertTrue(manifest.diagnostics.none { it.severity == ManifestDiagnosticSeverity.ERROR }, manifest.diagnostics.toString())
    }

    @Test
    fun ownedDocumentLoadsThroughTheOrderedSourcesWithTheGivenVersion() = runTest {
        val url = "https://git.example.org/sales/orders/raw/v1.2.0/schemas/common.yml"
        val io = Recording(mapOf(url to "common: git"))
        val loader = ZenWaveManifestLoader(listOf(io), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[workspace, apicurio, maven, git]"))
        val service = manifest.services.single()
        val result = loader.loadOwnedDocument(manifest, service, "1.2.0", "./schemas/../schemas/common.yml")
        assertEquals("common: git", result.content)
        assertEquals("schemas/common.yml", result.path)
        assertEquals(ManifestSourceName.GIT, result.resource?.source)
        assertEquals(listOf(url), io.loaded)
    }

    @Test
    fun ownedDocumentOnALocalManifestTriesWorkspaceFirst() = runTest {
        val loader = ZenWaveManifestLoader(listOf(Recording(emptyMap())), archiveEntryLoader = null)
        val manifest = loader.parse(local, manifestText("[workspace, git]"))
        val candidates = loader.buildOwnedDocumentCandidates(manifest, manifest.services.single(), "1.2.0", "schemas/common.yml")
        assertEquals(
            listOf(
                ManifestSourceName.WORKSPACE to "file:///work/orders/schemas/common.yml",
                ManifestSourceName.GIT to "https://git.example.org/sales/orders/raw/v1.2.0/schemas/common.yml",
            ),
            candidates.map { it.source to it.uri },
        )
    }

    @Test
    fun ownedDocumentRefusesAPathAboveTheRootBeforeAnyIo() = runTest {
        val io = Recording(emptyMap())
        val loader = ZenWaveManifestLoader(listOf(io), archiveEntryLoader = null)
        val manifest = loader.parse(local, manifestText("[workspace, git]"))
        val service = manifest.services.single()
        listOf("../secret.yml", "schemas/../../secret.yml", "..\\secret.yml").forEach { path ->
            assertFailsWith<ManifestResolutionException>(path) { loader.loadOwnedDocument(manifest, service, "1.2.0", path) }
            assertFailsWith<ManifestResolutionException>(path) {
                loader.buildOwnedDocumentCandidates(manifest, service, "1.2.0", path)
            }
        }
        assertTrue(io.loaded.isEmpty())
    }

    @Test
    fun artifactVariablesFailBeforeIoForAnOwnedDocument() = runTest {
        val io = Recording(emptyMap())
        val loader = ZenWaveManifestLoader(listOf(io), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[git]", "\${artifact.type}/\${content.path}"))
        val service = manifest.services.single()
        val error = assertFailsWith<ManifestResolutionException> {
            loader.loadOwnedDocument(manifest, service, "1.2.0", "schemas/common.yml")
        }
        assertTrue("artifact.type" in error.message.orEmpty(), error.message)
        assertTrue(io.loaded.isEmpty())
    }

    @Test
    fun noGivenVersionLeavesVersionUnresolved() = runTest {
        val loader = ZenWaveManifestLoader(listOf(Recording(emptyMap())), archiveEntryLoader = null)
        val manifest = loader.parse(remote, manifestText("[git]"))
        val error = assertFailsWith<ManifestResolutionException> {
            loader.buildOwnedDocumentCandidates(manifest, manifest.services.single(), null, "schemas/common.yml")
        }
        assertTrue("version" in error.message.orEmpty(), error.message)
    }
}
