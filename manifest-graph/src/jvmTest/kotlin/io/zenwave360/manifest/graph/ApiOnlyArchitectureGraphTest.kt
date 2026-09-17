package io.zenwave360.manifest.graph

import io.zenwave360.manifest.ZenWaveManifestLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiOnlyArchitectureGraphTest {
    @Test
    fun standardsArtifactsProduceTheConceptLayerWithoutZenWaveDsls() = runTest {
        val manifestUri = requireNotNull(javaClass.getResource("/api-only/zenwave.yml")).toURI().toString()
        val loader = ZenWaveManifestLoader()
        val result = ArchitectureGraphBuilder(loader).build(loader.load(manifestUri))
        val graph = result.graph

        assertFalse(graph.nodes.any { it.kind.name.startsWith("ZDL_") || it.kind.name.startsWith("ZFL_") })
        val dump = graph.nodes.joinToString("\n") { "${it.kind} ${it.label} ${it.id}" } + "\n" +
            result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
        val event = assertNotNull(graph.nodes.singleOrNull {
            it.kind == ArchitectureNodeKind.EVENT && it.label == "OrderPlaced"
        }, dump)
        val command = assertNotNull(graph.nodes.singleOrNull {
            it.kind == ArchitectureNodeKind.COMMAND && it.label == "placeOrder"
        }, dump)
        assertEquals(ArchitectureIdentityOrigin.DERIVED, event.identityOrigin)
        assertEquals(ArchitectureIdentityOrigin.DERIVED, command.identityOrigin)
        assertTrue(graph.incoming(event.id, ArchitectureEdgeKind.REPRESENTS).size >= 2)
        assertTrue(graph.incoming(command.id, ArchitectureEdgeKind.REPRESENTS).isNotEmpty())
        assertTrue(graph.incoming(event.id, ArchitectureEdgeKind.PUBLISHES).isNotEmpty())
        assertTrue(graph.incoming(event.id, ArchitectureEdgeKind.SUBSCRIBES).isNotEmpty())
        assertTrue(graph.incoming(command.id, ArchitectureEdgeKind.HANDLES).isNotEmpty())
        assertTrue(event.evidence.any { it.kind == "asyncapi" && it.source.uri.endsWith("/orders/asyncapi.yml") })
        assertTrue(command.evidence.any { it.kind == "openapi" && it.source.uri.endsWith("/orders/openapi.yml") })
        assertTrue(graph.nodes.any {
            it.kind == ArchitectureNodeKind.CHANNEL && it.source?.semanticPath == "$.channels.order-placed"
        })
        assertTrue(graph.nodes.any {
            it.kind == ArchitectureNodeKind.API_OPERATION && it.label == "placeOrder" &&
                it.source?.semanticPath == "$.paths['/orders'].post"
        })
        assertFalse(result.diagnostics.any { it.severity == ArchitectureDiagnosticSeverity.ERROR },
            result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" })
    }
}
