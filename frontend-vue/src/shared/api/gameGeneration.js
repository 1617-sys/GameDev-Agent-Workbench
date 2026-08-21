import { apiDownload, apiRequest } from "./http.js";

const enc = encodeURIComponent;
const runRoot = (projectUuid) => `/api/v5/projects/${enc(projectUuid)}/generation-runs`;

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
    headers: { "Idempotency-Key": idempotencyKey },
    body: { spec }
  }),
  get: (projectUuid, runUuid) => apiRequest(`${runRoot(projectUuid)}/${enc(runUuid)}`),
  build: (projectUuid, runUuid, expectedVersion) => apiRequest(
    `${runRoot(projectUuid)}/${enc(runUuid)}/build?expectedVersion=${encodeURIComponent(expectedVersion)}`,
    { method: "POST", body: {}, timeoutMs: 660_000 }
  ),
  download: (projectUuid, runUuid) => apiDownload(`${runRoot(projectUuid)}/${enc(runUuid)}/artifact`, {
    timeoutMs: 120_000,
    fallbackFilename: `local-cocos-game-${runUuid}.zip`
  })
};

export function saveGenerationArtifact({ blob, filename }) {
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
