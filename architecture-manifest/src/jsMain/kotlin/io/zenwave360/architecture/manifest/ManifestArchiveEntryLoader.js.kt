package io.zenwave360.architecture.manifest

// Archive extraction is supplied by the JavaScript host. Candidate construction,
// ordered fallback, and all other providers remain fully multiplatform.
internal actual fun defaultManifestArchiveEntryLoader(): ManifestArchiveEntryLoader? = null

// No built-in archive reader on JS: extraction is reported as unavailable unless the host
// supplies a ManifestArchiveEntryLoader.
internal actual fun platformManifestCapabilities(): Set<String> = emptySet()

// Detected here rather than read from the $ref parser: the platform a manifest load runs on is
// this library's own fact to report. No Node module is imported, so the browser bundle is unaffected.
private fun isNodeRuntime(): Boolean =
    js("typeof globalThis.process === 'object' && globalThis.process !== null && typeof (globalThis.process.versions || {}).node === 'string'") as Boolean

internal actual fun manifestPlatformName(): String = if (isNodeRuntime()) "node" else "browser"
