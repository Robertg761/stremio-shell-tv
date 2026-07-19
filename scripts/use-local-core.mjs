#!/usr/bin/env node
// Build a local stremio-core fork's stremio-core-web package and install the
// packed tarball into packages/core-bridge.
//
// Usage:
//   node scripts/use-local-core.mjs [path-to-stremio-core-repo]
//   STREMIO_CORE_REPO=/path/to/stremio-core node scripts/use-local-core.mjs

import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const coreRepoPath = process.argv[2] ?? process.env.STREMIO_CORE_REPO;
if (!coreRepoPath) {
  console.error(
    'Usage: node scripts/use-local-core.mjs <path-to-stremio-core-repo>\n' +
      '(or set STREMIO_CORE_REPO)',
  );
  process.exit(1);
}

const coreWeb = path.join(path.resolve(coreRepoPath), 'stremio-core-web');
if (!existsSync(coreWeb)) {
  console.error(`stremio-core-web path not found: ${coreWeb}`);
  process.exit(1);
}

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const coreBridge = path.join(repoRoot, 'packages', 'core-bridge');

const run = (command, args, cwd) => execFileSync(command, args, { cwd, stdio: 'inherit' });

run('npm', ['ci'], coreWeb);
run('npm', ['run', 'build'], coreWeb);
run('npm', ['pack'], coreWeb);

const tarball = readdirSync(coreWeb)
  .filter((name) => name.endsWith('.tgz'))
  .map((name) => ({ name, mtime: statSync(path.join(coreWeb, name)).mtimeMs }))
  .sort((a, b) => b.mtime - a.mtime)[0];
if (!tarball) {
  console.error('No npm package archive found after npm pack');
  process.exit(1);
}

run('pnpm', ['add', `file:${path.join(coreWeb, tarball.name)}`], coreBridge);
console.log(`Installed ${tarball.name} into packages/core-bridge`);
