import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkRelease, npmVersion, packages } from './npm-packages.mjs';

test('all four npm modules share the Maven version train', () => {
  assert.deepEqual(packages, ['architecture-manifest', 'architecture-graph', 'workspace-runtime', 'workspace-mcp']);
  const snapshot = 'version = "1.0.0-SNAPSHOT"';
  assert.equal(npmVersion(snapshot), '1.0.0-next.0');
  assert.equal(npmVersion(`${snapshot}\r\n`), '1.0.0-next.0');
  assert.equal(npmVersion(snapshot, { version: '1.0.0-next.1' }), '1.0.0-next.1');
  assert.equal(npmVersion(snapshot, { snapshotBuild: '42.2' }), '1.0.0-next.42.2');
  assert.equal(npmVersion('version = "1.0.0"', { version: '1.0.0' }), '1.0.0');
  assert.equal(npmVersion('version = "1.0.0-rc.1"'), '1.0.0-rc.1');
});

test('release preflight checks the entire package set, including a duplicate in the last package', async () => {
  const requested = [];
  const registryFetch = async url => {
    requested.push(url);
    return {
      ok: true,
      json: async () => ({ versions: url.endsWith('workspace-mcp') ? { '1.0.0': {} } : {} }),
    };
  };
  await assert.rejects(checkRelease('1.0.0', registryFetch), /workspace-mcp@1.0.0 is already published/);
  assert.equal(requested.length, 4);
});

test('release preflight rejects missing bootstrap packages and unavailable registry responses', async () => {
  for (const status of [404, 500]) {
    await assert.rejects(checkRelease('1.0.0', async () => ({ ok: false, status })), new RegExp(`registry HTTP ${status}`));
  }
  await assert.rejects(checkRelease('1.0.0-next.0', async () => { throw new Error('Unexpected fetch'); }), /Invalid release version/);
  await checkRelease('1.0.0-rc.1', async () => ({ ok: true, json: async () => ({ versions: {} }) }));
});

test('reject version drift and malformed CI inputs before building', () => {
  assert.throws(() => npmVersion('version = "1.0.0"', { version: '1.0.1' }));
  assert.throws(() => npmVersion('version = "1.0.0"', { snapshotBuild: '42.1' }));
  assert.throws(() => npmVersion('version = "1.0.0-SNAPSHOT"', { version: '2.0.0-next.0' }));
  assert.throws(() => npmVersion('version = "1.0.0-SNAPSHOT"', { snapshotBuild: '42;echo' }));
  assert.throws(() => npmVersion('not a Gradle version'));
});
