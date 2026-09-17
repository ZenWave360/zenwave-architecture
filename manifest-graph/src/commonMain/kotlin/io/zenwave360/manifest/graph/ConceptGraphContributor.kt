package io.zenwave360.manifest.graph

/**
 * Projects source-specific representation nodes into the canonical architecture concept layer.
 * Concept IDs are independent of artifact locations and merge evidence from every formalism.
 */
internal object ConceptGraphContributor {
    fun contribute(
        graphNodes: Map<String, ArchitectureNode>,
        graphEdges: Map<String, ArchitectureEdge>,
    ): ManifestGraphContribution {
        val nodes = linkedMapOf<String, ArchitectureNode>()
        val edges = linkedMapOf<String, ArchitectureEdge>()
        val representationConcepts = linkedMapOf<String, String>()

        fun addConcept(
            representation: ArchitectureNode,
            ownerId: String,
            kind: ArchitectureNodeKind,
            confidence: ArchitectureConfidence = ArchitectureConfidence.DECLARED,
            name: String = representation.label,
        ): String {
            val conceptId = ArchitectureGraphIds.concept(ownerId, kind, name)
            val evidence = representation.source?.let { source ->
                listOf(ArchitectureEvidence(
                    representation.evidenceKind(), source, confidence,
                    "${representation.kind.name.lowercase().replace('_', ' ')} '${representation.label}' declares this concept",
                ))
            }.orEmpty()
            val concept = ArchitectureNode(
                id = conceptId,
                kind = kind,
                label = name,
                ownerId = ownerId,
                description = representation.description,
                identityOrigin = ArchitectureIdentityOrigin.DERIVED,
                evidence = evidence,
            )
            nodes[conceptId] = nodes[conceptId]?.mergeEvidence(concept) ?: concept
            representationConcepts[representation.id] = conceptId
            addOrMergeEdge(
                edges,
                ArchitectureEdge(
                    id = ArchitectureGraphIds.edge(ArchitectureEdgeKind.REPRESENTS, representation.id, conceptId),
                    kind = ArchitectureEdgeKind.REPRESENTS,
                    source = representation.id,
                    target = conceptId,
                    provenance = ArchitectureProvenanceKind.ARTIFACT,
                    sourceInfo = representation.source,
                    confidence = confidence,
                    evidence = evidence,
                ),
            )
            addOrMergeEdge(
                edges,
                ArchitectureEdge(
                    id = ArchitectureGraphIds.edge(ArchitectureEdgeKind.CONTAINS, ownerId, conceptId),
                    kind = ArchitectureEdgeKind.CONTAINS,
                    source = ownerId,
                    target = conceptId,
                    provenance = ArchitectureProvenanceKind.ARTIFACT,
                    sourceInfo = representation.source,
                    confidence = confidence,
                    evidence = evidence,
                ),
            )
            return conceptId
        }

        graphNodes.values.filter { it.kind == ArchitectureNodeKind.ZDL_EVENT }.forEach { representation ->
            representation.owningServiceId(graphNodes)?.let { owner ->
                addConcept(representation, owner, ArchitectureNodeKind.EVENT)
            }
        }
        graphNodes.values.filter { it.kind == ArchitectureNodeKind.ZDL_ENTITY }.forEach { representation ->
            representation.owningServiceId(graphNodes)?.let { owner ->
                val kind = if (representation.attributes["aggregate"] == "true") {
                    ArchitectureNodeKind.AGGREGATE
                } else ArchitectureNodeKind.ENTITY
                addConcept(representation, owner, kind)
            }
        }
        graphNodes.values.filter { it.kind == ArchitectureNodeKind.ZDL_METHOD }.forEach { representation ->
            if (representation.attributes["intent"] != "query") {
                representation.owningServiceId(graphNodes)?.let { owner ->
                    addConcept(representation, owner, ArchitectureNodeKind.COMMAND)
                }
            }
        }
        graphNodes.values.filter { it.kind == ArchitectureNodeKind.ZFL_FLOW }.forEach { representation ->
            representation.architecturalOwnerId(graphNodes)?.let { owner ->
                addConcept(representation, owner, ArchitectureNodeKind.FLOW)
            }
        }

        // Standards-first workspaces create the same concept layer without requiring ZDL or ZFL.
        graphNodes.values.filter { it.kind == ArchitectureNodeKind.CHANNEL }.forEach { representation ->
            representation.owningServiceId(graphNodes)?.let { owner ->
                val kind = if (representation.attributes["messageKind"] == "command") {
                    ArchitectureNodeKind.COMMAND
                } else ArchitectureNodeKind.EVENT
                addConcept(
                    representation,
                    owner,
                    kind,
                    name = representation.attributes["messageName"] ?: representation.label,
                )
            }
        }
        graphNodes.values.filter {
            it.kind == ArchitectureNodeKind.API_OPERATION && it.attributes["intent"] == "command"
        }.forEach { representation ->
            representation.owningServiceId(graphNodes)?.let { owner ->
                addConcept(representation, owner, ArchitectureNodeKind.COMMAND)
            }
        }

        // A resolved ZFL event or operation is another representation of the target ZDL concept.
        graphEdges.values.filter { it.kind == ArchitectureEdgeKind.RESOLVES_TO }.forEach { resolution ->
            val representation = graphNodes[resolution.source] ?: return@forEach
            val conceptId = representationConcepts[resolution.target] ?: return@forEach
            representationConcepts[representation.id] = conceptId
            addRepresentationEdge(edges, representation, conceptId, ArchitectureConfidence.RESOLVED)
            nodes[conceptId] = nodes[conceptId]?.withEvidence(representation, ArchitectureConfidence.RESOLVED)
                ?: graphNodes[conceptId]?.withEvidence(representation, ArchitectureConfidence.RESOLVED)
                ?: return@forEach
        }

        // API operations and channels represent a concept only when the available bindings agree.
        val representationCandidates = graphEdges.values
            .filter { it.kind in setOf(ArchitectureEdgeKind.BINDS_TO, ArchitectureEdgeKind.EMITS, ArchitectureEdgeKind.CONSUMES) }
            .mapNotNull { binding ->
                val source = graphNodes[binding.source] ?: return@mapNotNull null
                val target = graphNodes[binding.target] ?: return@mapNotNull null
                if (source.kind == ArchitectureNodeKind.API_OPERATION && target.kind == ArchitectureNodeKind.CHANNEL) {
                    val conceptId = representationConcepts[target.id] ?: return@mapNotNull null
                    return@mapNotNull source.id to conceptId
                }
                val role = binding.attributes[ArchitectureBindingAttributes.ROLE]
                val emittedEventConcept = if (target.kind == ArchitectureNodeKind.CHANNEL &&
                    role == ArchitectureBindingValues.ROLE_EMISSION) {
                    graphEdges.values.asSequence()
                        .filter { it.kind == ArchitectureEdgeKind.EMITS && it.source == source.id }
                        .mapNotNull { representationConcepts[it.target] }
                        .distinct()
                        .singleOrNull()
                } else null
                val conceptId = emittedEventConcept ?: representationConcepts[binding.source]
                    ?: return@mapNotNull null
                val eligible = when (target.kind) {
                    ArchitectureNodeKind.API_OPERATION -> true
                    ArchitectureNodeKind.CHANNEL -> source.kind == ArchitectureNodeKind.ZDL_EVENT ||
                        role in setOf(ArchitectureBindingValues.ROLE_INVOCATION, ArchitectureBindingValues.ROLE_EMISSION)
                    else -> false
                }
                if (eligible) target.id to conceptId else null
            }
            .groupBy({ it.first }, { it.second })
        representationCandidates.forEach { (representationId, candidates) ->
            val conceptId = candidates.distinct().singleOrNull() ?: return@forEach
            val representation = graphNodes.getValue(representationId)
            representationConcepts[representation.id] = conceptId
            addRepresentationEdge(edges, representation, conceptId, ArchitectureConfidence.RESOLVED)
            nodes[conceptId] = nodes[conceptId]?.withEvidence(representation, ArchitectureConfidence.RESOLVED)
                ?: return@forEach
        }

        // Project source relationships onto concepts so architecture queries do not need formalism knowledge.
        graphNodes.values.filter { it.kind == ArchitectureNodeKind.ZDL_METHOD }.forEach { method ->
            val commandId = representationConcepts[method.id] ?: return@forEach
            val serviceId = method.owningServiceId(graphNodes) ?: return@forEach
            addSemanticEdge(edges, ArchitectureEdgeKind.HANDLES, serviceId, commandId, method)
        }
        graphNodes.values.filter {
            it.kind == ArchitectureNodeKind.API_OPERATION && it.attributes["intent"] == "command"
        }.forEach { operation ->
            val commandId = representationConcepts[operation.id] ?: return@forEach
            val serviceId = operation.owningServiceId(graphNodes) ?: return@forEach
            addSemanticEdge(edges, ArchitectureEdgeKind.HANDLES, serviceId, commandId, operation)
        }
        graphEdges.values.filter { it.kind == ArchitectureEdgeKind.EMITS }.forEach { emission ->
            val eventId = representationConcepts[emission.target] ?: return@forEach
            if ((nodes[eventId] ?: graphNodes[eventId])?.kind != ArchitectureNodeKind.EVENT) return@forEach
            val serviceId = graphNodes[emission.source]?.owningServiceId(graphNodes) ?: return@forEach
            addSemanticEdge(
                edges, ArchitectureEdgeKind.PUBLISHES, serviceId, eventId,
                graphNodes.getValue(emission.source), emission.confidence,
            )
        }
        graphEdges.values.filter { it.kind == ArchitectureEdgeKind.CONSUMES }.forEach { consumption ->
            val eventId = representationConcepts[consumption.target] ?: return@forEach
            if ((nodes[eventId] ?: graphNodes[eventId])?.kind != ArchitectureNodeKind.EVENT) return@forEach
            val serviceId = graphNodes[consumption.source]?.owningServiceId(graphNodes) ?: return@forEach
            addSemanticEdge(
                edges, ArchitectureEdgeKind.SUBSCRIBES, serviceId, eventId,
                graphNodes.getValue(consumption.source), consumption.confidence,
            )
        }

        return ManifestGraphContribution(nodes.values.toList(), edges.values.toList())
    }
}

