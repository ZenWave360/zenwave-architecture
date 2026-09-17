package io.zenwave360.manifest.workspace

import io.zenwave360.manifest.graph.ArchitectureDiagnostic
import io.zenwave360.manifest.graph.ArchitectureDiagnosticSeverity
import io.zenwave360.manifest.graph.ArchitectureGraph
import io.zenwave360.manifest.graph.ArchitectureGraphResult
import io.zenwave360.manifest.graph.ArchitectureConfidence
import io.zenwave360.manifest.graph.ArchitectureEdgeKind
import io.zenwave360.manifest.graph.ArchitectureIdentityOrigin
import io.zenwave360.manifest.graph.ArchitectureNodeKind
import io.zenwave360.manifest.graph.SemanticId
import io.zenwave360.manifest.graph.SemanticResolutionResult

class DefaultWorkspaceQueryApi(
    private val result: ArchitectureGraphResult,
) : WorkspaceQueryApi {
    private val graph: ArchitectureGraph get() = result.graph
    private val nodesBySource: Map<WorkspaceSourceLocation, List<io.zenwave360.manifest.graph.ArchitectureNode>> =
        result.graph.nodes.flatMap { node ->
            (node.evidence.map { it.source } + listOfNotNull(node.source)).distinct().map { source ->
                WorkspaceSourceLocation(source.uri, source.semanticPath) to node
            }
        }.groupBy({ it.first }, { it.second })

    override suspend fun overview(): WorkspaceOverview = WorkspaceOverview(
        domains = graph.nodes.filter { it.kind == ArchitectureNodeKind.DOMAIN }.take(100).map { it.ref() },
        serviceCount = graph.nodes.count { it.kind == ArchitectureNodeKind.SERVICE },
        elementCount = graph.nodes.size,
        diagnosticCount = result.diagnostics.size,
        truncated = graph.nodes.count { it.kind == ArchitectureNodeKind.DOMAIN } > 100,
    )

    override suspend fun resolve(id: SemanticId): WorkspaceResolutionResult = when (val resolution = graph.resolve(id)) {
        is SemanticResolutionResult.Resolved -> WorkspaceResolutionResult.Resolved(
            resolution.node.element(), resolution.redirectedFrom?.value,
        )
        is SemanticResolutionResult.NotFound -> WorkspaceResolutionResult.NotFound(resolution.candidates.map { it.ref() })
    }

    override suspend fun children(id: SemanticId, kinds: List<String>, page: Page): PageResult<NodeRef> {
        val canonical = resolvedNodeId(id) ?: return PageResult(emptyList(), false)
        val normalizedKinds = parseValues<ArchitectureNodeKind>(kinds, "node kind") { it.normalizedKind() }
        val matches = graph.nodes.asSequence()
            .filter { it.ownerId == canonical }
            .filter { normalizedKinds.isEmpty() || it.kind in normalizedKinds }
            .map { it.ref() }
            .toList()
        val offset = page.offset.coerceAtLeast(0)
        val limit = page.limit.coerceIn(1, 500)
        val elements = matches.drop(offset).take(limit)
        val next = (offset + elements.size).takeIf { it < matches.size }
        return PageResult(elements, next != null, next)
    }

    override suspend fun search(query: String, kinds: List<String>, scope: SemanticId?, limit: Int): SearchResult {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return SearchResult(emptyList(), false)
        val normalizedKinds = parseValues<ArchitectureNodeKind>(kinds, "node kind") { it.normalizedKind() }
        val scopeId = scope?.let(::resolvedNodeId)
        if (scope != null && scopeId == null) return SearchResult(emptyList(), false)
        val all = graph.nodes.asSequence()
            .filter { normalizedKinds.isEmpty() || it.kind in normalizedKinds }
            .filter { scopeId == null || it.id == scopeId || it.id.startsWith("$scopeId/") }
            .filter { it.label.lowercase().contains(needle) || it.id.lowercase().contains(needle) }
            .sortedWith(compareBy({ !it.label.equals(query, ignoreCase = true) }, { it.label.lowercase() }, { it.id }))
            .toList()
        val bound = limit.coerceIn(1, 100)
        return SearchResult(all.take(bound).map { it.ref() }, all.size > bound)
    }

    override suspend fun at(location: WorkspaceSourceLocation, limit: Int): SearchResult {
        require(location.uri.isNotBlank()) { "location uri must not be blank" }
        require(limit in 1..100) { "limit must be between 1 and 100" }
        val matches = if (location.semanticPath != null) {
            nodesBySource[location].orEmpty()
        } else {
            nodesBySource.entries.asSequence()
                .filter { it.key.uri == location.uri }
                .flatMap { it.value.asSequence() }
                .toList()
        }
        val concepts = matches.asSequence().flatMap { node ->
            val represented = graph.outgoing(node.id, ArchitectureEdgeKind.REPRESENTS)
                .mapNotNull { graph.node(it.target) }
            when {
                represented.isNotEmpty() -> represented.asSequence()
                node.kind in ARCHITECTURE_CONCEPT_KINDS -> sequenceOf(node)
                else -> emptySequence()
            }
        }.distinctBy { it.id }.sortedBy { it.id }.toList()
        return SearchResult(concepts.take(limit).map { it.ref() }, concepts.size > limit)
    }

    override suspend fun related(
        id: SemanticId,
        direction: Direction,
        edgeKinds: List<String>,
        confidence: List<String>,
        origin: List<String>,
        depth: Int,
        maxNodes: Int,
    ): WorkspaceSubgraph {
        val rootId = resolvedNodeId(id) ?: return WorkspaceSubgraph(emptyList(), emptyList(), false)
        val kindFilter = parseValues<ArchitectureEdgeKind>(edgeKinds, "edge kind") { it.normalizedKind() }
        val confidenceFilter = parseValues<ArchitectureConfidence>(confidence, "confidence") { it.uppercase().trim() }
        val originFilter = parseValues<ArchitectureIdentityOrigin>(origin, "identity origin") { it.uppercase().trim() }
        require(depth in 0..20) { "depth must be between 0 and 20" }
        require(maxNodes in 1..1000) { "maxNodes must be between 1 and 1000" }
        val traversal = graph.related(
            rootId = rootId,
            direction = direction.toGraphDirection(),
            kinds = kindFilter,
            confidence = confidenceFilter,
            origins = originFilter,
            maxDepth = depth,
            maxNodes = maxNodes,
        )
        return WorkspaceSubgraph(
            nodes = traversal.graph.nodes.map { it.ref() },
            edges = traversal.graph.edges.map { edge ->
                WorkspaceEdge(edge.source, edge.target, edge.workspaceKind(), edge.confidence.name)
            },
            truncated = traversal.truncated,
        )
    }

    override suspend fun sources(id: SemanticId) = resolvedNodeId(id)
        ?.let(graph.nodesById::get)
        ?.evidence
        .orEmpty()

    override suspend fun diagnostics(scope: SemanticId?, severity: String?): List<ArchitectureDiagnostic> {
        val scopeId = scope?.let(::resolvedNodeId)
        if (scope != null && scopeId == null) return emptyList()
        val requestedSeverity = severity?.uppercase()?.let { value ->
            ArchitectureDiagnosticSeverity.entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException(
                    "Unknown diagnostic severity '$severity'; expected one of error, info, warning",
                )
        }
        return result.diagnostics.filter { diagnostic ->
            (requestedSeverity == null || diagnostic.severity == requestedSeverity) &&
                (scopeId == null || diagnostic.semanticId.inScope(scopeId))
        }
    }

    private fun resolvedNodeId(id: SemanticId): String? = when (val resolution = graph.resolve(id)) {
        is SemanticResolutionResult.Resolved -> resolution.node.id
        is SemanticResolutionResult.NotFound -> null
    }
}

