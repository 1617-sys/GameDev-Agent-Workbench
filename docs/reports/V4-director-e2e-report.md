# V4 Director goal-to-DRAFT E2E report

## Result

The isolated deterministic fixture covers the complete goal → baseline → candidate DRAFT → PlayerRun/Episodes → comparison → `WAITING_APPROVAL` path and the approve/reject branches. It also exercises Python timeout/retry, PlayerRun partial failure, a crash after successful tool execution but before checkpoint, duplicate delivery, duplicate approval, budget exhaustion, cancellation, and cross-project access rejection.

Run and regenerate all evidence with one command:

```powershell
powershell -ExecutionPolicy Bypass -File tools/director-e2e/Invoke-DirectorE2E.ps1
```

## Traceability

- Protocols: `director-e2e/1.0`, `director/1.0`
- Fixture/provider: `fake-director-3c.1`, deterministic fake (no paid model)
- Commit, fixture image digest and input digest: `tools/director-e2e/output/manifest.json`
- Raw state and row evidence: `tools/director-e2e/output/raw-evidence.json`
- Database counts, terminal states and uniqueness facts: `tools/director-e2e/output/database-facts.json`
- Fixture inputs: `tools/director-e2e/fixtures/scenarios.json`

The recovery assertions require one DRAFT per `(DirectorRun, ordinal)`, one PlayerRun per `(DirectorRun, ordinal)`, unique Episode IDs, and a single approval result per idempotency key. Cross-project approval attempts must fail before any approval row is written.

## Limits

This is an intentionally fake, deterministic Director and a JSON persistence fixture. It proves orchestration protocol, recovery, idempotency, evidence shape and authorization invariants; it does not prove real-model quality or replace production MySQL integration tests. No human approval is automated—the fixture invokes approval as an explicit test actor command.
