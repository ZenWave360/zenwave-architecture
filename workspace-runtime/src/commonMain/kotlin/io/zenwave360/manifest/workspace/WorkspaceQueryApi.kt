package io.zenwave360.manifest.workspace

import io.zenwave360.manifest.graph.ArchitectureDiagnostic
import io.zenwave360.manifest.graph.ArchitectureEdge
import io.zenwave360.manifest.graph.ArchitectureNode
import io.zenwave360.manifest.graph.ArchitectureEvidence
import io.zenwave360.manifest.graph.SemanticId

interface WorkspaceQueryApi {
    suspend fun overview(): WorkspaceOverview
    suspend fun resolve(id: SemanticId): WorkspaceResolutionResult
    suspend fun children(id: SemanticId, kinds: List<String> = emptyList(), page: Page = Page()): PageResult<NodeRef>
    suspend fun search(query: String, kinds: List<String> = emptyList(), scope: SemanticId? = null, limit: Int = 20): SearchResult
    /** Exact reverse-provenance lookup used to cross from a document symbol to architecture concepts. */
    suspend fun at(location: WorkspaceSourceLocation, limit: Int = 20): SearchResult
    suspend fun related(
        id: SemanticId,
        direction: Direction = Direction.BOTH,
        edgeKinds: List<String> = emptyList(),
        confidence: List<String> = emptyList(),
        origin: List<String> = emptyList(),
        depth: Int = 1,
        maxNodes: Int = 100,
    ): WorkspaceSubgraph
    suspend fun sources(id: SemanticId): List<ArchitectureEvidence>
    suspend fun diagnostics(scope: SemanticId? = null, severity: String? = null): List<ArchitectureDiagnostic>
}

data class WorkspaceOverview(
    val domains: List<NodeRef>,
    val serviceCount: Int,
    val elementCount: Int,
    val diagnosticCount: Int,
    val truncated: Boolean = false,
)

data class NodeRef(
    val id: String,
    val kind: String,
    val name: String,
    val identityOrigin: String,
)

sealed interface WorkspaceResolutionResult {
    data class Resolved(
        val element: WorkspaceElement,
        val redirectedFrom: String? = null,
    ) : WorkspaceResolutionResult

    data class NotFound(val candidates: List<NodeRef> = emptyList()) : WorkspaceResolutionResult
}

data class WorkspaceElement(
    val id: String,
    val kind: String,
    val name: String,
    val description: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val identityOrigin: String,
)

data class Page(val offset: Int = 0, val limit: Int = 100)
data class PageResult<T>(val elements: List<T>, val truncated: Boolean, val nextOffset: Int? = null)
data class SearchResult(val elements: List<NodeRef>, val truncated: Boolean)

data class WorkspaceSourceLocation(
    val uri: String,
    val semanticPath: String? = null,
)

enum class Direction { OUTGOING, INCOMING, BOTH }

data class WorkspaceSubgraph(
    val nodes: List<NodeRef>,
    val edges: List<WorkspaceEdge>,
    val truncated: Boolean,
)

data class WorkspaceEdge(
    val from: String,
    val to: String,
    val kind: String,
    val confidence: String,
)

internal fun ArchitectureNode.ref() = NodeRef(
    id, kind.name.lowercase().replace('_', '-'), label, identityOrigin.name.lowercase(),
)
internal fun ArchitectureNode.element() = WorkspaceElement(
    id, kind.name.lowercase().replace('_', '-'), label, description, attributes, identityOrigin.name.lowercase(),
)
internal fun ArchitectureEdge.workspaceKind(): String = kind.name.lowercase().replace('_', '-')
