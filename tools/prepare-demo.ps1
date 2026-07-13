param(
    [ValidateSet('offline', 'real')]
    [string]$ProviderMode = 'offline'
)

. (Join-Path $PSScriptRoot 'demo\Demo.Common.ps1')

$startedAt = [DateTime]::UtcNow
$paths = Get-DemoLocalPaths
[IO.Directory]::CreateDirectory($paths.Base) | Out-Null

if ($ProviderMode -eq 'real' -and [string]::IsNullOrWhiteSpace($env:LLM_API_KEY)) {
    throw 'REAL PROVIDER mode requires LLM_API_KEY in the current process. The key is never written to repository evidence.'
}

if (-not (Test-Path -LiteralPath $paths.Environment)) {
    $environment = @(
        'MYSQL_ROOT_PASSWORD=' + (New-DemoSecret)
        'MYSQL_DATABASE=gamedev_demo_r7'
        'DB_USERNAME=gamedev_demo_app'
        'DB_PASSWORD=' + (New-DemoSecret)
        'JWT_SECRET=' + (New-DemoSecret -Length 64)
        'JWT_EXPIRE_SECONDS=3600'
        'REDIS_PASSWORD=' + (New-DemoSecret)
        'RABBITMQ_USERNAME=r7demo'
        'RABBITMQ_PASSWORD=' + (New-DemoSecret)
        'RABBITMQ_VHOST=/'
        'PYTHON_AGENT_INTERNAL_TOKEN=' + (New-DemoSecret -Length 64)
        'LLM_API_KEY='
        'LLM_BASE_URL=' + $(if ($env:LLM_BASE_URL) { $env:LLM_BASE_URL } else { 'https://api.openai.com/v1' })
        'LLM_MODEL=' + $(if ($env:LLM_MODEL) { $env:LLM_MODEL } else { 'gpt-4o-mini' })
        'LLM_ENABLE_MOCK_FALLBACK=false'
        'MYSQL_HOST_PORT=3307'
        'PYTHON_AGENT_HOST_PORT=8000'
        'BACKEND_HOST_PORT=8080'
        'FRONTEND_HOST_PORT=5173'
    ) -join [Environment]::NewLine
    Write-Utf8NoBom -Path $paths.Environment -Content ($environment + [Environment]::NewLine)
}

$credentials = Read-DemoJson -Path $paths.Credentials
if ($null -eq $credentials) {
    $credentials = [pscustomobject]@{ namespace = $script:DemoNamespace; username = $script:DemoUsername; password = New-DemoSecret -Length 28 }
    Save-DemoJson -Path $paths.Credentials -Value $credentials
}
if ($credentials.namespace -ne $script:DemoNamespace -or $credentials.username -ne $script:DemoUsername) {
    throw 'Local demo credentials do not match the controlled namespace.'
}

$state = Read-DemoJson -Path $paths.State
if ($null -ne $state -and ($state.namespace -ne $script:DemoNamespace -or $state.composeProject -ne $script:DemoComposeProject)) {
    throw 'Local demo state does not match the controlled namespace.'
}

Write-Host "Preparing $script:DemoNamespace in explicit provider mode: $($ProviderMode.ToUpperInvariant())$(if ($ProviderMode -eq 'offline') { ' / MOCK' } else { ' PROVIDER' })."
Invoke-DemoCompose -ProviderMode $ProviderMode -Arguments @('config', '--quiet') | Out-Null
try {
    Invoke-DemoCompose -ProviderMode $ProviderMode -Arguments @('up', '-d', '--build') | Out-Null
}
catch {
    try { Invoke-DemoCompose -ProviderMode $ProviderMode -Arguments @('down', '--remove-orphans') | Out-Null } catch { }
    throw
}
Wait-DemoHttp -Uri 'http://127.0.0.1:8080/actuator/health/readiness' -TimeoutSeconds 90
Wait-DemoHttp -Uri 'http://127.0.0.1:5173/' -TimeoutSeconds 30

$loginBody = @{ username = $credentials.username; password = $credentials.password }
try {
    $login = Invoke-DemoApi -Path '/api/auth/login' -Method POST -Body $loginBody
}
catch {
    try {
        Invoke-DemoApi -Path '/api/auth/register' -Method POST -Body $loginBody | Out-Null
    }
    catch {
        throw 'The fixed demo username already exists but cannot be opened with the repository-external credential file. Refusing to overwrite it.'
    }
    $login = Invoke-DemoApi -Path '/api/auth/login' -Method POST -Body $loginBody
}
$token = $login.token

