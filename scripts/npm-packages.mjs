import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, appendFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const packages = Object.keys(JSON.parse(readFileSync(new URL('../gradle/npm-packages.json', import.meta.url))));
const registry = 'https://registry.npmjs.org/';
const semver = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$/;

export function npmVersion(source, { version = '', snapshotBuild = '' } = {}) {
  const mavenVersion = source.match(/^version = "([^"]+)"\r?$/m)?.[1];
  assert(mavenVersion, 'Expected a root Gradle version');
  if (snapshotBuild) {
    assert(/^\d+\.\d+$/.test(snapshotBuild), 'Expected run_number.run_attempt');
    assert(/^\d+\.\d+\.\d+-SNAPSHOT$/.test(mavenVersion), 'npm snapshots require a Maven SNAPSHOT version');
    return `${mavenVersion.replace('-SNAPSHOT', '')}-next.${snapshotBuild}`;
  }
  const result = version || mavenVersion.replace('-SNAPSHOT', '-next.0');
  assert(semver.test(result), `Invalid npm version: ${result}`);
  if (!mavenVersion.endsWith('-SNAPSHOT')) {
    assert.equal(result, mavenVersion, 'npm and Maven release versions must match');
  } else {
    assert(result.startsWith(`${mavenVersion.replace('-SNAPSHOT', '')}-next.`), 'Bootstrap npm version must belong to the Maven snapshot train');
  }
  return result;
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', ...options });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `${command} failed: ${result.stderr || ''}`);
  return result.stdout;
}

export function verifyTarballs(version, directory = 'build/npm') {
  assert(semver.test(version), `Invalid npm version: ${version}`);
  return packages.map(module => {
    const path = resolve(directory, `zenwave360-${module}-${version}.tgz`);
    const contents = readFileSync(path);
    const entries = run('tar', ['-tzf', path]).trim().split(/\r?\n/);
    assert(entries.every(entry => entry.startsWith('package/') && !entry.split('/').includes('..')), `Invalid entries in ${path}`);
    const pkg = JSON.parse(run('tar', ['-xzOf', path, 'package/package.json']));
    assert.equal(pkg.name, `@zenwave360/${module}`);
    assert.equal(pkg.version, version);
    assert.equal(pkg.license, 'MIT');
    assert.equal(pkg.publishConfig?.access, 'public');
    assert.equal(pkg.publishConfig?.registry, registry);
    assert.equal(pkg.publishConfig?.tag, version.includes('-') ? 'next' : 'latest');
    assert.equal(Object.keys(pkg.scripts ?? {}).length, 0, 'Packages must not have lifecycle scripts');
    assert(pkg.main && entries.includes(`package/${pkg.main.replace(/^\.\//, '')}`), `Missing entry point for ${pkg.name}`);
    assert(entries.includes('package/README.md') && entries.includes('package/LICENSE'), `Missing documentation for ${pkg.name}`);
    for (const dependency of Object.values(pkg.dependencies ?? {})) {
      assert(!/^(?:file:|link:|workspace:)/.test(dependency), `Local dependency in ${pkg.name}`);
    }
    return { path, name: pkg.name, version, integrity: `sha512-${createHash('sha512').update(contents).digest('base64')}` };
  });
}

async function publish(version) {
  const artifacts = verifyTarballs(version);
  // Check every package before publishing any of them. A rerun can finish a
  // partial publication only when an existing tarball has identical contents.
  const pending = [];
  for (const artifact of artifacts) {
    const response = await fetch(`${registry}${encodeURIComponent(artifact.name)}`);
    assert(response.ok, `${artifact.name}: registry HTTP ${response.status}. The first publication must be performed manually before enabling trusted publishing.`);
    const metadata = await response.json();
    const existing = metadata.versions?.[version];
    if (existing) {
      assert.equal(existing.dist?.integrity, artifact.integrity, `${artifact.name}@${version} already exists with different contents`);
      console.log(`Already published: ${artifact.name}@${version}`);
    } else {
      pending.push(artifact);
    }
  }
  const tag = version.includes('-') ? 'next' : 'latest';
  for (const artifact of pending) {
    run('npm', ['publish', artifact.path, '--registry', registry, '--ignore-scripts', '--access', 'public', '--tag', tag, '--provenance'], { stdio: 'inherit' });
  }
  if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, artifacts.map(artifact => `- ${artifact.name}@${version} (\`${tag}\`)\n`).join(''));
  }
}

export async function checkRelease(version, registryFetch = fetch) {
  assert(/^\d+\.\d+\.\d+(?:-rc\.\d+)?$/.test(version), `Invalid release version: ${version}`);
  for (const module of packages) {
    const name = `@zenwave360/${module}`;
    const response = await registryFetch(`${registry}${encodeURIComponent(name)}`);
    assert(response.ok, `${name}: registry HTTP ${response.status}. Publish the first next version manually and configure trusted publishing before enabling publishNpm.`);
    const metadata = await response.json();
    assert(!metadata.versions?.[version], `${name}@${version} is already published`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [command, version] = process.argv.slice(2);
  if (command === 'version') {
    console.log(npmVersion(readFileSync('build.gradle.kts', 'utf8'), {
      version: process.env.NPM_VERSION,
      snapshotBuild: process.env.SNAPSHOT_BUILD,
    }));
  } else if (command === 'verify') {
    for (const artifact of verifyTarballs(version)) console.log(`${artifact.name}@${version}: ${artifact.path}`);
  } else if (command === 'publish') {
    await publish(version);
  } else if (command === 'check-release') {
    await checkRelease(version);
  } else {
    throw new Error('Usage: node scripts/npm-packages.mjs version|verify VERSION|publish VERSION|check-release VERSION');
  }
}
