package io.zenwave360.architecture.mcp

import io.zenwave360.architecture.graph.ArchitectureDiagnostic
import io.zenwave360.architecture.graph.ArchitectureEvidence
import io.zenwave360.architecture.graph.SemanticId
import io.zenwave360.architecture.workspace.Direction
import io.zenwave360.architecture.workspace.NodeRef
import io.zenwave360.architecture.workspace.Page
import io.zenwave360.architecture.workspace.WorkspaceElement
import io.zenwave360.architecture.workspace.WorkspaceQueryApi
import io.zenwave360.architecture.workspace.WorkspaceResolutionResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public data class WorkspaceMcpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

public data class WorkspaceMcpCallResult(
    val summary: String,
    val structuredContent: JsonObject,
    val isError: Boolean = false,
)

/**
 * Thin MCP mapping over [WorkspaceQueryApi]. Transports register [tools] and delegate calls to [call].
 * This class owns argument validation and JSON projection, but no architecture semantics.
 */
public class WorkspaceMcpTools(private val queryApi: WorkspaceQueryApi) {
    public val tools: List<WorkspaceMcpTool> = listOf(
        tool("zenwave_workspace", "Return a bounded overview of the current architecture Workspace."),
        tool("zenwave_resolve", "Resolve one canonical or aliased semantic ID.", required = listOf("id"),
            properties = props("id" to string("Semantic ID beginning with zw:"))),
        tool("zenwave_search", "Find architecture elements by human-readable text.", required = listOf("query"),
            properties = props(
                "query" to string("Text to find"), "kinds" to strings("Optional node kinds"),
                "scope" to string("Optional semantic-ID scope"), "limit" to integer("Maximum results", 1, 100),
            )),
        tool("zenwave_browse", "List direct children of an architecture element.", required = listOf("id"),
            properties = props(
                "id" to string("Parent semantic ID"), "kinds" to strings("Optional node kinds"),
                "offset" to integer("Zero-based result offset", 0, 1_000_000),
                "limit" to integer("Maximum results", 1, 500),
            )),
        tool("zenwave_related", "Traverse typed architecture relationships with explicit bounds.", required = listOf("id"),
            properties = props(
                "id" to string("Root semantic ID"),
                "direction" to enumSchema("Traversal direction", "outgoing", "incoming", "both"),
                "edgeKinds" to strings("Optional relationship kinds"),
                "confidence" to strings("Optional confidence values"),
                "origin" to strings("Optional identity-origin values"),
                "depth" to integer("Traversal depth", 0, 20),
                "maxNodes" to integer("Maximum nodes", 1, 1000),
            )),
        tool("zenwave_sources", "Return provenance and source locations for one semantic ID.",
            required = listOf("id"), properties = props("id" to string("Semantic ID"))),
        tool("zenwave_diagnostics", "Return architecture diagnostics, optionally scoped and filtered.",
            properties = props(
                "scope" to string("Optional semantic-ID scope"),
                "severity" to enumSchema("Optional severity", "error", "warning", "info"),
            )),
    )

    public suspend fun call(name: String, arguments: JsonObject = JsonObject(emptyMap())): WorkspaceMcpCallResult = try {
        when (name) {
            "zenwave_workspace" -> workspace()
            "zenwave_resolve" -> resolve(arguments)
            "zenwave_search" -> search(arguments)
            "zenwave_browse" -> browse(arguments)
            "zenwave_related" -> related(arguments)
            "zenwave_sources" -> sources(arguments)
            "zenwave_diagnostics" -> diagnostics(arguments)
            else -> error("Unknown ZenWave MCP tool '$name'")
        }
    } catch (error: IllegalArgumentException) {
        WorkspaceMcpCallResult(
            summary = error.message ?: "Invalid tool arguments",
            structuredContent = buildJsonObject {
                put("error", buildJsonObject {
                    put("code", "invalid-arguments")
                    put("message", error.message ?: "Invalid tool arguments")
                })
            },
            isError = true,
        )
    }

