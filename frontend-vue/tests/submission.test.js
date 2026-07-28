import test from "node:test";
import assert from "node:assert/strict";
import { prepareSubmission } from "../src/shared/presentation/submission.js";

test("builds the frozen GAME_GENERATE request", () => {
  const result = prepareSubmission({ idea: "  博物馆夺宝  ", durationSeconds: 90, difficulty: "normal", visualTheme: "  霓虹风  ", additionalRequirements: "  两件藏品  " });
  assert.deepEqual(result.request, { workflowKey: "GAME_GENERATE", idea: "博物馆夺宝", durationSeconds: 90, difficulty: "normal", visualTheme: "霓虹风", additionalRequirements: "两件藏品" });
  assert.ok(result.pending.idempotencyKey);
});

test("reuses the same pending idempotency key", () => {
  const pending = { idempotencyKey: "fixed-key" };
  assert.equal(prepareSubmission({ idea: "test", durationSeconds: 60, difficulty: "easy", visualTheme: "明亮" }, pending).pending, pending);
});

test("rejects empty and oversized ideas", () => {
  assert.equal(prepareSubmission({ idea: "  " }).error, "请先描述你的游戏想法");
  assert.match(prepareSubmission({ idea: "x".repeat(5001), durationSeconds: 90, difficulty: "normal", visualTheme: "风格" }).error, /5000/);
  assert.match(prepareSubmission({ idea: "主题", durationSeconds: 10, difficulty: "normal", visualTheme: "风格" }).error, /30/);
});
