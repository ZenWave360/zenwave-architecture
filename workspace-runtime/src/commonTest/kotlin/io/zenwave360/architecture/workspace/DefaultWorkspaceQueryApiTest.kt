package io.zenwave360.architecture.workspace

import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import io.zenwave360.architecture.graph.ArchitectureGraphBuilder
import io.zenwave360.architecture.graph.ArchitectureConfidence
import io.zenwave360.architecture.graph.ArchitectureEdge
import io.zenwave360.architecture.graph.ArchitectureEdgeKind
import io.zenwave360.architecture.graph.ArchitectureEvidence
import io.zenwave360.architecture.graph.ArchitectureGraph
import io.zenwave360.architecture.graph.ArchitectureGraphResult
import io.zenwave360.architecture.graph.ArchitectureNode
import io.zenwave360.architecture.graph.ArchitectureNodeKind
import io.zenwave360.architecture.graph.ArchitectureSource
import io.zenwave360.architecture.graph.SemanticId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultWorkspaceQueryApiTest {
    @Test
    fun runtimeBuildsThePublicQueryFacadeWithoutExposingTheGraph() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse("file:///workspace/zenwave.yml", """
            domains:
              sales:
                services:
                  orders: {}
        """.trimIndent())

        val api: WorkspaceQueryApi = WorkspaceRuntime(loader).build(manifest)

        assertEquals(1, api.overview().serviceCount)
    }

    @Test
    fun exposesBoundedAdapterNeutralWorkspaceQueries() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            domains:
              sales:
                subdomains:
                  ordering:
                    services:
                      orders:
                        aliases: [order-service]
                      payments: {}
            """.trimIndent(),
        )
        val result = ArchitectureGraphBuilder(loader, emptyList()).build(manifest)
        val api: WorkspaceQueryApi = DefaultWorkspaceQueryApi(result)

        val overview = api.overview()
        assertEquals(1, overview.domains.size)
        assertEquals(2, overview.serviceCount)

        val resolution = api.resolve(SemanticId("zw:sales/ordering/order-service"))
            as WorkspaceResolutionResult.Resolved
        assertEquals("zw:sales/ordering/orders", resolution.element.id)
        assertEquals("zw:sales/ordering/order-service", resolution.redirectedFrom)

        val children = api.children(SemanticId("zw:sales/ordering"), page = Page(limit = 1))
        assertEquals(1, children.elements.size)
        assertTrue(children.truncated)
        assertEquals(1, children.nextOffset)

        val search = api.search("pay")
        assertEquals("zw:sales/ordering/payments", search.elements.single().id)
        assertFalse(search.truncated)

        val missing = api.resolve(SemanticId("zw:sales/ordering/paymnts")) as WorkspaceResolutionResult.NotFound
        assertEquals("zw:sales/ordering/payments", missing.candidates.first().id)

        val related = api.related(SemanticId("zw:sales"), direction = Direction.OUTGOING, depth = 1)
        assertTrue(related.nodes.any { it.id == "zw:sales/ordering" })
        assertTrue(related.truncated)
    }

    @Test
    fun resolvesDocumentLocationsThroughGraphProvenance() = runTest {
        val source = ArchitectureSource("file:///workspace/orders/model.zdl", semanticPath = "events.OrderPlaced")
        val representationId = "zw:sales/orders/zdl-event/OrderPlaced"
        val conceptId = "zw:sales/orders/event/OrderPlaced"
        val representation = ArchitectureNode(
            representationId, ArchitectureNodeKind.ZDL_EVENT, "OrderPlaced", source = source,
        )
        val concept = ArchitectureNode(
            conceptId, ArchitectureNodeKind.EVENT, "OrderPlaced",
            evidence = listOf(ArchitectureEvidence("zdl", source, ArchitectureConfidence.DECLARED)),
        )
        val represents = ArchitectureEdge(
            "represents", ArchitectureEdgeKind.REPRESENTS, representationId, conceptId,
        )
        val api = DefaultWorkspaceQueryApi(ArchitectureGraphResult(
            ArchitectureGraph(listOf(representation, concept), listOf(represents)),
        ))
        val result = api.at(WorkspaceSourceLocation(source.uri, source.semanticPath))

        assertEquals(conceptId, result.elements.single().id)
        assertFalse(result.truncated)
        assertTrue(api.at(WorkspaceSourceLocation("file:///workspace/unknown.zdl", "events.Missing")).elements.isEmpty())
    }

    @Test
    fun resolvesAServiceMoveAndEveryStoredDescendantId() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            domains:
              sales:
                subdomains:
                  fulfilment:
                    services:
                      fulfilment-service:
                        aliases: [sales/ordering/order-service]
                        artifacts:
                          - { artifactId: orders-api, type: openapi, path: api.yml, version: 1.0.0 }
            """.trimIndent(),
        )
        val api = DefaultWorkspaceQueryApi(ArchitectureGraphBuilder(loader, emptyList()).build(manifest))

        val service = api.resolve(SemanticId("zw:sales/ordering/order-service"))
            as WorkspaceResolutionResult.Resolved
        assertEquals("zw:sales/fulfilment/fulfilment-service", service.element.id)
        val artifact = api.resolve(SemanticId("zw:sales/ordering/order-service/artifact/orders-api"))
            as WorkspaceResolutionResult.Resolved
        assertEquals("zw:sales/fulfilment/fulfilment-service/artifact/orders-api", artifact.element.id)
    }

    @Test
    fun validatesFiltersAndScopesDiagnosticsBySemanticId() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            domains:
              sales:
                services:
                  orders: {}
                  payments:
                    aliases: [orders]
            """.trimIndent(),
        )
        val api = DefaultWorkspaceQueryApi(ArchitectureGraphBuilder(loader, emptyList()).build(manifest))

        val diagnostics = api.diagnostics(SemanticId("zw:sales/payments"), severity = "error")
        assertTrue(diagnostics.any { it.code == "semantic-alias-canonical-collision" })
        assertTrue(diagnostics.all { it.semanticId == "zw:sales/payments" })
        assertFailsWith<IllegalArgumentException> {
            api.related(SemanticId("zw:sales"), edgeKinds = listOf("does-not-exist"))
        }
        assertFailsWith<IllegalArgumentException> { api.diagnostics(severity = "fatal") }
        assertTrue(api.search("orders", scope = SemanticId("zw:missing")).elements.isEmpty())
        assertTrue(api.diagnostics(scope = SemanticId("zw:missing")).isEmpty())
    }
}
