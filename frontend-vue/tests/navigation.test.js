import test from "node:test";
import assert from "node:assert/strict";
import { visibleNavigation } from "../src/app/navigation.js";

const keys = items => items.flatMap(section => section.items.map(item => item.key));

test("ordinary users only see safe user navigation", () => {
  const items = visibleNavigation(["projects.read", "generation.read", "artifacts.read"], { projectUuid: "p-1" });

  assert.deepEqual(keys(items), ["projects", "generation", "artifacts"]);
  assert.equal(items.some(section => section.key === "admin"), false);
  assert.equal(keys(items).some(key => ["diagnostics", "legacy-workflow", "agent-runs"].includes(key)), false);
});

test("project advanced users gain project operations without admin navigation", () => {
  const items = visibleNavigation([
    "projects.read", "generation.read", "artifacts.read", "prototype-versions.manage",
    "player-runs.read", "knowledge.read", "workflow-runs.manage", "director-runs.manage"
  ], { projectUuid: "p-1" });

  assert.deepEqual(keys(items), [
    "projects", "generation", "artifacts", "prototype-versions", "player-runs",
    "knowledge", "workflow-runs", "director-runs"
  ]);
  assert.equal(items.some(section => section.key === "admin"), false);
});

test("administrators see diagnostics and operations only when declared by backend", () => {
  const items = visibleNavigation([
    "projects.read", "admin.dashboard", "admin.agent-runs", "prompt-ops.manage",
    "prompt-analytics.read", "admin.diagnostics"
  ], {});

  assert.deepEqual(keys(items), ["projects", "dashboard", "agent-runs", "prompt-ops", "analytics", "diagnostics"]);
  assert.equal(items.find(section => section.key === "admin")?.items.length, 5);
});
