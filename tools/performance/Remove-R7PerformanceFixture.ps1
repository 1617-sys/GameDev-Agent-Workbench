param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^r7perf-[a-z0-9]{8}$')]
    [string]$FixtureUsername,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^r7perf-[a-z0-9]{8}$')]
    [string]$ComposeProject
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sharedCleanup = Join-Path (Split-Path -Parent $PSScriptRoot) 'e2e/Remove-R7E2EFixture.ps1'
& $sharedCleanup -FixtureUsername $FixtureUsername -ComposeProject $ComposeProject
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Verified cleanup of only the R7 performance namespace '$FixtureUsername*'."
exit 0
