[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [string]$SigningConfigPath = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'LinkBridge Signing\linkbridge-signing.json')
)

$ErrorActionPreference = 'Stop'

$requiredEnvironment = @(
    'LINKBRIDGE_KEYSTORE_PATH',
    'LINKBRIDGE_KEYSTORE_PASSWORD',
    'LINKBRIDGE_KEY_ALIAS',
    'LINKBRIDGE_KEY_PASSWORD'
)
$loadedFromProtectedConfig = $false

function Convert-SecureStringToPlainText {
    param([Security.SecureString]$Value)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

$providedCount = @($requiredEnvironment | Where-Object {
    -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
}).Count

if ($providedCount -eq 0 -and (Test-Path -LiteralPath $SigningConfigPath -PathType Leaf)) {
    $signingConfig = Get-Content -LiteralPath $SigningConfigPath -Raw | ConvertFrom-Json
    if ($signingConfig.version -ne 1 -or
        [string]::IsNullOrWhiteSpace($signingConfig.keystorePath) -or
        [string]::IsNullOrWhiteSpace($signingConfig.keyAlias) -or
        [string]::IsNullOrWhiteSpace($signingConfig.protectedStorePassword) -or
        [string]::IsNullOrWhiteSpace($signingConfig.protectedKeyPassword)) {
        throw 'The LinkBridge signing configuration is incomplete or unsupported.'
    }

    $storePassword = Convert-SecureStringToPlainText (ConvertTo-SecureString $signingConfig.protectedStorePassword)
    $keyPassword = Convert-SecureStringToPlainText (ConvertTo-SecureString $signingConfig.protectedKeyPassword)
    [Environment]::SetEnvironmentVariable('LINKBRIDGE_KEYSTORE_PATH', $signingConfig.keystorePath, 'Process')
    [Environment]::SetEnvironmentVariable('LINKBRIDGE_KEYSTORE_PASSWORD', $storePassword, 'Process')
    [Environment]::SetEnvironmentVariable('LINKBRIDGE_KEY_ALIAS', $signingConfig.keyAlias, 'Process')
    [Environment]::SetEnvironmentVariable('LINKBRIDGE_KEY_PASSWORD', $keyPassword, 'Process')
    $storePassword = $null
    $keyPassword = $null
    $loadedFromProtectedConfig = $true
}
elseif ($providedCount -gt 0 -and $providedCount -lt $requiredEnvironment.Count) {
    throw 'Set all four LINKBRIDGE signing environment variables, or none of them.'
}

try {
    foreach ($name in $requiredEnvironment) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            throw "Signing credentials were not found. Run Initialize-ReleaseSigning.ps1 once, or set $name."
        }
    }

    $keystorePath = [Environment]::GetEnvironmentVariable('LINKBRIDGE_KEYSTORE_PATH')
    if (-not (Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
        throw 'LINKBRIDGE_KEYSTORE_PATH does not point to a readable keystore.'
    }

    $projectRoot = $PSScriptRoot
    $gradle = Join-Path $projectRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        throw 'The bundled Gradle wrapper was not found.'
    }

    $javaHome = [Environment]::GetEnvironmentVariable('LINKBRIDGE_JAVA_HOME')
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $androidStudioJava = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path -LiteralPath (Join-Path $androidStudioJava 'bin\java.exe') -PathType Leaf) {
            $javaHome = $androidStudioJava
        }
    }
    if ([string]::IsNullOrWhiteSpace($javaHome) -or
        -not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe') -PathType Leaf)) {
        throw 'Set LINKBRIDGE_JAVA_HOME to a 64-bit JDK 17 or newer before building.'
    }

    & $gradle "-Dorg.gradle.java.home=$javaHome" --no-daemon -p $projectRoot :app:clean :app:testReleaseUnitTest :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "The Android release build failed with exit code $LASTEXITCODE."
    }

    $metadataPath = Join-Path $projectRoot 'app\build\outputs\apk\release\output-metadata.json'
    $artifactPath = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        throw 'The signed release APK was not produced.'
    }

    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $versionName = $metadata.elements[0].versionName
    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $OutputDirectory = Join-Path $projectRoot "..\..\releases\$versionName"
    }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $destination = Join-Path $OutputDirectory "LinkBridgePhone-$versionName.apk"
    Copy-Item -LiteralPath $artifactPath -Destination $destination -Force

    $androidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $androidSdk 'build-tools') -Filter apksigner.bat -Recurse -File |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $apksigner) {
        throw 'Android apksigner was not found; the release cannot be verified.'
    }

    & $apksigner.FullName verify --verbose --print-certs $destination
    if ($LASTEXITCODE -ne 0) {
        throw 'The release APK signature could not be verified.'
    }

    $hash = Get-FileHash -LiteralPath $destination -Algorithm SHA256
    Write-Host ''
    Write-Host 'Signed LinkBridge release created.' -ForegroundColor Green
    Write-Host $destination
    Write-Host "SHA-256: $($hash.Hash)"
}
finally {
    if ($loadedFromProtectedConfig) {
        foreach ($name in $requiredEnvironment) {
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
    }
}
