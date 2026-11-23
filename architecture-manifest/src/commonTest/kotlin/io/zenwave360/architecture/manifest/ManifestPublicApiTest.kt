package io.zenwave360.architecture.manifest

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManifestPublicApiTest {

    @Test
    fun domainArtifactsUseTheSameDefaultCoordinateApiAsServiceArtifacts() = runTest {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.parse(
            "https://example.com/zenwave-architecture.yml",
            """
            config:
              groupIdExpression: com.example.${'$'}{owner.id}
            domains:
              architecture:
                repository: architecture-repository
                artifacts:
                  - type: zfl
                    path: business-flows/place-order-flow.zfl
                    version: 1.2.3
            """.trimIndent(),
        )

        val owner = manifest.domains.single()
        val artifact = owner.artifacts.single()
        val variables = loader.artifactResolutionContext(manifest, owner, artifact).variables()

        assertEquals(listOf(owner) + manifest.services, manifest.artifactOwners)
        assertEquals("architecture", variables["owner.id"])
        assertEquals("architecture-repository", variables["owner.repository"])
        assertEquals("com.example.architecture", variables["groupId"])
        assertEquals("business-flows/place-order-flow", variables["artifactId"])
    }

    @Test
    fun queriesArtifactsAndBuildsReferencesWithoutPluginAdapters() = runTest {
        val manifest = ZenWaveManifestLoader().parse(
            "file:///workspace/architecture/zenwave-architecture.yml",
            """
            config:
              sources:
                workspace:
                  basePathExpression: "../services/${'$'}{service.id}"
            domains:
              commerce:
                services:
                  orders:
                    artifacts:
                      - type: openapi
                        path: contracts/orders.yaml
                        version: 1.0.0
                      - type: openapi
                        path: contracts/admin.yaml
                        version: 1.0.0
            """.trimIndent(),
        )

        val service = assertNotNull(manifest.findService("commerce/orders"))
        assertEquals("contracts/orders.yaml", service.findArtifact("openapi")?.path)
        assertEquals(2, service.findArtifacts("openapi").size)

        val artifact = assertNotNull(service.findArtifact("openapi"))
        val resource = assertNotNull(
            ZenWaveManifestLoader().resolveArtifactReference(
                manifest,
                service,
                artifact,
                "schemas/order.yaml",
            ),
        )
        assertEquals(
            "file:///workspace/services/orders/contracts/schemas/order.yaml",
            resource.referenceUri(),
        )
    }

    @Test
    fun loadOptionsSupportFluentImmutableConfiguration() {
        val options = ManifestLoadOptions()
            .withPreferredSource(" git ")
            .withFallback(false)

        assertEquals("git", options.preferredSource)
        assertEquals(false, options.allowFallback)
    }

    @Test
    fun archiveReferencesRemainInsideTheArchive() {
        val artifact = ManifestResolvedResource(
            source = ManifestSourceName.MAVEN,
            uri = "https://repo.example.com/orders.jar",
            archiveEntry = "contracts/orders.yaml",
        )

        val schema = artifact.resolveReference("../schemas/order.yaml")
        assertEquals("schemas/order.yaml", schema.archiveEntry)
        assertEquals("https://repo.example.com/orders.jar!/schemas/order.yaml", schema.referenceUri())
    }

    @Test
    fun subdomainsDeclareDocsAndArtifactsLikeDomains() = runTest {
        val loader = ZenWaveManifestLoader()
        val manifest = loader.parse(
            "https://example.com/zenwave-architecture.yml",
            """
            config:
              groupIdExpression: com.example.${'$'}{owner.id}
              contentResolution: [artifactory]
              sources:
                artifactory:
                  server: https://artifacts.example.com
                  contentUrlExpression: ${'$'}{server}/${'$'}{domain.id}/${'$'}{subdomain.id}/${'$'}{version}/${'$'}{content.path}
            domains:
              commerce:
                version: 1
                subdomains:
                  fulfillment:
                    id: shipping-area
                    repository: fulfillment-repository
                    version: 2
                    docs: { summary: docs/SUMMARY.md }
                    artifacts:
                      - type: zfl
                        path: flows/ship.zfl
                        version: 4.5.6
                    services:
                      shipping: {}
                  billing:
                    docs: { summary: docs/SUMMARY.md }
            """.trimIndent(),
        )

        assertTrue(manifest.diagnostics.isEmpty(), manifest.diagnostics.toString())
        val domain = manifest.domains.single()
        val fulfillment = domain.subdomains.first { it.key == "fulfillment" }
        val billing = domain.subdomains.first { it.key == "billing" }
        assertEquals(domain.subdomains + manifest.services, manifest.artifactOwners.drop(1))
        assertEquals("commerce/fulfillment", fulfillment.artifactOwnerRef)
        assertEquals(mapOf("summary" to "docs/SUMMARY.md"), fulfillment.docs)
        assertEquals("flows/ship", fulfillment.artifacts.single().pathWithoutExtension)
        assertEquals("2", fulfillment.documentVersion())
        assertEquals("1", billing.documentVersion())

        val artifact = fulfillment.artifacts.single()
        val variables = loader.artifactResolutionContext(manifest, fulfillment, artifact).variables()
        assertEquals("shipping-area", variables["owner.id"])
        assertEquals("shipping-area", variables["subdomain.id"])
        assertEquals("commerce", variables["domain.id"])
        assertEquals("fulfillment-repository", variables["owner.repository"])
        assertEquals("com.example.shipping-area", variables["groupId"])
        assertEquals("flows/ship", variables["artifactId"])
        assertEquals("4.5.6", variables["version"])
        assertEquals(
            "https://artifacts.example.com/commerce/shipping-area/4.5.6/flows/ship.zfl",
            loader.buildArtifactCandidates(manifest, fulfillment, artifact).single().uri,
        )
        val inventory = ManifestArtifactCatalog.resolve(manifest).owner("commerce/fulfillment")
        assertEquals(1, inventory.artifacts.size)
    }

    @Test
    fun subdomainsWithoutDocsOrArtifactsKeepTheirPreviousShape() = runTest {
        val manifest = ZenWaveManifestLoader().parse(
            "https://example.com/zenwave-architecture.yml",
            "domains: { commerce: { subdomains: { fulfillment: { services: { shipping: {} } } } } }",
        )

        val subdomain = manifest.domains.single().subdomains.single()
        assertTrue(subdomain.docs.isEmpty())
        assertTrue(subdomain.artifacts.isEmpty())
        assertEquals(null, subdomain.repository)
        assertEquals(manifest.domains + subdomain + manifest.services, manifest.artifactOwners)
    }

    @Test
    fun subdomainArtifactsReportAMissingVersionLikeDomainArtifacts() = runTest {
        val manifest = ZenWaveManifestLoader().parse(
            "https://example.com/zenwave-architecture.yml",
            "domains: { commerce: { subdomains: { fulfillment: { artifacts: [{ type: zfl, path: a.zfl }] } } } }",
        )

        assertEquals(1, manifest.errors.size, manifest.diagnostics.toString())
        assertTrue(manifest.errors.single().location.orEmpty().contains("commerce/fulfillment"))
    }
}
