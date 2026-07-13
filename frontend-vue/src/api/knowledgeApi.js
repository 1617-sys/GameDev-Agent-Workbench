export function createKnowledgeApi(http) {
  const base = (projectUuid) => `/api/projects/${encodeURIComponent(projectUuid)}/knowledge-documents`;
  return {
    list: (projectUuid) => http(base(projectUuid)),
    upload: (projectUuid, file) => {
      const body = new FormData();
      body.append("file", file);
      return http(base(projectUuid), { method: "POST", body });
    }
  };
}
