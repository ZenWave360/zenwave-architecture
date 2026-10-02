package io.zenwave360.architecture.manifest

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException
import io.zenwave360.jsonrefparser.platform.RefParserPlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs in headless Chromium (`jsBrowserTest`) and on Node (`jsNodeTest`). In the browser it is the
 * check that architecture-manifest loads and works without a Node runtime: a Node-only import anywhere in
 * the module graph fails the browser bundle before any test runs.
 */
class ManifestBrowserSmokeTest {

    @Test
    fun archiveExtractionIsNotAvailableOnJs() {
        assertEquals(emptySet(), ManifestPlatform.capabilities())
        assertFalse(ManifestPlatform.isAvailable(ManifestPlatform.ARCHIVE_EXTRACTION))
        assertTrue(ManifestPlatform.name == "node" || ManifestPlatform.name == "browser")
    }

    @Test
    fun loadsAManifestAndItsContentOnlyThroughACallerSuppliedReader() = runTest {
        val reader = RecordingReader(
            mapOf(
                "https://architecture.example.com/zenwave-architecture.yml" to MANIFEST,
                "https://architecture.example.com/domains/commerce.yml" to COMMERCE_DOMAIN,
                "https://architecture.example.com/orders/openapi.yml" to "openapi: 3.1.0",
                "https://architecture.example.com/orders/README.md" to "# Orders",
            ),
        )
        // No platform default is involved: the caller supplies every reader.
        val loader = ZenWaveManifestLoader(documentLoaders = listOf(reader), archiveEntryLoader = null)

        val manifest = loader.load("https://architecture.example.com/zenwave-architecture.yml")
        val service = manifest.services.single()
        assertEquals("commerce/orders", service.serviceRef)

        assertEquals("openapi: 3.1.0", loader.loadArtifactText(manifest, service, service.artifacts.single()))
        assertEquals(mapOf("readme" to "# Orders"), loader.loadServiceDocs(manifest, service))

        assertEquals(reader.documents.keys, reader.requested.toSet(), "every document is read through the caller's reader")
    }

    @Test
    fun theFilesystemIsReportedAsUnavailableInABrowser() = runTest {
        if (ManifestPlatform.name != "browser") return@runTest
        // The default readers load; a file read is reported as unavailable rather than as a defect.
        val error = assertFailsWith<CapabilityUnavailableException> {
            ZenWaveManifestLoader().load("file:///workspace/zenwave-architecture.yml")
        }
        assertEquals(RefParserPlatform.FILESYSTEM, error.capability)
        assertEquals("browser", error.platform)
    }

    private class RecordingReader(val documents: Map<String, String>) : DocumentLoader {
        val requested = mutableListOf<String>()

        override fun canLoad(uri: String): Boolean = uri in documents

        override suspend fun load(uri: String): String {
            requested += uri
            return documents.getValue(uri)
        }
    }

    private companion object {
        val MANIFEST = """
            config:
              contentResolution: [workspace]
              sources:
                workspace:
                  basePathExpression: "${'$'}{service.id}"
            domains:
              commerce:
                ${'$'}ref: "domains/commerce.yml"
        """.trimIndent()

        val COMMERCE_DOMAIN = """
            services:
              orders:
                docs:
                  readme: README.md
                artifacts:
                  - { type: openapi, path: openapi.yml, version: 1.0.0 }
        """.trimIndent()
    }
}
