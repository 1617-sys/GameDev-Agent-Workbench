. (Join-Path $PSScriptRoot 'demo\Demo.Common.ps1')
$verificationStartedAt = [DateTime]::UtcNow

$requiredBackups = @(
    (Join-Path $script:RepoRoot 'docs\demo-script.md'),
    (Join-Path $script:RepoRoot 'docs\reports\R7-demo-reproducibility-report.md')
)
foreach ($backup in $requiredBackups) {
    if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) { throw 'A required redacted demo fallback document is missing.' }
}

$paths = Get-DemoLocalPaths
$state = Read-DemoJson -Path $paths.State
$credentials = Read-DemoJson -Path $paths.Credentials
if ($null -eq $state -or $null -eq $credentials) { throw 'Run .\tools\prepare-demo.ps1 first.' }
if ($state.namespace -ne $script:DemoNamespace -or $state.composeProject -ne $script:DemoComposeProject -or $state.username -ne $script:DemoUsername) {
    throw 'Demo state failed the namespace boundary check.'
}
Assert-DemoUuid -Value $state.projectUuid -Name 'project UUID'
Assert-DemoUuid -Value $state.documentUuid -Name 'document UUID'
$workflowRunUuid = $state.runs.($state.providerMode)

$mode = $state.providerMode
$composePs = @(Invoke-DemoCompose -ProviderMode $mode -Arguments @('ps', '--format', 'json'))
if ($composePs.Count -eq 0) { throw 'No demo Compose services are running.' }
foreach ($service in @('mysql', 'redis', 'rabbitmq', 'python-agent', 'backend-java', 'frontend-vue')) {
    $container = Get-DemoContainer -Service $service
    $healthOutput = @(Invoke-DemoDocker -Arguments @('inspect', '--format', '{{.State.Health.Status}}', $container))
    $health = $healthOutput[0].Trim()
    if ($health -ne 'healthy') { throw "Demo service $service is not healthy." }
}
foreach ($service in @('mysql', 'python-agent', 'backend-java', 'frontend-vue')) {
    $container = Get-DemoContainer -Service $service
    $bindings = @(Invoke-DemoDocker -Arguments @('port', $container))
    if ($bindings.Count -eq 0 -or @($bindings | Where-Object { $_ -notmatch '-> 127\.0\.0\.1:' }).Count -gt 0) {
        throw "Demo service $service does not have a loopback-only host port binding."
    }
}
Wait-DemoHttp -Uri 'http://127.0.0.1:8080/actuator/health/readiness' -TimeoutSeconds 15
Wait-DemoHttp -Uri 'http://127.0.0.1:5173/' -TimeoutSeconds 15
Wait-DemoHttp -Uri 'http://127.0.0.1:8000/health' -TimeoutSeconds 15
$agentHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8000/health' -UseBasicParsing -TimeoutSec 5
if ($mode -eq 'offline' -and ($agentHealth.providerMode -ne 'offline-mock' -or $agentHealth.fixture -ne $script:DemoFixtureVersion)) {
    throw 'Python Agent health does not identify the expected offline mock fixture.'
}
$browserCandidates = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
)
if (@($browserCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }).Count -eq 0) {
    throw 'No supported local Chrome or Edge browser was found for the recording preflight.'
}
if ([string]::IsNullOrWhiteSpace($workflowRunUuid)) {
    throw 'Demo preparation did not complete an asynchronous workflow; verify the R3 submission prerequisite before retrying.'
}
Assert-DemoUuid -Value $workflowRunUuid -Name 'workflow run UUID'

$login = Invoke-DemoApi -Path '/api/auth/login' -Method POST -Body @{ username = $credentials.username; password = $credentials.password }
$token = $login.token
$projects = @(Invoke-DemoApi -Path '/api/projects' -Token $token)
if ($projects.Count -ne 1 -or $projects[0].projectUuid -ne $state.projectUuid -or $projects[0].description -ne $script:DemoMarker) {
    throw 'Demo user/project isolation check failed.'
}
$library = Invoke-DemoApi -Path "/api/projects/$($state.projectUuid)/knowledge-documents" -Token $token
$documents = @($library.documents | Where-Object { $_.documentUuid -eq $state.documentUuid -and $_.name -eq 'r7-demo-knowledge.md' -and $_.status -eq 'READY' })
if ($documents.Count -ne 1) { throw 'Controlled knowledge fixture is not uniquely READY.' }
$run = Invoke-DemoApi -Path "/api/v1/workflow-runs/$workflowRunUuid" -Token $token
$artifacts = @(Invoke-DemoApi -Path "/api/v1/workflow-runs/$workflowRunUuid/artifacts" -Token $token)
$rag = @(Invoke-DemoApi -Path "/api/v1/workflow-runs/$workflowRunUuid/rag-evidence" -Token $token)
if ($run.status -ne 'SUCCESS' -or @($run.steps).Count -ne 4 -or @($run.steps | Where-Object status -eq 'SUCCESS').Count -ne 4) {
    throw 'Demo workflow does not contain four successful persisted steps.'
}
$gameConfig = @($artifacts | Where-Object { $_.type -eq 'GAME_CONFIG' -and $_.status -eq 'AVAILABLE' -and $_.url -like '/demo/play*' })
if ($gameConfig.Count -ne 1) { throw 'Demo workflow does not expose exactly one available Phaser GameConfig artifact.' }
$ragOn = @($rag | Where-Object { $_.ragEnabled -eq $true -and @($_.references).Count -gt 0 })
if ($ragOn.Count -eq 0) { throw 'Demo workflow has no persisted RAG-on reference evidence.' }
if ($mode -eq 'offline' -and @($rag | Where-Object { $_.mock -ne $true }).Count -gt 0) {
    throw 'Offline evidence is not explicitly marked mock.'
}

