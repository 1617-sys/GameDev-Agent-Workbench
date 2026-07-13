param(
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180,

    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') exited with code $LASTEXITCODE."
    }
}

function Get-ComposeServiceHealth {
    param([Parameter(Mandatory = $true)][string]$Service)

    $containerIdOutput = & docker compose ps -q $Service
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to query Compose service '$Service'."
    }

    $containerId = ($containerIdOutput | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        return 'missing'
    }

    $stateOutput = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect Compose service '$Service'."
    }

    return ($stateOutput | Out-String).Trim()
}

function Wait-ComposeHealth {
    param([Parameter(Mandatory = $true)][string[]]$Services)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $states = @{}
        foreach ($service in $Services) {
            $states[$service] = Get-ComposeServiceHealth -Service $service
        }

        if (($states.Values | Where-Object { $_ -eq 'unhealthy' }).Count -gt 0) {
            $details = ($states.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '
            throw "Compose health check failed: $details. Run 'docker compose logs --tail 100 <service>' for the failed service."
        }

        if (($states.Values | Where-Object { $_ -ne 'healthy' }).Count -eq 0) {
            return
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    $details = ($states.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '
    throw "Timed out after $TimeoutSeconds seconds waiting for Compose health: $details."
}

function Assert-HttpOk {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$Name
    )

    try {
        $response = Invoke-WebRequest -Uri $Uri -TimeoutSec 5 -UseBasicParsing
        if ($response.StatusCode -ne 200) {
            throw "HTTP $($response.StatusCode)"
        }
    } catch {
        throw "$Name readiness endpoint failed at ${Uri}: $($_.Exception.Message)"
    }
}

function Get-HostPort {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$Default
    )

    foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env') -Encoding UTF8) {
        if ($line -match "^\s*$([regex]::Escape($Name))=(.*)$") {
            $value = $Matches[1].Trim()
            $port = 0
            if (-not [int]::TryParse($value, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
                throw "$Name must be a TCP port between 1 and 65535."
            }
            return $port
        }
    }

    return $Default
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI was not found on PATH.'
}

if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.env'))) {
    throw 'Missing .env. Run .\start-docker.ps1 so local credentials are generated safely.'
}

Invoke-Docker -Arguments @('compose', 'config', '--quiet')

if ($SkipBuild) {
    Invoke-Docker -Arguments @('compose', 'up', '-d')
} else {
    Invoke-Docker -Arguments @('compose', 'up', '-d', '--build')
}

Wait-ComposeHealth -Services @('mysql', 'redis', 'rabbitmq', 'python-agent', 'backend-java', 'frontend-vue')
$pythonAgentPort = Get-HostPort -Name 'PYTHON_AGENT_HOST_PORT' -Default 8000
$backendPort = Get-HostPort -Name 'BACKEND_HOST_PORT' -Default 8080
$frontendPort = Get-HostPort -Name 'FRONTEND_HOST_PORT' -Default 5173
Assert-HttpOk -Name 'Python Agent' -Uri "http://127.0.0.1:$pythonAgentPort/health"
Assert-HttpOk -Name 'Java dependency readiness' -Uri "http://127.0.0.1:$backendPort/actuator/health"
Assert-HttpOk -Name 'Vue frontend' -Uri "http://127.0.0.1:$frontendPort/"

Invoke-Docker -Arguments @('compose', 'ps')
Write-Host 'Fresh-environment bootstrap health gate passed.'
