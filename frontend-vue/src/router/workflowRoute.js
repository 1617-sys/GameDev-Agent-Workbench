const pattern = /^\/workflow-runs\/([^/]+)$/;
export function workflowRunUuidFromPath(pathname = window.location.pathname) { const match = pattern.exec(pathname); return match ? decodeURIComponent(match[1]) : null; }
export function navigateToWorkflowRun(uuid, historyImpl = window.history) { historyImpl.pushState({}, "", `/workflow-runs/${encodeURIComponent(uuid)}`); }
