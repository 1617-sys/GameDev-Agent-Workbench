import { apiDownload, apiRequest } from "./http.js";

const enc = encodeURIComponent;
const jobPath = (projectUuid, jobUuid) => `/api/projects/${enc(projectUuid)}/exports/${enc(jobUuid)}`;

export const exportsApi = {
  create: (projectUuid, versionUuid, key) => apiRequest(
    `/api/projects/${enc(projectUuid)}/prototype-versions/${enc(versionUuid)}/exports`,
    { method: "POST", headers: { "Idempotency-Key": key }, body: {} }
  ),
  get: (projectUuid, jobUuid) => apiRequest(jobPath(projectUuid, jobUuid)),
  retry: (projectUuid, jobUuid) => apiRequest(`${jobPath(projectUuid, jobUuid)}/retry`, { method: "POST", body: {} }),
  download: (projectUuid, jobUuid, packageName) => apiDownload(`${jobPath(projectUuid, jobUuid)}/download`, {
    fallbackFilename: packageName
  })
};

export function saveExport({ blob, filename }) {
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
