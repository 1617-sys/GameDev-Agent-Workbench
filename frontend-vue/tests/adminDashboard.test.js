import test from "node:test";
import assert from "node:assert/strict";
import { dashboardModel, safeAgentSummary, safeProjectSummary } from "../src/features/admin/dashboardPresentation.js";

test("dashboard accepts empty and partially missing server summaries", () => {
  assert.deepEqual(dashboardModel([], []), { projects: [], agentTypes: [], empty: true });
  const model = dashboardModel([safeProjectSummary({ projectUuid: "p", projectName: "A".repeat(300), totalRunCount: 4 })], [safeAgentSummary({ agentType: "DESIGNER" })]);
  assert.equal(model.projects[0].projectName.length, 120);
  assert.equal(model.projects[0].successRunCount, 0);
  assert.equal(model.agentTypes[0].avgTimeTakenMs, null);
  assert.equal(model.empty, false);
});

test("dashboard response drops unexpected sensitive fields", () => {
  const project = safeProjectSummary({ projectUuid: "p", projectName: "x", ownerPassword: "secret" });
  const agent = safeAgentSummary({ agentType: "QA", rawPrompt: "secret" });
  assert.equal("ownerPassword" in project, false);
  assert.equal("rawPrompt" in agent, false);
});