    private suspend fun workspace(): WorkspaceMcpCallResult {
        val overview = queryApi.overview()
        return WorkspaceMcpCallResult(
            "Workspace contains ${overview.serviceCount} services and ${overview.elementCount} architecture elements.",
            buildJsonObject {
                put("domains", overview.domains.json())
                put("serviceCount", overview.serviceCount)
                put("elementCount", overview.elementCount)
                put("diagnosticCount", overview.diagnosticCount)
                put("truncated", overview.truncated)
            },
        )
    }

    private suspend fun resolve(arguments: JsonObject): WorkspaceMcpCallResult {
        val rawId = arguments.requiredString("id")
        val semanticId = semanticId(rawId)
            ?: return WorkspaceMcpCallResult(
                "No architecture element found for '$rawId'.",
                buildJsonObject { put("candidates", JsonArray(emptyList())) },
            )
        return when (val result = queryApi.resolve(semanticId)) {
            is WorkspaceResolutionResult.Resolved -> WorkspaceMcpCallResult(
                if (result.redirectedFrom == null) "Resolved ${result.element.name}."
                else "Resolved ${result.element.name} through an alias.",
                buildJsonObject {
                    put("element", result.element.json())
                    result.redirectedFrom?.let { put("redirectedFrom", it) }
                },
            )
            is WorkspaceResolutionResult.NotFound -> WorkspaceMcpCallResult(
                "No architecture element found for '$rawId'.",
                buildJsonObject { put("candidates", result.candidates.json()) },
            )
        }
    }

    private suspend fun search(arguments: JsonObject): WorkspaceMcpCallResult {
        val result = queryApi.search(
            query = arguments.requiredString("query"),
            kinds = arguments.stringList("kinds"),
            scope = arguments.optionalSemanticId("scope"),
            limit = arguments.int("limit", 20),
        )
        return WorkspaceMcpCallResult(
            "Found ${result.elements.size} architecture elements.",
            buildJsonObject { put("elements", result.elements.json()); put("truncated", result.truncated) },
        )
    }

    private suspend fun browse(arguments: JsonObject): WorkspaceMcpCallResult {
        val id = arguments.requiredSemanticId("id")
        val result = queryApi.children(
            id, arguments.stringList("kinds"), Page(arguments.int("offset", 0), arguments.int("limit", 100)),
        )
        return WorkspaceMcpCallResult(
            "Found ${result.elements.size} direct children.",
            buildJsonObject {
                put("elements", result.elements.json())
                put("truncated", result.truncated)
                result.nextOffset?.let { put("nextOffset", it) }
            },
        )
    }

    private suspend fun related(arguments: JsonObject): WorkspaceMcpCallResult {
        val direction = when (arguments.string("direction", "both").lowercase()) {
            "outgoing" -> Direction.OUTGOING
            "incoming" -> Direction.INCOMING
            "both" -> Direction.BOTH
            else -> throw IllegalArgumentException("direction must be outgoing, incoming, or both")
        }
        val result = queryApi.related(
            id = arguments.requiredSemanticId("id"),
            direction = direction,
            edgeKinds = arguments.stringList("edgeKinds"),
            confidence = arguments.stringList("confidence"),
            origin = arguments.stringList("origin"),
            depth = arguments.int("depth", 1),
            maxNodes = arguments.int("maxNodes", 100),
        )
        return WorkspaceMcpCallResult(
            "Traversal returned ${result.nodes.size} nodes and ${result.edges.size} relationships.",
            buildJsonObject {
                put("nodes", result.nodes.json())
                put("edges", buildJsonArray { result.edges.forEach { edge -> add(buildJsonObject {
                    put("from", edge.from); put("to", edge.to); put("kind", edge.kind); put("confidence", edge.confidence)
                }) } })
                put("truncated", result.truncated)
            },
        )
    }

    private suspend fun sources(arguments: JsonObject): WorkspaceMcpCallResult {
        val sources = queryApi.sources(arguments.requiredSemanticId("id"))
        return WorkspaceMcpCallResult(
            "Found ${sources.size} provenance entries.",
            buildJsonObject { put("sources", buildJsonArray { sources.forEach { add(it.json()) } }) },
        )
    }

