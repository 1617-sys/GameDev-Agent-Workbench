param([switch]$Confirm)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $Confirm) {
    throw 'Destructive operation refused. This removes only this Compose project''s local volumes. Re-run with -Confirm after backing up any needed local data.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

docker compose down -v --remove-orphans
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host 'Project containers and named volumes were removed.'
