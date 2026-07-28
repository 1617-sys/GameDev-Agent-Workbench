# V4 Player Evaluation Report

- Persistent batch: `a4658835-bb2b-4c08-b212-0ba31af4d1a4`
- PrototypeVersion: `02a89964-ef7b-4a27-b0bc-14668a1d1d12`
- Sample size: 25
- Confidence: five fixed seeds per cohort; descriptive comparison only, not statistical proof.
- Recorded-decision replay: 5/5 verified against the frozen Simulation Core without calling a model.

| Cohort | N | Completion | P50 ms | P95 ms | Action efficiency | Invalid | Provider P95 ms | Tokens | Cost (micros) | Mock | Failures |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| deterministic-neutral | 5 | 100.0% | 703 | 719 | 100.0% | 0 | N/A | 0 | 0 | 0 | none |
| deterministic-novice | 5 | 0.0% | 12083 | 13289 | 100.0% | 0 | N/A | 0 | 0 | 0 | TIME_EXPIRED: 5 |
| deterministic-regular | 5 | 40.0% | 13247 | 13355 | 100.0% | 0 | N/A | 0 | 0 | 0 | TIME_EXPIRED: 3 |
| deterministic-expert | 5 | 100.0% | 1065 | 1128 | 100.0% | 0 | N/A | 0 | 0 | 0 | none |
| llm-regular | 5 | 0.0% | 30 | 49 | N/A | 0 | N/A | 0 | 0 | 0 | INVALID_POLICY_OUTPUT: 5 |
