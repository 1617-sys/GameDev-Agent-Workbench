. (Join-Path $PSScriptRoot 'demo\Demo.Common.ps1')

$paths = Get-DemoLocalPaths
$state = Read-DemoJson -Path $paths.State
if ($null -eq $state) { throw 'No local demo state exists; refusing an unscoped reset.' }
if ($state.namespace -ne $script:DemoNamespace -or $state.composeProject -ne $script:DemoComposeProject -or $state.username -ne $script:DemoUsername) {
    throw 'Demo reset confirmation failed: namespace, Compose project, or username is unexpected.'
}
Assert-DemoUuid -Value $state.projectUuid -Name 'project UUID'
$projectUuid = $state.projectUuid
$mode = $state.providerMode

Write-Host "Confirmed reset target: namespace=$script:DemoNamespace, user=$script:DemoUsername, project=$projectUuid."
Write-Host 'This command is the explicit reset confirmation. It never removes Docker volumes or non-demo projects.'

$ownership = @(Invoke-DemoMySql -Sql @"
select project.project_uuid, project.description
from game_project project
join sys_user user_row on user_row.id = project.user_id
where user_row.username = '$script:DemoUsername'
order by project.id;
"@)
if ($ownership.Count -ne 1) { throw "Expected exactly one project for the demo user; found $($ownership.Count)." }
$owned = $ownership[0] -split "`t", 2
if ($owned[0] -ne $projectUuid -or $owned[1] -ne $script:DemoMarker) {
    throw 'Demo user owns data outside the exact project marker; reset refused.'
}

$storageReferences = @(Invoke-DemoMySql -Sql @"
select storage_ref from knowledge_document document_row
join game_project project on project.id = document_row.project_id
where project.project_uuid = '$projectUuid' and storage_ref is not null
union
select extracted_text_ref from knowledge_document document_row
join game_project project on project.id = document_row.project_id
where project.project_uuid = '$projectUuid' and extracted_text_ref is not null;
"@)
$backend = Get-DemoContainer -Service 'backend-java'
foreach ($reference in $storageReferences) {
    if ($reference -notmatch '^[A-Za-z0-9._-]+$') { throw 'Refusing an unexpected knowledge storage reference.' }
    Invoke-DemoDocker -Arguments @('exec', $backend, 'sh', '-lc', "rm -f -- '/tmp/gamedev-knowledge/$reference'") | Out-Null
}

$result = @(Invoke-DemoMySql -Sql @"
delete rr from retrieval_record rr join agent_run ar on ar.id = rr.agent_run_id
join game_project project on project.id = ar.project_id where project.project_uuid = '$projectUuid';
delete metric from model_call_metric metric join agent_run ar on ar.id = metric.agent_run_id
join game_project project on project.id = ar.project_id where project.project_uuid = '$projectUuid';
delete report from evaluation_report report join agent_artifact artifact on artifact.id = report.artifact_id
join game_project project on project.id = artifact.project_id where project.project_uuid = '$projectUuid';
delete artifact from agent_artifact artifact join game_project project on project.id = artifact.project_id
where project.project_uuid = '$projectUuid';
delete event_row from workflow_run_event event_row join workflow_run workflow on workflow.workflow_run_uuid = event_row.workflow_run_uuid
join game_project project on project.id = workflow.project_id where project.project_uuid = '$projectUuid';
delete audit_row from workflow_recovery_audit_event audit_row join workflow_run workflow on workflow.workflow_run_uuid = audit_row.workflow_run_uuid
join game_project project on project.id = workflow.project_id where project.project_uuid = '$projectUuid';
delete outbox from outbox_event outbox join workflow_run workflow on workflow.id = outbox.workflow_run_id
join game_project project on project.id = workflow.project_id where project.project_uuid = '$projectUuid';
delete step_row from workflow_step_run step_row join workflow_run workflow on workflow.id = step_row.workflow_run_id
join game_project project on project.id = workflow.project_id where project.project_uuid = '$projectUuid';
delete ar from agent_run ar join game_project project on project.id = ar.project_id where project.project_uuid = '$projectUuid';
delete chunk_row from knowledge_chunk chunk_row join knowledge_document document_row on document_row.id = chunk_row.document_id
join game_project project on project.id = document_row.project_id where project.project_uuid = '$projectUuid';
delete document_row from knowledge_document document_row join game_project project on project.id = document_row.project_id
where project.project_uuid = '$projectUuid';
delete workflow from workflow_run workflow join game_project project on project.id = workflow.project_id
where project.project_uuid = '$projectUuid';
delete project from game_project project join sys_user user_row on user_row.id = project.user_id
where project.project_uuid = '$projectUuid' and project.description = '$script:DemoMarker' and user_row.username = '$script:DemoUsername';
delete from sys_user where username = '$script:DemoUsername'
and not exists (select 1 from game_project project where project.user_id = sys_user.id);
select (select count(*) from game_project where project_uuid = '$projectUuid') +
       (select count(*) from sys_user where username = '$script:DemoUsername');
"@)
if ($result[-1] -ne '0') { throw 'Demo reset did not prove zero remaining project/user rows.' }

Invoke-DemoCompose -ProviderMode $mode -Arguments @('down', '--remove-orphans') | Out-Null
Remove-Item -LiteralPath $paths.State -Force
Remove-Item -LiteralPath $paths.Credentials -Force
Write-Host "PASS: removed only $script:DemoNamespace data and stopped $script:DemoComposeProject without deleting volumes."
Write-Host 'The repository-external Compose environment is retained so the preserved infrastructure volumes remain reusable; demo login credentials and state were removed.'
