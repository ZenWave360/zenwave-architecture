## What's Changed

Release **0.9.6** of the ZenWave Architecture Kotlin Multiplatform libraries (`architecture-manifest`, `architecture-graph`, `workspace-runtime`, `workspace-mcp`) on Maven Central, and of the four `@zenwave360/*` npm packages.

### Added

- Artifacts declare a `releaseGroup`; runtime expressions expose the original artifact type and accept `|` fallbacks, resolved before URL encoding.
- A subdomain declares `docs`, `artifacts`, `repository` and `groupId` like a domain; its documents inherit the subdomain version, then the domain version.
- A remote manifest skips the workspace source; a document next to an artifact loads with that artifact's owner and version.
- A load result carries its ordered candidates, each with its redacted error, and the final error.
- `workspace-runtime` and `workspace-mcp` build and are tested in a headless browser.

### Changed

- `unresolved-consumer-reference` is always reported as an error.
- User info in URLs is redacted up to the last `@`.
- Depends on `dsl-kotlin` 1.10.0 and `json-schema-ref-parser-kmp` 0.9.24.

**Full Changelog**: https://github.com/ZenWave360/zenwave-architecture/compare/v0.9.5...v0.9.6
