package io.zenwave360.manifest.graph

import io.zenwave360.jsonrefparser.io.DocumentLoader
import io.zenwave360.manifest.ZenWaveManifestLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArchitectureGraphBuilderTest {
    @Test
    fun conceptIdentitySurvivesArtifactFileMoves() = runTest {
        suspend fun eventId(path: String): String {
            val uri = "file:///workspace/orders/$path"
            val loader = ZenWaveManifestLoader(
                listOf(MapDocumentLoader(mapOf(uri to "event OrderPlaced { id String required }"))),
                archiveEntryLoader = null,
            )
            val manifest = loader.parse(
                "file:///workspace/zenwave.yml",
                """
                config:
                  sources:
                    workspace:
                      basePathExpression: "${'$'}{owner.repository}"
                domains:
                  sales:
                    subdomains:
                      ordering:
                        services:
                          order-service:
                            repository: orders
                            artifacts:
                              - { type: zdl, path: $path, version: 1.0.0 }
                """.trimIndent(),
            )
            return ArchitectureGraphBuilder(loader).build(manifest).graph.nodes.single {
                it.kind == ArchitectureNodeKind.EVENT && it.label == "OrderPlaced"
            }.id
        }

        assertEquals(
            "zw:sales/ordering/order-service/event/OrderPlaced",
            eventId("model/orders.zdl"),
        )
        assertEquals(eventId("model/orders.zdl"), eventId("contracts/domain.zdl"))
    }

    @Test
    fun indexesApiOnlyWorkspaceWithoutConsumptionAnalysis() = runTest {
        val resources = mapOf(
            "file:///workspace/orders/openapi.yml" to """
                openapi: 3.1.0
                paths:
                  /orders:
                    get:
                      operationId: listOrders
            """.trimIndent(),
            "file:///workspace/orders/asyncapi.yml" to """
                asyncapi: 3.0.0
                channels:
                  order-placed:
                    address: orders.placed
                operations:
                  publishOrderPlaced:
                    action: send
                    channel: { ${'$'}ref: '#/channels/order-placed' }
            """.trimIndent(),
        )
        val loader = ZenWaveManifestLoader(listOf(MapDocumentLoader(resources)), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            config:
              sources:
                workspace:
                  basePathExpression: "${'$'}{owner.repository}"
            domains:
              sales:
                services:
                  orders:
                    repository: orders
                    artifacts:
                      - { artifactId: orders-http, type: openapi, path: openapi.yml, version: 1.0.0 }
                      - { artifactId: orders-events, type: asyncapi, path: asyncapi.yml, version: 1.0.0 }
            """.trimIndent(),
        )

        val graph = ArchitectureGraphBuilder(loader, emptyList()).build(
            manifest,
            ArchitectureGraphBuildOptions(includeApiConsumptions = false),
        ).graph

        assertTrue(graph.nodes.any { it.kind == ArchitectureNodeKind.API_OPERATION && it.label == "listOrders" })
        assertTrue(graph.nodes.any { it.kind == ArchitectureNodeKind.CHANNEL && it.attributes["address"] == "orders.placed" })
    }

    @Test
    fun missingLocalArtifactIsAnErrorKeyedToTheArtifact() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse("file:///workspace/zenwave.yml", """
            config:
              sources:
                workspace:
                  basePathExpression: "${'$'}{owner.repository}"
            domains:
              sales:
                services:
                  orders:
                    repository: orders
                    artifacts:
                      - { artifactId: missing, type: zdl, path: missing.zdl, version: 1.0.0 }
        """.trimIndent())

        val result = ArchitectureGraphBuilder(loader).build(manifest)
        val artifact = result.graph.nodes.single { it.kind == ArchitectureNodeKind.ARTIFACT }
        val diagnostic = result.diagnostics.single { it.code == "artifact-load-failed" }

        assertEquals(ArchitectureDiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(artifact.id, diagnostic.semanticId)
    }

    @Test
    fun clampsRelationshipResolverConfidenceToItsDeclaredMaximum() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            domains:
              sales:
                services:
                  orders: {}
                  payments: {}
            """.trimIndent(),
        )
        val resolver = object : RelationshipResolver {
            override val maxConfidence = ArchitectureConfidence.INFERRED_HEURISTIC
            override suspend fun resolve(context: RelationshipResolverContext): ManifestGraphContribution {
                val services = context.graph.nodes.filter { it.kind == ArchitectureNodeKind.SERVICE }
                val capabilityId = ArchitectureGraphIds.concept(services[0].id, ArchitectureNodeKind.CAPABILITY, "Checkout")
                return ManifestGraphContribution(
                    nodes = listOf(ArchitectureNode(
                        capabilityId,
                        ArchitectureNodeKind.CAPABILITY,
                        "Checkout",
                        services[0].id,
                        evidence = listOf(ArchitectureEvidence(
                            "java", ArchitectureSource("file:///workspace/Checkout.java"), ArchitectureConfidence.DECLARED,
                        )),
                    )),
                    edges = listOf(ArchitectureEdge(
                    id = ArchitectureGraphIds.edge(ArchitectureEdgeKind.DEPENDS_ON, services[0].id, services[1].id),
                    kind = ArchitectureEdgeKind.DEPENDS_ON,
                    source = services[0].id,
                    target = services[1].id,
                    confidence = ArchitectureConfidence.DECLARED,
                )))
            }
        }

        val result = ArchitectureGraphBuilder(loader, emptyList(), listOf(resolver)).build(manifest)

        assertEquals(ArchitectureConfidence.INFERRED_HEURISTIC, result.graph.edges(ArchitectureEdgeKind.DEPENDS_ON).single().confidence)
        assertEquals(
            ArchitectureConfidence.INFERRED_HEURISTIC,
            result.graph.nodes.single { it.kind == ArchitectureNodeKind.CAPABILITY }.evidence.single().confidence,
        )
        assertTrue(result.diagnostics.any { it.code == "resolver-confidence-exceeded" })
    }

    @Test
    fun mergesIndependentEvidenceForTheSameRelationship() = runTest {
        val loader = ZenWaveManifestLoader(emptyList(), archiveEntryLoader = null)
        val manifest = loader.parse("file:///workspace/zenwave.yml", """
            domains:
              sales:
                services:
                  orders: {}
                  payments: {}
        """.trimIndent())
        fun resolver(confidence: ArchitectureConfidence, uri: String) = object : RelationshipResolver {
            override val maxConfidence = confidence
            override suspend fun resolve(context: RelationshipResolverContext): ManifestGraphContribution {
                val services = context.graph.nodes.filter { it.kind == ArchitectureNodeKind.SERVICE }.sortedBy { it.id }
                val edge = ArchitectureEdge(
                    id = ArchitectureGraphIds.edge(ArchitectureEdgeKind.DEPENDS_ON, services[0].id, services[1].id),
                    kind = ArchitectureEdgeKind.DEPENDS_ON,
                    source = services[0].id,
                    target = services[1].id,
                    confidence = confidence,
                    evidence = listOf(ArchitectureEvidence("java", ArchitectureSource(uri), confidence, "resolver evidence")),
                )
                return ManifestGraphContribution(edges = listOf(edge))
            }
        }

        val result = ArchitectureGraphBuilder(loader, emptyList(), listOf(
            resolver(ArchitectureConfidence.INFERRED_HEURISTIC, "file:///workspace/a.java"),
            resolver(ArchitectureConfidence.RESOLVED, "file:///workspace/b.java"),
        )).build(manifest)

        val edge = result.graph.edges(ArchitectureEdgeKind.DEPENDS_ON).single()
        assertEquals(ArchitectureConfidence.RESOLVED, edge.confidence)
        assertEquals(2, edge.evidence.size)
        assertFalse(result.diagnostics.any { it.code == "duplicate-graph-edge" })
    }

    @Test
    fun buildsManifestZdlZflAndConsumerGraph() = runTest {
        val resources = mapOf(
            "file:///workspace/orders/model.zdl" to ZDL,
            "file:///workspace/orders/openapi.yml" to "openapi: 3.1.0",
            "file:///workspace/client/openapi.yml" to "openapi: 3.1.0",
            "file:///workspace/architecture/checkout.zfl" to ZFL,
        )
        val loader = ZenWaveManifestLoader(listOf(MapDocumentLoader(resources)), archiveEntryLoader = null)
        val manifest = loader.parse("file:///workspace/zenwave.yml", MANIFEST)

        val domainArtifact = manifest.domains.single().artifacts.single()
        val loaded = loader.loadArtifactResult(manifest, manifest.domains.single(), domainArtifact)
        assertTrue(loaded.successful)
        assertEquals("file:///workspace/architecture/checkout.zfl", loaded.resource?.referenceUri())

        val result = ArchitectureGraphBuilder(loader).build(manifest)
        val graph = result.graph

        val graphSummary = graph.nodes.joinToString { "${it.kind}:${it.label}" }
        val diagnosticSummary = result.diagnostics.joinToString { "${it.code}:${it.message}" }
        val zdlMethod = assertNotNull(
            graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZDL_METHOD && it.label == "placeOrder" },
            "Missing ZDL method. Nodes: $graphSummary. Diagnostics: $diagnosticSummary",
        )
        val zdlEvent = assertNotNull(
            graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZDL_EVENT && it.label == "OrderPlaced" },
            "Missing ZDL event. Nodes: $graphSummary. Diagnostics: $diagnosticSummary",
        )
        val zflSystem = assertNotNull(
            graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZFL_SYSTEM && it.label == "Orders" },
            "Missing ZFL system. Nodes: $graphSummary. Diagnostics: $diagnosticSummary",
        )
        val zflOperation = assertNotNull(
            graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.ZFL_OPERATION && it.label == "placeOrder" },
            "Missing ZFL operation. Nodes: $graphSummary. Diagnostics: $diagnosticSummary",
        )
        val zdlArtifact = graph.nodes.single { it.kind == ArchitectureNodeKind.ARTIFACT && it.attributes["type"] == "zdl" }
        val providerOpenApi = graph.nodes.single { it.kind == ArchitectureNodeKind.ARTIFACT && it.attributes["artifactId"] == "orders-api" }
        val consumerOpenApi = graph.nodes.single { it.kind == ArchitectureNodeKind.ARTIFACT && it.attributes["artifactId"] == "client-api" }

        assertTrue(graph.edges.any { it.kind == ArchitectureEdgeKind.EMITS && it.source == zdlMethod.id && it.target == zdlEvent.id })
        assertTrue(graph.edges.any { it.kind == ArchitectureEdgeKind.DECLARES_DOMAIN && it.source == zflSystem.id && it.target == zdlArtifact.id })
        assertTrue(graph.edges.any { it.kind == ArchitectureEdgeKind.RESOLVES_TO && it.source == zflOperation.id && it.target == zdlMethod.id })
        assertTrue(graph.operationOccurrences(zflOperation.id).isNotEmpty())
        assertTrue(graph.edges.any {
            it.kind == ArchitectureEdgeKind.CONSUMES &&
                it.source == consumerOpenApi.id && it.target == providerOpenApi.id &&
                it.provenance == ArchitectureProvenanceKind.DECLARED_CONSUMER
        })
        assertFalse(result.diagnostics.any { it.code == "dangling-graph-edge" })
    }

    @Test
    fun customAnalyzerExtendsArtifactNamespace() = runTest {
        val uri = "file:///workspace/custom/contract.txt"
        val loader = ZenWaveManifestLoader(listOf(MapDocumentLoader(mapOf(uri to "custom"))), archiveEntryLoader = null)
        val manifest = loader.parse(
            "file:///workspace/zenwave.yml",
            """
            config:
              sources:
                workspace:
                  basePathExpression: "${'$'}{owner.repository}"
            domains:
              sample:
                services:
                  app:
                    id: sample.app
                    repository: custom
                    artifacts:
                      - { artifactId: custom-contract, type: custom, path: contract.txt, version: 1.0.0 }
            """.trimIndent(),
        )
        val analyzer = object : ManifestGraphArtifactAnalyzer {
            override fun supports(artifact: io.zenwave360.manifest.ResolvedManifestArtifact): Boolean =
                artifact.artifact.type == "custom"

            override suspend fun analyze(context: ManifestGraphArtifactContext): ManifestGraphContribution {
                val artifactId = ArchitectureGraphIds.artifact(context.artifact.ownerRef, context.artifact.artifactId)
                val nodeId = ArchitectureGraphIds.semantic(artifactId, ArchitectureNodeKind.MESSAGE, "Example")
                return ManifestGraphContribution(
                    nodes = listOf(ArchitectureNode(nodeId, ArchitectureNodeKind.MESSAGE, context.content, artifactId)),
                    edges = listOf(
                        ArchitectureEdge(
                            ArchitectureGraphIds.edge(ArchitectureEdgeKind.DEFINES, artifactId, nodeId),
                            ArchitectureEdgeKind.DEFINES,
                            artifactId,
                            nodeId,
                            provenance = ArchitectureProvenanceKind.ARTIFACT,
                        ),
                    ),
                )
            }
        }

        val result = ArchitectureGraphBuilder(loader, listOf(analyzer)).build(manifest)

        assertNotNull(result.graph.nodes.singleOrNull { it.kind == ArchitectureNodeKind.MESSAGE && it.label == "custom" })
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun identifiersAreStableAndEscapeSeparators() {
        assertEquals(
            ArchitectureGraphIds.artifact("sales/orders", "orders api"),
            ArchitectureGraphIds.artifact("sales/orders", "orders api"),
        )
        assertFalse(ArchitectureGraphIds.artifact("sales/orders", "orders api").contains(" "))
    }

    private class MapDocumentLoader(private val content: Map<String, String>) : DocumentLoader {
        override fun canLoad(uri: String): Boolean = uri in content
        override suspend fun load(uri: String): String = requireNotNull(content[uri]) { "No test resource for $uri" }
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
                    consumers: [client#client-api]
                    artifacts:
                      - { artifactId: orders-model, type: zdl, path: model.zdl, version: 1.0.0 }
                      - { artifactId: orders-api, type: openapi, path: openapi.yml, version: 1.0.0 }
                  client:
                    id: sales.client
                    repository: client
                    artifacts:
                      - { artifactId: client-api, type: openapi, path: openapi.yml, version: 1.0.0 }
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
