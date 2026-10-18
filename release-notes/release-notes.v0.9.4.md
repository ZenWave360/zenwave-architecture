## What's Changed

Release **0.9.4** renames ZenWave Manifest to **ZenWave Architecture** and
extends it with browser-ready libraries plus a reusable workspace query layer.

### Breaking changes

- Maven coordinates move from `io.zenwave360.manifest` to
  `io.zenwave360.architecture`.
- `manifest-core` and `manifest-graph` are now `architecture-manifest` and
  `architecture-graph`. Update Gradle dependency declarations accordingly.
- Kotlin packages move from `io.zenwave360.manifest.*` to
  `io.zenwave360.architecture.*`.
- npm packages are renamed to `@zenwave360/architecture-manifest` and
  `@zenwave360/architecture-graph`.
- Architecture graph identifiers now use canonical `zw:` IDs. Invalid
  references and graph-integrity problems that were warnings are now errors.

### Highlights

- Adds browser and Web Worker support for `architecture-manifest` and
  `architecture-graph`, including browser smoke tests.
- Adds `ManifestPlatform` capability reporting. Archive extraction is
  available on JVM and can be supplied by JavaScript hosts through a
  `ManifestArchiveEntryLoader`.
- Adds the `workspace-runtime` module with bounded architecture overview,
  resolution, browsing, search, relationship, provenance, and diagnostics
  queries.
- Adds `workspace-mcp`, exposing the workspace query API through seven MCP
  tools, and `workspace-cli`, which provides `zenwave validate` for manifest
  validation and diagnostic reporting.
- Adds canonical concepts, aliases, evidence-backed relationships, provenance,
  confidence, and standards-first graph projection.
- Automates publishing for the renamed Maven and npm packages and adds brand
  assets for ZenWave Architecture.

### Upgrade notes

Use `zenwave-architecture.yml` as the Architecture Manifest file and update
imports, module names, Maven coordinates, and npm package names together. The
new Maven Central artifact is
`io.zenwave360.architecture:architecture-manifest`.

**Full Changelog**: https://github.com/ZenWave360/zenwave-architecture/compare/v0.9.3...v0.9.4
