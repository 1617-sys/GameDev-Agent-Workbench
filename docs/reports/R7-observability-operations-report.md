# R7 Observability and Operations Report

## Execution identity

- Run ID / evidence: `20260713T130512Z-30421db`, [`evidence/r7/20260713T130512Z-30421db`](evidence/r7/20260713T130512Z-30421db/)
- Date/timezone: 2026-07-13, Asia/Shanghai; drill window `21:05:12`–`21:28:49`
- Branch / RC SHA: `r7` / `30421db646ca95f00d03f490c1e2f9caf260518c`
- Environment: isolated Compose project `r705obs`; Java 21.0.8, Python 3.13.3, Docker 29.1.5, Prometheus 3.5.0
- Provider mode: mock fallback configured, but the workflow submission gate blocked before provider invocation

## Delivered diagnostics

- Java MDC and Python request context carry a validated `traceId`; Java also carries `workflowRunUuid`, `stepRunUuid`, `agentRunUuid` and `messageId` without putting UUIDs into metrics labels.
- Logs use stable key/value fields and omit request bodies, prompts, document content, credentials and provider raw output. Python Agent logs record only agent type and content length.
- Micrometer covers unique workflow events, consumer message outcomes, queue and execution duration, retry/DLQ hand-off, Provider calls/latency, evaluation reports, RAG outcomes, unique retrieval selections and SSE connections. Every application label is allow-listed and bounded.
- Liveness/readiness and Prometheus run on the Compose-only management port. Anonymous Prometheus access defaults off and is enabled only in the overlay where the management port is not published. `env`, `configprops` and `shutdown` remain inaccessible.
- [`../operations-runbook.md`](../operations-runbook.md) defines safe queries, metric units/labels/timing/denominators, failure playbooks and non-destructive recovery boundaries.

## Runtime evidence

| Check | Result |
| --- | --- |
| Java/Python `X-Trace-Id` response propagation | PASS; both returned `r705-observability-trace-001` |
| Java health/liveness/readiness | PASS; HTTP 200, readiness `{"status":"UP"}` |
| Python readiness | PASS; HTTP 200 |
| Prometheus scrape | PASS; target `backend-java:8081` was `up`, query `up{job="gamedev-backend"}=1` |
| Dangerous management endpoints | PASS; `env`, `configprops`, `shutdown` returned 403 |
| High-cardinality application metric labels | PASS; automated allow-list test and scrape inspection found no UUID/trace labels |
| Cleanup | PASS; `docker compose down` without `-v`, zero residual containers, temporary credential file removed |

The workflow drill correlated registration, login, project creation and the submission rejection with `traceId=r705-observability-workflow-001`. `WorkflowSubmissionGateImpl` reported Redis unavailable before a durable WorkflowRun was created. This reproduces the prerequisite R3 blocker already recorded by R7-04, so no successful, controlled-failure or recovery run could be produced honestly.

## Validation

- `mvn -Dtest=*Observability*Test,*Trace*Test,*Health*Test,*Security*Test test`: PASS, 10/10.
- `mvn test`: PASS, 136 tests, 0 failures/errors, 1 existing skip.
- `python -m pytest`: PASS, 6 tests; one upstream TestClient deprecation warning.
- `.\tools\verify.ps1 -Profile integration`: command PASS, but its 3 Testcontainers tests were skipped because Testcontainers 1.19.8 could not negotiate with Docker 29.1.5. Compose configuration validation did execute and pass.
- Isolated Compose observability drill: PASS for health, safe diagnostics and Prometheus collection.
- Final audit: PASS; zero out-of-scope paths, zero secret-pattern hits, zero UUID/trace metric labels, evidence checksums and `git diff --check` valid.
- Final image rebuild retry: infrastructure warning; Maven Central returned partial downloads. The runtime drill used the prior image containing the final health/security/Prometheus configuration; final metrics code was covered by local compilation and tests.

## Conclusion

- `BLOCKED`
- Implemented R7-05 scope is locally green and the safe diagnostics stack works in Compose.
- Release acceptance remains blocked by the R3 Redis submission gate, the skipped Testcontainers integration harness, and consequently the missing required successful/failure/recovery WorkflowRun evidence.
- Owner/follow-up: R3 must repair the Redis Lua/submission gate and update the Testcontainers/Docker API compatibility; then rerun integration plus one successful run, one controlled failure and one recovery run on the new candidate SHA. No cross-stage fix was made in this task.

This exercise demonstrates single-host diagnostic behavior only; it is not a production HA or enterprise observability claim.
