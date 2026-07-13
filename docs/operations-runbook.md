# Operations Runbook

This runbook covers the local/Compose release candidate. It is diagnostic guidance, not a production SLA or a claim of multi-host availability.

## Safety rules

- Start with read-only API, SQL, RabbitMQ and metric queries. Correlate by `traceId` or `workflowRunUuid` before taking recovery action.
- Never paste Authorization headers, tokens, passwords, API keys, full prompts, document text or model output into tickets, logs or evidence.
- Do not update `workflow_run`, `workflow_step_run`, `outbox_event`, audit or artifact terminal state manually.
- Do not delete messages, audit rows or project volumes to make a gate pass.
- `docker compose down` is the default stop. `docker compose down -v`, migration rollback, `docker system prune` and global queue purges are destructive and prohibited by the release workflow.

## Start the local observability profile

Use an environment file containing local random credentials. Do not save the expanded Compose configuration because it contains secret values.

```powershell
docker compose --env-file .env `
  -f docker-compose.yml `
  -f docker/observability/docker-compose.observability.yml `
  --profile observability config --quiet

docker compose --env-file .env `
  -f docker-compose.yml `
  -f docker/observability/docker-compose.observability.yml `
  --profile observability up -d --build
```

The application remains on its normal loopback port. The management server listens only inside the Compose network on `backend-java:8081`; Prometheus is the only host-exposed diagnostics UI and is bound to `127.0.0.1:9090`. The management surface exposes only `health` and `prometheus`; `env`, `configprops` and `shutdown` are disabled. Anonymous Prometheus access defaults to off and is enabled only by this overlay because port `8081` is not published to the host.

Safe stop:

```powershell
docker compose --env-file .env `
  -f docker-compose.yml `
  -f docker/observability/docker-compose.observability.yml `
  --profile observability down
```

## Correlation timeline

Use the persisted run as the source of truth. The following query is read-only and deliberately returns identifiers and statuses, not prompts or payload bodies:

```sql
select wr.workflow_run_uuid, wr.trace_id, wr.status, wr.attempt, wr.retry_count,
       wr.error_code, wr.created_at, wr.queued_at, wr.started_at, wr.finished_at,
       oe.event_uuid, oe.status as outbox_status, oe.publish_attempt,
       ws.step_run_uuid, ws.step_key, ws.status as step_status,
       ar.run_uuid as agent_run_uuid, ar.status as agent_status,
       aa.artifact_uuid, aa.artifact_type
from workflow_run wr
left join outbox_event oe on oe.workflow_run_id = wr.id
left join workflow_step_run ws on ws.workflow_run_id = wr.id
left join agent_run ar on ar.id = ws.agent_run_id
left join agent_artifact aa on aa.step_run_id = ws.id
where wr.workflow_run_uuid = :workflowRunUuid
order by oe.id, ws.step_order, ws.attempt;
```

Search structured logs with the returned ID. Quote the value and never search for user content:

```powershell
docker compose logs --no-color backend-java | Select-String 'traceId=<traceId>'
docker compose logs --no-color backend-java | Select-String 'workflowRunUuid=<workflowRunUuid>'
docker compose logs --no-color python-agent | Select-String 'traceId=<traceId>'
```

Expected propagation:

```text
HTTP X-Trace-Id -> Java MDC -> workflow_run/outbox trace_id
-> RabbitMQ traceId header/message -> Consumer MDC
-> Python X-Trace-Id -> Python response/log
-> WorkflowRunEvent, StepRun, AgentRun, Metric, Evaluation, Retrieval and Artifact joins
```

## Health and readiness

From inside the isolated containers:

```powershell
docker compose exec -T backend-java curl --fail --silent http://127.0.0.1:8081/actuator/health/liveness
docker compose exec -T backend-java curl --fail --silent http://127.0.0.1:8081/actuator/health/readiness
docker compose exec -T python-agent python -c "from urllib.request import urlopen; print(urlopen('http://127.0.0.1:8000/health/ready', timeout=2).read().decode())"
```

Liveness answers whether the process can serve. Readiness is the deployment gate and may be `DOWN` while the process remains live. Health responses never include component/configuration details. Use service logs and the read-only persistence queries for diagnosis after readiness fails.

## Metric dictionary

Prometheus endpoint: `http://127.0.0.1:9090`. Correlation UUIDs are intentionally absent from labels.

