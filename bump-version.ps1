# Bumps versionCode by 1 and versionName patch (e.g. 1.1.0 -> 1.1.1).
# Usage: .\bump-version.ps1 [-Build]
#   -Build  Run .\gradlew assembleDebug after bumping.

param([switch]$Build)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$propsPath = Join-Path $scriptDir "version.properties"

if (-not (Test-Path $propsPath)) {
    Write-Error "version.properties not found at $propsPath"
    exit 1
}

$lines = Get-Content $propsPath
$versionCode = [int]($lines | Where-Object { $_ -match '^versionCode=(\d+)' } | ForEach-Object { $matches[1] })
$versionName = ($lines | Where-Object { $_ -match '^versionName=(.+)$' } | ForEach-Object { $matches[1].Trim() })

$newVersionCode = $versionCode + 1
$parts = $versionName -split '\.'
$parts[-1] = [string]([int]$parts[-1] + 1)
$newVersionName = $parts -join '.'

$newContent = @"
# Auto-updated by bump-version script. Used by app/build.gradle.kts.
versionCode=$newVersionCode
versionName=$newVersionName
"@
Set-Content -Path $propsPath -Value $newContent -NoNewline

Write-Host "Version: $versionName ($versionCode) -> $newVersionName ($newVersionCode)"

if ($Build) {
    Set-Location $scriptDir
    & .\gradlew.bat assembleDebug
}
