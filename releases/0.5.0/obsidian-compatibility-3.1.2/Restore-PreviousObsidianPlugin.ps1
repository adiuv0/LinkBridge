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
$pluginDir = Join-Path $obsidianDir 'plugins\supernote'
$backupRoot = Join-Path $obsidianDir 'linkbridge-backups'

if (-not (Test-Path -LiteralPath $pluginDir -PathType Container)) {
    throw 'The Supernote plugin directory was not found in this vault.'
}
if (-not (Test-Path -LiteralPath $backupRoot -PathType Container)) {
    throw 'No LinkBridge plugin backup was found in this vault.'
}

$latest = Get-ChildItem -LiteralPath $backupRoot -Directory |
    Where-Object { $_.Name -like 'supernote-*' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $latest) {
    throw 'No LinkBridge plugin backup was found in this vault.'
}

Get-ChildItem -LiteralPath $latest.FullName -Force | Copy-Item -Destination $pluginDir -Recurse -Force

Write-Host ''
Write-Host 'Previous Supernote plugin restored.' -ForegroundColor Green
Write-Host "Restored from: $($latest.FullName)"
Write-Host 'Restart Obsidian, or disable and re-enable the Supernote plugin.'
