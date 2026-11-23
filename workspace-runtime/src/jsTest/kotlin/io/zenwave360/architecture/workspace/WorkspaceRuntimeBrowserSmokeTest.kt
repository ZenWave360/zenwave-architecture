package io.zenwave360.architecture.workspace

import io.zenwave360.architecture.manifest.ManifestPlatform
import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException
import io.zenwave360.jsonrefparser.platform.RefParserPlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Runs in headless Chromium (`jsBrowserTest`) and on Node (`jsNodeTest`). In the browser it is the
 * check that workspace-runtime loads and works without a Node runtime: a Node-only import anywhere in
 * the module graph fails the browser bundle before any test runs.
 */
class WorkspaceRuntimeBrowserSmokeTest {

    @Test
    fun buildsAWorkspaceQueryFacadeThroughCallerSuppliedReader() = runTest {
        val reader = RecordingReader(
            mapOf(
                "https://architecture.example.com/zenwave-architecture.yml" to MANIFEST,
                "https://architecture.example.com/domains/commerce.yml" to COMMERCE_DOMAIN,
            ),
        )
        // No platform default is involved: the caller supplies every reader.
        val loader = ZenWaveManifestLoader(documentLoaders = listOf(reader), archiveEntryLoader = null)

        val api = WorkspaceRuntime(loader).open("https://architecture.example.com/zenwave-architecture.yml")
        val overview = api.overview()
        val domain = overview.domains.single()

        assertEquals(1, overview.serviceCount)
        assertEquals("zw:commerce", domain.id)
        assertEquals("Commerce", domain.name)
        assertEquals(reader.documents.keys, reader.requested.toSet(), "every document is read through the caller's reader")
    }

    @Test
    fun filesystemIsReportedUnavailableInABrowser() = runTest {
        if (ManifestPlatform.name != "browser") return@runTest
        // The default readers load; a file read is reported as unavailable rather than as a defect.
        val error = assertFailsWith<CapabilityUnavailableException> {
            WorkspaceRuntime().open("file:///workspace/zenwave-architecture.yml")
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
              contentResolution: [git]
              sources:
                git:
                  provider: generic
                  server: https://architecture.example.com
                  contentUrlExpression: "${'$'}{server}/${'$'}{content.path}"
            domains:
              commerce:
                ${'$'}ref: "domains/commerce.yml"
        """.trimIndent()

        val COMMERCE_DOMAIN = """
            id: commerce
            name: Commerce
            services:
              orders:
                name: Orders
        """.trimIndent()
    }
}
