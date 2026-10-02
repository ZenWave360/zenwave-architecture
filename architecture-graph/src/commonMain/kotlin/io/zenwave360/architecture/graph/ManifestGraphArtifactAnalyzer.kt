package io.zenwave360.architecture.graph

import io.zenwave360.architecture.manifest.ManifestArtifactCatalog
import io.zenwave360.architecture.manifest.ResolvedManifestArtifact
import io.zenwave360.architecture.manifest.ZenWaveManifest

/** Extension point for artifact formats that contribute semantic nodes and edges to a graph. */
interface ManifestGraphArtifactAnalyzer {
    fun supports(artifact: ResolvedManifestArtifact): Boolean

    suspend fun analyze(context: ManifestGraphArtifactContext): ManifestGraphContribution
}

data class ManifestGraphArtifactContext(
    val manifest: ZenWaveManifest,
    val catalog: ManifestArtifactCatalog,
    val artifact: ResolvedManifestArtifact,
    val content: String,
    val sourceUri: String,
)

data class ManifestGraphContribution(
    val nodes: List<ArchitectureNode> = emptyList(),
    val edges: List<ArchitectureEdge> = emptyList(),
    val diagnostics: List<ArchitectureDiagnostic> = emptyList(),
)

/** Adds relationships after all artifact indexes and the canonical concept layer are available. */
interface RelationshipResolver {
    val maxConfidence: ArchitectureConfidence

    suspend fun resolve(context: RelationshipResolverContext): ManifestGraphContribution
}

data class RelationshipResolverContext(
    val manifest: ZenWaveManifest,
    val graph: ArchitectureGraph,
)
