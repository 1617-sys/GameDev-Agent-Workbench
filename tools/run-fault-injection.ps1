param(
    [ValidateRange(60, 600)]
    [int]$ReadyTimeoutSeconds = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$rcSha = (& git -C $projectRoot rev-parse HEAD).Trim()
$shortSha = (& git -C $projectRoot rev-parse --short HEAD).Trim()
$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$runId = "$timestamp-$shortSha"
$evidenceRoot = Join-Path $projectRoot "docs/reports/evidence/r7/$runId"
$faultEvidenceDir = Join-Path $evidenceRoot 'fault'
$composeEvidenceDir = Join-Path $evidenceRoot 'compose'
$consoleDir = Join-Path $evidenceRoot 'console'
New-Item -ItemType Directory -Force -Path $faultEvidenceDir, $composeEvidenceDir, $consoleDir | Out-Null

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$composeProject = "r7fault-$suffix"
$fixtureUsername = "r7fault-$suffix"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "gamedev-r7-fault-$suffix"
$envFile = Join-Path $temporaryRoot 'compose.env'
$stackStarted = $false
$matrixExitCode = 1
$cleanupExitCode = 1
$startedAt = (Get-Date).ToUniversalTime()

function New-RandomSecret {
    param([Parameter(Mandatory = $true)][int]$Length)

    $bytes = [byte[]]::new(48)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $encoded = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    return $encoded.Substring(0, $Length)
}

function Write-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][object]$Value)
    [System.IO.File]::WriteAllText(
        $Path,
        "$(ConvertTo-Json $Value -Depth 10)$([Environment]::NewLine)",
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Write-TextFile {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Text)
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Get-AvailablePort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally { $listener.Stop() }
}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments, [switch]$AllowFailure)

    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    $text = @($output | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_.ToString() } })
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "docker $($Arguments -join ' ') exited with code $exitCode.`n$($text -join [Environment]::NewLine)"
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = $text }
}

function Get-IsolatedContainer {
    param([Parameter(Mandatory = $true)][string]$Service)

    $ids = @(& docker ps -a --filter "label=com.docker.compose.project=$composeProject" --filter "label=com.docker.compose.service=$Service" --format '{{.ID}}')
    if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1 -or [string]::IsNullOrWhiteSpace($ids[0])) {
        throw "Expected exactly one '$Service' container owned by isolated Compose project '$composeProject'."
    }
    return $ids[0].Trim()
}

function Wait-HttpReady {
    param([Parameter(Mandatory = $true)][string]$Url, [Parameter(Mandatory = $true)][int]$TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 -Uri $Url
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Url after $TimeoutSeconds seconds."
}

function Restore-IsolatedServices {
    foreach ($service in @('redis', 'rabbitmq')) {
        try {
            $container = Get-IsolatedContainer -Service $service
            Invoke-Docker -Arguments @('unpause', $container) -AllowFailure | Out-Null
            Invoke-Docker -Arguments @('start', $container) -AllowFailure | Out-Null
        } catch { }
    }
    try {
        $python = Get-IsolatedContainer -Service 'python-agent'
        Invoke-Docker -Arguments @('exec', $python, 'sh', '-lc', "printf '%s\n' normal > /tmp/r7-fault-mode") -AllowFailure | Out-Null
    } catch { }
}

function Assert-EvidenceRedacted {
    $patterns = @(
        '(?i)authorization\s*[:=]\s*bearer\s+',
        '(?i)(mysql_root_password|db_password|jwt_secret|redis_password|rabbitmq_password)\s*=',
        '(?i)llm_api_key\s*=\s*[^\s]+'
    )
    $matches = foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -Recurse -File) {
        if ($file.Extension -notin @('.json', '.txt', '.md', '.log') -or $file.Length -gt 2MB) { continue }
        $content = [System.IO.File]::ReadAllText($file.FullName)
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) { $file.FullName; break }
        }
    }
    if (@($matches).Count -gt 0) { throw "Sensitive credential marker found in fault evidence: $($matches -join ', ')" }
}

function Write-EvidenceChecksums {
    $lines = foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -Recurse -File | Where-Object Name -ne 'checksums.sha256') {
        $relative = $file.FullName.Substring($evidenceRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relative"
    }
    [System.IO.File]::WriteAllLines((Join-Path $evidenceRoot 'checksums.sha256'), @($lines | Sort-Object), [System.Text.UTF8Encoding]::new($false))
}

$baseCompose = Join-Path $projectRoot 'docker-compose.yml'
$faultCompose = Join-Path $projectRoot 'tools/fault/docker-compose.fault.yml'
$composeArgs = @('compose', '--env-file', $envFile, '-p', $composeProject, '-f', $baseCompose, '-f', $faultCompose)
$mysqlPort = Get-AvailablePort
$pythonPort = Get-AvailablePort
$backendPort = Get-AvailablePort
$frontendPort = Get-AvailablePort

