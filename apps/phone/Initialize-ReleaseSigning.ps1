[CmdletBinding()]
param(
    [string]$SigningDirectory = (Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'LinkBridge Signing')
)

$ErrorActionPreference = 'Stop'

$keystorePath = Join-Path $SigningDirectory 'linkbridge-release.jks'
$configPath = Join-Path $SigningDirectory 'linkbridge-signing.json'
$recoveryPath = Join-Path $SigningDirectory 'RECOVERY-CREDENTIALS.txt'
$alias = 'linkbridge-release'

foreach ($path in @($keystorePath, $configPath, $recoveryPath)) {
    if (Test-Path -LiteralPath $path) {
        throw "Signing initialization stopped because this file already exists: $path"
    }
}

$javaHome = [Environment]::GetEnvironmentVariable('LINKBRIDGE_JAVA_HOME')
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $androidStudioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $androidStudioJava 'bin\keytool.exe') -PathType Leaf) {
        $javaHome = $androidStudioJava
    }
}
$keytool = if ([string]::IsNullOrWhiteSpace($javaHome)) { $null } else { Join-Path $javaHome 'bin\keytool.exe' }
if ([string]::IsNullOrWhiteSpace($keytool) -or -not (Test-Path -LiteralPath $keytool -PathType Leaf)) {
    throw 'Set LINKBRIDGE_JAVA_HOME to a JDK containing keytool.exe.'
}

New-Item -ItemType Directory -Path $SigningDirectory -Force | Out-Null

$randomBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$password = [Convert]::ToHexString($randomBytes)

try {
    & $keytool -genkeypair -v `
        -keystore $keystorePath `
        -storepass $password `
        -keypass $password `
        -alias $alias `
        -keyalg RSA `
        -keysize 4096 `
        -sigalg SHA256withRSA `
        -validity 36500 `
        -dname 'CN=LinkBridge, OU=Open Source, O=LinkBridge, C=US'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
        throw 'Android keytool did not create the release keystore.'
    }

    $securePassword = ConvertTo-SecureString $password -AsPlainText -Force
    $protectedPassword = ConvertFrom-SecureString $securePassword
    [ordered]@{
        version = 1
        keystorePath = $keystorePath
        keyAlias = $alias
        protectedStorePassword = $protectedPassword
        protectedKeyPassword = $protectedPassword
    } | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8

    @"
LINKBRIDGE ANDROID SIGNING RECOVERY

Keep this file private. Store it in a password manager or encrypted backup,
then remove the plaintext copy from this computer if desired.

Keystore: $keystorePath
Alias: $alias
Password: $password

Losing either the keystore or this password prevents future Android updates
from being signed as the same LinkBridge application.
"@ | Set-Content -LiteralPath $recoveryPath -Encoding utf8

    Write-Host 'Permanent LinkBridge signing identity created.' -ForegroundColor Green
    Write-Host "Keystore: $keystorePath"
    Write-Host "Windows-protected build config: $configPath"
    Write-Host "Recovery credentials: $recoveryPath" -ForegroundColor Yellow
}
finally {
    if ($null -ne $randomBytes) {
        [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    }
    $password = $null
}
