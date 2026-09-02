import { apiRequest } from "./http";

const root = (projectUuid) => `/api/projects/${encodeURIComponent(projectUuid)}`;
export const episodesApi = {
  runs: (projectUuid, versionUuid) => apiRequest(`${root(projectUuid)}/player-runs?prototypeVersionUuid=${encodeURIComponent(versionUuid)}`),
  run: (projectUuid, runUuid) => apiRequest(`${root(projectUuid)}/player-runs/${encodeURIComponent(runUuid)}`),
  batch: (projectUuid, batchUuid) => apiRequest(`${root(projectUuid)}/machine-episodes/batches/${encodeURIComponent(batchUuid)}`),
  detail: (projectUuid, episodeUuid) => apiRequest(`${root(projectUuid)}/machine-episodes/${encodeURIComponent(episodeUuid)}`),
  summary: (projectUuid, episodeUuid) => apiRequest(`${root(projectUuid)}/machine-episodes/${encodeURIComponent(episodeUuid)}/summary`),
  steps: (projectUuid, episodeUuid, page = 0, size = 50) => apiRequest(`${root(projectUuid)}/machine-episodes/${encodeURIComponent(episodeUuid)}/steps?page=${page}&size=${size}`),
  aggregate: (projectUuid, versionUuid) => apiRequest(`${root(projectUuid)}/machine-episodes/prototype-versions/${encodeURIComponent(versionUuid)}/aggregate`)
};
