param([switch]$KeepFixture)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$arguments = @((Join-Path $PSScriptRoot 'run-director-e2e.mjs'))
if ($KeepFixture) { $arguments += '--keep-fixture' }
try {
    & node @arguments
    if ($LASTEXITCODE -ne 0) { throw "Director E2E exited with code $LASTEXITCODE" }
}
finally {
    Remove-Item Env:DIRECTOR_E2E_OUTPUT -ErrorAction SilentlyContinue
}
Write-Host "Director goal-to-DRAFT fixture started, executed, verified and cleaned." -ForegroundColor Green