private fun addRepresentationEdge(
    edges: MutableMap<String, ArchitectureEdge>,
    representation: ArchitectureNode,
    conceptId: String,
    confidence: ArchitectureConfidence,
) {
    val evidence = representation.source?.let { source ->
        listOf(ArchitectureEvidence(
            representation.evidenceKind(), source, confidence,
            "${representation.kind.name.lowercase().replace('_', ' ')} '${representation.label}' represents this concept",
        ))
    }.orEmpty()
    addOrMergeEdge(
        edges,
        ArchitectureEdge(
            id = ArchitectureGraphIds.edge(ArchitectureEdgeKind.REPRESENTS, representation.id, conceptId),
            kind = ArchitectureEdgeKind.REPRESENTS,
            source = representation.id,
            target = conceptId,
            provenance = ArchitectureProvenanceKind.INFERRED,
            sourceInfo = representation.source,
            confidence = confidence,
            evidence = evidence,
        ),
    )
}

private fun addSemanticEdge(
    edges: MutableMap<String, ArchitectureEdge>,
    kind: ArchitectureEdgeKind,
    sourceId: String,
    targetId: String,
    representation: ArchitectureNode,
    confidence: ArchitectureConfidence = ArchitectureConfidence.DECLARED,
) {
    val source = representation.source
    val evidence = source?.let {
        listOf(ArchitectureEvidence(
            representation.evidenceKind(), it, confidence,
            "${representation.kind.name.lowercase().replace('_', ' ')} '${representation.label}' supports this relationship",
        ))
    }.orEmpty()
    addOrMergeEdge(edges, ArchitectureEdge(
        id = ArchitectureGraphIds.edge(kind, sourceId, targetId),
        kind = kind,
        source = sourceId,
        target = targetId,
        provenance = if (confidence == ArchitectureConfidence.DECLARED) {
            ArchitectureProvenanceKind.ARTIFACT
        } else ArchitectureProvenanceKind.INFERRED,
        sourceInfo = source,
        confidence = confidence,
        evidence = evidence,
    ))
}

