package io.zenwave360.manifest.mcp

import io.zenwave360.manifest.ZenWaveManifestLoader
import io.zenwave360.manifest.graph.ArchitectureGraphBuilder
import io.zenwave360.manifest.workspace.DefaultWorkspaceQueryApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceMcpToolsTest {
    @Test
    fun exposesExactlyTheSevenWorkspaceTools() = runTest {
        val adapter = adapter()
        assertEquals(
            setOf(
                "zenwave_workspace", "zenwave_resolve", "zenwave_search", "zenwave_browse",
                "zenwave_related", "zenwave_sources", "zenwave_diagnostics",
            ),
            adapter.tools.map { it.name }.toSet(),
        )
        assertTrue(adapter.tools.all { it.inputSchema["type"]?.jsonPrimitive?.content == "object" })
    }

    @Test
    fun dispatchesStructuredAliasResolutionAndValidatesArguments() = runTest {
        val adapter = adapter()
        val resolved = adapter.call("zenwave_resolve", buildJsonObject {
            put("id", "zw:sales/ordering/order-service")
        })

        assertFalse(resolved.isError)
        assertEquals(
            "zw:sales/ordering/orders",
            resolved.structuredContent.getValue("element").jsonObject.getValue("id").jsonPrimitive.content,
        )
        assertEquals(
            "zw:sales/ordering/order-service",
            resolved.structuredContent.getValue("redirectedFrom").jsonPrimitive.content,
        )

        val malformed = adapter.call("zenwave_resolve", buildJsonObject { put("id", "not-semantic") })
        assertFalse(malformed.isError)
        assertTrue(malformed.structuredContent.containsKey("candidates"))

        val invalid = adapter.call("zenwave_related", buildJsonObject {
            put("id", "zw:sales")
            put("depth", 21)
        })
        assertTrue(invalid.isError)
        assertEquals(
            "invalid-arguments",
            invalid.structuredContent.getValue("error").jsonObject.getValue("code").jsonPrimitive.content,
        )
    }

    private suspend fun adapter(): WorkspaceMcpTools {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse("file:///workspace/zenwave.yml", """
            domains:
              sales:
                subdomains:
                  ordering:
                    services:
                      orders:
                        aliases: [order-service]
        """.trimIndent())
        val graph = ArchitectureGraphBuilder(loader, emptyList()).build(manifest)
        return WorkspaceMcpTools(DefaultWorkspaceQueryApi(graph))
    }
}
