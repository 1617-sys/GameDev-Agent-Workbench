import test from "node:test";
import assert from "node:assert/strict";
import { agentRunAttempt, agentRunPayload, agentRunsQuery, safeAgentRun } from "../src/features/admin/agentRunsAdmin.js";

test("agent run list query supports filters and pagination", () => {
  assert.equal(agentRunsQuery({ pageNum: 2, pageSize: 25, projectUuid: "p 1", agentType: "DESIGNER", status: "SUCCESS" }).toString(), "pageNum=2&pageSize=25&projectUuid=p+1&agentType=DESIGNER&status=SUCCESS");
});

test("costly create requires confirmation and validates bounded input", () => {
  assert.throws(() => agentRunPayload({ projectUuid: "p", agentType: "DESIGNER", title: "t", content: "c" }, false), /确认/);
  assert.deepEqual(agentRunPayload({ projectUuid: "p", agentType: "DESIGNER", title: "t", content: "c", ragEnabled: true, ragTopK: 5, ragContextBudget: 8000 }, true), { projectUuid: "p", agentType: "DESIGNER", title: "t", content: "c", context: "", ragEnabled: true, ragTopK: 5, ragContextBudget: 8000 });
});

test("same payload retry reuses idempotency key and response is whitelisted", () => {
  const first = agentRunAttempt({ projectUuid: "p", content: "x" }, null, () => "key-1");
  const replay = agentRunAttempt({ projectUuid: "p", content: "x" }, first, () => "key-2");
  const changed = agentRunAttempt({ projectUuid: "p", content: "y" }, first, () => "key-2");
  assert.equal(replay.key, "key-1"); assert.equal(changed.key, "key-2");
  const safe = safeAgentRun({ runUuid: "r", status: "SUCCESS", inputContent: "secret", outputContent: "secret", rawOutputRef: "secret" });
  assert.equal("inputContent" in safe, false); assert.equal("outputContent" in safe, false); assert.equal("rawOutputRef" in safe, false);
});
