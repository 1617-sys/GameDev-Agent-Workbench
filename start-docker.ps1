param(
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180,
    [switch]$InfrastructureOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSCommandPath
Set-Location -LiteralPath $projectRoot

function New-RandomSecret {
    param([Parameter(Mandatory = $true)][int]$Length)

    # GetBytes works in both Windows PowerShell 5.1 and PowerShell 7.
    $bytes = New-Object byte[] 48
    $randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomNumberGenerator.GetBytes($bytes)
    }
    finally {
        $randomNumberGenerator.Dispose()
    }
    $encoded = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    return $encoded.Substring(0, $Length)
}

function Get-DotEnvValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env') -Encoding UTF8) {
        if ($line -match "^\s*$([regex]::Escape($Name))=(.*)$") {
            return $Matches[1].Trim()
        }
    }

    return $null
}

function Assert-StrongSecret {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$MinimumLength
    )

    $value = Get-DotEnvValue -Name $Name
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt $MinimumLength -or
        $value -match '(?i)replace|change[_-]?me|your[_-]?|password|secret|123456') {
        throw "$Name must be a non-placeholder local secret of at least $MinimumLength characters. Regenerate or rotate it without committing .env."
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $projectRoot '.env'))) {
    $envLines = @(
        "MYSQL_ROOT_PASSWORD=$(New-RandomSecret -Length 48)",
        'MYSQL_DATABASE=gamedev_agent_workbench',
        'DB_USERNAME=gamedev_app',
        "DB_PASSWORD=$(New-RandomSecret -Length 40)",
        "JWT_SECRET=$(New-RandomSecret -Length 48)",
        'JWT_EXPIRE_SECONDS=3600',
        "REDIS_PASSWORD=$(New-RandomSecret -Length 40)",
        'PYTHON_AGENT_BASE_URL=http://python-agent:8000',
        "PYTHON_AGENT_INTERNAL_TOKEN=$(New-RandomSecret -Length 48)",
        "SIMULATION_SERVICE_INTERNAL_TOKEN=$(New-RandomSecret -Length 48)",
        'SIMULATION_SESSION_TTL_MS=300000',
        'SIMULATION_MAX_SESSIONS=100',
        'GAME_BUILD_BASE_URL=http://localhost:5173',
        'LLM_API_KEY=',
        'LLM_BASE_URL=https://api.deepseek.com',
        'LLM_MODEL=deepseek-chat',
        'LLM_ENABLE_MOCK_FALLBACK=true',
        'RABBITMQ_USERNAME=gamedev_app',
        "RABBITMQ_PASSWORD=$(New-RandomSecret -Length 40)",
        'RABBITMQ_VHOST=/'
    )
    # WriteAllLines keeps the generated .env UTF-8 without a BOM in both PowerShell 5.1 and 7.
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines((Join-Path $projectRoot '.env'), [string[]]$envLines, $utf8WithoutBom)
    Write-Host 'Created .env with generated local credentials and deterministic mock fallback.'
    Write-Host 'No secret values were printed. Keep .env local and out of version control.'
}

# Upgrade older local .env files without revealing generated credentials.
if ([string]::IsNullOrWhiteSpace((Get-DotEnvValue -Name 'SIMULATION_SERVICE_INTERNAL_TOKEN'))) {
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::AppendAllText(
        (Join-Path $projectRoot '.env'),
        "`nSIMULATION_SERVICE_INTERNAL_TOKEN=$(New-RandomSecret -Length 48)`nSIMULATION_SESSION_TTL_MS=300000`nSIMULATION_MAX_SESSIONS=100`n",
        $utf8WithoutBom
    )
    Write-Host 'Upgraded .env with missing Simulation Service settings.'
}

Assert-StrongSecret -Name 'MYSQL_ROOT_PASSWORD' -MinimumLength 24
Assert-StrongSecret -Name 'DB_PASSWORD' -MinimumLength 24
Assert-StrongSecret -Name 'JWT_SECRET' -MinimumLength 32
Assert-StrongSecret -Name 'REDIS_PASSWORD' -MinimumLength 24
Assert-StrongSecret -Name 'RABBITMQ_PASSWORD' -MinimumLength 24
Assert-StrongSecret -Name 'PYTHON_AGENT_INTERNAL_TOKEN' -MinimumLength 32
Assert-StrongSecret -Name 'SIMULATION_SERVICE_INTERNAL_TOKEN' -MinimumLength 32

$databaseUser = Get-DotEnvValue -Name 'DB_USERNAME'
if ([string]::IsNullOrWhiteSpace($databaseUser) -or $databaseUser -eq 'root' -or $databaseUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'DB_USERNAME must be a non-root identifier containing only letters, digits, and underscores.'
}

$rabbitUser = Get-DotEnvValue -Name 'RABBITMQ_USERNAME'
if ([string]::IsNullOrWhiteSpace($rabbitUser) -or $rabbitUser -eq 'guest') {
    throw 'RABBITMQ_USERNAME must not be empty or the default guest account.'
}

$mockFallback = Get-DotEnvValue -Name 'LLM_ENABLE_MOCK_FALLBACK'
if ($mockFallback -notin @('true', 'false')) {
    throw 'LLM_ENABLE_MOCK_FALLBACK must be true or false.'
}

if ($InfrastructureOnly) {
    & docker compose up -d mysql mysql-bootstrap rabbitmq redis simulation-service
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & docker compose wait mysql-bootstrap
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & docker compose up -d --wait --wait-timeout $TimeoutSeconds mysql rabbitmq redis simulation-service python-agent
    exit $LASTEXITCODE
}

& (Join-Path $projectRoot 'tools\verify-bootstrap.ps1') -TimeoutSeconds $TimeoutSeconds
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ''
Write-Host 'Bootstrap completed. Default mode is mock/fake unless a real local Provider key was explicitly configured.'