private fun ArchitectureNode.withEvidence(
    representation: ArchitectureNode,
    confidence: ArchitectureConfidence,
): ArchitectureNode = copy(
    evidence = (evidence + representation.source?.let {
        ArchitectureEvidence(
            representation.evidenceKind(), it, confidence,
            "${representation.kind.name.lowercase().replace('_', ' ')} '${representation.label}' represents this concept",
        )
    }).filterNotNull().distinct(),
)

private fun ArchitectureNode.mergeEvidence(other: ArchitectureNode): ArchitectureNode = copy(
    description = description ?: other.description,
    evidence = (evidence + other.evidence).distinct(),
)

private fun ArchitectureNode.evidenceKind(): String = when (kind) {
    ArchitectureNodeKind.ZDL_API, ArchitectureNodeKind.ZDL_SERVICE, ArchitectureNodeKind.ZDL_METHOD,
    ArchitectureNodeKind.ZDL_EVENT, ArchitectureNodeKind.ZDL_ENTITY -> "zdl"
    ArchitectureNodeKind.ZFL_SYSTEM, ArchitectureNodeKind.ZFL_FLOW, ArchitectureNodeKind.ZFL_OPERATION,
    ArchitectureNodeKind.ZFL_STEP, ArchitectureNodeKind.ZFL_EVENT, ArchitectureNodeKind.ZFL_OUTCOME -> "zfl"
    ArchitectureNodeKind.API_OPERATION -> source?.artifactId?.let { if (attributes.containsKey("method")) "openapi" else "asyncapi" }
        ?: "api"
    ArchitectureNodeKind.CHANNEL, ArchitectureNodeKind.MESSAGE -> "asyncapi"
    else -> kind.name.lowercase().replace('_', '-')
}

