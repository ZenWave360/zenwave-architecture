package io.zenwave360.architecture.graph

import io.zenwave360.architecture.manifest.ZenWaveManifestLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnresolvedConsumerDiagnosticTest {
    @Test
    fun unresolvedConsumerReferenceIsReportedOnceAsError() = runTest {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.parse(
            "file:///architecture/zenwave-architecture.yml",
            """
            domains:
              catalog:
                subdomains:
                  inventory:
                    services:
                      catalog-inventory:
                        id: catalog.inventory.catalog-inventory
                        artifacts:
                          - type: asyncapi
                            path: asyncapi.yml
                            version: 1.0.0
                        consumers:
                          - "missing.service#asyncapi-client"
            """.trimIndent(),
        )

        val result = ArchitectureGraphBuilder(loader).build(
            manifest,
            ArchitectureGraphBuildOptions(includeDeclaredConsumers = true),
        )

        val unresolved = result.diagnostics.filter { it.code == "unresolved-consumer-reference" }
        assertEquals(listOf(ArchitectureDiagnosticSeverity.ERROR), unresolved.map { it.severity })
    }
}
