#!/usr/bin/env node
// Sync the pinned upstream stremio-web commit into vendor/stremio-web/source
// and refresh VENDOR_METADATA.json.
//
// Usage:
//   node scripts/sync-upstream-stremio-web.mjs [--commit <sha>] [--repository <url>]

import { execFileSync } from 'node:child_process';
import { cpSync, mkdirSync, rmSync, writeFileSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_REPOSITORY = 'https://github.com/Stremio/stremio-web.git';
const DEFAULT_COMMIT = 'a77faea0b9e6f06ca49777ecd168a1f50b88ff6e';
const EXCLUDED_DIRS = new Set(['.git', 'node_modules', 'dist', 'build', '.next']);

function parseArgs(argv) {
  const options = { repository: DEFAULT_REPOSITORY, commit: DEFAULT_COMMIT };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--commit' && argv[i + 1]) {
      options.commit = argv[++i];
    } else if (argv[i] === '--repository' && argv[i + 1]) {
      options.repository = argv[++i];
    } else {
      throw new Error(`Unknown or incomplete argument: ${argv[i]}`);
    }
  }
  return options;
}

const { repository, commit } = parseArgs(process.argv.slice(2));
const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const vendorPath = path.join(repoRoot, 'vendor', 'stremio-web');
const sourcePath = path.join(vendorPath, 'source');
const tempPath = mkdtempSync(path.join(tmpdir(), 'stremio-web-sync-'));

try {
  console.log(`Cloning ${repository} at ${commit}...`);
  execFileSync('git', ['clone', '--filter=blob:none', '--no-checkout', repository, tempPath], {
    stdio: 'inherit',
  });
  execFileSync('git', ['-C', tempPath, 'checkout', commit], { stdio: 'inherit' });

  rmSync(sourcePath, { recursive: true, force: true });
  mkdirSync(sourcePath, { recursive: true });
  cpSync(tempPath, sourcePath, {
    recursive: true,
    filter: (src) => !EXCLUDED_DIRS.has(path.basename(src)) || src === tempPath,
  });

  const metadata = {
    repository,
    commit,
    syncedAtUtc: new Date().toISOString().replace(/\.\d{3}Z$/, 'Z'),
    sourcePath: 'vendor/stremio-web/source',
  };
  writeFileSync(path.join(vendorPath, 'VENDOR_METADATA.json'), JSON.stringify(metadata, null, 2));

  console.log(`Synced upstream stremio-web to ${sourcePath} at commit ${commit}`);
} finally {
  rmSync(tempPath, { recursive: true, force: true });
}
