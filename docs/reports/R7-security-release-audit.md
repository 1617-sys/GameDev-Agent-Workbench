# R7 Security Release Audit

## Execution identity

- Run ID / evidence path: `20260713T142023Z-692f709` / `docs/reports/evidence/r7/20260713T142023Z-692f709/`
- Date and timezone: 2026-07-13, Asia/Shanghai
- Branch / RC_SHA / dirty state: `r7` / `692f709f5c7effc3902f5278afcbeb2efe096072` / clean at audit start, fixes applied during audit
- Operator / environment manifest / image digest: Codex / `manifest.json` / image digest `MISSING` because Docker daemon was unavailable
- Provider mode: mock/fake; no real provider credential used

## Scenario

- Gate, input fixture, command and timeout: R7-06 security release gate; owner/attacker service fixtures, forged upload/model/RAG inputs, tracked-file and migration patterns, dependency tooling, and the task-card validation commands.
- Expected threshold and actual result: expected zero real secrets/weak defaults, complete cross-user/project denial, production test capability closed, no unhandled Critical dependency/image issue, and full regression. Actual result is `BLOCKED` because Docker-backed tests/image scanning and complete Maven/Python CVE scans are missing, and the Python Agent remains mock-backed without a production-mode closure gate.
- Correlation IDs: controlled unit fixtures only; no live workflow was created.

## Findings and disposition

| ID | Severity | Finding | Disposition |
| --- | --- | --- | --- |
| SEC-01 | High | Redis used permissive `LaissezFaireSubTypeValidator` for cached values. | Fixed with an allowlist limited to `SysUser`; unexpected polymorphic type test added. |
| SEC-02 | High | Java-to-Python Agent calls had no service-to-service credential. | Fixed with required 32+ character internal token, constant-time comparison, Compose/bootstrap wiring and positive/negative tests. |
| SEC-03 | High | `npm audit` reported Playwright certificate-verification advisory through `1.55.0`. | Fixed by pinning `@playwright/test`, `playwright` and core to `1.55.1`; final audit reports 0. |
| SEC-04 | Medium | Browser origins were hard-coded to development values and Java Demo/Swagger lacked an explicit production closure. | Fixed with configurable CORS; `prod` defaults to no origins, excludes Demo controller and disables OpenAPI/Swagger. |
| SEC-05 | Blocking | Docker daemon unavailable; container image CVE scan and all three Testcontainers integration tests were not executed. | Not accepted. Re-run on Compose integration host; an exit-zero script with 3 skips is not treated as gate evidence. |
| SEC-06 | Blocking | OWASP Maven scan did not complete; Python CVE scanner was unavailable. | Not accepted. Run a pinned, cached vulnerability database/scanner and triage Critical/High findings. `dependency:tree` and `pip check` are inventory/sanity only. |
| SEC-07 | Blocking | Python service identifies as a Mock API and several normal Agent routes directly use mock builders; there is no Python production profile that proves mock capability is closed. | Cross-stage owner R0-R2. Add a real production execution mode or fail-closed route registration and production-profile tests; do not relabel mock output as real. |

No real secret was copied into this report or evidence. The tracked pattern scan found zero candidate files for the bounded token/key rules. The migration scan found zero destructive-operation candidates in forward migrations.

## Authorization and untrusted-input evidence

| Boundary | Enforcement / test evidence | Result |
| --- | --- | --- |
| Workflow query / Artifact summaries | User-scoped read mapper; foreign and unauthenticated tests | PASS (unit) |
| SSE replay | Snapshot ownership is checked before emitter creation/replay | PASS (unit) |
| Cancel / retry | User-scoped run lookup and update predicates | PASS (unit) |
| Artifact list / UUID guessing | Project owner lookup plus artifact project owner check; dedicated attacker tests | PASS (unit) |
| Metric | Mapper query includes authenticated `userId` and optional `projectId`; argument test added | PASS (unit) |
| Knowledge Document | Owner project lookup before document query | PASS (unit) |
| Vector retrieval | Vector metadata project filter plus active document/project database check | PASS (unit) |
| RetrievalRecord / RAG evidence | Agent run and workflow queries include user/project ownership | PASS (unit) |
| Upload | 10 MiB maximum, allowed extension/MIME, PDF magic bytes, binary-text and traversal checks; normalized random storage key | PASS (unit) |
| Prompt injection | Retrieved text is bounded and marked as untrusted material that cannot override constraints | PASS (unit) |
| Model output execution | GameConfig accepts JSON object contract only; executable text is rejected and never evaluated | PASS (unit) |
| Production test capability | Java Demo/Swagger closed under `prod`; Python mock-backed routes remain | BLOCKED |

Unit evidence does not replace the required two-user Compose fixture. The missing integration execution remains a release blocker.

## Dependency, image and configuration review

- npm: initial 2 High entries from one Playwright advisory; minimal patch to exact `1.55.1`; final `npm audit` is 0.
- Maven: runtime dependency inventory succeeded. OWASP dependency-check was terminated after a controlled wait without a completed vulnerability result; `NOT RUN` to conclusion.
- Python: `pip check` succeeded; `pip-audit`, OSV Scanner and Trivy were unavailable, so CVE status is `NOT RUN`.
- Containers: base images were inventoried (`eclipse-temurin:21-jre`, `python:3.13-slim`, `node:22-alpine`, MySQL 8.4, RabbitMQ 3.13, Redis 7.4). Docker Scout binary was present but daemon was unavailable, so image vulnerability/digest evidence is `MISSING`.
- Secrets/config: runtime credentials are required or generated locally; the new internal token has no repository default. Compose rendering passed using process-only random values with output suppressed.
- Data: Flyway clean remains disabled; no destructive pattern candidate was found in `db/migration`.

## Evidence

- Command exit code and console artifact: summarized in `commands/validation-summary.md`; raw rendered configuration was intentionally not retained.
- API/DB/UI/queue/metric/log evidence (redacted): unit/service security matrix in `security/audit-summary.md`; live DB/queue evidence `MISSING` because Docker was unavailable.
- Failure timeline, impact, recovery/rollback action: first quick run stopped on incomplete local `.env`; rerun with ephemeral random values passed. Docker remained unavailable, so no destructive recovery action was taken. Rollback for these fixes is the audit commit only; no migration or data mutation was introduced.

## Validation

- `git diff --check`: PASS.
- `docker compose config`: PASS with output suppressed and ephemeral random values.
- `backend-java/mvn test`: PASS, 142 tests with 1 environment-conditioned skip.
- `python-agent/python -m pytest`: PASS, 8 tests.
- `frontend-vue/npm audit`: PASS, 0 vulnerabilities after pin.
- `frontend-vue/npm run build`: PASS; non-blocking bundle-size warning.
- `tools/verify.ps1 -Profile quick`: PASS on the credential-safe rerun.
- `tools/verify.ps1 -Profile integration`: exit 0, but Testcontainers tests 3/3 skipped; treated as `BLOCKED`, not PASS.

## Conclusion

- `BLOCKED`.
- Regression owner: R7 minimal fixes for SEC-01 through SEC-04 are complete; Docker/scanner environment and the R0-R2 production Agent mode remain unresolved.
- Follow-up commit and gates rerun: commit this audit/fix set, then on a Docker-enabled clean candidate run the two-user/project security fixture, image scan, completed Maven/Python CVE scans, and both verification profiles with zero required skips. The release gate must remain blocked until those artifacts exist and Critical findings are either fixed or explicitly accepted by the release owner.
