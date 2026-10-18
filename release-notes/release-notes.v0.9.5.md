## What's Changed

Release **0.9.5** fixes Maven Central publication of the ZenWave Architecture
Kotlin Multiplatform artifacts.

### Fixed

- Replaces the unpublished `json-schema-ref-parser-kmp` `1.0.0-SNAPSHOT`
  dependency with published version `0.9.24`.
- Ensures `architecture-manifest-js` and `architecture-manifest-jvm` no longer
  declare forbidden SNAPSHOT dependencies, allowing the complete 0.9.5 artifact
  set to be released to Maven Central.

**Full Changelog**: https://github.com/ZenWave360/zenwave-architecture/compare/v0.9.4...v0.9.5