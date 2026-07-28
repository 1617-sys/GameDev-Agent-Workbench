$ErrorActionPreference = 'Stop'
& node (Join-Path $PSScriptRoot 'run-director-evaluation.mjs')
if ($LASTEXITCODE -ne 0) { throw "Director evaluation exited with code $LASTEXITCODE" }
Write-Host "Director fixed evaluation completed; mock and confidence labels preserved." -ForegroundColor Green
