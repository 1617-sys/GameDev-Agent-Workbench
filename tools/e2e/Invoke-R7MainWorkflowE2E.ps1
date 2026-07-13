param(
    [ValidateRange(30, 600)]
    [int]$ReadyTimeoutSeconds = 240,

    [switch]$KeepFixture
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$rcSha = (& git -C $projectRoot rev-parse HEAD).Trim()
$shortSha = (& git -C $projectRoot rev-parse --short HEAD).Trim()
$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$evidenceRoot = Join-Path $projectRoot "docs/reports/evidence/r7/$timestamp-$shortSha"
$consoleDir = Join-Path $evidenceRoot 'console'
$e2eEvidenceDir = Join-Path $evidenceRoot 'e2e'
$composeEvidenceDir = Join-Path $evidenceRoot 'compose'
New-Item -ItemType Directory -Force -Path $consoleDir, $e2eEvidenceDir, $composeEvidenceDir | Out-Null

$runSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$fixtureUsername = "r7e2e-$runSuffix"
$composeProject = "r7e2e-$runSuffix"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "gamedev-r7-e2e-$runSuffix"
$envFile = Join-Path $temporaryRoot 'compose.env'
$testExitCode = 1
$stackStarted = $false

function New-RandomSecret {
    param([Parameter(Mandatory = $true)][int]$Length)

    $bytes = [byte[]]::new(48)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }
    $encoded = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    return $encoded.Substring(0, $Length)
}

function Assert-PortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)

    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try {
        $listener.Start()
    }
    catch {
        throw "Required loopback port $Port is already in use. Stop the conflicting process before running the isolated R7 E2E stack."
    }
    finally {
        $listener.Stop()
    }
}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    # Compose writes normal build progress to stderr. Capture it so StrictMode does not treat it as a PowerShell error record.
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $output | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_ }
    } | Write-Output
    if ($exitCode -ne 0) {
        throw "docker $($Arguments -join ' ') exited with code $exitCode."
    }
}

function Get-ComposeContainer {
    param([Parameter(Mandatory = $true)][string]$Service)

    $ids = @(
        & docker ps --filter "label=com.docker.compose.project=$composeProject" --filter "label=com.docker.compose.service=$Service" --format '{{.ID}}' |
            ForEach-Object { $_.ToString().Trim() } |
            Where-Object { $_ }
    )
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -or $ids.Count -ne 1) {
        throw "Expected exactly one running '$Service' container for Compose project '$composeProject'."
    }
    return $ids[0]
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 -Uri $Url
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        }
        catch {
            # Readiness is checked with a bounded poll; no arbitrary sleep is used.
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Url after $TimeoutSeconds seconds."
}

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][object]$Value
    )

    $json = $Value | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($Path, "$json$([Environment]::NewLine)", [System.Text.UTF8Encoding]::new($false))
}

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )

    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Assert-EvidenceRedacted {
    $patterns = @(
        '(?i)authorization\s*[:=]\s*bearer\s+',
        '(?i)(mysql_root_password|db_password|jwt_secret|redis_password|rabbitmq_password)\s*=',
        '(?i)llm_api_key\s*=\s*[^\s]+'
    )
    $textExtensions = @('.json', '.txt', '.md', '.html', '.xml')
    $matches = foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -Recurse -File) {
        if ($file.Extension -notin $textExtensions -or $file.Length -gt 2MB) {
            continue
        }
        $content = [System.IO.File]::ReadAllText($file.FullName)
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) {
                $file.FullName
                break
            }
        }
    }
    if (@($matches).Count -gt 0) {
        throw "Sensitive credential marker found in E2E evidence: $($matches -join ', ')"
    }
}

$baseCompose = Join-Path $projectRoot 'docker-compose.yml'
$e2eCompose = Join-Path $PSScriptRoot 'docker-compose.e2e.yml'
$cleanupScript = Join-Path $PSScriptRoot 'Remove-R7E2EFixture.ps1'
$composeArgs = @('compose', '--env-file', $envFile, '-p', $composeProject, '-f', $baseCompose, '-f', $e2eCompose)

