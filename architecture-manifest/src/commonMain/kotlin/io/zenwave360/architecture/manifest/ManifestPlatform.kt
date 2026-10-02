package io.zenwave360.architecture.manifest

/**
 * Thrown when a caller reaches a manifest capability that the current platform cannot provide
 * (for example reading an entry inside a JAR from a browser or a Web Worker).
 *
 * It reports an absent platform facility, not a defect: use [ManifestPlatform.isAvailable] to find
 * out beforehand. [capability] is always one of this library's own ids, declared on
 * [ManifestPlatform]; the underlying `$ref` parser throws its own
 * `io.zenwave360.jsonrefparser.platform.CapabilityUnavailableException` for filesystem, classpath
 * and HTTP reads.
 *
 * @param capability the capability id, e.g. [ManifestPlatform.ARCHIVE_EXTRACTION]
 * @param platform   the platform name, `"jvm"`, `"node"` or `"browser"`
 */
class ManifestCapabilityUnavailableException(
    val capability: String,
    val platform: String,
    message: String = "Capability '$capability' is not available on $platform",
) : UnsupportedOperationException(message)

/**
 * Side-effect-free query of what this library can do on the platform it is running on.
 * Asking never throws and never loads a platform module.
 *
 * - JVM: `{ARCHIVE_EXTRACTION}`
 * - JS (Node.js, browsers and Web Workers): `{}`. A JavaScript host that can read archives
 *   supplies its own [ManifestArchiveEntryLoader] to [ZenWaveManifestLoader].
 *
 * When a content candidate needs an absent capability, [ManifestCapabilityUnavailableException] is
 * thrown for it and the next candidate is tried. Capabilities of the underlying `$ref` parser
 * (filesystem, classpath, HTTP) are reported separately by
 * `io.zenwave360.jsonrefparser.platform.RefParserPlatform`.
 */
object ManifestPlatform {
    /** Reading an entry from inside an archive (for example a file inside a Maven JAR). */
    const val ARCHIVE_EXTRACTION = "manifest.archiveExtraction"

    /** `"jvm"`, `"node"` or `"browser"`; computed when read. */
    val name: String
        get() = manifestPlatformName()

    fun capabilities(): Set<String> = platformManifestCapabilities()

    /** `false` for unknown capability ids. */
    fun isAvailable(capability: String): Boolean = capability in capabilities()
}

internal expect fun manifestPlatformName(): String

internal expect fun platformManifestCapabilities(): Set<String>
