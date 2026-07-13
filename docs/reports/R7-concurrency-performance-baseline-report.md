# R7 Concurrency and Performance Baseline Report

## Execution identity

- Run ID / evidence path: `20260713T082701Z-5603c23`, [`evidence/r7/20260713T082701Z-5603c23`](evidence/r7/20260713T082701Z-5603c23/)
- Date and timezone: 2026-07-13, UTC; execution started at `2026-07-13T08:27:03Z` and completed at `2026-07-13T08:28:12Z`.
- Branch / RC_SHA / dirty state: `r7` / `5603c238cc8dc19112a85f36d6fa4ccdd9c35a77` / clean at start.
- Operator / environment manifest / image digest: `codex-local`; [`manifest.json`](evidence/r7/20260713T082701Z-5603c23/manifest.json) records tool versions, hardware, Docker allocation and content-addressed image IDs; [`images.jsonl`](evidence/r7/20260713T082701Z-5603c23/compose/images.jsonl) contains the per-container image inventory.
- Provider mode: `fake`, fixture `r7-performance-fixed-agent-v1`, fixed success latency `300 ms`; no real Provider, paid account or Provider secret was used.

## Environment qualification

This run is **not a Reference baseline environment**:

- Host: 16 logical CPUs, 16,295,424,000 bytes memory (less than the required 16 GiB), and 97,258,455,040 bytes free disk.
- PowerShell: 5.1, below the required PowerShell 7 baseline.
- Docker Desktop: 16 logical CPUs and 7.35 GiB, not the required fixed 6 CPU / 8 GiB allocation.
- AC power and closed-background-load conditions were not operator-attested.
- The configured duration was the required 60-second warm-up plus 300-second measurement, but those phases were not entered because the prerequisite failed.

Therefore, even without the application blocker, results from this machine could only be labeled `NON-BASELINE` and could not be compared with the release threshold.

## Scenario

- Gate, fixture, command and timeout: isolated Compose project `r7perf-b6f6e4a8`; 20 users/projects; two Java Consumer processes; 300 ms fake Agent; `20` unique-key concurrency, `10` same-key concurrency, `20` query and `20` SSE connections; `1000` request hard cap and 15-minute hard deadline. Command: `./tools/run-performance-baseline.ps1`.
- Expected threshold: API HTTP 202 P95 <= 1 s, error rate <= 1%, completion P95 <= 20 s, exactly one same-key WorkflowRun and at most one successful AgentRun/Artifact per step, query/SSE P95 <= 1 s, queue drain <= 90 s, and no OOM/restart.
- Actual result: the mandatory single-request preflight returned HTTP `200`, business code `50302`, message `Workflow submission is temporarily unavailable`, in `96.73 ms`. No `workflowRunUuid` was returned, so the harness stopped before applying sustained load.
- Correlation IDs: fixture namespace `r7perf-b6f6e4a8`; `workflowRunUuid`, `traceId`, Outbox event ID and RabbitMQ message ID are `NOT CREATED`.

## Metric result matrix

| Layer / assertion | Result | Evidence or reason |
| --- | --- | --- |
| Preflight submission | **BLOCKED** | HTTP 200 / code 50302 / 96.73 ms; this single sample is not an API percentile baseline. |
| 60-second warm-up | **NOT RUN** | Preflight stopped the load before warm-up. |
| 300-second unique-key measurement | **NOT RUN** | API P50/P95/P99, completion P50/P95/P99, throughput and error rate are unavailable. |
| 10-way same-key idempotency | **NOT RUN** | No valid WorkflowRun could be created; persistent duplicate assertions were not reachable. |
| Duplicate RabbitMQ delivery | **NOT RUN** | No committed Outbox event/message existed to duplicate safely. |
| 20 query / 20 SSE connections | **NOT RUN** | No terminal WorkflowRun existed as the read target. |
| Queue peak / drain | **NOT RUN** | Sustained workflow load was never started. |
| Agent calls / Artifact / Metric duplicates | **NOT RUN** | No workflow execution or fake Agent call occurred. |
| CPU / memory | **PARTIAL, NON-BASELINE** | Startup/preflight-only samples exist in `summary.json`; sample counts of 1-2 are insufficient for performance claims. |
| Container safety | **PASS for attempted interval** | Two Consumers were present; no sampled container had OOM or restart before safe shutdown. |

