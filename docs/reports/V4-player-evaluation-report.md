# V4 Player Evaluation Report

## Evidence identity

- PlayerRun: `13545005-0f5d-4e76-ab11-34f41755d186`
- Persistent batch: `a4658835-bb2b-4c08-b212-0ba31af4d1a4`
- PrototypeVersion: `02a89964-ef7b-4a27-b0bc-14668a1d1d12`
- Config digest: `1483a23a55d2abfb7362da90afec039257d4ae783c8291ad8eaf562971f1b872`
- Runtime / metric: `simulation-core/1.0.0` / `score-delta/1.0`
- Seeds: `11, 29, 47, 71, 97`; no failed sample was removed.
- Sample size: 25 (five samples in each cohort).
- Recorded-decision replay: 5/5 representative Episodes matched every persisted previous/final state hash without calling a policy or model.

## Results

| Cohort | N | Completion | P50 ms | P95 ms | Action efficiency | Invalid | Provider P95 ms | Tokens | Cost (micros) | Mock | Failures |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| deterministic-neutral | 5 | 100.0% | 703 | 719 | 100.0% | 0 | N/A | 0 | 0 | 0 | none |
| deterministic-novice | 5 | 0.0% | 12083 | 13289 | 100.0% | 0 | N/A | 0 | 0 | 0 | TIME_EXPIRED: 5 |
| deterministic-regular | 5 | 40.0% | 13247 | 13355 | 100.0% | 0 | N/A | 0 | 0 | 0 | TIME_EXPIRED: 3 |
| deterministic-expert | 5 | 100.0% | 1065 | 1128 | 100.0% | 0 | N/A | 0 | 0 | 0 | none |
| llm-regular | 5 | 0.0% | 30 | 49 | N/A | 0 | N/A | 0 | 0 | 0 | INVALID_POLICY_OUTPUT: 5 |

## Conclusions and limits

The deterministic cohorts show the intended Persona separation on this frozen map: neutral and EXPERT completed all seeds, REGULAR completed two, and NOVICE timed out on all five. The small sample is descriptive only and is not statistical proof of general performance.

The LLM cohort is not evidence of real-model quality. The credential-free evaluation environment produced no auditable model call, token, cost, or provider-latency record; every sample failed at the first decision with `INVALID_POLICY_OUTPUT`. Those failures are retained in the raw evidence and the LLM policy cannot be claimed to outperform the deterministic baseline. A credentialed real-model rerun is required before drawing an LLM comparison conclusion.

Raw persisted results and representative step evidence are in `tools/player-evaluation/output/raw.json`; the machine-readable aggregation and replay hashes are in `tools/player-evaluation/output/summary.json`.