private val ARCHITECTURE_CONCEPT_KINDS = setOf(
    ArchitectureNodeKind.WORKSPACE, ArchitectureNodeKind.DOMAIN, ArchitectureNodeKind.SUBDOMAIN,
    ArchitectureNodeKind.SERVICE, ArchitectureNodeKind.AGGREGATE, ArchitectureNodeKind.ENTITY,
    ArchitectureNodeKind.COMMAND, ArchitectureNodeKind.EVENT, ArchitectureNodeKind.POLICY,
    ArchitectureNodeKind.FLOW, ArchitectureNodeKind.STEP, ArchitectureNodeKind.CAPABILITY,
)

private fun String.normalizedKind(): String = lowercase().replace('_', '-').trim()

private inline fun <reified T : Enum<T>> parseValues(
    values: List<String>,
    label: String,
    normalize: (String) -> String,
): Set<T> {
    val known = enumValues<T>().associateBy { normalize(it.name) }
    return values.mapTo(linkedSetOf()) { raw ->
        known[normalize(raw)] ?: throw IllegalArgumentException(
            "Unknown $label '$raw'; expected one of ${known.keys.sorted().joinToString()}",
        )
    }
}

private fun Direction.toGraphDirection() = when (this) {
    Direction.OUTGOING -> io.zenwave360.manifest.graph.ArchitectureTraversalDirection.OUTGOING
    Direction.INCOMING -> io.zenwave360.manifest.graph.ArchitectureTraversalDirection.INCOMING
    Direction.BOTH -> io.zenwave360.manifest.graph.ArchitectureTraversalDirection.BOTH
}

private fun String?.inScope(scope: String): Boolean = this == scope || this?.startsWith("$scope/") == true
