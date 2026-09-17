package io.zenwave360.manifest.graph

import io.zenwave360.manifest.ManifestConsumptionRules
import io.zenwave360.manifest.ManifestLoadOptions
import io.zenwave360.manifest.ZenWaveManifest
import io.zenwave360.manifest.ZenWaveManifestLoader
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

enum class ArchitectureNodeKind {
    WORKSPACE, DOMAIN, SUBDOMAIN, SERVICE,
    AGGREGATE, ENTITY, COMMAND, EVENT, POLICY, FLOW, STEP, CAPABILITY,
    ARTIFACT,
    ZDL_API, ZDL_SERVICE, ZDL_METHOD, ZDL_EVENT,
    ZFL_SYSTEM, ZFL_FLOW, ZFL_OPERATION, ZFL_STEP, ZFL_EVENT, ZFL_OUTCOME,
    ZDL_ENTITY, API_OPERATION, CHANNEL, MESSAGE,
}

enum class ArchitectureEdgeKind {
    OWNS, CONTAINS, REFERENCES, REPRESENTS,
    PUBLISHES, SUBSCRIBES, CALLS, IMPLEMENTS, IMPLEMENTED_BY, HANDLES,
    USES_SCHEMA, TRANSPORTED_BY, DEPENDS_ON,
    DECLARES_ARTIFACT, DECLARES_DOMAIN, DEFINES,
    INVOKES, EMITS, CONSUMES, REFERENCES_API, TRIGGERS,
    OCCURRENCE_OF, RESOLVES_TO, BINDS_TO, RESPONDS, COMPENSATES, RESULTS_IN,
}

object ArchitectureBindingAttributes {
    const val ROLE = "role"
    const val TRANSPORT = "transport"
    const val MESSAGE_KIND = "messageKind"
    const val DIRECTION = "direction"
    const val OPERATION_ID = "operationId"
    const val METHOD = "method"
    const val PATH = "path"
    const val CHANNEL_KEY = "channelKey"
    const val ADDRESS = "address"
}

object ArchitectureBindingValues {
    const val ROLE_INVOCATION = "invocation"
    const val ROLE_TRIGGER = "trigger"
    const val ROLE_EMISSION = "emission"
    const val ROLE_RESPONSE = "response"
    const val TRANSPORT_OPENAPI = "openapi"
    const val TRANSPORT_ASYNCAPI = "asyncapi"
    const val KIND_COMMAND = "command"
    const val KIND_QUERY = "query"
    const val KIND_EVENT = "event"
    const val DIRECTION_SEND = "send"
    const val DIRECTION_RECEIVE = "receive"
}

enum class ArchitectureBindingRole(val wireValue: String) {
    INVOCATION(ArchitectureBindingValues.ROLE_INVOCATION),
    TRIGGER(ArchitectureBindingValues.ROLE_TRIGGER),
    EMISSION(ArchitectureBindingValues.ROLE_EMISSION),
    RESPONSE(ArchitectureBindingValues.ROLE_RESPONSE),
}

enum class ArchitectureBindingTransport(val wireValue: String) {
    OPENAPI(ArchitectureBindingValues.TRANSPORT_OPENAPI),
    ASYNCAPI(ArchitectureBindingValues.TRANSPORT_ASYNCAPI),
}

enum class ArchitectureBindingMessageKind(val wireValue: String) {
    COMMAND(ArchitectureBindingValues.KIND_COMMAND),
    QUERY(ArchitectureBindingValues.KIND_QUERY),
    EVENT(ArchitectureBindingValues.KIND_EVENT),
}

enum class ArchitectureBindingDirection(val wireValue: String) {
    SEND(ArchitectureBindingValues.DIRECTION_SEND),
    RECEIVE(ArchitectureBindingValues.DIRECTION_RECEIVE),
}

