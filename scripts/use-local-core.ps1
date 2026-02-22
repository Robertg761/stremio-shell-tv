param(
  [string]$CoreRepoPath = "g:\Projects\Current Work in Progress Projects\stremio-core"
)

$ErrorActionPreference = "Stop"

$coreWeb = Join-Path $CoreRepoPath "stremio-core-web"
if (!(Test-Path $coreWeb)) {
  throw "stremio-core-web path not found: $coreWeb"
}

Push-Location $coreWeb
try {
  npm ci
  npm run build
  npm pack

  $tgz = Get-ChildItem -Filter "*.tgz" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $tgz) {
    throw "No npm package archive found after npm pack"
  }

  $target = "g:\Projects\Current Work in Progress Projects\stremio-shell-tv\packages\core-bridge"
  Push-Location $target
  try {
    $packageFile = "file:$($coreWeb)\$($tgz.Name)"
    pnpm add $packageFile
  }
  finally {
    Pop-Location
  }
}
finally {
  Pop-Location
}
