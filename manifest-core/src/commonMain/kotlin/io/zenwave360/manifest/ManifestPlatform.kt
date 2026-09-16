package io.zenwave360.manifest

/**
 * Side-effect-free query of what this library can do on the platform it is running on.
 * Asking never throws and never loads a platform module.
 *
 * - JVM: `{ARCHIVE_EXTRACTION}`
 * - JS (Node.js, browsers and Web Workers): `{}`. A JavaScript host that can read archives
 *   supplies its own [ManifestArchiveEntryLoader] to [ZenWaveManifestLoader].
 *
 * When a content candidate needs an absent capability,
 * `io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException` is thrown for it and the
 * next candidate is tried. Capabilities of the underlying `$ref` parser (filesystem, classpath,
 * HTTP) are reported by `io.zenwave360.jsonrefparser.platform.RefParserPlatform`.
 */
object ManifestPlatform {
    /** Reading an entry from inside an archive (for example a file inside a Maven JAR). */
    const val ARCHIVE_EXTRACTION = "manifest.archiveExtraction"

    fun capabilities(): Set<String> = platformManifestCapabilities()

    /** `false` for unknown capability ids. */
    fun isAvailable(capability: String): Boolean = capability in capabilities()
}

internal expect fun platformManifestCapabilities(): Set<String>
