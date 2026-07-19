#!/usr/bin/env node
// Run the Android host Gradle wrapper with the right executable per platform.
//
// Usage: node scripts/run-gradle.mjs <gradle-args...>

import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const hostDir = path.join(repoRoot, 'apps', 'android-tv-host');
const wrapper = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

const result = spawnSync(wrapper, process.argv.slice(2), {
  cwd: hostDir,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
process.exit(result.status ?? 1);
