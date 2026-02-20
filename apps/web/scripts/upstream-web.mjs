import { createHash } from "node:crypto";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import fs from "node:fs/promises";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(webRoot, "..", "..");

const vendorSourceRoot = path.resolve(repoRoot, "vendor", "stremio-web", "source");
const stageRoot = path.resolve(webRoot, ".upstream-build", "source");
const distRoot = path.resolve(webRoot, "dist");
const installHashFile = path.resolve(stageRoot, ".install-hash");

const mode = process.argv[2] ?? "build";
const excludedMirrorEntries = new Set(["node_modules", "build", "dist", ".git", ".install-hash"]);
const overlayRoots = [
  path.resolve(webRoot, "src", "patches", "shared", "upstream-overrides"),
  path.resolve(webRoot, "src", "patches", "phone", "upstream-overrides"),
  path.resolve(webRoot, "src", "patches", "tv", "upstream-overrides")
];

async function pathExists(targetPath) {
  try {
    await fs.access(targetPath);
    return true;
  } catch {
    return false;
  }
}

async function syncDirectory(sourceDir, targetDir, exclude = excludedMirrorEntries) {
  await fs.mkdir(targetDir, { recursive: true });

  const [sourceEntries, targetEntries] = await Promise.all([
    fs.readdir(sourceDir, { withFileTypes: true }),
    fs.readdir(targetDir, { withFileTypes: true }).catch(() => [])
  ]);

  const sourceMap = new Map(
    sourceEntries.filter((entry) => !exclude.has(entry.name)).map((entry) => [entry.name, entry])
  );

  await Promise.all(
    targetEntries
      .filter((entry) => !exclude.has(entry.name) && !sourceMap.has(entry.name))
      .map((entry) => fs.rm(path.join(targetDir, entry.name), { recursive: true, force: true }))
  );

  for (const entry of sourceMap.values()) {
    const sourcePath = path.join(sourceDir, entry.name);
    const targetPath = path.join(targetDir, entry.name);

    if (entry.isDirectory()) {
      await syncDirectory(sourcePath, targetPath, exclude);
      continue;
    }

    if (entry.isFile()) {
      await fs.copyFile(sourcePath, targetPath);
      continue;
    }

    const stats = await fs.lstat(sourcePath);
    if (stats.isSymbolicLink()) {
      const linkTarget = await fs.readlink(sourcePath);
      await fs.rm(targetPath, { recursive: true, force: true });
      await fs.symlink(linkTarget, targetPath, "junction");
    }
  }
}

async function copyDirectory(sourceDir, targetDir) {
  await fs.mkdir(targetDir, { recursive: true });
  const entries = await fs.readdir(sourceDir, { withFileTypes: true });

  for (const entry of entries) {
    const sourcePath = path.join(sourceDir, entry.name);
    const targetPath = path.join(targetDir, entry.name);

    if (entry.isDirectory()) {
      await copyDirectory(sourcePath, targetPath);
      continue;
    }

    if (entry.isFile()) {
      await fs.mkdir(path.dirname(targetPath), { recursive: true });
      await fs.copyFile(sourcePath, targetPath);
    }
  }
}

function countOccurrences(content, token) {
  if (!token) {
    return 0;
  }
  return content.split(token).length - 1;
}

