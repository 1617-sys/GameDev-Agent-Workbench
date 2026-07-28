# V4 Director agentic evaluation report

> Classification: **MOCK / INSUFFICIENT SAMPLE / NOT COMPARABLE TO REAL-MODEL PERFORMANCE**

- Dataset: `fixed-goals-3c.1` (6 fixed goals per arm)
- Candidate generator: `deterministic-neighbor/1.0`
- RAG: off
- Same budget: `{"maxCandidates":3,"maxEpisodes":9,"maxRounds":4,"maxToolCalls":12}`
- Provider/model/version: `deterministic-fixture/director-fixture/3c.1`
- Input digest: `cf71e731912d8aae79103650e7e0fe2da9d3e02df284f40bd9bc492d220e27ea`

| Metric | Fixed workflow | Director + Tools |
|---|---:|---:|
| Goal + guardrail rate | 0.333 | 0.667 |
| Illegal tool-call rate | 0 | 0.063 |
| Invalid / duplicate candidates | 0 / 0 | 1 / 1 |
| Average rounds / Episodes | 1 / 9 | 2.667 / 8.500 |
| Average latency ms | 812.500 | 1433.333 |
| Average tokens / cost micros | 0 / 0 | 1198.333 / 1533.333 |
| Recovery success | N/A | 0.667 |
| Approval A/R/M | 2/3/1 | 3/2/1 |
| Traceability | 1 | 1 |

## Honest conclusion

In the deterministic mock fixture, Director improves simultaneous goal/guardrail completion but costs more rounds, latency, tokens and money, and still has an illegal-call and recovery failure. Because evidence is mock and N=6 per arm, no real-model superiority claim is supported; Upgrade 4 should run the same matrix with a separately labelled real provider and larger sample.

## Evidence and limitations

Every fixed goal, including failures, is retained in `tools/director-evaluation/output/raw.json`. Machine-readable metrics are in `summary.json`. The fixture versions, maps, seeds, budgets, provider label and raw sample facts are checked in under `tools/director-evaluation/fixtures`. Descriptive rates from six samples per arm are not confidence estimates. RAG and Bayesian optimization are excluded.
