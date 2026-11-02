package io.zenwave360.architecture.manifest

data class ManifestInterpolationResult(
    val value: String,
    val unresolvedVariables: List<String> = emptyList(),
)

object ManifestVariableInterpolator {
    private val placeholderPattern = Regex("""[${'$'}][{]([^}]*)[}]""")

    internal fun alternatives(expression: String): List<String> =
        if ('|' in expression) expression.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        else listOf(expression)

    fun interpolate(value: String, properties: Map<String, String?>): ManifestInterpolationResult =
        interpolate(value, { properties[it] })

    internal fun interpolate(
        value: String,
        resolve: (String) -> String?,
        transform: (String, String) -> String = { _, resolved -> resolved },
        defer: (String) -> Boolean = { false },
    ): ManifestInterpolationResult {
        val unresolved = linkedSetOf<String>()
        var resolved = value
        placeholderPattern.findAll(value).toList().forEach { match ->
            val name = match.groupValues[1]
            val selected = if (defer(name)) null else alternatives(name).asSequence()
                .mapNotNull { variable -> resolve(variable)?.let { variable to it } }
                .firstOrNull { (_, resolved) -> '|' !in name || resolved.isNotBlank() }
            val replacement = selected?.let { (variable, resolved) -> transform(variable, resolved) }
            if (replacement == null) {
                unresolved += name
            } else {
                resolved = resolved.replace(match.value, replacement)
            }
        }
        return ManifestInterpolationResult(
            value = resolved,
            unresolvedVariables = unresolved.toList()
        )
    }
}
