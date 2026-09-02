export function diagnosticActions(profile, catalog = []) {
  return profile === "non-prod" && catalog.some(item => item.lifecycle === "non_prod") ? [{ label: "打开非生产 Demo", path: "/demo/play" }] : [];
}
export function safeHealth(source = {}) { return { status: String(source.status || "UNKNOWN"), version: String(source.version || "unknown") }; }