$projectUuid = $state.projectUuid
$dbRows = @(Invoke-DemoMySql -Sql @"
select wr.workflow_run_uuid, coalesce(wr.trace_id, ''), wr.status,
       count(distinct ws.id), count(distinct metric.id), count(distinct artifact.id),
       count(distinct case when report.status = 'PASS' then report.id end),
       count(distinct case when ar.mock_state = 'TRUE' then ar.id end)
from workflow_run wr
join game_project project on project.id = wr.project_id
left join workflow_step_run ws on ws.workflow_run_id = wr.id
left join agent_run ar on ar.id = ws.agent_run_id
left join model_call_metric metric on metric.agent_run_id = ar.id
left join agent_artifact artifact on artifact.step_run_id = ws.id
left join evaluation_report report on report.artifact_id = artifact.id
where project.project_uuid = '$projectUuid' and wr.workflow_run_uuid = '$workflowRunUuid'
group by wr.id, wr.workflow_run_uuid, wr.trace_id, wr.status;
"@)
if ($dbRows.Count -ne 1) { throw 'Expected one database correlation row for the demo workflow.' }
$columns = $dbRows[0] -split "`t"
if (-not $columns[1] -or $columns[2] -ne 'SUCCESS' -or [int]$columns[3] -ne 4 -or [int]$columns[4] -ne 4 -or [int]$columns[5] -ne 4 -or [int]$columns[6] -lt 1) {
    throw 'Persisted trace/step/metric/artifact/evaluation correlation failed.'
}
if ($mode -eq 'offline' -and [int]$columns[7] -ne 4) { throw 'Offline AgentRun rows are not all persisted as mock.' }

$shortSha = (git -C $script:RepoRoot rev-parse --short=7 HEAD).Trim()
$rcSha = (git -C $script:RepoRoot rev-parse HEAD).Trim()
$branch = (git -C $script:RepoRoot branch --show-current).Trim()
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$relativeEvidence = "docs/reports/evidence/r7/$stamp-$shortSha"
$evidence = Join-Path $script:RepoRoot ($relativeEvidence -replace '/', '\')
[IO.Directory]::CreateDirectory((Join-Path $evidence 'demo')) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $evidence 'commands')) | Out-Null

$manifest = [pscustomobject]@{
    runId = "$stamp-$shortSha"
    rcSha = $rcSha
    branch = $branch
    startedAtUtc = $verificationStartedAt.ToString('o')
    finishedAtUtc = [DateTime]::UtcNow.ToString('o')
    timezone = 'UTC'
    providerMode = $(if ($mode -eq 'offline') { 'fake/offline-mock' } else { 'real' })
    fixtureVersion = $script:DemoFixtureVersion
    namespace = $script:DemoNamespace
    operator = 'redacted-local-operator'
    os = [Environment]::OSVersion.VersionString
    docker = (& docker --version)
    compose = (& docker compose version)
    containsSecrets = $false
}
$summary = [pscustomobject]@{
    conclusion = 'PASS'
    providerMode = $manifest.providerMode
    namespace = $script:DemoNamespace
    projectUuid = $state.projectUuid
    workflowRunUuid = $workflowRunUuid
    traceIdPresent = [bool]$columns[1]
    workflowStatus = $columns[2]
    successfulSteps = [int]$columns[3]
    metrics = [int]$columns[4]
    artifacts = [int]$columns[5]
    passingEvaluations = [int]$columns[6]
    mockAgentRuns = [int]$columns[7]
    ragOnStepsWithReferences = $ragOn.Count
    phaserArtifactUrl = $gameConfig[0].url
    secretFieldsRecorded = @()
}
Save-DemoJson -Path (Join-Path $evidence 'manifest.json') -Value $manifest
Save-DemoJson -Path (Join-Path $evidence 'demo\verification-summary.json') -Value $summary
Write-Utf8NoBom -Path (Join-Path $evidence 'commands\verify-demo.txt') -Content ".\tools\verify-demo.ps1`r`nexitCode=0`r`n"

$checksumLines = Get-ChildItem -LiteralPath $evidence -Recurse -File | Where-Object Name -ne 'checksums.sha256' | Sort-Object FullName | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $relative = $_.FullName.Substring($evidence.Length + 1).Replace('\', '/')
    "$hash  $relative"
}
Write-Utf8NoBom -Path (Join-Path $evidence 'checksums.sha256') -Content (($checksumLines -join [Environment]::NewLine) + [Environment]::NewLine)

Write-Host "PASS: $($mode.ToUpperInvariant())$(if ($mode -eq 'offline') { ' / MOCK' } else { ' PROVIDER' }) demo is healthy and isolated."
Write-Host "workflowRunUuid=$workflowRunUuid; traceId present; steps=4; metrics=4; artifacts=4; RAG references=$($ragOn.Count)."
Write-Host "Redacted evidence: $relativeEvidence"
