# R7 Main Workflow E2E Report

## Execution identity

- Run ID / evidence path: `20260713T074703Z-cc2e31b`, [`evidence/r7/20260713T074703Z-cc2e31b`](evidence/r7/20260713T074703Z-cc2e31b/)
- Date and timezone: 2026-07-13, UTC (execution completed at `2026-07-13T07:48:06Z`)
- Branch / RC_SHA / dirty state at execution: `r7` / `cc2e31bf69a3232999a5f452dd69168bf7beec5e` / dirty only because prior, retained local R7 failure-evidence directories existed
- Environment: isolated Docker Compose project with new named volumes, MySQL, Redis, RabbitMQ, Java, Vue, and a deterministic `fake` Python Agent (`r7-e2e-fixed-agent-v1`)
- Provider mode: `fake`; no paid provider or provider secret was used

## Scenario

The harness starts an isolated Compose stack, creates a unique user/project fixture, logs in through the real Vue page, submits `DEMO_GAME_CONFIG` with an Idempotency-Key, and then intends to verify SSE, persisted run/step/agent/metric/retrieval/evaluation/artifact links, legal/illegal GameConfig, RAG-on/off, refresh, and Phaser readiness.

Required command:

```powershell
.\tools\verify.ps1 -Profile e2e
```

Actual result: the first UI submission returned HTTP 200 with business code `50302` and message `Workflow submission is temporarily unavailable`; no `workflowRunUuid` was issued. The submission part completed in 2.5 seconds, but the chain cannot meet the 120-second login-to-Phaser threshold because it stops before asynchronous creation.

Correlation IDs: fixture namespace `r7e2e-bcb3d37a`; `workflowRunUuid`, workflow trace, outbox message, StepRun, AgentRun, Metric, RetrievalRecord, EvaluationReport, and Artifact are all **NOT CREATED** because the submission gate rejected the request.

## Evidence

- [`manifest.json`](evidence/r7/20260713T074703Z-cc2e31b/manifest.json), [`result.json`](evidence/r7/20260713T074703Z-cc2e31b/result.json), and [`checksums.sha256`](evidence/r7/20260713T074703Z-cc2e31b/checksums.sha256): candidate identity, fake-provider mode, test exit `1`, cleanup exit `0`, and verified SHA-256 checksums for every other evidence payload.
- [`sanitized client trace`](evidence/r7/20260713T074703Z-cc2e31b/e2e/sanitized-client-trace.json) and [`failure screenshot`](evidence/r7/20260713T074703Z-cc2e31b/e2e/workflow-submit-1-failed.png): actual UI request result, with no token or password retained.
- [`backend-java.log`](evidence/r7/20260713T074703Z-cc2e31b/compose/backend-java.log) records `WorkflowRateLimit redis unavailable`; [`redis.log`](evidence/r7/20260713T074703Z-cc2e31b/compose/redis.log) records that Redis reached `Ready to accept connections`.
- [`playwright console`](evidence/r7/20260713T074703Z-cc2e31b/console/playwright.txt), [`Compose state`](evidence/r7/20260713T074703Z-cc2e31b/compose/ps.json), and [`safe shutdown`](evidence/r7/20260713T074703Z-cc2e31b/compose/down.txt) provide the browser, infrastructure, and cleanup trail.

Security controls used by the harness:

- Temporary per-run random Compose credentials are stored outside the repository and deleted at shutdown.
- The fake Agent is network-isolated and returns an explicit mock marker.
- Playwright raw tracing is disabled because it can contain Authorization headers; the harness writes a redacted client trace and screenshots instead.
- The evidence text scan found no Bearer authorization or configured password/key marker. Only the test-user namespace is cleaned; `docker compose down` is used without `-v`.

## Supplementary checks

- `npm run test:unit`: PASS (20 tests).
- `npm run test:e2e`: PASS (6 browser regressions; the real Compose test is intentionally skipped unless `RUN_MAIN_WORKFLOW_E2E=1`).
- `npm run test:runtime-smoke`: PASS (desktop and 375px Phaser readiness).
- `npm run build`: PASS (Vite reports the existing large-chunk warning only).

## Conclusion

**BLOCKED**

The release gate is blocked by the R3 workflow-rate-limit/Redis integration: Redis is healthy, but `WorkflowSubmissionGateImpl` translates the Redis execution failure into `50302`, preventing creation of any asynchronous workflow record. This task adds the deterministic harness and evidence/cleanup path only; it does not change the R3 business or reliability implementation.

Required follow-up: R3 must reproduce and fix the RedisTemplate/Lua rate-limit execution failure, then rerun this harness. Only after a real `202` returns may the harness assess the remaining R2/R5/R6 contract assertions for run-to-agent/metric links, runtime evaluation, Phaser artifact routing, and workflow-level RAG evidence.