private fun ArchitectureNode.owningServiceId(nodes: Map<String, ArchitectureNode>): String? {
    var current: ArchitectureNode? = this
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current.id)) {
        if (current.kind == ArchitectureNodeKind.SERVICE) return current.id
        current = current.ownerId?.let(nodes::get)
    }
    return null
}

private fun ArchitectureNode.architecturalOwnerId(nodes: Map<String, ArchitectureNode>): String? {
    var current: ArchitectureNode? = this
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current.id)) {
        if (current.kind in setOf(ArchitectureNodeKind.SERVICE, ArchitectureNodeKind.SUBDOMAIN, ArchitectureNodeKind.DOMAIN)) {
            return current.id
        }
        current = current.ownerId?.let(nodes::get)
    }
    return null
}

private fun addOrMergeEdge(edges: MutableMap<String, ArchitectureEdge>, edge: ArchitectureEdge) {
    val existing = edges[edge.id]
    edges[edge.id] = if (existing == null) edge else existing.copy(
        confidence = stronger(existing.confidence, edge.confidence),
        evidence = (existing.evidence + edge.evidence).distinct(),
    )
}

private fun stronger(left: ArchitectureConfidence, right: ArchitectureConfidence): ArchitectureConfidence {
    val order = listOf(
        ArchitectureConfidence.INFERRED_HEURISTIC,
        ArchitectureConfidence.INFERRED_EXACT,
        ArchitectureConfidence.RESOLVED,
        ArchitectureConfidence.DECLARED,
    )
    return if (order.indexOf(left) >= order.indexOf(right)) left else right
}
