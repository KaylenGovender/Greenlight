# Starts the Greenlight Admin API (https://localhost:7141) and web console (https://localhost:7142)
# in two windows, then opens the console in your browser.
#   .\run.ps1            run both
#   .\run.ps1 -Trust     also trust the local HTTPS development certificate (first time only)
param([switch]$Trust)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if ($Trust) { dotnet dev-certs https --trust }

Write-Host "Building..." -ForegroundColor Green
dotnet build "$root\Greenlight.Admin.sln" -v q
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed." -ForegroundColor Red; exit 1 }

Start-Process pwsh -ArgumentList "-NoExit", "-Command", "Set-Location '$root\Greenlight.Admin.Api'; `$host.UI.RawUI.WindowTitle='Greenlight Admin API'; dotnet run --no-build --launch-profile https"
Start-Process pwsh -ArgumentList "-NoExit", "-Command", "Set-Location '$root\Greenlight.Admin.Web'; `$host.UI.RawUI.WindowTitle='Greenlight Admin Web'; dotnet run --no-build --launch-profile https"

Write-Host "Waiting for the console to start..." -ForegroundColor Green
for ($i = 0; $i -lt 60; $i++) {
    try { Invoke-WebRequest "https://localhost:7142/Account/Login" -SkipCertificateCheck -TimeoutSec 2 | Out-Null; break } catch { Start-Sleep -Seconds 1 }
}
Start-Process "https://localhost:7142"
Write-Host "Greenlight Admin: https://localhost:7142   API reference: https://localhost:7141/scalar/v1" -ForegroundColor Green
