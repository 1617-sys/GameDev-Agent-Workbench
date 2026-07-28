# Director goal-to-DRAFT recovery harness

Runs a deterministic, no-paid-model fixture that implements the `director/1.0` decision/tool protocol and persists every fact to an isolated JSON database. The fixture deliberately crashes and replays operations to verify checkpoint recovery and idempotency.

```powershell
powershell -ExecutionPolicy Bypass -File tools/director-e2e/Invoke-DirectorE2E.ps1
```

The command creates the fixture, executes all scenarios, verifies row-level invariants, writes sanitized evidence under `tools/director-e2e/output`, and removes its temporary database. Pass `-KeepFixture` to retain the temporary database for inspection. This validates orchestration contracts and recovery with a fake Director; it is not evidence of a real model's quality.