    private suspend fun diagnostics(arguments: JsonObject): WorkspaceMcpCallResult {
        val diagnostics = queryApi.diagnostics(
            arguments.optionalSemanticId("scope"), arguments.optionalString("severity"),
        )
        return WorkspaceMcpCallResult(
            "Found ${diagnostics.size} architecture diagnostics.",
            buildJsonObject { put("diagnostics", buildJsonArray { diagnostics.forEach { add(it.json()) } }) },
        )
    }
}

private fun tool(
    name: String,
    description: String,
    required: List<String> = emptyList(),
    properties: JsonObject = JsonObject(emptyMap()),
): WorkspaceMcpTool = WorkspaceMcpTool(name, description, buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    put("properties", properties)
    if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
})

private fun props(vararg entries: Pair<String, JsonObject>): JsonObject = JsonObject(entries.toMap())
private fun string(description: String): JsonObject = buildJsonObject { put("type", "string"); put("description", description) }
private fun strings(description: String): JsonObject = buildJsonObject {
    put("type", "array"); put("description", description); put("items", buildJsonObject { put("type", "string") })
}
private fun integer(description: String, minimum: Int, maximum: Int): JsonObject = buildJsonObject {
    put("type", "integer"); put("description", description); put("minimum", minimum); put("maximum", maximum)
}
private fun enumSchema(description: String, vararg values: String): JsonObject = buildJsonObject {
    put("type", "string"); put("description", description)
    put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}

private fun JsonObject.requiredString(name: String): String = optionalString(name)
    ?: throw IllegalArgumentException("$name is required")
private fun JsonObject.optionalString(name: String): String? = this[name]?.let { value ->
    require(value is JsonPrimitive && value.isString) { "$name must be a string" }
    value.content.takeIf(String::isNotBlank)
}
private fun JsonObject.string(name: String, default: String): String = optionalString(name) ?: default
private fun JsonObject.int(name: String, default: Int): Int = this[name]?.let { value ->
    require(value is JsonPrimitive) { "$name must be an integer" }
    value.content.toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer")
} ?: default
private fun JsonObject.stringList(name: String): List<String> = this[name]?.let { value ->
    require(value is JsonArray) { "$name must be an array of strings" }
    value.map { item ->
        require(item is JsonPrimitive && item.isString) { "$name must be an array of strings" }
        item.content
    }
}.orEmpty()
private fun JsonObject.requiredSemanticId(name: String): SemanticId = semanticId(requiredString(name))
    ?: throw IllegalArgumentException("$name must be a semantic ID beginning with zw:")
private fun JsonObject.optionalSemanticId(name: String): SemanticId? = optionalString(name)?.let { raw ->
    semanticId(raw) ?: throw IllegalArgumentException("$name must be a semantic ID beginning with zw:")
}
private fun semanticId(value: String): SemanticId? = SemanticId.parseOrNull(value)

private fun List<NodeRef>.json(): JsonArray = buildJsonArray { forEach { add(it.json()) } }
private fun NodeRef.json(): JsonObject = buildJsonObject {
    put("id", id); put("kind", kind); put("name", name); put("identityOrigin", identityOrigin)
}
private fun WorkspaceElement.json(): JsonObject = buildJsonObject {
    put("id", id); put("kind", kind); put("name", name); put("identityOrigin", identityOrigin)
    description?.let { put("description", it) }
    put("attributes", JsonObject(attributes.mapValues { JsonPrimitive(it.value) }))
}
private fun ArchitectureEvidence.json(): JsonObject = buildJsonObject {
    put("kind", kind); put("confidence", confidence.name)
    explanation?.let { put("explanation", it) }
    put("source", source.json())
}
private fun io.zenwave360.architecture.graph.ArchitectureSource.json(): JsonObject = buildJsonObject {
    put("uri", uri)
    artifactOwnerRef?.let { put("artifactOwnerRef", it) }
    artifactId?.let { put("artifactId", it) }
    semanticPath?.let { put("semanticPath", it) }
    line?.let { put("line", it) }
    column?.let { put("column", it) }
}
private fun ArchitectureDiagnostic.json(): JsonObject = buildJsonObject {
    put("severity", severity.name.lowercase()); put("code", code); put("message", message)
    semanticId?.let { put("id", it) }
    source?.let { put("source", it.json()) }
}