data class ArchitectureOperationBinding(
    val edge: ArchitectureEdge,
    val role: ArchitectureBindingRole,
    val transport: ArchitectureBindingTransport,
    val messageKind: ArchitectureBindingMessageKind,
    val direction: ArchitectureBindingDirection,
    val operationId: String? = null,
    val method: String? = null,
    val path: String? = null,
    val channelKey: String? = null,
    val address: String? = null,
) {
    companion object {
        @JvmStatic
        fun from(edge: ArchitectureEdge): ArchitectureOperationBinding? {
            if (edge.kind != ArchitectureEdgeKind.BINDS_TO) return null
            val attributes = edge.attributes
            val role = ArchitectureBindingRole.entries.find {
                it.wireValue == attributes[ArchitectureBindingAttributes.ROLE]
            } ?: return null
            val transport = ArchitectureBindingTransport.entries.find {
                it.wireValue == attributes[ArchitectureBindingAttributes.TRANSPORT]
            } ?: return null
            val messageKind = ArchitectureBindingMessageKind.entries.find {
                it.wireValue == attributes[ArchitectureBindingAttributes.MESSAGE_KIND]
            } ?: return null
            val direction = ArchitectureBindingDirection.entries.find {
                it.wireValue == attributes[ArchitectureBindingAttributes.DIRECTION]
            } ?: return null
            return ArchitectureOperationBinding(
                edge, role, transport, messageKind, direction,
                attributes[ArchitectureBindingAttributes.OPERATION_ID],
                attributes[ArchitectureBindingAttributes.METHOD],
                attributes[ArchitectureBindingAttributes.PATH],
                attributes[ArchitectureBindingAttributes.CHANNEL_KEY],
                attributes[ArchitectureBindingAttributes.ADDRESS],
            )
        }
    }
}

enum class ArchitectureProvenanceKind { MANIFEST, ARTIFACT, DECLARED_CONSUMER, INFERRED }

enum class ArchitectureConfidence { DECLARED, RESOLVED, INFERRED_EXACT, INFERRED_HEURISTIC }

fun ArchitectureProvenanceKind.defaultConfidence(): ArchitectureConfidence = when (this) {
    ArchitectureProvenanceKind.MANIFEST,
    ArchitectureProvenanceKind.ARTIFACT,
    ArchitectureProvenanceKind.DECLARED_CONSUMER -> ArchitectureConfidence.DECLARED
    ArchitectureProvenanceKind.INFERRED -> ArchitectureConfidence.INFERRED_EXACT
}

enum class ArchitectureDiagnosticSeverity { INFO, WARNING, ERROR }

enum class ArchitectureTraversalDirection { OUTGOING, INCOMING, BOTH }

enum class ArchitectureIdentityOrigin { EXPLICIT, DERIVED }