| Metric | Unit | Labels | Count/record timing | Denominator or interpretation |
| --- | --- | --- | --- | --- |
| `http_server_requests_seconds_*` | seconds | normalized URI, method, status, outcome | Spring MVC response completion | Request count for the same normalized URI/window |
| `gamedev_workflow_events_total` | events | bounded `event`, bounded `status` | After a unique event key is inserted | Persisted workflow transitions; duplicate event keys do not increment |
| `gamedev_workflow_messages_total` | messages | bounded `outcome` | At received/duplicate/redelivery/ACK or successful retry/DLQ hand-off boundaries | Consumer boundary events; `RECEIVED` is the denominator for outcome rates |
| `gamedev_workflow_queue_latency_seconds_*` | seconds | none | After the consumer successfully claims a run; creation-to-claim elapsed time | Successfully claimed runs only |
| `gamedev_workflow_execution_seconds_*` | seconds | bounded `outcome` | On return/throw from one runner invocation | Claimed runner invocations |
| `gamedev_workflow_failure_routes_total` | messages | bounded `destination`, bounded `error` | After RabbitTemplate accepts a retry or DLQ hand-off | Failed executions handed to retry/DLQ; compare with failed execution timer count |
| `gamedev_provider_calls_total` | calls | bounded provider, mock state, outcome | After the model-call metric is persisted | Persisted provider attempts; split mock and real |
| `gamedev_provider_latency_seconds_*` | seconds | bounded provider, mock state, outcome | When a persisted provider metric has non-negative latency | Calls with latency present; compare count with provider calls for missing samples |
| `gamedev_evaluations_total` | reports | bounded evaluator, bounded status | After an evaluation report insert succeeds | Persisted reports; group by evaluator for each stage denominator |
| `gamedev_rag_runs_total` | runs | bounded status, mock state | After the final AgentRun update | Persisted AgentRun outcomes; split mock and real |
| `gamedev_retrieval_selections_total` | chunks | bounded mock state | After a unique AgentRun/chunk selection insert | Persisted selected chunks; use RAG runs as run-level denominator |
| `gamedev_sse_connections_active_connections` | connections | none | Gauge changes on registered subscription/remove | Current active server-side subscriptions |
| `gamedev_sse_connections_total` | connections | action=`opened|closed` | Successful register and first removal | Open/close lifecycle, not delivered event count |
| `spring_rabbitmq_listener_seconds_*` | seconds | listener/result/exception | Rabbit listener completion | Consumer deliveries, including redeliveries |
| `hikaricp_connections_*` | connections | pool | Connection-pool sample | Pool saturation/availability, not business throughput |

Evaluation, RAG, retry and DLQ facts remain durable database/queue facts and must be queried with explicit denominators:

```sql
select evaluator_type, status, count(*) as reports
from evaluation_report
where evaluated_at >= :from and evaluated_at < :to
group by evaluator_type, status;

select rag_enabled, rag_status, mock_state, count(*) as agent_runs
from agent_run
where created_at >= :from and created_at < :to
group by rag_enabled, rag_status, mock_state;

select status, error_code, count(*) as runs
from workflow_run
where created_at >= :from and created_at < :to
group by status, error_code;
```

## Troubleshooting playbooks

### Run stuck in PENDING or QUEUED

1. Query the run and Outbox rows. Record `traceId`, `event_uuid`, status, `publish_attempt` and timestamps.
2. Check RabbitMQ readiness and queue depth; check backend logs for the same trace.
3. If Outbox is not `PUBLISHED`, restore the broker/publisher and allow the existing scheduler to retry. Do not create a replacement message manually.
4. If Outbox is published but the run is stale, inspect Consumer/Redis readiness and recovery audit rows before invoking the existing retry command.

### Duplicate delivery or suspected duplicate work

1. Compare message/event ID and workflow attempt in logs.
2. Count successful StepRun, AgentRun, Metric and Artifact rows per step; there must be at most one effective success fact per step attempt.
3. A duplicate delivery that only reads a terminal run and ACKs is expected. A second successful fact is a reliability failure; preserve evidence and stop release acceptance.

### Evaluation failure

1. Query `evaluation_report` by artifact and evaluator type. Record rule/schema versions and violation codes, not artifact content.
2. Confirm the artifact is not runtime eligible and that the run terminal state agrees with the evaluation gate.
3. Fix the producing workflow/provider in its owning stage; never edit the evaluation status.

### RAG empty retrieval

1. Confirm `agent_run.rag_enabled`, `rag_status`, retrieval version and mock state.
2. Count `retrieval_record` rows and verify document/chunk lifecycle separately without printing text.
3. Distinguish `DISABLED`, `EMPTY`, retrieval failure and mock fallback; do not relabel one as another.

### Provider timeout or rate limit

1. Correlate Java and Python logs by trace ID and inspect the persisted provider metric/error category.
2. Verify bounded retry count and the retry/DLQ queue facts. Do not retry manually while a scheduled retry is pending.
3. Restore the provider/fake mode, then use the existing recovery path. A false `SUCCESS` or duplicate billed call blocks release.

## Evidence and redaction

For every drill, record candidate SHA, command/exit code, timestamps, provider mode, sanitized correlation IDs, health snapshots, metric query and safe stop. Scan text evidence before Git:

```powershell
rg -n -i "authorization\s*[:=]\s*bearer|password\s*=|token\s*=|api[_-]?key\s*=" docs/reports/evidence/r7
```

If a real secret or private body appears, stop the gate, remove the public copy, rotate the credential, generate a redacted replacement and record only the affected location/type and replacement hash.
