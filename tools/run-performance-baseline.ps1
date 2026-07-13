param(
    [ValidateRange(0, 60)]
    [int]$WarmupSeconds = 60,

    [ValidateRange(1, 300)]
    [int]$MeasurementSeconds = 300,

    [ValidateRange(100, 1000)]
    [int]$MaxRequests = 1000,

    [ValidateRange(30, 600)]
    [int]$ReadyTimeoutSeconds = 240,

    [ValidateRange(0, 65535)]
    [int]$RabbitManagementPort = 0,

    [switch]$ReferenceEnvironmentConfirmed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$initialStatus = @(& git -C $projectRoot status --short)
$rcSha = (& git -C $projectRoot rev-parse HEAD).Trim()
$shortSha = (& git -C $projectRoot rev-parse --short HEAD).Trim()
$branch = (& git -C $projectRoot branch --show-current).Trim()
$timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$runId = "$timestamp-$shortSha"
$evidenceRoot = Join-Path $projectRoot "docs/reports/evidence/r7/$runId"
$commandDir = Join-Path $evidenceRoot 'commands'
$consoleDir = Join-Path $evidenceRoot 'console'
$composeDir = Join-Path $evidenceRoot 'compose'
$performanceDir = Join-Path $evidenceRoot 'performance'
New-Item -ItemType Directory -Force -Path $commandDir, $consoleDir, $composeDir, $performanceDir | Out-Null

$runSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$fixturePrefix = "r7perf-$runSuffix"
$composeProject = $fixturePrefix
$secondConsumerName = "$composeProject-backend-consumer-2"
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) "gamedev-r7-performance-$runSuffix"
$envFile = Join-Path $temporaryRoot 'compose.env'
$baseCompose = Join-Path $projectRoot 'docker-compose.yml'
$performanceCompose = Join-Path $PSScriptRoot 'performance/docker-compose.performance.yml'
$cleanupScript = Join-Path $PSScriptRoot 'performance/Remove-R7PerformanceFixture.ps1'
$loadClient = Join-Path $PSScriptRoot 'performance/run-load.mjs'
$composeArgs = @('compose', '--env-file', $envFile, '-p', $composeProject, '-f', $baseCompose, '-f', $performanceCompose)
$hardDeadline = (Get-Date).AddMinutes(15)
$stackStarted = $false
$secondConsumerStarted = $false
$loadExitCode = 1
$cleanupExitCode = 0
$images = @()
$nodeEnvironmentNames = @(
    'PERF_API_BASE_URL', 'PERF_AGENT_BASE_URL', 'PERF_RABBITMQ_API_BASE_URL',
    'PERF_RABBITMQ_USERNAME', 'PERF_RABBITMQ_PASSWORD', 'PERF_COMPOSE_PROJECT',
    'PERF_MYSQL_CONTAINER', 'PERF_FIXTURE_PREFIX', 'PERF_OUTPUT_DIR',
    'PERF_WARMUP_SECONDS', 'PERF_MEASUREMENT_SECONDS', 'PERF_MAX_REQUESTS',
    'PERF_REFERENCE_ELIGIBLE', 'PERF_HARD_DEADLINE_UTC'
)

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

function Write-JsonFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowNull()][object]$Value
    )

    $json = $Value | ConvertTo-Json -Depth 12
    [System.IO.File]::WriteAllText($Path, "$json$([Environment]::NewLine)", [System.Text.UTF8Encoding]::new($false))
}

function Write-TextFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )

    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $normalized = @($output | ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_.ToString() }
        })
    if ($exitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code ${exitCode}: $($normalized -join [Environment]::NewLine)"
    }
    return $normalized
}

function Invoke-NativeLogged {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogPath
    )

    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $FilePath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $normalized = @($output | ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_.ToString() }
        })
    Write-TextFile -Path $LogPath -Text (($normalized -join [Environment]::NewLine) + [Environment]::NewLine)
    $normalized | Out-Host
    return $exitCode
}

function Assert-PortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)

    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    try {
        $listener.Start()
    }
    catch {
        throw "Required loopback port $Port is already in use. Stop the conflicting process before running the isolated R7 performance stack."
    }
    finally {
        $listener.Stop()
    }
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
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
            # Bounded readiness polling is part of the 15-minute harness limit.
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Url after $TimeoutSeconds seconds."
}

