#!/usr/bin/env bash
set -euo pipefail
: "${NPM_VERSION:?NPM_VERSION is required}"
node --input-type=module <<'NODE'
import fs from 'node:fs';
import { packages } from './scripts/npm-packages.mjs';
fs.mkdirSync('build/npm-smoke', { recursive: true });
fs.writeFileSync('build/npm-smoke/package.json', JSON.stringify({
  name: 'zenwave-manifest-npm-smoke', version: '0.0.0', private: true,
  dependencies: Object.fromEntries(packages.map(name => [
    `@zenwave360/${name}`, `file:../npm/zenwave360-${name}-${process.env.NPM_VERSION}.tgz`,
  ])),
}));
NODE
npm install --prefix build/npm-smoke --ignore-scripts --no-package-lock --no-audit --no-fund
node --input-type=module <<'NODE'
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { packages } from './scripts/npm-packages.mjs';
const require = createRequire(resolve('build/npm-smoke/package.json'));
for (const name of packages) {
  require(`@zenwave360/${name}`);
  console.log(`@zenwave360/${name}: loaded`);
}
NODE
