package io.zenwave360.manifest

import io.zenwave360.jsonrefparser.io.DocumentLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestPlatformTest {

    @Test
    fun askingForCapabilitiesNeverFails() {
        val capabilities = ManifestPlatform.capabilities()
        assertTrue(capabilities.all { it.startsWith("manifest.") })
        assertEquals(
            ManifestPlatform.ARCHIVE_EXTRACTION in capabilities,
            ManifestPlatform.isAvailable(ManifestPlatform.ARCHIVE_EXTRACTION),
        )
        assertFalse(ManifestPlatform.isAvailable("manifest.unknown"))
    }

    @Test
    fun manifestCapabilitiesAreReportedThroughThisLibrarysOwnExceptionAndPlatformName() {
        // The $ref parser owns filesystem/classpath/HTTP capabilities and its own exception type;
        // archive extraction is this library's capability, so it reports it itself.
        val error = ManifestCapabilityUnavailableException(
            ManifestPlatform.ARCHIVE_EXTRACTION,
            ManifestPlatform.name,
        )
        assertEquals(ManifestPlatform.ARCHIVE_EXTRACTION, error.capability)
        assertTrue(ManifestPlatform.name in setOf("jvm", "node", "browser"), ManifestPlatform.name)
        assertEquals(ManifestPlatform.name, error.platform)
    }

    @Test
    fun missingArchiveReaderIsReportedAsUnavailableAndTheNextCandidateIsTried() = runTest {
        val gitUri = "https://git.example.com/contracts/openapi.yml"
        val loader = ZenWaveManifestLoader(
            listOf(MapDocumentLoader(mapOf(gitUri to "openapi: 3.1.0"))),
            archiveEntryLoader = null,
        )
        val manifest = parse(loader, contentResolution = "[maven, git]")
        val service = manifest.services.single()
        val artifact = service.artifacts.single()

        val candidates = loader.buildArtifactCandidates(manifest, service, artifact)
        assertEquals(listOf("maven", "git"), candidates.map { it.source })
        assertEquals("contracts/openapi.yml", candidates.first().archiveEntry)

        assertEquals("openapi: 3.1.0", loader.loadArtifactText(manifest, service, artifact))
        assertEquals(gitUri, loader.resolveArtifact(manifest, service, artifact).uri)
    }

    @Test
    fun anUnavailableArchiveReaderIsNamedInTheFinalLoadError() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = parse(loader, contentResolution = "[maven]")
        val service = manifest.services.single()
        val artifact = service.artifacts.single()

        val error = assertFailsWith<ManifestResourceLoadException> {
            loader.loadArtifactText(manifest, service, artifact)
        }
        val expected = "Final load error: Capability '${ManifestPlatform.ARCHIVE_EXTRACTION}' " +
            "is not available on ${ManifestPlatform.name}"
        assertTrue(error.message!!.contains(expected), error.message)

        val result = loader.loadArtifactResult(manifest, service, artifact)
        assertFalse(result.successful)
        assertTrue(result.errorMessage!!.contains(ManifestPlatform.ARCHIVE_EXTRACTION), result.errorMessage)
    }

    private suspend fun parse(loader: ZenWaveManifestLoader, contentResolution: String): ZenWaveManifest = loader.parse(
        "https://example.com/zenwave-architecture.yml",
        """
        config:
          groupIdExpression: io.arcadia
          artifactIdExpression: orders
          contentResolution: $contentResolution
          sources:
            maven:
              provider: central
              server: https://repo.example.com
              repository: maven2
            git:
              provider: generic
              contentUrlExpression: https://git.example.com/${'$'}{content.path}
        domains:
          commerce:
            services:
              orders:
                artifacts: [{ type: openapi, path: contracts/openapi.yml, version: 1.0.0 }]
        """.trimIndent(),
    )

    private class MapDocumentLoader(private val documents: Map<String, String>) : DocumentLoader {
        override fun canLoad(uri: String): Boolean = uri in documents

        override suspend fun load(uri: String): String = documents.getValue(uri)
    }
}