try {
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
        'LLM_MODEL=r7-fault-fixed-agent-v1',
        'LLM_ENABLE_MOCK_FALLBACK=true',
        'RABBITMQ_USERNAME=gamedev_app',
        "RABBITMQ_PASSWORD=$(New-RandomSecret -Length 40)",
        'RABBITMQ_VHOST=/',
        "MYSQL_HOST_PORT=$mysqlPort",
        "PYTHON_AGENT_HOST_PORT=$pythonPort",
        "BACKEND_HOST_PORT=$backendPort",
        "FRONTEND_HOST_PORT=$frontendPort"
    )
    [System.IO.File]::WriteAllLines($envFile, $envLines, [System.Text.UTF8Encoding]::new($false))

    Write-JsonFile -Path (Join-Path $evidenceRoot 'manifest.json') -Value ([ordered]@{
        runId = $runId; rcSha = $rcSha; branch = (& git -C $projectRoot branch --show-current).Trim()
        startedAtUtc = $startedAt.ToString('o'); providerMode = 'fake'; fixtureVersion = 'r7-fault-fixed-agent-v1'
        composeProject = $composeProject; fixtureNamespace = $fixtureUsername
        isolation = 'unique Compose project labels and per-run fixture namespace'
        secretsStoredIn = 'temporary local env file; excluded from evidence and deleted during cleanup'
    })

    $config = Invoke-Docker -Arguments ($composeArgs + @('config', '--quiet'))
    Write-TextFile -Path (Join-Path $composeEvidenceDir 'config.txt') -Text "Compose configuration validated; expanded secret-bearing output was not persisted.`nexitCode=$($config.ExitCode)`n"
    $up = Invoke-Docker -Arguments ($composeArgs + @('up', '-d', '--build'))
    Write-TextFile -Path (Join-Path $composeEvidenceDir 'up.txt') -Text (($up.Output -join [Environment]::NewLine) + [Environment]::NewLine)
    $stackStarted = $true

    Wait-HttpReady -Url "http://127.0.0.1:$pythonPort/health" -TimeoutSeconds $ReadyTimeoutSeconds
    Wait-HttpReady -Url "http://127.0.0.1:$backendPort/actuator/health" -TimeoutSeconds $ReadyTimeoutSeconds

    $ps = Invoke-Docker -Arguments ($composeArgs + @('ps', '--format', 'json'))
    Write-TextFile -Path (Join-Path $composeEvidenceDir 'ps.json') -Text (($ps.Output -join [Environment]::NewLine) + [Environment]::NewLine)

    $env:FAULT_API_BASE_URL = "http://127.0.0.1:$backendPort"
    $env:FAULT_EVIDENCE_DIR = $faultEvidenceDir
    $env:FAULT_FIXTURE_USERNAME = $fixtureUsername
    $env:FAULT_MYSQL_CONTAINER = Get-IsolatedContainer -Service 'mysql'
    $env:FAULT_REDIS_CONTAINER = Get-IsolatedContainer -Service 'redis'
    $env:FAULT_RABBITMQ_CONTAINER = Get-IsolatedContainer -Service 'rabbitmq'
    $env:FAULT_PYTHON_CONTAINER = Get-IsolatedContainer -Service 'python-agent'
    $env:FAULT_BACKEND_CONTAINER = Get-IsolatedContainer -Service 'backend-java'

    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $nodeOutput = & node (Join-Path $projectRoot 'tools/fault/run-fault-matrix.mjs') 2>&1
        $matrixExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    $nodeText = @($nodeOutput | ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_.ToString() } })
    Write-TextFile -Path (Join-Path $consoleDir 'fault-matrix.txt') -Text (($nodeText -join [Environment]::NewLine) + [Environment]::NewLine)
    $nodeText | ForEach-Object { Write-Host $_ }
} catch {
    Write-TextFile -Path (Join-Path $consoleDir 'harness-error.txt') -Text "$($_.Exception.Message)`n"
    Write-Error $_ -ErrorAction Continue
} finally {
    Restore-IsolatedServices
    if ($stackStarted) {
        foreach ($service in @('backend-java', 'python-agent', 'redis', 'rabbitmq', 'mysql')) {
            try {
                $logs = Invoke-Docker -Arguments ($composeArgs + @('logs', '--no-color', '--tail', '300', $service)) -AllowFailure
                Write-TextFile -Path (Join-Path $composeEvidenceDir "$service.log") -Text (($logs.Output -join [Environment]::NewLine) + [Environment]::NewLine)
            } catch { }
        }
        $down = Invoke-Docker -Arguments ($composeArgs + @('down', '--remove-orphans')) -AllowFailure
        $cleanupExitCode = $down.ExitCode
        Write-TextFile -Path (Join-Path $composeEvidenceDir 'down.txt') -Text (($down.Output -join [Environment]::NewLine) + "`nexitCode=$cleanupExitCode`nvolumesPreserved=true`n")
    }
    if (Test-Path -LiteralPath $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Recurse -Force }
    Get-ChildItem Env:FAULT_* -ErrorAction SilentlyContinue | ForEach-Object { Remove-Item "Env:$($_.Name)" }

    Write-JsonFile -Path (Join-Path $evidenceRoot 'result.json') -Value ([ordered]@{
        rcSha = $rcSha; completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        matrixExitCode = $matrixExitCode; cleanupExitCode = $cleanupExitCode
        safeStop = 'all paused services restored; fake mode normal; docker compose down without -v'
        result = if ($matrixExitCode -eq 0 -and $cleanupExitCode -eq 0) { 'PASS' } else { 'FAIL' }
    })
    try { Assert-EvidenceRedacted } catch { $matrixExitCode = 1; Write-Error $_ -ErrorAction Continue }
    Write-EvidenceChecksums
}

Write-Host "Fault evidence: $evidenceRoot"
if ($matrixExitCode -ne 0 -or $cleanupExitCode -ne 0) { exit 1 }
exit 0
