import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, "..");
const stageRoot = path.resolve(webRoot, ".upstream-build", "source");

function run(command, args, cwd) {
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

async function main() {
  await run("node", ["./scripts/upstream-web.mjs", "prepare"], webRoot);
  await run(
    "pnpm",
    ["--ignore-workspace", "exec", "jest", "src/common/Shortcuts/__tests__", "--runInBand"],
    stageRoot
  );
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