No performance tuning was performed, so there is no before/after comparison and no performance claim is made.

## Evidence

- Command exit code and console artifact: [`performance.json`](evidence/r7/20260713T082701Z-5603c23/commands/performance.json), [`performance.txt`](evidence/r7/20260713T082701Z-5603c23/console/performance.txt), and [`result.json`](evidence/r7/20260713T082701Z-5603c23/result.json) record load exit `1` and cleanup exit `0`.
- Load configuration and partial raw data: [`config.json`](evidence/r7/20260713T082701Z-5603c23/performance/config.json), [`summary.json`](evidence/r7/20260713T082701Z-5603c23/performance/summary.json), and [`raw-samples.json`](evidence/r7/20260713T082701Z-5603c23/performance/raw-samples.json).
- Infrastructure: [`consumer-count.txt`](evidence/r7/20260713T082701Z-5603c23/compose/consumer-count.txt) records two queue consumers; [`ps.json`](evidence/r7/20260713T082701Z-5603c23/compose/ps.json) records container state.
- Failure attribution: [`backend-java-2.log`](evidence/r7/20260713T082701Z-5603c23/compose/backend-java-2.log) records `WorkflowRateLimit redis unavailable`; [`redis-1.log`](evidence/r7/20260713T082701Z-5603c23/compose/redis-1.log) records `Ready to accept connections tcp`.
- Recovery: [`fixture-cleanup.txt`](evidence/r7/20260713T082701Z-5603c23/console/fixture-cleanup.txt), [`second-consumer-down.txt`](evidence/r7/20260713T082701Z-5603c23/compose/second-consumer-down.txt), and [`down.txt`](evidence/r7/20260713T082701Z-5603c23/compose/down.txt) show user-prefix cleanup, stateless second-Consumer removal and safe Compose shutdown. Named volumes were preserved; no volume deletion command was used.
- Integrity and security: [`checksums.sha256`](evidence/r7/20260713T082701Z-5603c23/checksums.sha256) verifies every other retained payload. The evidence scan found no persisted Bearer/Basic authorization value or configured password/key marker.

## Supporting gate results

- `./tools/verify.ps1 -Profile integration`: passed after temporary random credentials were injected only into the process environment. The Maven/Testcontainers section reported all 3 tests skipped because the Java Testcontainers client did not recognize the Docker environment, so it is not counted as concurrency evidence.
- `./tools/verify.ps1 -Profile e2e`: failed on candidate `bd7f6397eaabd8e688b95109fc1152c868c6a760`; the first UI submission returned the same `50302`. Evidence: [`20260713T081228Z-bd7f639`](evidence/r7/20260713T081228Z-bd7f639/).
- Static validation: PowerShell parser, Node syntax, Python fixture compilation, fake-Agent contract assertion, merged Compose configuration and `git diff --check` passed for the Harness candidate.

## Conclusion

**BLOCKED**

Regression owner: **R3**. `WorkflowSubmissionGateImpl` treats its Redis rate-limit execution as unavailable even while the isolated Redis container is ready, preventing the short submit transaction and every downstream performance assertion. R7-03 does not modify the R3 idempotency, Redis, Outbox, MQ or Consumer implementation.

Required follow-up: fix and regression-test the R3 RedisTemplate/Lua submission gate, restore a qualifying Reference environment (PowerShell 7, at least 16 GiB host memory, Docker fixed to 6 CPU / 8 GiB, AC/background-load attestation), then rerun `integration`, `e2e`, and `./tools/run-performance-baseline.ps1`. Only a new clean candidate with the full warm-up and measurement may publish P50/P95/P99, throughput, error, backlog or capacity numbers.