$projects = @(Invoke-DemoApi -Path '/api/projects' -Token $token)
$foreignProjects = @($projects | Where-Object { $_.description -ne $script:DemoMarker })
if ($foreignProjects.Count -gt 0) { throw 'The demo user owns a project outside the controlled marker; refusing to continue.' }
$demoProjects = @($projects | Where-Object { $_.description -eq $script:DemoMarker })
if ($demoProjects.Count -gt 1) { throw 'More than one controlled demo project exists; refusing an ambiguous seed.' }
if ($demoProjects.Count -eq 0) {
    $project = Invoke-DemoApi -Path '/api/projects' -Method POST -Token $token -Body @{
        name = 'DEMO - Crystal Relay'
        gameType = 'top_down_collect'
        targetPlatform = 'web'
        description = $script:DemoMarker
    }
}
else { $project = $demoProjects[0] }
Assert-DemoUuid -Value $project.projectUuid -Name 'project UUID'

$library = Invoke-DemoApi -Path "/api/projects/$($project.projectUuid)/knowledge-documents" -Token $token
$documents = @($library.documents | Where-Object { $_.name -eq 'r7-demo-knowledge.md' })
if ($documents.Count -gt 1) { throw 'Repeated demo fixture documents were found; refusing to add another.' }
if ($documents.Count -eq 0) {
    $upload = Send-DemoKnowledgeFile -ProjectUuid $project.projectUuid -Token $token
    $documentUuid = $upload.documentUuid
}
else { $documentUuid = $documents[0].documentUuid }
Assert-DemoUuid -Value $documentUuid -Name 'document UUID'
$document = Wait-DemoKnowledgeReady -ProjectUuid $project.projectUuid -DocumentUuid $documentUuid -Token $token

$runs = if ($null -ne $state -and $state.runs) { $state.runs } else { [pscustomobject]@{ offline = $null; real = $null } }
$state = [pscustomobject]@{
    namespace = $script:DemoNamespace
    composeProject = $script:DemoComposeProject
    fixtureVersion = $script:DemoFixtureVersion
    providerMode = $ProviderMode
    username = $script:DemoUsername
    projectUuid = $project.projectUuid
    documentUuid = $document.documentUuid
    runs = $runs
    preparedAtUtc = $null
}
# Persist the exact cleanup boundary before workflow submission so a failed prerequisite
# never strands an unscoped fixture or requires manual database intervention.
Save-DemoJson -Path $paths.State -Value $state

$modeLabel = if ($ProviderMode -eq 'offline') { 'DEMO / MOCK deterministic offline Provider' } else { 'REAL PROVIDER optional segment' }
$submission = Invoke-DemoApi -Path "/api/v1/projects/$($project.projectUuid)/workflow-runs" -Method POST -Token $token -Headers @{
    'Idempotency-Key' = "r7-demo-v1-$ProviderMode"
} -Body @{
    workflowKey = 'DEMO_GAME_CONFIG'
    idea = 'Crystal Relay: collect the relay core and signal key, avoid the patrol, then unlock the exit.'
    context = "$modeLabel; namespace r7-demo-v1; knowledge fixture r7-demo-fixture-v1."
}
$workflowRunUuid = $submission.workflowRunUuid
Assert-DemoUuid -Value $workflowRunUuid -Name 'workflow run UUID'
$run = Wait-DemoWorkflow -WorkflowRunUuid $workflowRunUuid -Token $token
if ($run.status -ne 'SUCCESS') { throw "Demo workflow ended in $($run.status); do not present it as successful." }

$runs.$ProviderMode = $workflowRunUuid
$state.runs = $runs
$state.preparedAtUtc = [DateTime]::UtcNow.ToString('o')
Save-DemoJson -Path $paths.State -Value $state

try { Set-Clipboard -Value $credentials.password } catch { }
$elapsed = [math]::Round(([DateTime]::UtcNow - $startedAt).TotalSeconds, 1)
Write-Host "Prepared in ${elapsed}s: project=$($project.projectUuid), workflowRunUuid=$workflowRunUuid."
Write-Host "Open http://127.0.0.1:5173/ and sign in as '$script:DemoUsername'. The password was copied to the clipboard when supported."
Write-Host "Provider label: $modeLabel. No token, password, API key, prompt body, or document body was printed."
