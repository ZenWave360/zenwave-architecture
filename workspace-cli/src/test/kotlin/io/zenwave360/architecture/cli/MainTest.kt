package io.zenwave360.architecture.cli

import io.zenwave360.architecture.graph.ArchitectureDiagnostic
import io.zenwave360.architecture.graph.ArchitectureDiagnosticSeverity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun formatsStableCodeAndSemanticId() {
        val diagnostic = ArchitectureDiagnostic(
            message = "Alias collides",
            severity = ArchitectureDiagnosticSeverity.ERROR,
            code = "semantic-alias-canonical-collision",
            semanticId = "zw:sales/orders",
        )

        assertEquals(
            "error [semantic-alias-canonical-collision] zw:sales/orders: Alias collides",
            formatDiagnostic(diagnostic),
        )
    }

    @Test
    fun rejectsUnknownCommands() = runTest {
        assertEquals(2, runCli(listOf("unknown", "manifest.yml")))
    }

    @Test
    fun normalizesWindowsPathsToFileUris() {
        assertTrue(normalizeManifestLocation("C:\\workspace\\zenwave.yml").startsWith("file:"))
    }
}