function Get-BoundedTimeoutSeconds {
    param([Parameter(Mandatory = $true)][int]$RequestedSeconds)

    $remaining = [int][Math]::Floor(($hardDeadline - (Get-Date)).TotalSeconds)
    if ($remaining -le 0) {
        throw 'The 15-minute R7 performance harness deadline was reached.'
    }
    return [Math]::Min($RequestedSeconds, $remaining)
}

function Get-ComposeContainer {
    param([Parameter(Mandatory = $true)][string]$Service)

    $ids = @(Invoke-Docker @(
            'ps', '--filter', "label=com.docker.compose.project=$composeProject",
            '--filter', "label=com.docker.compose.service=$Service",
            '--format', '{{.ID}}'
        ) | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    if ($ids.Count -ne 1) {
        throw "Expected exactly one '$Service' container for Compose project '$composeProject', found $($ids.Count)."
    }
    return $ids[0]
}

function Wait-QueueConsumers {
    param(
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][int]$ManagementPort,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $encoded = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("${Username}:${Password}"))
    $headers = @{ Authorization = "Basic $encoded" }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $queue = Invoke-RestMethod -UseBasicParsing -TimeoutSec 3 -Headers $headers -Uri "http://127.0.0.1:$ManagementPort/api/queues/%2F/workflow.run.execute"
            if ([int]$queue.consumers -ge 2) {
                return [int]$queue.consumers
            }
        }
        catch {
            # The management endpoint and second consumer start asynchronously.
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for two workflow consumers after $TimeoutSeconds seconds."
}

function Get-OptionalVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        return 'MISSING'
    }
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Command @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) { return 'ERROR' }
        return (@($output | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ }) -join ' ')
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
}

function Assert-EvidenceRedacted {
    $patterns = @(
        '(?i)authorization\s*[:=]\s*(bearer|basic)\s+',
        '(?i)(mysql_root_password|db_password|jwt_secret|redis_password|rabbitmq_password)\s*=',
        '(?i)llm_api_key\s*=\s*[^\s]+'
    )
    $textExtensions = @('.json', '.txt', '.md', '.html', '.xml', '.log')
    $matches = foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -Recurse -File) {
        if ($file.Extension -notin $textExtensions -or $file.Length -gt 5MB) { continue }
        $content = [System.IO.File]::ReadAllText($file.FullName)
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) {
                $file.FullName
                break
            }
        }
    }
    if (@($matches).Count -gt 0) {
        throw "Sensitive credential marker found in performance evidence: $($matches -join ', ')"
    }
}

function Write-EvidenceChecksums {
    $lines = foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -Recurse -File | Where-Object { $_.Name -ne 'checksums.sha256' }) {
        $relativePath = $file.FullName.Substring($evidenceRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relativePath"
    }
    [System.IO.File]::WriteAllLines(
        (Join-Path $evidenceRoot 'checksums.sha256'),
        @($lines | Sort-Object),
        [System.Text.UTF8Encoding]::new($false)
    )
}

