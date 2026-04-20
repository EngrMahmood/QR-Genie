<#
Helper PowerShell script to build a signed release AAB and (optionally) build+install universal APKs using bundletool.
Usage examples:
  # Interactive (will prompt for missing values)
  .\tools\build_and_install.ps1

  # Non-interactive (provide parameters)
  .\tools\build_and_install.ps1 -KeystorePath 'C:\keys\keystore.jks' -KeystorePassword 'ksPass' -KeyAlias 'upload' -KeyPassword 'keyPass' -BundletoolPath 'C:\tools\bundletool.jar'

This script will:
 - set environment variables used by the Gradle build to sign the AAB
 - run gradlew clean bundleRelease
 - if bundletool is available, produce a universal .apks and install it to a connected device
#>
param(
    [string] $KeystorePath,
    [string] $KeystorePassword,
    [string] $KeyAlias,
    [string] $KeyPassword,
    [string] $BundletoolPath
)

function PromptIfEmpty([string] $value, [string] $prompt) {
    if ([string]::IsNullOrEmpty($value)) {
        Write-Host -NoNewline "$prompt: " -ForegroundColor Yellow
        return Read-Host -AsSecureString | ConvertFrom-SecureString -AsPlainText
    }
    return $value
}

# Ask for missing values interactively
if (-not $KeystorePath) {
    $KeystorePath = Read-Host "Path to keystore.jks (absolute)"
}
if (-not (Test-Path $KeystorePath)) {
    Write-Error "Keystore file not found at: $KeystorePath"
    exit 1
}

if (-not $KeystorePassword) {
    Write-Host "Enter keystore password (input hidden): " -NoNewline
    $secure = Read-Host -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $KeystorePassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) } finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
if (-not $KeyAlias) {
    $KeyAlias = Read-Host "Key alias"
}
if (-not $KeyPassword) {
    Write-Host "Enter key password (input hidden): " -NoNewline
    $secure = Read-Host -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $KeyPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) } finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

# Optional bundletool path
if (-not $BundletoolPath) {
    $bundleDefault = Join-Path $PSScriptRoot '..\tools\bundletool.jar'
    if (Test-Path $bundleDefault) { $BundletoolPath = (Resolve-Path $bundleDefault).Path }
}

Write-Host "Using keystore: $KeystorePath"
Write-Host "Building signed AAB..." -ForegroundColor Cyan

# Set env variables for Gradle signing (current session)
$env:KEYSTORE_FILE = $KeystorePath
$env:KEYSTORE_PASSWORD = $KeystorePassword
$env:KEY_ALIAS = $KeyAlias
$env:KEY_PASSWORD = $KeyPassword

# Run Gradle bundleRelease
Push-Location (Resolve-Path (Join-Path $PSScriptRoot '..')) > $null
try {
    & .\gradlew.bat clean bundleRelease --stacktrace
} catch {
    Write-Error "Gradle build failed. See output above."
    Pop-Location
    exit 2
}

$bundlePath = "app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $bundlePath)) {
    Write-Error "AAB not found at $bundlePath"
    Pop-Location
    exit 3
}

Write-Host "AAB built: $bundlePath" -ForegroundColor Green

if ($BundletoolPath -and (Test-Path $BundletoolPath)) {
    Write-Host "bundletool found at $BundletoolPath — building universal APKS and installing to device..." -ForegroundColor Cyan
    $apks = Join-Path (Split-Path $bundlePath) 'app-release.apks'
    & java -jar $BundletoolPath build-apks --bundle=$bundlePath --output=$apks --mode=universal --ks=$KeystorePath --ks-pass=pass:$KeystorePassword --ks-key-alias=$KeyAlias --key-pass=pass:$KeyPassword
    if ($LASTEXITCODE -ne 0) {
        Write-Error "bundletool build-apks failed"
        Pop-Location
        exit 4
    }
    Write-Host "Installing apks to connected device..." -ForegroundColor Cyan
    & java -jar $BundletoolPath install-apks --apks=$apks
    if ($LASTEXITCODE -ne 0) {
        Write-Error "bundletool install-apks failed"
        Pop-Location
        exit 5
    }
    Write-Host "App installed on device." -ForegroundColor Green
} else {
    Write-Host "bundletool not found. To install the AAB locally, download bundletool (https://github.com/google/bundletool) and run build-apks & install-apks as described in the README." -ForegroundColor Yellow
}

Pop-Location
Write-Host "Done." -ForegroundColor Green

