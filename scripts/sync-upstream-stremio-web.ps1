param(
  [string]$Repository = "https://github.com/Stremio/stremio-web.git",
  [string]$Commit = "a77faea0b9e6f06ca49777ecd168a1f50b88ff6e",
  [string]$VendorDir = "vendor/stremio-web"
)

$ErrorActionPreference = "Stop"

function Assert-Command([string]$Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command '$Name' is not available in PATH."
  }
}

function Invoke-RobocopyMirror([string]$Source, [string]$Destination) {
  $excludeDirs = @(".git", "node_modules", "dist", "build", ".next")
  $args = @(
    $Source,
    $Destination,
    "/MIR",
    "/R:2",
    "/W:1",
    "/NFL",
    "/NDL",
    "/NJH",
    "/NJS",
    "/NP"
  )

  foreach ($dir in $excludeDirs) {
    $args += "/XD"
    $args += (Join-Path $Source $dir)
  }

  & robocopy @args | Out-Null
  if ($LASTEXITCODE -gt 7) {
    throw "robocopy failed with exit code $LASTEXITCODE"
  }
}

Assert-Command "git"
Assert-Command "robocopy"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$vendorPath = Join-Path $repoRoot $VendorDir
$sourcePath = Join-Path $vendorPath "source"
$tempPath = Join-Path $env:TEMP "stremio-web-sync"

if (Test-Path $tempPath) {
  Remove-Item $tempPath -Recurse -Force
}

New-Item -ItemType Directory -Path $tempPath | Out-Null

Write-Host "Cloning upstream repository..."
git clone --filter=blob:none --no-checkout $Repository $tempPath
git -C $tempPath checkout $Commit

if (Test-Path $sourcePath) {
  Remove-Item $sourcePath -Recurse -Force
}

New-Item -ItemType Directory -Path $sourcePath -Force | Out-Null
Invoke-RobocopyMirror -Source $tempPath -Destination $sourcePath

$metadata = [ordered]@{
  repository = $Repository
  commit = $Commit
  syncedAtUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  sourcePath = "vendor/stremio-web/source"
}

$metadataJson = $metadata | ConvertTo-Json -Depth 4
New-Item -ItemType Directory -Path $vendorPath -Force | Out-Null
Set-Content -Path (Join-Path $vendorPath "VENDOR_METADATA.json") -Value $metadataJson -NoNewline -Encoding UTF8

Remove-Item $tempPath -Recurse -Force

Write-Host "Synced upstream stremio-web to $sourcePath at commit $Commit"