data class ArchitectureSource(
    val uri: String,
    val artifactOwnerRef: String? = null,
    val artifactId: String? = null,
    val semanticPath: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

data class ArchitectureEvidence(
    val kind: String,
    val source: ArchitectureSource,
    val confidence: ArchitectureConfidence,
    val explanation: String? = null,
)

data class ArchitectureNode(
    val id: String,
    val kind: ArchitectureNodeKind,
    val label: String,
    val ownerId: String? = null,
    val description: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val source: ArchitectureSource? = null,
    val identityOrigin: ArchitectureIdentityOrigin = ArchitectureIdentityOrigin.DERIVED,
    val evidence: List<ArchitectureEvidence> = source?.let {
        listOf(ArchitectureEvidence(evidenceKind(kind, attributes), it, ArchitectureConfidence.DECLARED))
    }.orEmpty(),
)

data class ArchitectureEdge(
    val id: String,
    val kind: ArchitectureEdgeKind,
    val source: String,
    val target: String,
    val label: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val provenance: ArchitectureProvenanceKind = ArchitectureProvenanceKind.INFERRED,
    val sourceInfo: ArchitectureSource? = null,
    val confidence: ArchitectureConfidence = provenance.defaultConfidence(),
    val evidence: List<ArchitectureEvidence> = sourceInfo?.let {
        listOf(ArchitectureEvidence(provenance.name.lowercase().replace('_', '-'), it, confidence))
    }.orEmpty(),
)

data class ArchitectureDiagnostic(
    val message: String,
    val severity: ArchitectureDiagnosticSeverity,
    val code: String,
    val source: ArchitectureSource? = null,
    val semanticId: String? = null,
)

private fun evidenceKind(kind: ArchitectureNodeKind, attributes: Map<String, String>): String = when (kind) {
    ArchitectureNodeKind.ZDL_API, ArchitectureNodeKind.ZDL_SERVICE, ArchitectureNodeKind.ZDL_METHOD,
    ArchitectureNodeKind.ZDL_EVENT, ArchitectureNodeKind.ZDL_ENTITY -> "zdl"
    ArchitectureNodeKind.ZFL_SYSTEM, ArchitectureNodeKind.ZFL_FLOW, ArchitectureNodeKind.ZFL_OPERATION,
    ArchitectureNodeKind.ZFL_STEP, ArchitectureNodeKind.ZFL_EVENT, ArchitectureNodeKind.ZFL_OUTCOME -> "zfl"
    ArchitectureNodeKind.CHANNEL, ArchitectureNodeKind.MESSAGE -> "asyncapi"
    ArchitectureNodeKind.API_OPERATION -> if (attributes.containsKey("method")) "openapi" else "asyncapi"
    ArchitectureNodeKind.ARTIFACT -> attributes["type"] ?: "manifest"
    else -> "manifest"
}

data class ArchitectureTraversalResult(
    val graph: ArchitectureGraph,
    val truncated: Boolean,
)

data class ArchitectureGraphResult(
    val graph: ArchitectureGraph,
    val diagnostics: List<ArchitectureDiagnostic> = emptyList(),
)

data class ArchitectureGraph(
    val nodes: List<ArchitectureNode>,
    val edges: List<ArchitectureEdge>,
    /** Historical semantic-ID prefixes mapped to their current canonical prefixes. */
    val aliases: Map<String, String> = emptyMap(),
) {
    val nodesById: Map<String, ArchitectureNode> = nodes.associateBy { it.id }
    private val outgoingById: Map<String, List<ArchitectureEdge>> = edges.groupBy { it.source }
    private val incomingById: Map<String, List<ArchitectureEdge>> = edges.groupBy { it.target }
    private val edgesByKind: Map<ArchitectureEdgeKind, List<ArchitectureEdge>> = edges.groupBy { it.kind }

    fun node(id: String): ArchitectureNode? = nodesById[id]

    /** Resolves canonical IDs and aliases without falling back to fuzzy matching. */
    fun resolve(id: SemanticId): SemanticResolutionResult {
        val lookupValue = id.value.removeVersionCoordinate()
        nodesById[lookupValue]?.let { return SemanticResolutionResult.Resolved(it) }
        val redirect = aliases.entries
            .filter { (alias, _) -> lookupValue == alias || lookupValue.startsWith("$alias/") }
            .maxByOrNull { it.key.length }
        if (redirect != null) {
            val canonical = redirect.value + lookupValue.removePrefix(redirect.key)
            nodesById[canonical]?.let {
                return SemanticResolutionResult.Resolved(it, redirectedFrom = id)
            }
        }
        return SemanticResolutionResult.NotFound(closestCandidates(id.value))
    }

    fun outgoing(id: String, kind: ArchitectureEdgeKind? = null): List<ArchitectureEdge> =
        outgoingById[id].orEmpty().filter { kind == null || it.kind == kind }

    fun incoming(id: String, kind: ArchitectureEdgeKind? = null): List<ArchitectureEdge> =
        incomingById[id].orEmpty().filter { kind == null || it.kind == kind }

    fun edges(kind: ArchitectureEdgeKind): List<ArchitectureEdge> = edgesByKind[kind].orEmpty()

    @JvmOverloads
    fun related(
        rootId: String,
        direction: ArchitectureTraversalDirection = ArchitectureTraversalDirection.BOTH,
        kinds: Set<ArchitectureEdgeKind> = emptySet(),
        confidence: Set<ArchitectureConfidence> = emptySet(),
        origins: Set<ArchitectureIdentityOrigin> = emptySet(),
        maxDepth: Int = 1,
        maxNodes: Int = 100,
    ): ArchitectureTraversalResult {
        val root = nodesById[rootId]
            ?: return ArchitectureTraversalResult(ArchitectureGraph(emptyList(), emptyList(), aliases), false)
        val depthLimit = maxDepth.coerceAtLeast(0)
        val nodeLimit = maxNodes.coerceAtLeast(1)
        val visited = linkedSetOf(root.id)
        val selectedEdges = linkedMapOf<String, ArchitectureEdge>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue += root.id to 0
        var truncated = false

        fun adjacentEdges(nodeId: String): List<ArchitectureEdge> = when (direction) {
            ArchitectureTraversalDirection.OUTGOING -> outgoingById[nodeId].orEmpty()
            ArchitectureTraversalDirection.INCOMING -> incomingById[nodeId].orEmpty()
            ArchitectureTraversalDirection.BOTH -> outgoingById[nodeId].orEmpty() + incomingById[nodeId].orEmpty()
        }.filter { edge ->
            (kinds.isEmpty() || edge.kind in kinds) &&
                (confidence.isEmpty() || edge.confidence in confidence)
        }.distinctBy { it.id }

        while (queue.isNotEmpty()) {
            val (nodeId, depth) = queue.removeFirst()
            val candidates = adjacentEdges(nodeId)
            if (depth >= depthLimit) {
                if (candidates.any { edge ->
                        val adjacent = if (edge.source == nodeId) edge.target else edge.source
                        adjacent in nodesById && adjacent !in visited &&
                            (origins.isEmpty() || nodesById.getValue(adjacent).identityOrigin in origins)
                    }) truncated = true
                continue
            }
            candidates.forEach { edge ->
                val adjacent = if (edge.source == nodeId) edge.target else edge.source
                val adjacentNode = nodesById[adjacent] ?: return@forEach
                if (origins.isNotEmpty() && adjacentNode.identityOrigin !in origins) return@forEach
                if (adjacent !in visited && visited.size >= nodeLimit) {
                    truncated = true
                    return@forEach
                }
                selectedEdges[edge.id] = edge
                if (visited.add(adjacent)) queue += adjacent to depth + 1
            }
        }
        return ArchitectureTraversalResult(
            ArchitectureGraph(visited.mapNotNull(nodesById::get), selectedEdges.values.toList(), aliases),
            truncated,
        )
    }

    fun resolvedMethod(zflOperationId: String): ArchitectureNode? =
        outgoing(zflOperationId, ArchitectureEdgeKind.RESOLVES_TO)
            .mapNotNull { nodesById[it.target] }
            .singleOrNull { it.kind == ArchitectureNodeKind.ZDL_METHOD }

    @JvmOverloads
    fun operationBindings(methodNodeId: String, role: String? = null): List<ArchitectureEdge> {
        val sources = buildSet {
            add(methodNodeId)
            outgoing(methodNodeId, ArchitectureEdgeKind.EMITS).forEach { add(it.target) }
        }
        return sources.flatMap { outgoing(it, ArchitectureEdgeKind.BINDS_TO) }
            .filter { edge ->
                val binding = ArchitectureOperationBinding.from(edge) ?: return@filter false
                role == null || binding.role.wireValue == role
            }
            .distinctBy { it.id }
    }

    fun operationOccurrences(zflOperationId: String): List<ArchitectureNode> =
        incoming(zflOperationId, ArchitectureEdgeKind.OCCURRENCE_OF)
            .mapNotNull { nodesById[it.source] }
            .sortedBy { it.attributes["occurrenceIndex"]?.toIntOrNull() ?: Int.MAX_VALUE }

    fun flowOutcomes(flowId: String): List<ArchitectureNode> =
        outgoing(flowId, ArchitectureEdgeKind.CONTAINS)
            .mapNotNull { nodesById[it.target] }
            .filter { it.kind == ArchitectureNodeKind.ZFL_OUTCOME }

    /** Finds the manifest service owning a semantic or artifact node, if one exists. */
    fun owningService(nodeId: String): ArchitectureNode? {
        var current = nodesById[nodeId]
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current.id)) {
            if (current.kind == ArchitectureNodeKind.SERVICE) return current
            current = current.ownerId?.let(nodesById::get)
        }
        return null
    }

    /** Direct nodes with a typed CONSUMES edge targeting [nodeId]. */
    fun consumersOf(nodeId: String): List<ArchitectureNode> = incoming(nodeId, ArchitectureEdgeKind.CONSUMES)
        .mapNotNull { nodesById[it.source] }
        .distinctBy { it.id }

    /**
     * Breadth-first projection used by LSP cross references and MCP graph tools.
     */
    @JvmOverloads
    fun subgraphFrom(
        rootId: String,
        direction: ArchitectureTraversalDirection = ArchitectureTraversalDirection.OUTGOING,
        kinds: Set<ArchitectureEdgeKind> = emptySet(),
        maxDepth: Int = Int.MAX_VALUE,
    ): ArchitectureGraph {
        return related(rootId, direction, kinds, maxDepth = maxDepth, maxNodes = Int.MAX_VALUE).graph
    }

    private fun closestCandidates(value: String): List<ArchitectureNode> {
        val query = value.substringAfterLast('/').substringAfterLast(':').lowercase()
        if (query.isBlank()) return emptyList()
        val maximumDistance = maxOf(2, query.length / 3)
        return nodes.asSequence()
            .map { node -> node to minOf(editDistance(query, node.label.lowercase()), editDistance(query, node.id.substringAfterLast('/').lowercase())) }
            .filter { (_, distance) -> distance <= maximumDistance }
            .sortedWith(compareBy<Pair<ArchitectureNode, Int>>({ it.second }, { it.first.id }))
            .take(5)
            .map { it.first }
            .toList()
    }

    companion object {
        /** Canonical asynchronous entry point for Kotlin/JVM and Kotlin/JS callers. */
        @JvmStatic
        @JvmOverloads
        suspend fun build(
            manifest: ZenWaveManifest,
            loader: ZenWaveManifestLoader = ZenWaveManifestLoader(),
            options: ArchitectureGraphBuildOptions = ArchitectureGraphBuildOptions(),
        ): ArchitectureGraphResult = ArchitectureGraphBuilder(loader).build(manifest, options)
    }
}

