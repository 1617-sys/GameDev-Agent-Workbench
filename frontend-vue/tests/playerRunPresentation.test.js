import test from "node:test";
import assert from "node:assert/strict";
import { playerRunPresentation } from "../src/features/episodes/playerRunPresentation.js";

test("faithfully distinguishes running, failed, missing Episode and completed Player Runs", () => {
  assert.deepEqual(playerRunPresentation({ status: "RUNNING" }), { tone: "info", title: "运行中", detail: "Player 正在执行，尚未生成持久化 Episode。" });
  assert.deepEqual(playerRunPresentation({ status: "FAILED", errorCode: "BUDGET", errorMessage: "预算耗尽" }), { tone: "danger", title: "运行失败", detail: "BUDGET：预算耗尽" });
  assert.deepEqual(playerRunPresentation({ status: "SUCCEEDED" }), { tone: "warning", title: "运行完成但没有 Episode", detail: "后端未返回 persistedBatchUuid，不会伪造轨迹。" });
  assert.deepEqual(playerRunPresentation({ status: "SUCCEEDED", persistedBatchUuid: "batch-1" }), { tone: "success", title: "运行完成", detail: "Episode 批次 batch-1 已持久化。" });
});
