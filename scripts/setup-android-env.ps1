param(
  [string]$SdkRoot = "G:\Android\Sdk",
  [string]$AndroidHome = "G:\Android\.android",
  [switch]$PersistUserEnvironment
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Path $SdkRoot -Force | Out-Null
New-Item -ItemType Directory -Path $AndroidHome -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $AndroidHome "avd") -Force | Out-Null

$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_USER_HOME = $AndroidHome
$env:ANDROID_AVD_HOME = Join-Path $AndroidHome "avd"

Write-Host "Set current-session Android paths:"
Write-Host "ANDROID_SDK_ROOT=$($env:ANDROID_SDK_ROOT)"
Write-Host "ANDROID_HOME=$($env:ANDROID_HOME)"
Write-Host "ANDROID_USER_HOME=$($env:ANDROID_USER_HOME)"
Write-Host "ANDROID_AVD_HOME=$($env:ANDROID_AVD_HOME)"

if ($PersistUserEnvironment) {
  [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $env:ANDROID_SDK_ROOT, "User")
  [Environment]::SetEnvironmentVariable("ANDROID_HOME", $env:ANDROID_HOME, "User")
  [Environment]::SetEnvironmentVariable("ANDROID_USER_HOME", $env:ANDROID_USER_HOME, "User")
  [Environment]::SetEnvironmentVariable("ANDROID_AVD_HOME", $env:ANDROID_AVD_HOME, "User")
  Write-Host "Persisted Android environment variables to current user profile."
}
