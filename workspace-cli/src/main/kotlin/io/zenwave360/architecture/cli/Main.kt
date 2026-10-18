package io.zenwave360.architecture.cli

import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import io.zenwave360.architecture.graph.ArchitectureDiagnostic
import io.zenwave360.architecture.graph.ArchitectureDiagnosticSeverity
import io.zenwave360.architecture.graph.ArchitectureGraphBuilder
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = runBlocking { runCli(args.toList()) }
    if (exitCode != 0) exitProcess(exitCode)
}

internal suspend fun runCli(args: List<String>): Int {
    if (args.size != 2 || args.first() != "validate") {
        System.err.println("Usage: zenwave validate <manifest-path-or-uri>")
        return 2
    }
    return try {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.load(normalizeManifestLocation(args[1]))
        val result = ArchitectureGraphBuilder(loader).build(manifest)
        result.diagnostics.forEach { println(formatDiagnostic(it)) }
        val errors = result.diagnostics.count { it.severity == ArchitectureDiagnosticSeverity.ERROR }
        val warnings = result.diagnostics.count { it.severity == ArchitectureDiagnosticSeverity.WARNING }
        println("Validated ${result.graph.nodes.size} elements and ${result.graph.edges.size} relationships: $errors errors, $warnings warnings")
        if (errors == 0) 0 else 1
    } catch (error: Exception) {
        System.err.println("Validation failed: ${error.message ?: error::class.simpleName}")
        1
    }
}

internal fun normalizeManifestLocation(value: String): String {
    val windowsPath = value.length >= 3 && value[1] == ':' && value[2] in setOf('/', '\\')
    return if (windowsPath || "://" !in value) Path.of(value).toAbsolutePath().normalize().toUri().toString() else value
}

internal fun formatDiagnostic(diagnostic: ArchitectureDiagnostic): String = buildString {
    append(diagnostic.severity.name.lowercase())
    append(" [").append(diagnostic.code).append(']')
    diagnostic.semanticId?.let { append(' ').append(it) }
    append(": ").append(diagnostic.message)
    diagnostic.source?.let { source ->
        append(" (").append(source.uri)
        source.line?.let { append(':').append(it) }
        append(')')
    }
}
