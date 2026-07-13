# R7 Fault Injection and Recovery Report

## Execution identity

- Run ID / evidence path: `20260713T085206Z-aa48cf8`, [`evidence/r7/20260713T085206Z-aa48cf8`](evidence/r7/20260713T085206Z-aa48cf8/)
- Date and timezone: 2026-07-13, UTC; completed at `2026-07-13T08:53:22Z`
- Branch / RC_SHA / dirty state: `r7` / `aa48cf8408594f70c61c878071b8663532fe66ec` / dirty with this R7-04 implementation and retained earlier failed-run evidence
- Operator / environment manifest / image digest: local Codex operator; isolated Docker Compose project `r7fault-aed1b277`; manifest recorded; image digest `MISSING` (the driver did not persist `docker inspect` digest evidence)
- Provider mode: `fake` (`r7-fault-fixed-agent-v1`)

## Scenario

- Gate: Redis unavailable and owner-token lock behavior; RabbitMQ unavailable and Outbox recovery; Python 429/invalid output; Consumer restart; SSE snapshot after recovery.
- Controlled command and timeout: `./tools/run-fault-injection.ps1`; readiness is bounded to 240 seconds, each in-matrix wait is bounded, and the driver always restores paused services before safe shutdown.
- Expected threshold: no false success, no falsely published Outbox event, fail-closed Redis behavior, bounded retry evidence, and no duplicate successful AgentRun/Metric/Artifact facts.
- MySQL transient failure: `NOT RUN` until a bounded connection proxy exists; directly pausing MySQL can strand pooled calls beyond the five-minute scenario contract.
- Correlation IDs: fixture namespace `r7fault-aed1b277`; downstream workflow/trace/event/message IDs were not created in blocked scenarios because the submission gate rejected them before durable creation

## Evidence

- Command exit code and console artifact: exit `1`; [`fault-matrix.txt`](evidence/r7/20260713T085206Z-aa48cf8/console/fault-matrix.txt)
- API/DB/UI/queue/metric/log evidence (redacted): [`fault-matrix.json`](evidence/r7/20260713T085206Z-aa48cf8/fault/fault-matrix.json), [`manifest.json`](evidence/r7/20260713T085206Z-aa48cf8/manifest.json), Compose service logs under [`compose/`](evidence/r7/20260713T085206Z-aa48cf8/compose/), and verified [`checksums.sha256`](evidence/r7/20260713T085206Z-aa48cf8/checksums.sha256)
- Failure timeline, impact, recovery/rollback action: Redis was paused at `08:52:57Z`; the client timed out, Redis was restored at `08:53:10Z`, and no WorkflowRun was persisted. Owner-token/TTL checks completed at `08:53:14Z`. RabbitMQ was paused and restored at `08:53:15Z`; submission returned business code `50302` before creating an Outbox event. Python and Consumer scenarios then received the same pre-execution rejection. Cleanup completed at `08:53:22Z` with exit `0`.

Scenario results:

| Scenario | Result | Persisted fact |
| --- | --- | --- |
| Redis unavailable | PASS | Request did not become a successful submission and matching persisted WorkflowRun count remained `0` |
| Redis wrong owner / TTL | PASS | Wrong-owner delete count `0`; owner remained `owner-a`; key existence after TTL `0` |
| RabbitMQ unavailable / Outbox recovery | BLOCKED | Submission returned HTTP 200/business `50302`; no run or Outbox correlation ID was created |
| Python 429 | BLOCKED | Submission gate rejected the fixture before Agent invocation |
| Python invalid output | BLOCKED | Submission gate rejected the fixture before Agent invocation |
| Consumer restart / duplicate success | BLOCKED | Submission gate rejected the fixture before Consumer delivery |
| SSE recovery snapshot | BLOCKED | No terminal recovered run existed to subscribe to |
| MySQL transient failure | NOT RUN | No bounded connection proxy exists; directly pausing MySQL risks exceeding the five-minute safety boundary |

The harness uses a unique Compose project and fixture namespace, creates per-run random credentials outside the repository, controls the fake Agent through a container-local file rather than a network debug endpoint, and runs `docker compose down` without `-v`. Its `finally` path restores Redis and RabbitMQ, resets the fake Agent to `normal`, removes the temporary credential file, scans text evidence for credential markers, and records checksums.

## Conclusion

- `BLOCKED`
- Regression owner: R3 for the Redis submission-gate/Lua integration. Further R3 verification is required for the fixed 30-second/5-minute/30-minute retry topology against R7-04's five-minute per-scenario limit. R2 owns the missing bounded Python HTTP timeout.
- Follow-up commit and gates rerun: no cross-boundary fix was made. After the R3/R2 owners provide minimal regression fixes, rerun `./tools/verify.ps1 -Profile integration`, `./tools/run-fault-injection.ps1`, and `./tools/verify.ps1 -Profile e2e` on the new candidate SHA.

This single-host Compose exercise is a controlled release gate, not a claim of multi-host or production high availability.
