package io.zenwave360.manifest.graph

import io.zenwave360.manifest.ZenWaveManifestLoader
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArcadiaArchitectureGraphSmokeTest {
    @Test
    fun buildsLocalArcadiaArchitectureGraphWhenConfigured() = runTest {
        val configuredPath = System.getProperty("arcadia.architecture")?.takeIf(String::isNotBlank)
        val manifestUri = configuredPath?.let { Path.of(it).toUri().toString() }
            ?: requireNotNull(javaClass.getResource("/arcadia-editions/zenwave.yml")).toURI().toString()
        val loader = ZenWaveManifestLoader()
        val manifest = loader.load(manifestUri)

        val result = ArchitectureGraphBuilder(loader).build(manifest)
        val graph = result.graph
        val flow = assertNotNull(graph.nodes.singleOrNull {
            it.kind == ArchitectureNodeKind.ZFL_FLOW && it.label == "PlaceOrderFlow"
        })
        val declaredSystems = graph.edges(ArchitectureEdgeKind.DECLARES_DOMAIN)
            .filter { edge -> graph.node(edge.source)?.kind == ArchitectureNodeKind.ZFL_SYSTEM }
        assertEquals(if (configuredPath == null) 1 else 5, declaredSystems.size,
            result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" })
        assertTrue(graph.outgoing(flow.id).isNotEmpty())
        assertEquals("flows.PlaceOrderFlow", flow.source?.semanticPath)
        assertTrue(graph.edges(
            if (configuredPath == null) ArchitectureEdgeKind.RESOLVES_TO else ArchitectureEdgeKind.INVOKES,
        ).any { edge -> graph.node(edge.target)?.kind == ArchitectureNodeKind.ZDL_METHOD })
        assertTrue(graph.nodes.any { it.kind == ArchitectureNodeKind.CHANNEL })
        assertTrue(graph.nodes.any { it.kind == ArchitectureNodeKind.EVENT && it.label == "OrderPlaced" })

        if (configuredPath != null) {
            val authorizePayment = assertNotNull(graph.nodes.singleOrNull {
                it.kind == ArchitectureNodeKind.ZDL_METHOD &&
                    it.attributes["asyncapiApi"] == "PaymentsProcessingApi" &&
                    it.attributes["asyncapiChannel"] == "payment-authorized-event-v1"
            })
            assertTrue(graph.outgoing(authorizePayment.id).any { edge ->
                edge.kind == ArchitectureEdgeKind.CONSUMES &&
                    graph.node(edge.target)?.kind == ArchitectureNodeKind.CHANNEL &&
                    graph.node(edge.target)?.attributes?.get("channelKey") == "payment-authorized-event-v1"
            })
        } else {
            val event = graph.nodes.single { it.kind == ArchitectureNodeKind.EVENT && it.label == "OrderPlaced" }
            assertTrue(graph.incoming(event.id, ArchitectureEdgeKind.REPRESENTS).size >= 3)
            assertTrue(graph.incoming(event.id, ArchitectureEdgeKind.PUBLISHES).isNotEmpty())
        }
        assertFalse(result.diagnostics.any { it.code == "dangling-graph-edge" })
    }
}
