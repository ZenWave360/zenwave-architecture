package io.zenwave360.architecture.graph

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs in headless Chromium (`jsBrowserTest`) and on Node (`jsNodeTest`). In the browser it is the
 * check that architecture-graph (with architecture-manifest, dsl-kotlin and json-schema-ref-parser-kmp) loads and
 * builds a graph without a Node runtime, reading every document through the caller's reader.
 */
class ManifestGraphBrowserSmokeTest {

    @Test
    fun buildsAnArchitectureGraphThroughACallerSuppliedReader() = runTest {
        val reader = RecordingReader(
            mapOf(
                "https://architecture.example.com/zenwave-architecture.yml" to MANIFEST,
                "https://architecture.example.com/orders/model.zdl" to ZDL,
                "https://architecture.example.com/architecture/checkout.zfl" to ZFL,
            ),
        )
        // No platform default is involved: the caller supplies every reader.
        val loader = ZenWaveManifestLoader(documentLoaders = listOf(reader), archiveEntryLoader = null)
        val manifest = loader.load("https://architecture.example.com/zenwave-architecture.yml")

        val result = ArchitectureGraphBuilder(loader).build(manifest)
        val graph = result.graph
        val summary = graph.nodes.joinToString { "${it.kind}:${it.label}" } +
            " / " + result.diagnostics.joinToString { "${it.code}:${it.message}" }

        val method = graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZDL_METHOD && it.label == "placeOrder" }
        val event = graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZDL_EVENT && it.label == "OrderPlaced" }
        val operation = graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZFL_OPERATION && it.label == "placeOrder" }
        assertTrue(method != null && event != null && operation != null, summary)
        assertTrue(graph.edges.any { it.kind == ArchitectureEdgeKind.EMITS && it.source == method.id && it.target == event.id }, summary)
        assertTrue(graph.edges.any { it.kind == ArchitectureEdgeKind.RESOLVES_TO && it.source == operation.id && it.target == method.id }, summary)
        assertFalse(result.diagnostics.any { it.code == "dangling-graph-edge" }, summary)

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
              sources:
                workspace:
                  basePathExpression: "${'$'}{owner.repository}"
            domains:
              sales:
                id: sales
                repository: architecture
                artifacts:
                  - { artifactId: checkout-flow, type: zfl, path: checkout.zfl, version: 1.0.0 }
                services:
                  orders:
                    id: sales.orders
                    repository: orders
                    artifacts:
                      - { artifactId: orders-model, type: zdl, path: model.zdl, version: 1.0.0 }
        """.trimIndent()

        val ZDL = """
            input PlaceOrderInput {
                orderId String required
            }

            event OrderPlaced {
                orderId String required
            }

            @aggregate
            entity Order {
                orderId String required
            }

            service OrdersService for (Order) {
                placeOrder(PlaceOrderInput) withEvents OrderPlaced
            }
        """.trimIndent()

        val ZFL = """
            systems {
                @zdl("orders/model.zdl")
                Orders {
                    service OrdersService {
                        commands: placeOrder
                    }
                }
            }

            flow Checkout {
                start StartCheckout {
                    orderId String
                }

                when StartCheckout do placeOrder {
                    service Orders.OrdersService
                    emits OrderPlaced
                }

                end {
                    completed: OrderPlaced
                }
            }
        """.trimIndent()
    }
}
