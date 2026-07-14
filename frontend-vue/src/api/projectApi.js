export function createProjectApi(http) {
  const base = (projectUuid) => `/api/projects/${encodeURIComponent(projectUuid)}`;

  return {
    list: () => http("/api/projects"),
    create: (project) => http("/api/projects", { method: "POST", body: project }),
    get: (projectUuid) => http(base(projectUuid)),
    update: (projectUuid, project) => http(base(projectUuid), { method: "PUT", body: project })
  };
}
