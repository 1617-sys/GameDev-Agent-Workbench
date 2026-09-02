const boundedText = value => String(value ?? "").replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 120);
export function ragEvidenceState(evidence) {
  if (!evidence?.ragEnabled) return { kind: "off", label: "RAG 未启用", mock: Boolean(evidence?.mock) };
  if (evidence.ragStatus === "FAILED") return { kind: "failed", label: "检索失败", mock: Boolean(evidence.mock) };
  if (!Array.isArray(evidence.references) || evidence.references.length === 0) return { kind: "no-candidates", label: "未命中候选", mock: Boolean(evidence.mock) };
  return { kind: "on", label: "RAG 已提供证据", mock: Boolean(evidence.mock) };
}
export function safeReference(reference) {
  return { documentUuid: boundedText(reference?.documentUuid), documentVersion: Number(reference?.documentVersion || 0), chunkUuid: boundedText(reference?.chunkUuid), rank: Number(reference?.rank || 0), score: Number(reference?.score || 0) };
}
