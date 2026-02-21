#!/usr/bin/env node
/**
 * Prints the CHANGELOG.md section for a given version.
 *
 * Usage:
 *   node scripts/extract_changelog_section.js 0.1.0
 */

const fs = require("fs");
const path = require("path");

const version = process.argv[2];
if (!version) {
  console.error("Usage: node scripts/extract_changelog_section.js <version>");
  process.exit(2);
}

const changelogPath = path.join(process.cwd(), "CHANGELOG.md");
const txt = fs.readFileSync(changelogPath, "utf8");

// Matches: ## [0.1.0] - 2026-02-21
const headerRe = new RegExp(
  String.raw`^##\s*\[${version.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\]\s*-\s*.*$`,
  "m"
);

const headerMatch = txt.match(headerRe);
if (!headerMatch || headerMatch.index == null) {
  console.error(`Could not find version [${version}] in ${changelogPath}`);
  process.exit(1);
}

const startIdx = headerMatch.index;
const afterHeaderIdx = startIdx + headerMatch[0].length;
const rest = txt.slice(afterHeaderIdx);
const nextHeaderMatch = rest.match(/^##\s*\[/m);
const endIdx =
  nextHeaderMatch && nextHeaderMatch.index != null
    ? afterHeaderIdx + nextHeaderMatch.index
    : txt.length;

const section = txt.slice(startIdx, endIdx).trimEnd();
process.stdout.write(section + "\n");
