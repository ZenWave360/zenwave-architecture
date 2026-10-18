# Releasing

This repository publishes Kotlin Multiplatform artifacts to Maven Central through the Sonatype Central Portal.

All four JavaScript libraries share the same version and release lifecycle.
Gradle `build` also creates their npm tarballs in `build/npm/`; the snapshot and
release workflows build and optionally publish the complete package set.
See [npm publication](docs/npm-publishing.md) for first-publication commands,
CI artifacts, versioning, and trusted publisher setup.

## Artifacts

The release workflow publishes these coordinates:

- `io.zenwave360.architecture:architecture-manifest`
- `io.zenwave360.architecture:architecture-manifest-jvm`
- `io.zenwave360.architecture:architecture-manifest-js`
- `io.zenwave360.architecture:architecture-graph` (also `-jvm` and `-js`)
- `io.zenwave360.architecture:workspace-runtime` (also `-jvm` and `-js`)
- `io.zenwave360.architecture:workspace-mcp` (also `-jvm` and `-js`)

## Repository Prerequisites

Before the first public release, make sure all of the following are in place:

1. The GitHub repository is public.
2. The `io.zenwave360.architecture` namespace is registered and verified in the Sonatype Central Portal.
3. A GPG keypair exists for artifact signing and the public key has been published.
4. GitHub Actions secrets are configured.

Supported secret names used by the current GitHub workflows:

- `CENTRAL_USERNAME`
- `CENTRAL_TOKEN`
- `SIGN_KEY`
- `SIGN_KEY_PASS`

## Release Flow

There are two workflows involved:

The `Release` workflow (`release.yml`) delegates version preparation and Maven
publication to the shared Gradle release workflow. It creates and merges a
release PR, tags the release commit, bumps the next development version, uploads
to the Central Portal, creates the GitHub release, and synchronizes `develop`.
The local npm job then builds from the release tag and uploads all four
tarballs; selecting `publishNpm` also publishes them through trusted publishing.

## Manual Release Steps

1. Commit release notes for the chosen version under `release-notes/`.
2. Dispatch `Release` from `main` with `version` and optional
   `developmentVersion`. Select `publishNpm` after all four packages have their
   first manual publication and trusted publishers configured.
3. Approve the configured publication environments and review the workflow.
4. Complete publication in the Central Portal after validation; the shared
   workflow uploads with `publishToMavenCentral` rather than auto-releasing.

## Snapshot Publishing

The `Build and Publish Snapshots` workflow uses `./gradlew publishToMavenCentral`. Keep the project version on a `-SNAPSHOT` suffix when using that workflow.
