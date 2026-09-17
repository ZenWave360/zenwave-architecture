package io.zenwave360.manifest.graph

import io.zenwave360.manifest.ManifestService
import io.zenwave360.manifest.ZenWaveManifest
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

@JvmInline
value class SemanticId(val value: String) {
    init {
        require(value.startsWith("zw:") && value.length > 3) {
            "Semantic ID must start with 'zw:' and contain at least one segment"
        }
    }

    override fun toString(): String = value

    companion object {
        /** Boundary-safe parser for protocol clients, which must treat malformed IDs as input misses. */
        @JvmStatic
        fun parseOrNull(value: String?): SemanticId? = value
            ?.takeIf { it.startsWith("zw:") && it.length > 3 }
            ?.let(::SemanticId)
    }
}

sealed interface SemanticResolutionResult {
    data class Resolved(
        val node: ArchitectureNode,
        val redirectedFrom: SemanticId? = null,
    ) : SemanticResolutionResult

    data class NotFound(
        val candidates: List<ArchitectureNode> = emptyList(),
    ) : SemanticResolutionResult
}

internal data class SemanticAliases(
    val values: Map<String, String>,
    val diagnostics: List<ArchitectureDiagnostic>,
)

internal fun semanticAliases(
    manifest: ZenWaveManifest,
    liveCanonicalIds: Collection<String> = emptyList(),
): SemanticAliases {
    val canonicalIds = linkedSetOf<String>().apply { addAll(liveCanonicalIds) }
    val declarations = mutableListOf<Pair<String, String>>()

    manifest.domains.forEach { domain ->
        val domainId = ArchitectureGraphIds.domain(domain.key)
        canonicalIds += domainId
        domain.aliases.forEach { declarations += aliasId(it, null) to domainId }

        domain.subdomains.forEach { subdomain ->
            val subdomainId = ArchitectureGraphIds.subdomain(domain.key, subdomain.key)
            canonicalIds += subdomainId
            subdomain.aliases.forEach { declarations += aliasId(it, domainId) to subdomainId }
            subdomain.services.forEach { service -> addServiceAliases(service, subdomainId, canonicalIds, declarations) }
        }
        domain.services.forEach { service -> addServiceAliases(service, domainId, canonicalIds, declarations) }
    }
    manifest.services.forEach { service ->
        val serviceId = ArchitectureGraphIds.service(service.serviceRef)
        val parentId = serviceId.substringBeforeLast('/')
        addServiceAliases(service, parentId, canonicalIds, declarations)
    }

    val aliases = linkedMapOf<String, String>()
    val diagnostics = mutableListOf<ArchitectureDiagnostic>()
    declarations.forEach { (alias, canonical) ->
        val existing = aliases[alias]
        when {
            alias == canonical -> Unit
            alias in canonicalIds -> diagnostics += aliasDiagnostic(
                canonical,
                "Alias '$alias' collides with a live canonical semantic ID",
                "semantic-alias-canonical-collision",
            )
            existing != null && existing != canonical -> diagnostics += aliasDiagnostic(
                canonical,
                "Alias '$alias' is declared for both '$existing' and '$canonical'",
                "semantic-alias-duplicate",
            )
            else -> aliases[alias] = canonical
        }
    }
    return SemanticAliases(aliases, diagnostics)
}

private fun addServiceAliases(
    service: ManifestService,
    parentId: String,
    canonicalIds: MutableSet<String>,
    declarations: MutableList<Pair<String, String>>,
) {
    val serviceId = ArchitectureGraphIds.service(service.serviceRef)
    canonicalIds += serviceId
    service.aliases.forEach { declarations += aliasId(it, parentId) to serviceId }
}

private fun aliasId(alias: String, parentId: String?): String = when {
    alias.startsWith("zw:") -> alias.trimEnd('/')
    '/' in alias -> "zw:" + alias.trim('/').split('/').joinToString("/") { canonicalStructuralSegment(it) }
    parentId != null -> "$parentId/${canonicalStructuralSegment(alias)}"
    else -> "zw:${canonicalStructuralSegment(alias)}"
}

private fun aliasDiagnostic(id: String, message: String, code: String) = ArchitectureDiagnostic(
    message = message,
    severity = ArchitectureDiagnosticSeverity.ERROR,
    code = code,
    source = ArchitectureSource(uri = "", semanticPath = id),
    semanticId = id,
)
