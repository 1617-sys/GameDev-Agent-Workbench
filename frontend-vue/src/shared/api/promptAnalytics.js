import { apiRequest } from "./http.js";
import { analyticsQuery } from "../../features/prompt-analytics/promptAnalytics.js";

export const promptAnalyticsApi = {
  list: filters => apiRequest(`/api/v1/analytics/prompt-versions?${analyticsQuery(filters)}`)
};
