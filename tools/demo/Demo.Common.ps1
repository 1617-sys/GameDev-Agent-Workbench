Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:DemoNamespace = 'r7-demo-v1'
$script:DemoComposeProject = 'r7demo07'
$script:DemoUsername = 'r7-demo'
$script:DemoMarker = 'R7_DEMO_NAMESPACE:r7-demo-v1'
$script:DemoFixtureVersion = 'r7-demo-fixture-v1'
$script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:BaseCompose = Join-Path $script:RepoRoot 'docker-compose.yml'
$script:DemoCompose = Join-Path $script:RepoRoot 'tools\demo\docker-compose.demo.yml'
$script:DemoFixture = Join-Path $script:RepoRoot 'tools\demo\fixtures\r7-demo-knowledge.md'

function Get-DemoLocalPaths {
    $base = if ($env:LOCALAPPDATA) {
        Join-Path $env:LOCALAPPDATA 'GameDevAgentWorkbench\r7-demo-v1'
    }
    else {
        Join-Path ([IO.Path]::GetTempPath()) 'GameDevAgentWorkbench\r7-demo-v1'
    }
    [pscustomobject]@{
        Base = $base
        Environment = Join-Path $base 'compose.env'
        Credentials = Join-Path $base 'credentials.json'
        State = Join-Path $base 'state.json'
    }
}

function New-DemoSecret {
    param([int]$Length = 48)
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $Length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    -join ($bytes | ForEach-Object { $alphabet[[int]$_ % $alphabet.Length] })
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $parent = Split-Path -Parent $Path
    if ($parent) { [IO.Directory]::CreateDirectory($parent) | Out-Null }
    [IO.File]::WriteAllText($Path, $Content, (New-Object Text.UTF8Encoding($false)))
}

function Read-DemoJson {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Save-DemoJson {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)]$Value)
    Write-Utf8NoBom -Path $Path -Content (($Value | ConvertTo-Json -Depth 12) + [Environment]::NewLine)
}

function Invoke-DemoDocker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $previous }
    $lines = @($output | ForEach-Object { $_.ToString() })
    if ($exitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code ${exitCode}: $($lines -join [Environment]::NewLine)"
    }
    @($lines)
}

function Get-DemoComposeArguments {
    param([Parameter(Mandatory = $true)][ValidateSet('offline', 'real')][string]$ProviderMode)
    $paths = Get-DemoLocalPaths
    $args = @('compose', '--env-file', $paths.Environment, '-p', $script:DemoComposeProject, '-f', $script:BaseCompose)
    if ($ProviderMode -eq 'offline') { $args += @('-f', $script:DemoCompose) }
    $args
}

function Invoke-DemoCompose {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('offline', 'real')][string]$ProviderMode,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    Invoke-DemoDocker -Arguments (@(Get-DemoComposeArguments -ProviderMode $ProviderMode) + $Arguments)
}

function Invoke-DemoApi {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [ValidateSet('GET', 'POST', 'PUT')][string]$Method = 'GET',
        [object]$Body,
        [string]$Token,
        [hashtable]$Headers = @{}
    )
    $requestHeaders = @{ Accept = 'application/json' }
    if ($Token) { $requestHeaders.Authorization = "Bearer $Token" }
    foreach ($key in $Headers.Keys) { $requestHeaders[$key] = $Headers[$key] }
    $params = @{
        Uri = "http://127.0.0.1:8080$Path"
        Method = $Method
        Headers = $requestHeaders
        UseBasicParsing = $true
        TimeoutSec = 15
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    $response = Invoke-RestMethod @params
    if ($null -eq $response -or $response.code -ne 0) {
        $code = if ($null -ne $response) { $response.code } else { 'missing' }
        throw "$Method $Path returned a non-success API envelope (code $code)."
    }
    $response.data
}

function Send-DemoKnowledgeFile {
    param([string]$ProjectUuid, [string]$Token)
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object Net.Http.HttpClient
    $content = New-Object Net.Http.MultipartFormDataContent
    try {
        $client.DefaultRequestHeaders.Authorization = New-Object Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        $bytes = [IO.File]::ReadAllBytes($script:DemoFixture)
        $file = New-Object Net.Http.ByteArrayContent -ArgumentList (, $bytes)
        $file.Headers.ContentType = New-Object Net.Http.Headers.MediaTypeHeaderValue('text/markdown')
        $content.Add($file, 'file', 'r7-demo-knowledge.md')
        $response = $client.PostAsync("http://127.0.0.1:8080/api/projects/$ProjectUuid/knowledge-documents", $content).GetAwaiter().GetResult()
        $json = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) { throw "Knowledge upload failed with HTTP $([int]$response.StatusCode)." }
        $payload = $json | ConvertFrom-Json
        if ($payload.code -ne 0) { throw 'Knowledge upload returned a non-success API envelope.' }
        $payload.data
    }
    finally {
        $content.Dispose()
        $client.Dispose()
    }
}

function Wait-DemoHttp {
    param([string]$Uri, [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return }
        }
        catch { Start-Sleep -Milliseconds 500 }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Uri."
}

function Wait-DemoKnowledgeReady {
    param([string]$ProjectUuid, [string]$DocumentUuid, [string]$Token, [int]$TimeoutSeconds = 60)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $library = Invoke-DemoApi -Path "/api/projects/$ProjectUuid/knowledge-documents" -Token $Token
        $document = @($library.documents | Where-Object { $_.documentUuid -eq $DocumentUuid }) | Select-Object -First 1
        if ($document.status -eq 'READY') { return $document }
        if ($document.status -eq 'FAILED') { throw 'Demo knowledge ingestion reached FAILED.' }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Demo knowledge document did not reach READY in time.'
}

function Wait-DemoWorkflow {
    param([string]$WorkflowRunUuid, [string]$Token, [int]$TimeoutSeconds = 90)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $run = Invoke-DemoApi -Path "/api/v1/workflow-runs/$WorkflowRunUuid" -Token $Token
        if ($run.status -in @('SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELED')) { return $run }
        Start-Sleep -Milliseconds 350
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Demo workflow $WorkflowRunUuid did not reach a terminal state in time."
}

function Get-DemoContainer {
    param([Parameter(Mandatory = $true)][string]$Service)
    $ids = @(Invoke-DemoDocker -Arguments @(
        'ps', '--filter', "label=com.docker.compose.project=$script:DemoComposeProject",
        '--filter', "label=com.docker.compose.service=$Service", '--format', '{{.ID}}'
    ) | Where-Object { $_.Trim() })
    if ($ids.Count -ne 1) { throw "Expected one running $Service container in $script:DemoComposeProject; found $($ids.Count)." }
    $ids[0].Trim()
}

function Invoke-DemoMySql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    $mysql = Get-DemoContainer -Service 'mysql'
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Sql))
    $command = 'printf %s ''{0}'' | base64 -d | mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE"' -f $encoded
    @(Invoke-DemoDocker -Arguments @('exec', '-i', $mysql, 'sh', '-lc', $command) |
        ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -notmatch '^mysql: \[Warning\]' })
}

function Assert-DemoUuid {
    param([Parameter(Mandatory = $true)][string]$Value, [string]$Name = 'UUID')
    if ($Value -notmatch '^[A-Za-z0-9-]{16,80}$') { throw "Refusing unexpected $Name format." }
}
