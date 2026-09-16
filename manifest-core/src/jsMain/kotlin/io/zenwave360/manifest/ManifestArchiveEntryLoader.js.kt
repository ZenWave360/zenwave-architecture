package io.zenwave360.manifest

// Archive extraction is supplied by the JavaScript host. Candidate construction,
// ordered fallback, and all other providers remain fully multiplatform.
internal actual fun defaultManifestArchiveEntryLoader(): ManifestArchiveEntryLoader? = null

// No built-in archive reader on JS: extraction is reported as unavailable unless the host
// supplies a ManifestArchiveEntryLoader.
internal actual fun platformManifestCapabilities(): Set<String> = emptySet()
