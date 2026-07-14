import test from "node:test";
import assert from "node:assert/strict";
import { GAME_GENERATE_WORKFLOW_KEY, prepareWorkflowSubmission } from "../src/utils/workflowSubmission.js";

test("workflow submission uses the fixed DTO contract and server-only project routing", () => {
  const prepared = prepareWorkflowSubmission({ idea: "  Build a signal scavenger game.  ", context: "  Browser players.  " }, null, () => "key-1");

  assert.deepEqual(prepared.request, {
    workflowKey: GAME_GENERATE_WORKFLOW_KEY,
    idea: "Build a signal scavenger game.",
    context: "Browser players."
  });
  assert.equal(prepared.pendingSubmission.idempotencyKey, "key-1");
});

test("empty game ideas are blocked before an API request is prepared", () => {
  const prepared = prepareWorkflowSubmission({ idea: "   ", context: "optional" }, null, () => "unused");

  assert.equal(prepared.validationError, "请输入游戏想法后再开始生成。");
  assert.equal(prepared.request, undefined);
});

test("the same failed submission reuses its key while edited input starts a new submission", () => {
  const keys = ["retry-key", "new-key"];
  const first = prepareWorkflowSubmission({ idea: "First idea", context: "" }, null, () => keys.shift());
  const retry = prepareWorkflowSubmission({ idea: "First idea", context: "" }, first.pendingSubmission, () => keys.shift());
  const edited = prepareWorkflowSubmission({ idea: "Edited idea", context: "" }, retry.pendingSubmission, () => keys.shift());

  assert.equal(retry.pendingSubmission.idempotencyKey, "retry-key");
  assert.equal(edited.pendingSubmission.idempotencyKey, "new-key");
  assert.equal(edited.request.context, null);
});
