# npm publication

The four Kotlin/JS libraries are published together, using the Maven version in
the root `build.gradle.kts`:

| Module | npm package |
| --- | --- |
| manifest-core | `@zenwave360/manifest-core` |
| manifest-graph | `@zenwave360/manifest-graph` |
| workspace-runtime | `@zenwave360/workspace-runtime` |
| workspace-mcp | `@zenwave360/workspace-mcp` |

`workspace-cli` is a JVM application and is not an npm package.

Package names and descriptions are declared in `gradle/npm-packages.json`.
Gradle configures publication metadata for every package and includes the
production JavaScript runtime modules, README, and LICENSE in each tarball.
Kotlin dependencies are included in the generated library; external npm
dependencies remain in the generated `package.json`.

These tarballs preserve the existing Kotlin/JS export surface. The four
modules currently have no `@JsExport` facades for their own Kotlin APIs;
their inherited parser/DSL exports are available. Publication automation does
not introduce new JavaScript APIs.

## Build tarballs

From the repository root in Git Bash:

```bash
./gradlew build
```

`build` tests every module and automatically generates all four tarballs in
`build/npm/`. To generate tarballs without running the full test suite:

```bash
./gradlew npmPack -PnpmVersion=1.0.0-next.0
```

On PowerShell use `.\gradlew.bat`, quoting the property argument:
`.\gradlew.bat npmPack '-PnpmVersion=1.0.0-next.0'`.
There is no separate documentation-copy or `npm pack` step.

Maven `1.0.0-SNAPSHOT` defaults to npm `1.0.0-next.0`. `-PnpmVersion` overrides
the npm version of all four packages without changing Maven versions.

Local builds use sibling parser/DSL checkouts when present. CI explicitly uses
`-PuseLocalDependencies=false` to resolve the published Maven dependencies; use
the same flag locally to reproduce CI dependency resolution.

## First publication

After committing these changes, dispatch **Build npm bootstrap tarballs**
(`npm-packages.yml`) on the desired snapshot branch with version
`1.0.0-next.0`. This standalone workflow only builds, tests, verifies, and uploads
the `npm-packages` artifact. It does not publish to Maven or npm.

Download and extract that artifact into `build/npm/`, or use the Gradle command
above to produce the same tarballs locally. Then run:

```bash
npm login --registry=https://registry.npmjs.org/

npm publish ./build/npm/zenwave360-manifest-core-1.0.0-next.0.tgz --registry=https://registry.npmjs.org/ --access public --tag next
npm publish ./build/npm/zenwave360-manifest-graph-1.0.0-next.0.tgz --registry=https://registry.npmjs.org/ --access public --tag next
npm publish ./build/npm/zenwave360-workspace-runtime-1.0.0-next.0.tgz --registry=https://registry.npmjs.org/ --access public --tag next
npm publish ./build/npm/zenwave360-workspace-mcp-1.0.0-next.0.tgz --registry=https://registry.npmjs.org/ --access public --tag next
```

You need permission to publish public packages under the `@zenwave360` scope.
Every npm version is immutable; if any of these versions already exists, use a
new prerelease version for the complete package set.

## GitHub Actions lifecycle

The existing shared workflows continue to own Maven snapshot publication,
release version preparation, tagging, Central upload, GitHub releases, and
development-branch synchronization. Their single-package npm publication is
disabled in this repository. Both callers use the shared
`ZenWave360/release-workflows/.github/workflows/npm-packages.yml` for the
complete npm package set. The local `npm-packages.yml` is only a bootstrap
dispatch caller. Package verification and fresh-install smoke tests remain
in `scripts/npm-packages.mjs` and `scripts/npm-smoke.sh`. npm continues
trusting this repository and its existing snapshot/release caller filenames.

- `publish-maven-snapshots.yml`: after Maven publication succeeds on `develop`
  or `next`, build all four packages at `<base>-next.<run_number>.<run_attempt>`
  and publish to `next`. Manual dispatch can disable `publishNpm` to generate
  downloadable tarballs without npm publication; Maven publication still runs.
- `release.yml`: when npm publication is requested, check all four packages
  for existing versions before preparing the release. After the shared release
  workflow succeeds, check out its
  `v<version>` tag, build and verify all four packages at exactly that release
  version, and upload the tarballs. Enable `publishNpm` to publish them. Stable
  releases use `latest`; prereleases use `next`.

The publishing job downloads the build's tarballs and uses OIDC trusted
publishing. It receives no Maven credentials and does not rebuild the artifacts.
All package metadata is verified before publication. Existing versions are
skipped only when their registry integrity matches the tarball, allowing a
retry to complete a partial publication. npm has no transaction that can
publish several packages atomically.

## Trusted publisher setup

After the first manual publication, configure **both** GitHub trusted publishers
on **each** of the four npm packages:

| Organization | Repository | Calling workflow filename | Environment |
| --- | --- | --- | --- |
| ZenWave360 | zenwave-architecture | `publish-maven-snapshots.yml` | `npm-snapshots` |
| ZenWave360 | zenwave-architecture | `release.yml` | `npm-publish` |

Allow direct `npm publish` for both publisher configurations. npm validates the
calling workflow filename when a reusable workflow performs publication.
Create the matching GitHub environments in this repository; retain the release
approval policy for `npm-publish` and allow the appropriate branches for each
environment. Node 24 supplies the npm version needed for trusted publishing.

See [npm trusted publishing](https://docs.npmjs.com/trusted-publishers/) for
publisher configuration and supported runners.
