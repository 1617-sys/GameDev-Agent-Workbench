import test from "node:test";
import assert from "node:assert/strict";
import { analyticsQuery, metricDisplay, safePromptMetric } from "../src/features/prompt-analytics/promptAnalytics.js";

test("analytics query preserves time window, project, agent type and includeMock", () => {
  assert.equal(analyticsQuery({ from: "2026-08-01T00:00", to: "2026-09-01T00:00", projectId: 12, agentType: "DESIGNER", includeMock: true }).toString(),
    "from=2026-08-01T00%3A00&to=2026-09-01T00%3A00&projectId=12&agentType=DESIGNER&includeMock=true");
});

test("metric display uses server aggregates without recalculating values", () => {
  const metric = safePromptMetric({ promptVersionId: 7, callCount: 0, realSampleCount: 0, mockSampleCount: 0, unknownMockCount: 0,
    successRate: 0.37, meanLatencyMs: 101.25, p50LatencyMs: 80, p95LatencyMs: 9999, latencyMissingCount: 2,
    inputTokens: null, outputTokens: null, estimatedCost: null, costMissingCount: 3, insufficientSample: true, secret: "drop" });
  assert.deepEqual(metricDisplay(metric), {
    promptVersionId: "7", calls: "0", successRate: "37.00%", meanLatency: "101.25 ms", p50: "80 ms", p95: "9999 ms",
    tokens: "usage 缺失", cost: "成本缺失", samples: "真实 0 / Mock 0 / 未知 0", warnings: "样本不足；延迟缺失 2；成本缺失 3"
  });
  assert.equal("secret" in metric, false);
});

test("zero and missing values are distinct from valid numeric zero", () => {
  const display = metricDisplay(safePromptMetric({ promptVersionId: 1, callCount: 1, successRate: 0, meanLatencyMs: null, p50LatencyMs: null, p95LatencyMs: null, inputTokens: 0, outputTokens: 0, estimatedCost: 0 }));
  assert.equal(display.successRate, "0.00%");
  assert.equal(display.meanLatency, "延迟缺失");
  assert.equal(display.tokens, "0 / 0 tokens");
  assert.equal(display.cost, "0.000000");
});
