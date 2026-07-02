# Adds api.localhost -> 127.0.0.1 to the Windows hosts file.
# Must be run as Administrator: right-click PowerShell → "Run as Administrator"

$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
$entry     = "127.0.0.1  api.localhost"

if (Select-String -Path $hostsPath -Pattern "api\.localhost" -Quiet) {
    Write-Host "api.localhost is already in $hostsPath — nothing to do."
    exit 0
}

Add-Content -Path $hostsPath -Value "`n$entry"
Write-Host "Done. Added '$entry' to $hostsPath"
Write-Host "Verify with: ping api.localhost"
