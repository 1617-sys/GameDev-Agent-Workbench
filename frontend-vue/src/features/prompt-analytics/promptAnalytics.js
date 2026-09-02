const metricFields = ["promptVersionId", "callCount", "realSampleCount", "mockSampleCount", "unknownMockCount", "successRate", "meanLatencyMs", "p50LatencyMs", "p95LatencyMs", "latencyMissingCount", "inputTokens", "outputTokens", "estimatedCost", "costMissingCount", "insufficientSample"];

export function safePromptMetric(source = {}) {
  return Object.fromEntries(metricFields.map(field => [field, source[field] ?? null]));
}

export function analyticsQuery(filters) {
  const query = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.projectId !== "" && filters.projectId != null) query.set("projectId", String(filters.projectId));
  if (filters.agentType) query.set("agentType", filters.agentType);
  query.set("includeMock", String(Boolean(filters.includeMock)));
  return query;
}

const latency = (value, digits = null) => value == null ? "延迟缺失" : `${digits == null ? value : Number(value).toFixed(digits)} ms`;

export function metricDisplay(metric) {
  const warnings = [];
  if (metric.insufficientSample) warnings.push("样本不足");
  if (Number(metric.latencyMissingCount) > 0) warnings.push(`延迟缺失 ${metric.latencyMissingCount}`);
  if (Number(metric.costMissingCount) > 0) warnings.push(`成本缺失 ${metric.costMissingCount}`);
  return {
    promptVersionId: String(metric.promptVersionId ?? "—"),
    calls: String(metric.callCount ?? 0),
    successRate: metric.successRate == null ? "成功率缺失" : `${(Number(metric.successRate) * 100).toFixed(2)}%`,
    meanLatency: latency(metric.meanLatencyMs, 2),
    p50: latency(metric.p50LatencyMs),
    p95: latency(metric.p95LatencyMs),
    tokens: metric.inputTokens == null || metric.outputTokens == null ? "usage 缺失" : `${metric.inputTokens} / ${metric.outputTokens} tokens`,
    cost: metric.estimatedCost == null ? "成本缺失" : Number(metric.estimatedCost).toFixed(6),
    samples: `真实 ${metric.realSampleCount ?? 0} / Mock ${metric.mockSampleCount ?? 0} / 未知 ${metric.unknownMockCount ?? 0}`,
    warnings: warnings.join("；") || "—"
  };
}
