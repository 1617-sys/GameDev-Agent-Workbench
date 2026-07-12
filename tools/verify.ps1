param(
    [ValidateSet("quick", "integration", "e2e")]
    [string]$Profile = "quick"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()
$results = [System.Collections.Generic.List[object]]::new()

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [string[]]$Arguments = @()
    )

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Required command '$Command' was not found on PATH."
    }

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command '$Command $($Arguments -join ' ')' exited with code $LASTEXITCODE."
    }
}

function Invoke-VerificationStep {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,

        [Parameter(Mandatory = $true)]
        [scriptblock]$Action
    )

    $startedAt = Get-Date
    $locationPushed = $false
    Write-Host ""
    Write-Host "[RUN ] $Name"

    try {
        Push-Location -LiteralPath $WorkingDirectory
        $locationPushed = $true
        & $Action

        $duration = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
        $results.Add([pscustomobject]@{
                Step     = $Name
                Status   = "PASS"
                Duration = "${duration}s"
            })
        Write-Host "[PASS] $Name (${duration}s)" -ForegroundColor Green
    }
    catch {
        $duration = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
        $failures.Add($Name)
        $results.Add([pscustomobject]@{
                Step     = $Name
                Status   = "FAIL"
                Duration = "${duration}s"
            })
        Write-Host "[FAIL] $Name (${duration}s)" -ForegroundColor Red
        Write-Host "       $($_.Exception.Message)" -ForegroundColor Red
    }
    finally {
        if ($locationPushed) {
            Pop-Location
        }
    }
}

Write-Host "GameDev Agent Workbench verification"
Write-Host "Profile: $Profile"
Write-Host "Root:    $projectRoot"

switch ($Profile) {
    "quick" {
        Invoke-VerificationStep `
            -Name "Java tests" `
            -WorkingDirectory (Join-Path $projectRoot "backend-java") `
            -Action { Invoke-NativeCommand "mvn" @("test") }

        Invoke-VerificationStep `
            -Name "Python compile" `
            -WorkingDirectory (Join-Path $projectRoot "python-agent") `
            -Action { Invoke-NativeCommand "python" @("-m", "compileall", "app") }

        Invoke-VerificationStep `
            -Name "Vue production build" `
            -WorkingDirectory (Join-Path $projectRoot "frontend-vue") `
            -Action { Invoke-NativeCommand "npm" @("run", "build") }

        Invoke-VerificationStep `
            -Name "Docker Compose config" `
            -WorkingDirectory $projectRoot `
            -Action { Invoke-NativeCommand "docker" @("compose", "config", "--quiet") }
    }
    "integration" {
        Invoke-VerificationStep `
            -Name "R3 async concurrency Testcontainers harness" `
            -WorkingDirectory (Join-Path $projectRoot "backend-java") `
            -Action { Invoke-NativeCommand "mvn" @("-Dtest=*AsyncWorkflow*IT,*WorkflowConcurrency*Test,*Outbox*IT,*DeadLetter*IT,*WorkflowRecovery*IT,*RabbitMqInfrastructureTest", "test") }

        Invoke-VerificationStep `
            -Name "Docker Compose config" `
            -WorkingDirectory $projectRoot `
            -Action { Invoke-NativeCommand "docker" @("compose", "config", "--quiet") }

        Write-Host "Integration profile validates the R3 Testcontainers concurrency harness and dependency smoke coverage."
    }
    "e2e" {
        Invoke-VerificationStep `
            -Name "R4 browser E2E harness" `
            -WorkingDirectory (Join-Path $projectRoot "frontend-vue") `
            -Action { Invoke-NativeCommand "npm" @("run", "test:e2e") }
    }
}

Write-Host ""
Write-Host "Verification summary"
$results | Format-Table -AutoSize

if ($failures.Count -gt 0) {
    Write-Host "Verification failed in: $($failures -join ', ')" -ForegroundColor Red
    exit 1
}

Write-Host "All '$Profile' verification steps passed." -ForegroundColor Green
exit 0
