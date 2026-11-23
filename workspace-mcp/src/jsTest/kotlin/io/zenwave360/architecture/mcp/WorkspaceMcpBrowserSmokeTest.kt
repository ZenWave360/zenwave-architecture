package io.zenwave360.architecture.mcp

import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import io.zenwave360.architecture.workspace.WorkspaceRuntime
import io.zenwave360.jsonrefparser.io.DocumentLoader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs in headless Chromium (`jsBrowserTest`) and on Node (`jsNodeTest`). In the browser it is the
 * check that workspace-mcp, and the runtime it adapts, load and answer without a Node runtime.
 */
class WorkspaceMcpBrowserSmokeTest {

    @Test
    fun exposesAndCallsAWorkspaceToolWithoutNode() = runTest {
        val reader = RecordingReader(
            mapOf(
                "https://architecture.example.com/zenwave-architecture.yml" to MANIFEST,
                "https://architecture.example.com/domains/commerce.yml" to COMMERCE_DOMAIN,
            ),
        )
        val loader = ZenWaveManifestLoader(documentLoaders = listOf(reader), archiveEntryLoader = null)
        val api = WorkspaceRuntime(loader).open("https://architecture.example.com/zenwave-architecture.yml")
        val tools = WorkspaceMcpTools(api)

        assertEquals(
            setOf(
                "zenwave_workspace", "zenwave_resolve", "zenwave_search", "zenwave_browse",
                "zenwave_related", "zenwave_sources", "zenwave_diagnostics",
            ),
            tools.tools.map { it.name }.toSet(),
        )

        val result = tools.call("zenwave_workspace")
        assertFalse(result.isError)
        val content = result.structuredContent
        assertTrue(content.getValue("domains") is JsonArray)
        assertEquals(1, content.getValue("serviceCount").jsonPrimitive.int)
        assertTrue(content.getValue("elementCount").jsonPrimitive.int > 0)
        assertTrue(content.getValue("diagnosticCount").jsonPrimitive.int >= 0)
        assertEquals(false, content.getValue("truncated").jsonPrimitive.boolean)
        assertEquals(reader.documents.keys, reader.requested.toSet(), "every document is read through the caller's reader")
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
