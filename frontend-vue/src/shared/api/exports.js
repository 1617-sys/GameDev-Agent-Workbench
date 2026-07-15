import { apiDownload, apiRequest } from "./http.js";
const enc=encodeURIComponent;
export const exportsApi={
 create:(projectUuid,versionUuid,key)=>apiRequest(`/api/projects/${enc(projectUuid)}/prototype-versions/${enc(versionUuid)}/exports`,{method:"POST",headers:{"Idempotency-Key":key},body:{}}),
 get:(projectUuid,jobUuid)=>apiRequest(`/api/projects/${enc(projectUuid)}/exports/${enc(jobUuid)}`),
 retry:(projectUuid,jobUuid)=>apiRequest(`/api/projects/${enc(projectUuid)}/exports/${enc(jobUuid)}/retry`,{method:"POST",body:{}}),
 download:(projectUuid,jobUuid)=>apiDownload(`/api/projects/${enc(projectUuid)}/exports/${enc(jobUuid)}/download`)
};
export function saveExport({blob,filename}){const url=URL.createObjectURL(blob);const anchor=document.createElement("a");anchor.href=url;anchor.download=filename;anchor.click();URL.revokeObjectURL(url);}
