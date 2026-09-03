import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

const workspace = resolve(import.meta.dirname, "..");
const coveragePath = resolve(workspace, "docs/api-coverage/endpoints.json");
const coverage = JSON.parse(await readFile(coveragePath, "utf8"));

const maven = process.platform === "win32" ? "mvn.cmd" : "mvn";
const mavenArgs = [
  "-q",
  "-Dtest=NonProdOpenApiSnapshotTest,ProdOpenApiSnapshotTest",
  "-Dopenapi.update=true",
  "test"
];
const exportResult = process.platform === "win32"
  ? spawnSync("cmd.exe", ["/d", "/s", "/c", [maven, ...mavenArgs].join(" ")],
      { cwd: resolve(workspace, "backend-java"), stdio: "inherit" })
  : spawnSync(maven, mavenArgs, { cwd: resolve(workspace, "backend-java"), stdio: "inherit" });
if (exportResult.status !== 0) {
  throw new Error(`SpringDoc snapshot export failed with exit code ${exportResult.status}: ${exportResult.error || "unknown error"}`);
}
console.log("wrote real SpringDoc prod/non-prod snapshots");

const implementedFeatures = new Map(Object.entries({
  auth: ["frontend-vue/src/shared/api/auth.js", "frontend-vue/src/features/auth/AuthPage.vue"],
  session: ["frontend-vue/src/shared/api/auth.js", "frontend-vue/src/app/AppShell.vue"],
  projects: ["frontend-vue/src/shared/api/projects.js", "frontend-vue/src/features/projects/ProjectsPage.vue"],
  generation: ["frontend-vue/src/shared/api/gameGeneration.js", "frontend-vue/src/features/generation/GenerationStudioPage.vue"],
  director: ["frontend-vue/src/shared/api/director.js", "frontend-vue/src/features/director/DirectorRunPage.vue"],
  "prototype-versions": ["frontend-vue/src/shared/api/prototypes.js", "frontend-vue/src/features/prototypes/PrototypeVersionsPage.vue"],
  exports: ["frontend-vue/src/shared/api/exports.js", "frontend-vue/src/features/prototypes/PrototypeVersionsPage.vue"],
  playtest: ["frontend-vue/src/shared/api/telemetry.js", "frontend-vue/src/features/prototypes/PrototypeVersionsPage.vue"],
  "workflow-runs": ["frontend-vue/src/shared/api/workflows.js", "frontend-vue/src/features/runs/RunPage.vue"],
  "legacy-workflow": ["frontend-vue/src/shared/api/workflows.js", "frontend-vue/src/features/studio/StudioPage.vue"],
  episodes: ["frontend-vue/src/shared/api/episodes.js", "frontend-vue/src/features/episodes/EpisodeTracePage.vue"],
  artifacts: ["frontend-vue/src/shared/api/artifacts.js", "frontend-vue/src/features/artifacts/ArtifactOverviewPage.vue"],
  knowledge: ["frontend-vue/src/shared/api/knowledge.js", "frontend-vue/src/features/knowledge/KnowledgeLibraryPage.vue"],
  "player-runs": ["frontend-vue/src/shared/api/playerRuns.js", "frontend-vue/src/features/prototypes/PrototypeVersionsPage.vue"],
  "rag-evidence": ["frontend-vue/src/shared/api/workflows.js", "frontend-vue/src/features/runs/RunPage.vue"],
  "prompt-ops": ["frontend-vue/src/shared/api/promptOps.js", "frontend-vue/src/features/prompt-ops/PromptOpsPage.vue"],
  analytics: ["frontend-vue/src/shared/api/promptAnalytics.js", "frontend-vue/src/features/prompt-analytics/PromptAnalyticsPage.vue"],
  dashboard: ["frontend-vue/src/shared/api/dashboard.js", "frontend-vue/src/features/admin/AdminDashboardPage.vue"],
  "agent-runs": ["frontend-vue/src/shared/api/agentRuns.js", "frontend-vue/src/features/admin/AdminAgentRunsPage.vue"],
  diagnostics: ["frontend-vue/src/shared/api/diagnostics.js", "frontend-vue/src/features/admin/AdminDiagnosticsPage.vue"],
  "admin-users": ["frontend-vue/src/shared/api/users.js", "frontend-vue/src/features/admin/AdminUsersPage.vue"]
}));
const frontendMappings = coverage.endpoints
  .filter(endpoint => endpoint.lifecycle === "active" && !endpoint.audience.includes("internal"))
  .map(endpoint => {
    const implementation = implementedFeatures.get(endpoint.frontendFeature);
    return ({
    method: endpoint.method,
    path: endpoint.path,
    feature: endpoint.frontendFeature,
    adapter: implementation?.[0] || "planned",
    page: implementation?.[1] || "planned",
    status: implementation ? "implemented" : "planned"
  }); });
const manifestPath = resolve(workspace, "frontend-vue/src/shared/api/endpointManifest.json");
await writeFile(manifestPath, `${JSON.stringify({ schemaVersion: 1, endpoints: frontendMappings }, null, 2)}\n`, "utf8");
console.log(`wrote ${manifestPath}`);

const diagnosticCatalogPath = resolve(workspace, "frontend-vue/src/shared/api/diagnosticCatalog.json");
const diagnosticEndpoints = coverage.endpoints
  .filter(endpoint => ["internal", "deprecated", "non_prod"].includes(endpoint.lifecycle))
  .map(({ method, path, lifecycle, owner, dangerLevel, profiles, retentionReason, replacement }) => ({ method, path, lifecycle, owner, dangerLevel, profiles, retentionReason, replacement }));
await writeFile(diagnosticCatalogPath, `${JSON.stringify({ schemaVersion: 1, endpoints: diagnosticEndpoints }, null, 2)}\n`, "utf8");
console.log(`wrote ${diagnosticCatalogPath}`);
