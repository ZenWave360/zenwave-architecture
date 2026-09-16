package io.zenwave360.manifest

import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestPlatformJvmTest {

    @Test
    fun archiveExtractionIsAvailableOnTheJvm() {
        assertEquals(setOf(ManifestPlatform.ARCHIVE_EXTRACTION), ManifestPlatform.capabilities())
    }
}
