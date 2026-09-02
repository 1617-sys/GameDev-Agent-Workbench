import { apiRequest } from "./http.js";

const KEY = /^[A-Za-z0-9._:-]{1,80}$/;
const PERSONAS = new Set(["baseline-neutral", "NOVICE", "REGULAR", "EXPERT"]);
const POLICIES = new Set(["DETERMINISTIC", "LLM"]);
const root = projectUuid => `/api/projects/${encodeURIComponent(projectUuid)}/player-runs`;

export function validatePlayerRunRequest(request) {
  if (!request || typeof request !== "object") throw new Error("Player Run 请求不能为空");
  if (!request.prototypeVersionUuid || request.prototypeVersionUuid.length > 80) throw new Error("请选择有效的原型版本");
  if (!KEY.test(request.clientBatchKey || "")) throw new Error("批次幂等标识不合法");
  if (!Number.isInteger(request.concurrency) || request.concurrency < 1 || request.concurrency > 8) throw new Error("并发数必须为 1 到 8");
  if (!Array.isArray(request.episodes) || request.episodes.length < 1 || request.episodes.length > 100) throw new Error("Episode 数量必须为 1 到 100");
  for (const episode of request.episodes) {
    if (!KEY.test(episode.clientEpisodeKey || "")) throw new Error("Episode 标识不合法");
    if (!PERSONAS.has(episode.personaId)) throw new Error("Persona 不在后端允许列表中");
    if (!POLICIES.has(episode.policyKind)) throw new Error("Policy 类型不合法");
    if (!Number.isInteger(episode.maxSteps) || episode.maxSteps < 1 || episode.maxSteps > 10000) throw new Error("执行预算 maxSteps 必须为 1 到 10000");
    for (const [label, value] of [["seed", episode.seed], ["policySeed", episode.policySeed]]) {
      if (!Number.isInteger(value) || value < 0 || value > 4294967295) throw new Error(`${label} 不合法`);
    }
    if (episode.modelKey != null && episode.modelKey !== "default") throw new Error("模型键不合法");
  }
  return request;
}

export const playerRunsApi = {
  create: (projectUuid, request, idempotencyKey, traceId) => apiRequest(root(projectUuid), {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey, ...(traceId ? { "X-Trace-Id": traceId } : {}) },
    body: validatePlayerRunRequest(request)
  }),
  list: (projectUuid, versionUuid) => apiRequest(
    `${root(projectUuid)}?prototypeVersionUuid=${encodeURIComponent(versionUuid)}`
  ),
  get: (projectUuid, runUuid) => apiRequest(`${root(projectUuid)}/${encodeURIComponent(runUuid)}`)
};
