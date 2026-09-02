import { apiDownload, apiRequest } from "./http.js";

const enc = encodeURIComponent;
// 同一项目下的所有生成操作都挂在一个 run 根路径下，避免各方法重复拼接 URL。
const runRoot = (projectUuid) => `/api/v5/projects/${enc(projectUuid)}/generation-runs`;

/**
 * V5 游戏生成控制面的 HTTP 适配层。
 *
 * 页面组件只表达“编译、创建、构建、审批、发布”等业务动作；鉴权、JSON 编解码和
 * 错误归一化由 apiRequest/apiDownload 统一处理。这样页面不需要直接操作 fetch。
 */
export const gameGenerationApi = {
  capabilities: () => apiRequest("/api/v5/gamespec/capabilities"),
  compile: (projectUuid, spec) => apiRequest(`/api/v5/projects/${enc(projectUuid)}/gamespec/compile`, {
    method: "POST",
    body: { spec }
  }),
  author: (projectUuid, idea, currentSpec) => apiRequest(`/api/v5/projects/${enc(projectUuid)}/gamespec/author`, {
    method: "POST",
    body: { idea, currentSpec }
  }),
  create: (projectUuid, spec, idempotencyKey) => apiRequest(runRoot(projectUuid), {
    method: "POST",
    // 创建请求可安全重试：相同 key + 相同 spec 会复用原任务，不会重复创建。
    headers: { "Idempotency-Key": idempotencyKey },
    body: { spec }
  }),
  get: (projectUuid, runUuid) => apiRequest(`${runRoot(projectUuid)}/${enc(runUuid)}`),
  build: (projectUuid, runUuid, expectedVersion) => apiRequest(
    `${runRoot(projectUuid)}/${enc(runUuid)}/build?expectedVersion=${encodeURIComponent(expectedVersion)}`,
    // Cocos CLI 最长运行 10 分钟，前端超时需略长于服务端构建超时。
    { method: "POST", body: {}, timeoutMs: 660_000 }
  ),
  approve: (projectUuid, runUuid, decision, reason, idempotencyKey) => apiRequest(
    `${runRoot(projectUuid)}/${enc(runUuid)}/approval`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: { decision, reason } }
  ),
  release: (projectUuid, runUuid, expectedVersion) => apiRequest(
    `${runRoot(projectUuid)}/${enc(runUuid)}/release?expectedVersion=${encodeURIComponent(expectedVersion)}`,
    { method: "POST", body: {} }
  ),
  prototypeCompatibility: (projectUuid, runUuid) => apiRequest(
    `${runRoot(projectUuid)}/${enc(runUuid)}/prototype-version-compatibility`
  ),
  createPrototypeVersion: (projectUuid, runUuid, idempotencyKey) => apiRequest(
    `${runRoot(projectUuid)}/${enc(runUuid)}/prototype-version`,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: {} }
  ),
  downloadPreview: (projectUuid, runUuid) => apiDownload(`${runRoot(projectUuid)}/${enc(runUuid)}/preview-artifact`, {
    timeoutMs: 120_000,
    fallbackFilename: `preview-cocos-game-${runUuid}.zip`
  }),
  download: (projectUuid, runUuid) => apiDownload(`${runRoot(projectUuid)}/${enc(runUuid)}/artifact`, {
    timeoutMs: 120_000,
    fallbackFilename: `local-cocos-game-${runUuid}.zip`
  })
};

export function saveGenerationArtifact({ blob, filename }) {
  // 用临时 object URL 触发浏览器下载；下载动作发出后立即释放，避免长期占用内存。
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.hidden = true;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