try {
    Assert-PortAvailable -Port 8080
    Assert-PortAvailable -Port 5173
    Assert-PortAvailable -Port 8000
    Assert-PortAvailable -Port 3307

    New-Item -ItemType Directory -Force -Path $temporaryRoot | Out-Null
    $envLines = @(
        "MYSQL_ROOT_PASSWORD=$(New-RandomSecret -Length 48)",
        'MYSQL_DATABASE=gamedev_agent_workbench',
        'DB_USERNAME=gamedev_app',
        "DB_PASSWORD=$(New-RandomSecret -Length 40)",
        "JWT_SECRET=$(New-RandomSecret -Length 48)",
        'JWT_EXPIRE_SECONDS=3600',
        "REDIS_PASSWORD=$(New-RandomSecret -Length 40)",
        'LLM_API_KEY=',
        'LLM_BASE_URL=http://fixture.invalid',
        'LLM_MODEL=r7-e2e-fixed-agent-v1',
        'LLM_ENABLE_MOCK_FALLBACK=true',
        'RABBITMQ_USERNAME=gamedev_app',
        "RABBITMQ_PASSWORD=$(New-RandomSecret -Length 40)",
        'RABBITMQ_VHOST=/'
    )
    [System.IO.File]::WriteAllLines($envFile, $envLines, [System.Text.UTF8Encoding]::new($false))

    Write-JsonFile -Path (Join-Path $evidenceRoot 'manifest.json') -Value ([ordered]@{
            runId           = "$timestamp-$shortSha"
            rcSha           = $rcSha
            branch          = (& git -C $projectRoot branch --show-current).Trim()
            startedAtUtc    = (Get-Date).ToUniversalTime().ToString('o')
            providerMode    = 'fake'
            fixtureVersion  = 'r7-e2e-fixed-agent-v1'
            composeProject  = $composeProject
            apiBaseUrl      = 'http://127.0.0.1:8080'
            frontendBaseUrl = 'http://127.0.0.1:5173'
            secretsStoredIn = 'temporary local env file; excluded from evidence'
        })

    Invoke-Docker -Arguments ($composeArgs + @('config', '--quiet')) 2>&1 |
        Tee-Object -FilePath (Join-Path $composeEvidenceDir 'config.txt') | Out-Null
    Invoke-Docker -Arguments ($composeArgs + @('up', '-d', '--build')) 2>&1 |
        Tee-Object -FilePath (Join-Path $composeEvidenceDir 'up.txt') | Out-Null
    $stackStarted = $true

    Wait-HttpReady -Url 'http://127.0.0.1:8000/health' -TimeoutSeconds $ReadyTimeoutSeconds
    Wait-HttpReady -Url 'http://127.0.0.1:8080/actuator/health' -TimeoutSeconds $ReadyTimeoutSeconds
    Wait-HttpReady -Url 'http://127.0.0.1:5173/' -TimeoutSeconds $ReadyTimeoutSeconds
    Invoke-Docker -Arguments ($composeArgs + @('ps', '--format', 'json')) 2>&1 |
        Tee-Object -FilePath (Join-Path $composeEvidenceDir 'ps.json') | Out-Null

    $env:RUN_MAIN_WORKFLOW_E2E = '1'
    $env:E2E_FIXTURE_USERNAME = $fixtureUsername
    $env:E2E_API_BASE_URL = 'http://127.0.0.1:8080'
    $env:E2E_FRONTEND_BASE_URL = 'http://127.0.0.1:5173'
    $env:E2E_MYSQL_CONTAINER = Get-ComposeContainer -Service 'mysql'
    $env:E2E_EVIDENCE_DIR = $e2eEvidenceDir
    $env:PLAYWRIGHT_OUTPUT_DIR = (Join-Path $evidenceRoot 'traces')
    $env:PLAYWRIGHT_HTML_REPORT_DIR = (Join-Path $evidenceRoot 'playwright-report')
    $env:CI = '1'

    Push-Location -LiteralPath (Join-Path $projectRoot 'frontend-vue')
    try {
        & npm run test:e2e:main 2>&1 | Tee-Object -FilePath (Join-Path $consoleDir 'playwright.txt') | Out-Host
        $testExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    Assert-EvidenceRedacted
}
catch {
    Write-TextFile -Path (Join-Path $consoleDir 'harness-error.txt') -Text ($_ | Out-String)
    Write-Error "R7 E2E harness failed. Evidence: $evidenceRoot`n$($_.Exception.Message)"
    $testExitCode = 1
}
finally {
    $cleanupExitCode = 0
    if ($stackStarted -and -not $KeepFixture) {
        try {
            & $cleanupScript -FixtureUsername $fixtureUsername -ComposeProject $composeProject 2>&1 |
                Tee-Object -FilePath (Join-Path $consoleDir 'fixture-cleanup.txt') | Out-Host
            if ($LASTEXITCODE -ne 0) {
                $cleanupExitCode = $LASTEXITCODE
            }
        }
        catch {
            Write-TextFile -Path (Join-Path $consoleDir 'fixture-cleanup-error.txt') -Text ($_ | Out-String)
            Write-Error "Fixture cleanup failed. Evidence: $evidenceRoot`n$($_.Exception.Message)"
            $cleanupExitCode = 1
        }
    }
    if ($stackStarted) {
        try {
            Invoke-Docker -Arguments ($composeArgs + @('down')) 2>&1 |
                Tee-Object -FilePath (Join-Path $composeEvidenceDir 'down.txt') | Out-Null
        }
        catch {
            Write-TextFile -Path (Join-Path $consoleDir 'compose-down-error.txt') -Text ($_ | Out-String)
            Write-Error "Compose shutdown failed. Evidence: $evidenceRoot`n$($_.Exception.Message)"
            $cleanupExitCode = 1
        }
    }
    if (Test-Path -LiteralPath $envFile) {
        Remove-Item -LiteralPath $envFile -Force
    }
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Force
    }
    Write-JsonFile -Path (Join-Path $evidenceRoot 'result.json') -Value ([ordered]@{
            conclusion      = if ($testExitCode -eq 0 -and $cleanupExitCode -eq 0) { 'PASS' } else { 'FAIL_OR_BLOCKED' }
            testExitCode    = $testExitCode
            cleanupExitCode = $cleanupExitCode
            completedAtUtc  = (Get-Date).ToUniversalTime().ToString('o')
        })
}

if ($testExitCode -ne 0 -or $cleanupExitCode -ne 0) {
    Write-Host "R7 main workflow E2E did not pass. Evidence: $evidenceRoot" -ForegroundColor Red
    exit 1
}

Write-Host "R7 main workflow E2E passed. Evidence: $evidenceRoot" -ForegroundColor Green
exit 0