async function patchPlayerRoute(stageSourceRoot) {
  const playerRoutePath = path.resolve(stageSourceRoot, "src", "routes", "Player", "Player.js");
  let playerSource = await fs.readFile(playerRoutePath, "utf8");

  const helperRequireLine = "const { openNativePlaybackForStream } = require('stremio/patches/shared/nativePlaybackHandoff');";
  if (!playerSource.includes(helperRequireLine)) {
    const requireAnchor = "const { default: Indicator } = require('./Indicator/Indicator');";
    if (!playerSource.includes(requireAnchor)) {
      throw new Error("Failed to patch upstream Player.js: indicator import anchor missing.");
    }
    playerSource = playerSource.replace(requireAnchor, `${requireAnchor}\n${helperRequireLine}`);
  }

  if (!playerSource.includes("openNativePlaybackForStream({")) {
    const videoLoadAnchor = "            video.load({";
    if (countOccurrences(playerSource, videoLoadAnchor) !== 1) {
      throw new Error("Failed to patch upstream Player.js: expected exactly one video.load anchor.");
    }

    const handoffGuard = `            const nativePlaybackOpened = openNativePlaybackForStream({
                player,
                settings,
                queryParams,
                streamingServer,
                forceTranscoding: forceTranscoding || casting
            });

            if (nativePlaybackOpened) {
                return;
            }

            video.load({`;

    playerSource = playerSource.replace(videoLoadAnchor, handoffGuard);
  }

  await fs.writeFile(playerRoutePath, playerSource, "utf8");
}

async function computeInstallHash(stageSourceRoot) {
  const packageJsonPath = path.resolve(stageSourceRoot, "package.json");
  const lockFilePath = path.resolve(stageSourceRoot, "pnpm-lock.yaml");
  const [packageJson, lockFile] = await Promise.all([
    fs.readFile(packageJsonPath, "utf8"),
    fs.readFile(lockFilePath, "utf8")
  ]);

  return createHash("sha256").update(packageJson).update("\n").update(lockFile).digest("hex");
}

function runCommand(command, args, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      stdio: "inherit",
      env: process.env,
      shell: process.platform === "win32"
    });

    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`Command failed (${code}): ${command} ${args.join(" ")}`));
    });
  });
}

async function ensureDependencies(stageSourceRoot) {
  const [nodeModulesExists, installHash] = await Promise.all([
    pathExists(path.resolve(stageSourceRoot, "node_modules")),
    computeInstallHash(stageSourceRoot)
  ]);

  const existingHash = await fs.readFile(installHashFile, "utf8").catch(() => "");
  if (nodeModulesExists && existingHash.trim() === installHash) {
    return;
  }

  console.log("Installing vendored upstream dependencies...");
  await runCommand("pnpm", ["--ignore-workspace", "install", "--frozen-lockfile"], stageSourceRoot);
  await fs.writeFile(installHashFile, `${installHash}\n`, "utf8");
}

async function prepareStage() {
  if (!(await pathExists(vendorSourceRoot))) {
    throw new Error(`Missing vendored upstream source: ${vendorSourceRoot}`);
  }

  console.log("Syncing vendored upstream source to staging...");
  await syncDirectory(vendorSourceRoot, stageRoot);

  for (const overlayRoot of overlayRoots) {
    if (await pathExists(overlayRoot)) {
      console.log(`Applying overlay patch root: ${path.relative(webRoot, overlayRoot)}`);
      await copyDirectory(overlayRoot, stageRoot);
    }
  }

  console.log("Applying upstream route patches...");
  await patchPlayerRoute(stageRoot);
  await ensureDependencies(stageRoot);
}

async function copyBuildToDist() {
  const stageBuildRoot = path.resolve(stageRoot, "build");
  if (!(await pathExists(stageBuildRoot))) {
    throw new Error(`Expected upstream build output at: ${stageBuildRoot}`);
  }

  await fs.rm(distRoot, { recursive: true, force: true });
  await copyDirectory(stageBuildRoot, distRoot);
}

async function main() {
  if (!["build", "dev", "prepare"].includes(mode)) {
    throw new Error("Usage: node ./scripts/upstream-web.mjs [build|dev|prepare]");
  }

  await prepareStage();

  if (mode === "prepare") {
    return;
  }

  if (mode === "dev") {
    await runCommand("pnpm", ["--ignore-workspace", "run", "start", "--", "--port", "5173"], stageRoot);
    return;
  }

  console.log("Building staged upstream web app...");
  await runCommand("pnpm", ["--ignore-workspace", "run", "build"], stageRoot);
  console.log("Copying upstream build output to apps/web/dist...");
  await copyBuildToDist();
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
