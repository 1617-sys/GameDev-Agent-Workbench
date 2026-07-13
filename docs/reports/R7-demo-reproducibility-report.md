# R7 demo reproducibility report

## Execution identity

- Run ID / evidence path: `20260713T152416Z-7ae1072`, [`evidence/r7/20260713T152416Z-7ae1072`](evidence/r7/20260713T152416Z-7ae1072/)
- Date and timezone: 2026-07-13, Asia/Shanghai
- Branch / RC_SHA / dirty state: `r7` / `7ae10724c511d18ec8f9de828da4088df9026266` / dirty with only the R7-07 implementation under review
- Operator / environment manifest / image digest: local operator / [`manifest.json`](evidence/r7/20260713T152416Z-7ae1072/manifest.json) / locally built Compose images (digest not used as a release claim)
- Provider mode: `fake/offline-mock`, fixture `r7-demo-fixture-v1`

## Scenario

- Gate, input fixture, command and timeout: R7-07; isolated `r7-demo-v1`; `.\tools\prepare-demo.ps1`, `.\tools\verify-demo.ps1`, `.\tools\reset-demo.ps1`; prepare threshold 90 seconds.
- Expected threshold and actual result: the final prepare reached the async submission in 44.2 seconds, within the 90-second threshold. The preceding reset/reprepare rehearsal reached the same boundary in 46.3 seconds, proving retained-volume reuse. The API returned business code `50302`; therefore no workflow, trace, metric, evaluation, RAG evidence, or artifact was created. Final reset passed in 6.7 seconds, removed the exact demo namespace, and preserved reusable infrastructure volumes/configuration.
- Correlation IDs: project/document identifiers were used only to enforce cleanup boundaries and are not repeated in the report; `workflowRunUuid` and trace ID are `NOT CREATED`. Raw secrets, prompts, document bodies, and tokens are excluded.

## Evidence

- Command exit codes: prepare `1`, verify `1`, reset `0`; see [`commands/`](evidence/r7/20260713T152416Z-7ae1072/commands/).
- API/DB/UI/queue/metric/log evidence (redacted): [`verification-summary.json`](evidence/r7/20260713T152416Z-7ae1072/demo/verification-summary.json) records healthy Compose, loopback ports, browser/model/fallback preflight, READY knowledge seed, and the subsequent `50302`. It records zero downstream facts rather than fabricating them. Checksums are in [`checksums.sha256`](evidence/r7/20260713T152416Z-7ae1072/checksums.sha256).
- Failure timeline, impact, recovery/rollback action: the async submission gate reported Redis unavailable while the Redis container was healthy, matching the existing R7-02 blocker. R7-07 did not cross the frozen R3 ownership boundary. The partial seed wrote an exact cleanup state before submission; reset then proved scoped cleanup and stopped Compose without `--volumes`. The earlier immutable failure run [`20260713T151333Z-7ae1072`](evidence/r7/20260713T151333Z-7ae1072/) exposed that deleting Compose credentials while retaining volumes broke the next bootstrap; the final implementation retains the repository-external infrastructure environment. The rehearsal run [`20260713T152039Z-7ae1072`](evidence/r7/20260713T152039Z-7ae1072/) and final run both passed the reset/reprepare boundary.

## Conclusion

- **`BLOCKED`**. The reusable demo assets and safe reset are implemented, but the required async workflow evidence cannot pass while the R3 submission prerequisite returns `50302`.
- Regression owner: R3 workflow rate-limit/Redis integration. R7-07 contains only demo scripts, fixture, documentation, and redacted evidence; no R0–R6 business change was made.
- Follow-up commit and gates rerun: after the R3 fix, rerun prepare/verify/reset on one candidate SHA and replace this BLOCKED evidence with a new immutable run. Do not promote this report to PASS using cached or fabricated workflow results.