data class ArchitectureGraphBuildOptions(
    val loadOptions: ManifestLoadOptions = ManifestLoadOptions(),
    /** Empty means every artifact type supported by a configured analyzer. */
    val artifactTypes: Set<String> = emptySet(),
    val includeDeclaredConsumers: Boolean = true,
    val includeApiConsumptions: Boolean = true,
    val consumptionRules: Map<String, List<String>> = ManifestConsumptionRules.DEFAULT,
    /** Graphs default to declared, verified evidence rather than global legacy address inference. */
    val legacyAddressMatching: Boolean = false,
)

/** Stable, URI-safe identifiers shared by built-in and third-party graph analyzers. */
object ArchitectureGraphIds {
    @JvmStatic
    fun domain(domainKey: String): String = "zw:${canonicalStructuralSegment(domainKey)}"

    @JvmStatic
    fun subdomain(domainKey: String, subdomainKey: String): String =
        "${domain(domainKey)}/${canonicalStructuralSegment(subdomainKey)}"

    @JvmStatic
    fun service(serviceRef: String): String =
        "zw:" + serviceRef.split('/').joinToString("/") { canonicalStructuralSegment(it) }

    @JvmStatic
    fun artifact(ownerRef: String, artifactId: String): String =
        "${service(ownerRef)}/artifact/${segment(artifactId)}"

