import { apiRequest } from "./http.js";

const MAX_BYTES = 10 * 1024 * 1024;
const root = projectUuid => `/api/projects/${encodeURIComponent(projectUuid)}/knowledge-documents`;

export function validateKnowledgeFile(file) {
  if (!file || !file.name || !Number.isFinite(file.size) || file.size <= 0) throw new Error("请选择非空知识文件");
  if (file.size > MAX_BYTES) throw new Error("知识文件不得超过 10 MiB");
  if (/[\\/\u0000]/.test(file.name) || file.name.includes("..")) throw new Error("文件名包含不安全路径字符");
  const extension = file.name.split(".").pop()?.toLowerCase();
  const allowed = extension === "pdf" && file.type === "application/pdf"
    || ["md", "markdown", "txt"].includes(extension) && ["text/plain", "text/markdown"].includes(file.type);
  if (!allowed) throw new Error("仅支持 PDF、Markdown 或文本文件，且 MIME 类型必须匹配");
  return file;
}

export function shouldPollKnowledge(documents) {
  return Array.isArray(documents) && documents.some(document =>
    ["UPLOADED", "PARSING", "PARSED", "INDEXING"].includes(document.status));
}

export const knowledgeApi = {
  list: projectUuid => apiRequest(root(projectUuid)),
  upload: (projectUuid, file) => {
    const body = new FormData(); body.append("file", validateKnowledgeFile(file));
    return apiRequest(root(projectUuid), { method: "POST", body, timeoutMs: 120_000 });
  }
};
