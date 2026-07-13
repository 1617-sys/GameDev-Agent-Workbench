param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9-]{4,20}$')]
    [string]$FixtureUsername,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9-]{2,62}$')]
    [string]$ComposeProject
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
    $normalizedOutput = @($output | ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_.ToString() }
        })
    if ($exitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code ${exitCode}: $($normalizedOutput -join [Environment]::NewLine)"
    }
    return @($normalizedOutput | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -notmatch '^mysql: \[Warning\]' })
}

function Get-ComposeContainer {
    param([Parameter(Mandatory = $true)][string]$Service)

    $ids = @(Invoke-Docker @(
        'ps', '--filter', "label=com.docker.compose.project=$ComposeProject",
        '--filter', "label=com.docker.compose.service=$Service",
        '--format', '{{.ID}}'
    ))
    if ($ids.Count -ne 1) {
        throw "Expected exactly one running '$Service' container for Compose project '$ComposeProject', found $($ids.Count)."
    }
    return $ids[0]
}

$mysql = Get-ComposeContainer -Service 'mysql'
$backend = Get-ComposeContainer -Service 'backend-java'

function Invoke-MySql {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $encodedSql = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($Sql))
    $command = 'printf %s ''{0}'' | base64 -d | mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE"' -f $encodedSql
    return Invoke-Docker @('exec', '-i', $mysql, 'sh', '-lc', $command)
}

# The username is pattern-validated above and is only interpolated as a SQL literal here.
$sqlUsername = $FixtureUsername.Replace("'", "''")
$storageReferences = Invoke-MySql @"
select storage_ref from knowledge_document kd
join game_project gp on gp.id = kd.project_id
join sys_user su on su.id = gp.user_id
where su.username like '$sqlUsername%' and kd.storage_ref is not null
union
select extracted_text_ref from knowledge_document kd
join game_project gp on gp.id = kd.project_id
join sys_user su on su.id = gp.user_id
where su.username like '$sqlUsername%' and kd.extracted_text_ref is not null;
"@

foreach ($reference in $storageReferences) {
    if ($reference -notmatch '^[A-Za-z0-9._-]+$') {
        throw "Refusing to remove unexpected knowledge storage reference format."
    }
    Invoke-Docker @('exec', $backend, 'sh', '-lc', "rm -f -- '/tmp/gamedev-knowledge/$reference'") | Out-Null
}

Invoke-MySql @"
delete rr from retrieval_record rr
join agent_run ar on ar.id = rr.agent_run_id
join sys_user su on su.id = ar.user_id
where su.username like '$sqlUsername%';

delete metric from model_call_metric metric
join agent_run ar on ar.id = metric.agent_run_id
join sys_user su on su.id = ar.user_id
where su.username like '$sqlUsername%';

delete report from evaluation_report report
join agent_artifact artifact on artifact.id = report.artifact_id
join game_project project on project.id = artifact.project_id
join sys_user su on su.id = project.user_id
where su.username like '$sqlUsername%';

delete artifact from agent_artifact artifact
join game_project project on project.id = artifact.project_id
join sys_user su on su.id = project.user_id
where su.username like '$sqlUsername%';

delete event_row from workflow_run_event event_row
join workflow_run workflow on workflow.workflow_run_uuid = event_row.workflow_run_uuid
join sys_user su on su.id = workflow.user_id
where su.username like '$sqlUsername%';

delete audit_row from workflow_recovery_audit_event audit_row
join workflow_run workflow on workflow.workflow_run_uuid = audit_row.workflow_run_uuid
join sys_user su on su.id = workflow.user_id
where su.username like '$sqlUsername%';

delete outbox from outbox_event outbox
join workflow_run workflow on workflow.id = outbox.workflow_run_id
join sys_user su on su.id = workflow.user_id
where su.username like '$sqlUsername%';

delete step_row from workflow_step_run step_row
join workflow_run workflow on workflow.id = step_row.workflow_run_id
join sys_user su on su.id = workflow.user_id
where su.username like '$sqlUsername%';

delete ar from agent_run ar
join sys_user su on su.id = ar.user_id
where su.username like '$sqlUsername%';

delete chunk_row from knowledge_chunk chunk_row
join knowledge_document document_row on document_row.id = chunk_row.document_id
join game_project project on project.id = document_row.project_id
join sys_user su on su.id = project.user_id
where su.username like '$sqlUsername%';

delete document_row from knowledge_document document_row
join game_project project on project.id = document_row.project_id
join sys_user su on su.id = project.user_id
where su.username like '$sqlUsername%';

delete workflow from workflow_run workflow
join sys_user su on su.id = workflow.user_id
where su.username like '$sqlUsername%';
delete project from game_project project
join sys_user su on su.id = project.user_id
where su.username like '$sqlUsername%';
delete from sys_user where username like '$sqlUsername%';

select count(*) from sys_user where username like '$sqlUsername%';
"@ | ForEach-Object {
    if ($_ -ne '0') {
        throw "Fixture cleanup did not remove the user-scoped R7 E2E data."
    }
}

Write-Host "Removed only the user-scoped R7 E2E fixture namespace '$FixtureUsername*'."
