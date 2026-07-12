export function createAnalyticsApi(http) {
  return { promptVersions: (params) => http(`/api/v1/analytics/prompt-versions?${new URLSearchParams(params)}`) };
}
