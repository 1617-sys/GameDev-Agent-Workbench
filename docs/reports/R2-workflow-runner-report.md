# R2 Workflow Runner Report

## Environment

- Date: 2026-07-11 (Asia/Shanghai)
- Branch: `codex/r2`
- Baseline: `cef3942` (`R1` acceptance)
- R2 acceptance commit range: `d0b2c96..HEAD`

## Runner result

- `SynchronousWorkflowRunner` parses only `WorkflowRun.workflowDefinitionSnapshot`, creates or restores `WorkflowStepRun`, and orders work from frozen dependencies.
- `WorkflowExecutionContext`, `WorkflowStepExecutor`, `AgentStepExecutor`, and `ArtifactWriter` separate scheduling, one-step Agent execution, and Artifact persistence.
- A successful StepRun is restored into context and skipped; terminal WorkflowRuns are rejected before an Agent call. Failures persist `FAILED` StepRun/WorkflowRun state and stop downstream steps.
- `GameConfigWorkflowEvaluationHook` validates the documented raw contract before optional alias canonicalization. Valid artifacts persist `game-config` / `1.0` metadata and summary; invalid configuration fails before Artifact write and therefore before Demo GameBuild.

## Legacy-entry compatibility

- `WorkflowServiceImpl` performs authorization, snapshot creation, Runner delegation, then reloads persisted state for the legacy response. It no longer contains step ordering or context concatenation.
- `DemoStreamServiceImpl` retains the owner-aware Redis lock, executor, emitter, and GameBuild boundary. `DemoWorkflowExecutionListener` only translates persisted Runner events to the existing SSE event VO; listener I/O errors are non-fatal.
- The Demo definition version contains the four ordered steps, including `GAME_CONFIG_GENERATE`. GameBuild is invoked only after Runner success and valid GameConfig output.

## Verification

| Command | Result | Evidence |
| --- | --- | --- |
| `mvn -Dtest=WorkflowServiceImplTest,*WorkflowRunner*Test,*AgentStepExecutor*Test,*ArtifactWriter*Test,*GameConfig*Test,*DemoStream*Test,*RedisService*Test test` | PASS | 22 tests |
| `mvn test` | PASS | Full Java regression |
| `npm run test:game-config` | PASS | 9 contract tests |
| `npm run build` | PASS | Vue production build |
| `./tools/verify.ps1 -Profile quick` | PASS | Java, Python compile, Vue build, Compose config |
| boundary and secret scans | PASS | No R3 messaging primitives or scanned credentials added |

## Known risks

- R2 intentionally has no transaction across Agent I/O, durable queue, outbox, retry, recovery scan, or distributed execution claim. These are R3 responsibilities.
- The acceptance evidence is unit/service and harness based; a separately provisioned live Python Agent/GameBuild environment is not part of this task-card verification.
- Vue build reports a pre-existing large bundle warning; it does not affect Runner behavior.

## R3 entry conclusion

**PASS.** The two legacy paths use the synchronous Runner as their sole step-execution core, and failure/compatibility boundaries have regression evidence. R3 can add durable submission and recovery around this core without rewriting Runner scheduling or the structured GameConfig gate.
