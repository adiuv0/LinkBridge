[CmdletBinding()]
param(
    [string]$VaultPath
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($VaultPath)) {
    $VaultPath = Read-Host 'Paste the path to your Obsidian vault'
}

$resolvedVault = (Resolve-Path -LiteralPath $VaultPath).Path
$obsidianDir = Join-Path $resolvedVault '.obsidian'
if (-not (Test-Path -LiteralPath $obsidianDir -PathType Container)) {
    throw "This does not look like an Obsidian vault: $resolvedVault"
}

$pluginSource = Join-Path $PSScriptRoot 'plugin'
$pluginDir = Join-Path $obsidianDir 'plugins\supernote'
if (-not (Test-Path -LiteralPath $pluginDir -PathType Container)) {
    throw 'Install and enable the official Supernote plugin in this vault first, then run this installer again.'
}

$requiredFiles = @('main.js', 'manifest.json', 'styles.css')
foreach ($name in $requiredFiles) {
    $source = Join-Path $pluginSource $name
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "The compatibility bundle is incomplete: $name is missing."
    }
}

$backupRoot = Join-Path $obsidianDir 'linkbridge-backups'
$backupDir = Join-Path $backupRoot ('supernote-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
Get-ChildItem -LiteralPath $pluginDir -Force | Copy-Item -Destination $backupDir -Recurse -Force

foreach ($name in $requiredFiles) {
    Copy-Item -LiteralPath (Join-Path $pluginSource $name) -Destination (Join-Path $pluginDir $name) -Force
}

Write-Host ''
Write-Host 'LinkBridge compatibility installed.' -ForegroundColor Green
Write-Host "Previous plugin files backed up to: $backupDir"
Write-Host 'Restart Obsidian, or disable and re-enable the Supernote plugin.'
