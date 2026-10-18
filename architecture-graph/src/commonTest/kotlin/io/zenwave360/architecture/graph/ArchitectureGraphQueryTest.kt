package io.zenwave360.architecture.graph

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureGraphQueryTest {
    @Test
    fun resolvesCanonicalIdsAliasesAndAliasDescendants() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            domains:
              sales:
                aliases: [commerce]
                subdomains:
                  ordering:
                    services:
                      order-service:
                        aliases: [orders-service]
                        artifacts:
                          - { artifactId: orders-api, type: openapi, path: api.yml, version: 1.0.0 }
            """.trimIndent(),
        )
        val graph = ArchitectureGraphBuilder(loader, emptyList()).build(manifest).graph

        val canonical = SemanticId("zw:sales/ordering/order-service")
        val direct = graph.resolve(canonical) as SemanticResolutionResult.Resolved
        assertEquals(canonical.value, direct.node.id)
        assertEquals(null, direct.redirectedFrom)

        val alias = SemanticId("zw:sales/ordering/orders-service")
        val redirected = graph.resolve(alias) as SemanticResolutionResult.Resolved
        assertEquals(canonical.value, redirected.node.id)
        assertEquals(alias, redirected.redirectedFrom)

        val descendantAlias = SemanticId("zw:commerce/ordering/order-service/artifact/orders-api")
        val descendant = graph.resolve(descendantAlias) as SemanticResolutionResult.Resolved
        assertEquals("zw:sales/ordering/order-service/artifact/orders-api", descendant.node.id)
        assertEquals(descendantAlias, descendant.redirectedFrom)

        val versioned = graph.resolve(SemanticId("zw:sales/ordering/order-service/artifact/orders-api@1.0.0"))
            as SemanticResolutionResult.Resolved
        assertEquals("zw:sales/ordering/order-service/artifact/orders-api", versioned.node.id)
    }

    @Test
    fun exposesStableResourceIdsAndDecodesTypedBindings() {
        assertEquals(
            "zw:orders/checkout/channel/orders%2F%7Bid%7D",
            ArchitectureGraphIds.channel("orders/checkout", "async api#1", "orders/{id}"),
        )
        assertEquals(
            "zw:orders/checkout/api-operation/GET%20%2Forders%2F%7Bid%7D",
            ArchitectureGraphIds.apiOperation("orders/checkout", "open api#1", "GET /orders/{id}"),
        )
        val edge = ArchitectureEdge(
            id = "binding",
            kind = ArchitectureEdgeKind.BINDS_TO,
            source = "method",
            target = "channel",
            attributes = mapOf(
                ArchitectureBindingAttributes.ROLE to ArchitectureBindingValues.ROLE_INVOCATION,
                ArchitectureBindingAttributes.TRANSPORT to ArchitectureBindingValues.TRANSPORT_ASYNCAPI,
                ArchitectureBindingAttributes.MESSAGE_KIND to ArchitectureBindingValues.KIND_COMMAND,
                ArchitectureBindingAttributes.DIRECTION to ArchitectureBindingValues.DIRECTION_RECEIVE,
                ArchitectureBindingAttributes.CHANNEL_KEY to "orders/{id}",
            ),
        )
        val binding = ArchitectureOperationBinding.from(edge)!!
        assertEquals(ArchitectureBindingRole.INVOCATION, binding.role)
        assertEquals(ArchitectureBindingTransport.ASYNCAPI, binding.transport)
        assertEquals(ArchitectureBindingMessageKind.COMMAND, binding.messageKind)
        assertEquals(ArchitectureBindingDirection.RECEIVE, binding.direction)
        assertEquals("orders/{id}", binding.channelKey)
    }

    @Test
    fun traversesFlowToMethodAndChannelAndFindsOperationConsumers() = runTest {
        val resources = mapOf(
            "file:///workspace/architecture/place-order.zfl" to ZFL,
            "file:///workspace/orders/model.zdl" to ZDL,
            "file:///workspace/orders/asyncapi.yml" to PROVIDER,
            "file:///workspace/client/client.yml" to CLIENT,
        )
        val loader = ZenWaveManifestLoader(listOf(MapDocumentLoader(resources)), archiveEntryLoader = null)
        val manifest = loader.parse("file:///workspace/zenwave.yml", MANIFEST)

        val result = ArchitectureGraphBuilder(loader).build(manifest)
        val graph = result.graph
        val flow = graph.nodes.single { it.kind == ArchitectureNodeKind.ZFL_FLOW && it.label == "PlaceOrderFlow" }
        val channel = graph.nodes.single {
            it.kind == ArchitectureNodeKind.CHANNEL && it.attributes["channelKey"] == "order-placed-event-v1"
        }
        val consumerOperation = graph.nodes.single {
            it.kind == ArchitectureNodeKind.API_OPERATION && it.label == "onOrderPlaced"
        }
        val eventConcept = graph.nodes.single {
            it.kind == ArchitectureNodeKind.EVENT && it.label == "OrderPlaced"
        }
        val representations = graph.incoming(eventConcept.id, ArchitectureEdgeKind.REPRESENTS)
            .mapNotNull { graph.node(it.source) }

        val projection = graph.subgraphFrom(flow.id, maxDepth = 5)
        assertTrue(projection.nodes.any { it.kind == ArchitectureNodeKind.ZDL_METHOD && it.label == "placeOrder" })
        assertTrue(projection.nodes.any { it.id == channel.id })
        assertTrue(graph.consumersOf(channel.id).any { it.id == consumerOperation.id })
        assertTrue(graph.edges(ArchitectureEdgeKind.TRIGGERS).size >= 2)
        assertTrue(representations.any { it.kind == ArchitectureNodeKind.ZDL_EVENT })
        assertTrue(representations.any { it.kind == ArchitectureNodeKind.ZFL_EVENT })
        assertTrue(representations.any { it.kind == ArchitectureNodeKind.CHANNEL })
        assertTrue(eventConcept.evidence.map { it.kind }.containsAll(listOf("zdl", "zfl", "asyncapi")))
        assertTrue(graph.incoming(eventConcept.id, ArchitectureEdgeKind.PUBLISHES).any { edge ->
            graph.node(edge.source)?.kind == ArchitectureNodeKind.SERVICE
        })
        assertEquals("sales.orders", graph.owningService(channel.id)?.label)
        assertTrue(graph.edges.any {
            it.kind == ArchitectureEdgeKind.CONSUMES && it.source == consumerOperation.id && it.target == channel.id &&
                it.provenance == ArchitectureProvenanceKind.INFERRED && it.attributes["matchKind"] == "external-ref"
        })
        assertFalse(result.diagnostics.any { it.code == "dangling-graph-edge" })
    }

    private class MapDocumentLoader(private val documents: Map<String, String>) : DocumentLoader {
        override fun canLoad(uri: String): Boolean = uri in documents
        override suspend fun load(uri: String): String = requireNotNull(documents[uri])
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
                  - { artifactId: place-order-flow, type: zfl, path: place-order.zfl, version: 1.0.0 }
                services:
                  orders:
                    id: sales.orders
                    repository: orders
                    consumers: [client#client-contract]
                    artifacts:
                      - { artifactId: orders-model, type: zdl, path: model.zdl, version: 1.0.0 }
                      - { artifactId: orders-events, type: asyncapi, path: asyncapi.yml, version: 1.0.0 }
                  client:
                    id: sales.client
                    repository: client
                    artifacts:
                      - { artifactId: client-contract, type: asyncapi-client, path: client.yml, version: 1.0.0 }
        """.trimIndent()

        val ZDL = """
            apis {
                asyncapi provider OrdersApi "orders/asyncapi.yml"
            }
            @aggregate entity Order { id String required }
            service OrdersService for (Order) {
                @asyncapi({api: OrdersApi, channel: "order-placed-event-v1"})
                placeOrder(id) withEvents OrderPlaced
            }
            event OrderPlaced { id String required }
        """.trimIndent()

        val ZFL = """
            systems {
                @zdl("orders/model.zdl")
                Orders { service OrdersService }
            }
            flow PlaceOrderFlow {
                start StartOrder { id String }
                when StartOrder do placeOrder {
                    service Orders.OrdersService
                    emits OrderPlaced
                }
                end { completed: OrderPlaced }
            }
        """.trimIndent()

        val PROVIDER = """
            asyncapi: 3.0.0
            channels:
              order-placed-event-v1: { address: orders.placed }
            operations:
              publishOrderPlaced: { action: send, channel: { ${'$'}ref: '#/channels/order-placed-event-v1' } }
        """.trimIndent()

        val CLIENT = """
            asyncapi: 3.0.0
            channels:
              order-placed:
                ${'$'}ref: '../orders/asyncapi.yml#/channels/order-placed-event-v1'
            operations:
              onOrderPlaced: { action: receive, channel: { ${'$'}ref: '#/channels/order-placed' } }
        """.trimIndent()
    }
}
