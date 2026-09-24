param(
    [Parameter(Mandatory=$true)][string]$ApkPath,
    [Parameter(Mandatory=$true)][string]$HttpsUrl,
    [string]$Output = "provisioning.json"
)

$ErrorActionPreference = "Stop"
$apk = (Resolve-Path $ApkPath).Path
$bytes = [System.IO.File]::ReadAllBytes($apk)
$sha = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
$checksum = [Convert]::ToBase64String($sha).Replace('+','-').Replace('/','_')

$config = [ordered]@{
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" = "dev.scrolllock.dpc/dev.scrolllock.dpc.ScrollLockAdminReceiver"
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION" = $HttpsUrl
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" = $checksum
    "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED" = $true
}

$config | ConvertTo-Json | Set-Content -Encoding UTF8 $Output
Write-Host "Wrote $Output"
Write-Host "Package SHA-256 (URL-safe Base64): $checksum"