    @JvmStatic
    fun concept(ownerId: String, kind: ArchitectureNodeKind, name: String): String {
        require(kind in conceptKinds) { "'$kind' is not a concept kind" }
        return "$ownerId/${kind.name.lowercase().replace('_', '-')}/${segment(name)}"
    }

    @JvmStatic
    fun channel(ownerRef: String, artifactId: String, channelKey: String): String =
        "${service(ownerRef)}/channel/${segment(channelKey)}"

    @JvmStatic
    fun apiOperation(ownerRef: String, artifactId: String, operationId: String): String =
        "${service(ownerRef)}/api-operation/${segment(operationId)}"

    @JvmStatic
    fun zdlMethod(ownerRef: String, artifactId: String, serviceName: String, methodName: String): String =
        semantic(artifact(ownerRef, artifactId), ArchitectureNodeKind.ZDL_METHOD,
            "services.$serviceName.methods.$methodName")

    @JvmStatic
    fun semantic(artifactId: String, kind: ArchitectureNodeKind, path: String): String =
        "$artifactId/${kind.name.lowercase().replace('_', '-')}/${segment(path)}"

    @JvmStatic
    fun edge(kind: ArchitectureEdgeKind, source: String, target: String, discriminator: String = ""): String =
        "edge/${kind.name.lowercase()}/${segment(source)}/${segment(target)}" +
            discriminator.takeIf { it.isNotBlank() }?.let { "/${segment(it)}" }.orEmpty()

    private fun segment(value: String): String = buildString {
        value.encodeToByteArray().forEach { raw ->
            val byte = raw.toInt() and 0xff
            val character = byte.toChar()
            if (character.isLetterOrDigit() && byte < 128 || character in "-._~") append(character)
            else append('%').append(byte.toString(16).uppercase().padStart(2, '0'))
        }
    }

}

internal fun canonicalStructuralSegment(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .ifEmpty { "unnamed" }

internal val conceptKinds = setOf(
    ArchitectureNodeKind.WORKSPACE, ArchitectureNodeKind.DOMAIN, ArchitectureNodeKind.SUBDOMAIN,
    ArchitectureNodeKind.SERVICE, ArchitectureNodeKind.AGGREGATE, ArchitectureNodeKind.ENTITY,
    ArchitectureNodeKind.COMMAND, ArchitectureNodeKind.EVENT, ArchitectureNodeKind.POLICY,
    ArchitectureNodeKind.FLOW, ArchitectureNodeKind.STEP, ArchitectureNodeKind.CAPABILITY,
)

private fun editDistance(left: String, right: String): Int {
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    left.forEachIndexed { leftIndex, leftChar ->
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
            )
        }
        previous = current
    }
    return previous[right.length]
}

private fun String.removeVersionCoordinate(): String {
    if ("/artifact/" !in this) return this
    val at = lastIndexOf('@')
    val slash = lastIndexOf('/')
    return if (at > slash && at > indexOf(':')) substring(0, at) else this
}