$computer = Get-CimInstance Win32_ComputerSystem
$os = Get-CimInstance Win32_OperatingSystem
$logicalCpu = [int]$computer.NumberOfLogicalProcessors
$hostMemoryBytes = [int64]$computer.TotalPhysicalMemory
$projectDrive = Get-PSDrive -Name ([System.IO.Path]::GetPathRoot($projectRoot).TrimEnd('\').TrimEnd(':'))
$freeDiskBytes = [int64]$projectDrive.Free
$dockerInfoRaw = (Invoke-Docker @('info', '--format', '{{json .}}')) -join ''
$dockerInfo = $dockerInfoRaw | ConvertFrom-Json
$dockerCpu = [int]$dockerInfo.NCPU
$dockerMemoryBytes = [int64]$dockerInfo.MemTotal
$dockerMemoryGiB = [Math]::Round($dockerMemoryBytes / 1GB, 2)
$powerShellQualified = $PSVersionTable.PSVersion.Major -ge 7
$hostQualified = $logicalCpu -ge 8 -and $hostMemoryBytes -ge 16GB -and $freeDiskBytes -ge 30GB -and $powerShellQualified
$dockerQualified = $dockerCpu -eq 6 -and $dockerMemoryBytes -ge 7.5GB -and $dockerMemoryBytes -le 8.5GB
$durationQualified = $WarmupSeconds -eq 60 -and $MeasurementSeconds -eq 300
$referenceEligible = $hostQualified -and $dockerQualified -and $durationQualified -and $ReferenceEnvironmentConfirmed.IsPresent
$effectiveRabbitManagementPort = if ($RabbitManagementPort -eq 0) { Get-FreeTcpPort } else { $RabbitManagementPort }

$manifest = [ordered]@{
    runId = $runId
    rcSha = $rcSha
    branch = $branch
    dirtyStateAtStart = @($initialStatus)
    operator = 'codex-local'
    startedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    completedAtUtc = $null
    os = "$($os.Caption) $($os.Version)"
    powershell = $PSVersionTable.PSVersion.ToString()
    git = Get-OptionalVersion -Command 'git' -Arguments @('--version')
    docker = Get-OptionalVersion -Command 'docker' -Arguments @('--version')
    compose = Get-OptionalVersion -Command 'docker' -Arguments @('compose', 'version', '--short')
    java = Get-OptionalVersion -Command 'java' -Arguments @('-version')
    python = Get-OptionalVersion -Command 'python' -Arguments @('--version')
    node = Get-OptionalVersion -Command 'node' -Arguments @('--version')
    host = [ordered]@{
        logicalCpu = $logicalCpu
        memoryBytes = $hostMemoryBytes
        freeDiskBytes = $freeDiskBytes
    }
    dockerResources = [ordered]@{
        logicalCpu = $dockerCpu
        memoryBytes = $dockerMemoryBytes
        memoryGiB = $dockerMemoryGiB
        operatingSystem = $dockerInfo.OperatingSystem
    }
    referenceQualification = [ordered]@{
        powerShell7OrLaterMet = $powerShellQualified
        hostMinimumMet = $hostQualified
        dockerFixed6Cpu8GiBMet = $dockerQualified
        standardDurationMet = $durationQualified
        acPowerAndBackgroundLoadConfirmed = $ReferenceEnvironmentConfirmed.IsPresent
        eligible = $referenceEligible
    }
    providerMode = 'fake'
    fixtureVersion = 'r7-performance-fixed-agent-v1'
    fixedAgentLatencyMs = 300
    composeProject = $composeProject
    fixtureNamespace = $fixturePrefix
    profile = 'R7-03 performance'
    images = @()
    secretsStoredIn = 'temporary local env file and process environment; excluded from evidence'
}
Write-JsonFile -Path (Join-Path $evidenceRoot 'manifest.json') -Value $manifest

Write-JsonFile -Path (Join-Path $performanceDir 'config.json') -Value ([ordered]@{
        providerMode = 'fake'
        fixtureVersion = 'r7-performance-fixed-agent-v1'
        fixedAgentLatencyMs = 300
        uniqueConcurrency = 20
        sameKeyConcurrency = 10
        queryConnections = 20
        sseConnections = 20
        consumerProcesses = 2
        warmupSeconds = $WarmupSeconds
        measurementSeconds = $MeasurementSeconds
        maxRequests = $MaxRequests
        timeoutSeconds = 900
        rateLimitPolicy = 'r7-performance-v1'
        rateLimitMaxPerUserPerMinute = 120
        backpressureMaxPendingRuns = 1000
        percentileMethod = 'nearest-rank'
        rabbitManagementPort = $effectiveRabbitManagementPort
    })

try {
    if ($initialStatus.Count -gt 0) {
        throw "Performance execution must start from a clean candidate commit. Current git status: $($initialStatus -join ', ')"
    }
    foreach ($port in @(3307, 8000, 8080, $effectiveRabbitManagementPort)) {
        Assert-PortAvailable -Port $port
    }

    New-Item -ItemType Directory -Force -Path $temporaryRoot | Out-Null
    $rabbitUsername = 'gamedev_app'
    $rabbitPassword = New-RandomSecret -Length 40
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
        'LLM_MODEL=r7-performance-fixed-agent-v1',
        'LLM_ENABLE_MOCK_FALLBACK=true',
        "RABBITMQ_USERNAME=$rabbitUsername",
        "RABBITMQ_PASSWORD=$rabbitPassword",
        'RABBITMQ_VHOST=/',
        "RABBITMQ_MANAGEMENT_HOST_PORT=$effectiveRabbitManagementPort"
    )
    [System.IO.File]::WriteAllLines($envFile, $envLines, [System.Text.UTF8Encoding]::new($false))

    $configStartedAt = (Get-Date).ToUniversalTime().ToString('o')
    Invoke-Docker -Arguments ($composeArgs + @('config', '--quiet')) | Out-Null
    Write-JsonFile -Path (Join-Path $commandDir 'compose-config.json') -Value ([ordered]@{
            command = 'docker compose --env-file <temporary> -p <run-project> -f docker-compose.yml -f tools/performance/docker-compose.performance.yml config --quiet'
            startedAtUtc = $configStartedAt
            completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
            exitCode = 0
        })
    Write-TextFile -Path (Join-Path $composeDir 'config.txt') -Text "Compose configuration validated; secret-bearing expanded output was intentionally not persisted.$([Environment]::NewLine)"

    Invoke-Docker -Arguments ($composeArgs + @('up', '-d', '--build', 'backend-java')) |
        Tee-Object -FilePath (Join-Path $composeDir 'up.txt') | Out-Null
    $stackStarted = $true
    Wait-HttpReady -Url 'http://127.0.0.1:8000/health' -TimeoutSeconds (Get-BoundedTimeoutSeconds -RequestedSeconds $ReadyTimeoutSeconds)
    Wait-HttpReady -Url 'http://127.0.0.1:8080/actuator/health' -TimeoutSeconds (Get-BoundedTimeoutSeconds -RequestedSeconds $ReadyTimeoutSeconds)

    $secondConsumerId = (Invoke-Docker -Arguments ($composeArgs + @('run', '-d', '--no-deps', '--name', $secondConsumerName, 'backend-java')) | Select-Object -Last 1).Trim()
    $secondConsumerStarted = $true
    Write-TextFile -Path (Join-Path $composeDir 'second-consumer.txt') -Text "Started a second stateless backend consumer container: $secondConsumerId$([Environment]::NewLine)"
    $consumerCount = Wait-QueueConsumers -Username $rabbitUsername -Password $rabbitPassword -ManagementPort $effectiveRabbitManagementPort -TimeoutSeconds (Get-BoundedTimeoutSeconds -RequestedSeconds $ReadyTimeoutSeconds)
    Write-TextFile -Path (Join-Path $composeDir 'consumer-count.txt') -Text "workflow.run.execute consumers=$consumerCount$([Environment]::NewLine)"

    Invoke-Docker -Arguments ($composeArgs + @('ps', '--format', 'json')) |
        Tee-Object -FilePath (Join-Path $composeDir 'ps.json') | Out-Null
    $images = @(Invoke-Docker -Arguments ($composeArgs + @('images', '--format', 'json')) |
            ForEach-Object { $_.Trim() } | Where-Object { $_ })
    Write-TextFile -Path (Join-Path $composeDir 'images.jsonl') -Text (($images -join [Environment]::NewLine) + [Environment]::NewLine)

    $mysqlContainer = Get-ComposeContainer -Service 'mysql'
    $env:PERF_API_BASE_URL = 'http://127.0.0.1:8080'
    $env:PERF_AGENT_BASE_URL = 'http://127.0.0.1:8000'
    $env:PERF_RABBITMQ_API_BASE_URL = "http://127.0.0.1:$effectiveRabbitManagementPort"
    $env:PERF_RABBITMQ_USERNAME = $rabbitUsername
    $env:PERF_RABBITMQ_PASSWORD = $rabbitPassword
    $env:PERF_COMPOSE_PROJECT = $composeProject
    $env:PERF_MYSQL_CONTAINER = $mysqlContainer
    $env:PERF_FIXTURE_PREFIX = $fixturePrefix
    $env:PERF_OUTPUT_DIR = $performanceDir
    $env:PERF_WARMUP_SECONDS = $WarmupSeconds.ToString()
    $env:PERF_MEASUREMENT_SECONDS = $MeasurementSeconds.ToString()
    $env:PERF_MAX_REQUESTS = $MaxRequests.ToString()
    $env:PERF_REFERENCE_ELIGIBLE = $referenceEligible.ToString().ToLowerInvariant()
    $env:PERF_HARD_DEADLINE_UTC = $hardDeadline.ToUniversalTime().ToString('o')

    $loadStartedAt = (Get-Date).ToUniversalTime().ToString('o')
    $loadExitCode = Invoke-NativeLogged -FilePath 'node' -Arguments @($loadClient) -LogPath (Join-Path $consoleDir 'performance.txt')
    Write-JsonFile -Path (Join-Path $commandDir 'performance.json') -Value ([ordered]@{
            command = '.\tools\run-performance-baseline.ps1'
            childCommand = 'node tools/performance/run-load.mjs'
            startedAtUtc = $loadStartedAt
            completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
            exitCode = $loadExitCode
        })
}
catch {
    Write-TextFile -Path (Join-Path $consoleDir 'harness-error.txt') -Text ($_ | Out-String)
    Write-Error "R7 performance harness failed. Evidence: $evidenceRoot`n$($_.Exception.Message)"
    $loadExitCode = 1
}
finally {
    foreach ($service in @('backend-java', 'python-agent', 'redis', 'rabbitmq')) {
        if (-not $stackStarted) { break }
        try {
            $containers = @(Invoke-Docker @(
                    'ps', '--filter', "label=com.docker.compose.project=$composeProject",
                    '--filter', "label=com.docker.compose.service=$service",
                    '--format', '{{.ID}}'
                ) | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            $index = 0
            foreach ($container in $containers) {
                $index += 1
                $log = Invoke-Docker -Arguments @('logs', '--tail', '400', $container)
                Write-TextFile -Path (Join-Path $composeDir "$service-$index.log") -Text (($log -join [Environment]::NewLine) + [Environment]::NewLine)
            }
        }
        catch {
            Write-TextFile -Path (Join-Path $composeDir "$service-log-error.txt") -Text ($_ | Out-String)
            $cleanupExitCode = 1
        }
    }

    if ($stackStarted) {
        try {
            & $cleanupScript -FixtureUsername $fixturePrefix -ComposeProject $composeProject 2>&1 |
                Tee-Object -FilePath (Join-Path $consoleDir 'fixture-cleanup.txt') | Out-Host
            if ($LASTEXITCODE -ne 0) { $cleanupExitCode = $LASTEXITCODE }
        }
        catch {
            Write-TextFile -Path (Join-Path $consoleDir 'fixture-cleanup-error.txt') -Text ($_ | Out-String)
            $cleanupExitCode = 1
        }
        if ($secondConsumerStarted) {
            try {
                $removed = Invoke-Docker -Arguments @('rm', '-f', $secondConsumerName)
                Write-TextFile -Path (Join-Path $composeDir 'second-consumer-down.txt') -Text (($removed -join [Environment]::NewLine) + [Environment]::NewLine)
            }
            catch {
                Write-TextFile -Path (Join-Path $consoleDir 'second-consumer-down-error.txt') -Text ($_ | Out-String)
                $cleanupExitCode = 1
            }
        }
        try {
            Invoke-Docker -Arguments ($composeArgs + @('down')) |
                Tee-Object -FilePath (Join-Path $composeDir 'down.txt') | Out-Null
        }
        catch {
            Write-TextFile -Path (Join-Path $consoleDir 'compose-down-error.txt') -Text ($_ | Out-String)
            $cleanupExitCode = 1
        }
    }

    foreach ($name in $nodeEnvironmentNames) {
        Remove-Item -Path "Env:$name" -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $envFile) { Remove-Item -LiteralPath $envFile -Force }
    if (Test-Path -LiteralPath $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Force }

    $manifest.completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    $manifest.images = @($images)
    Write-JsonFile -Path (Join-Path $evidenceRoot 'manifest.json') -Value $manifest
    try {
        Assert-EvidenceRedacted
    }
    catch {
        Write-TextFile -Path (Join-Path $consoleDir 'evidence-redaction-error.txt') -Text ($_ | Out-String)
        $cleanupExitCode = 1
    }
    Write-JsonFile -Path (Join-Path $evidenceRoot 'result.json') -Value ([ordered]@{
            conclusion = if ($loadExitCode -eq 0 -and $cleanupExitCode -eq 0) { 'PASS' } else { 'BLOCKED_OR_FAIL' }
            loadExitCode = $loadExitCode
            cleanupExitCode = $cleanupExitCode
            rcSha = $rcSha
            fixtureNamespace = $fixturePrefix
            nextDiagnostic = "rg -n 'WorkflowRateLimit|redis unavailable|50302' docs/reports/evidence/r7/$runId/compose/backend-java-*.log"
            completedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        })
    Write-EvidenceChecksums
}

if ($loadExitCode -ne 0 -or $cleanupExitCode -ne 0) {
    Write-Host "R7 performance baseline did not pass. RC_SHA=$rcSha Evidence=$evidenceRoot Fixture=$fixturePrefix" -ForegroundColor Red
    Write-Host "Next diagnostic: rg -n 'WorkflowRateLimit|redis unavailable|50302' '$evidenceRoot/compose/backend-java-*.log'" -ForegroundColor Yellow
    exit 1
}

Write-Host "R7 performance baseline passed. RC_SHA=$rcSha Evidence=$evidenceRoot" -ForegroundColor Green
exit 0
