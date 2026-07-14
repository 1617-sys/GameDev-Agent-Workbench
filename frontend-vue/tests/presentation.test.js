import test from "node:test";
import assert from "node:assert/strict";
import { artifactLabel, formatDuration, statusMeta, stepLabel } from "../src/shared/presentation/workflow.js";

test("maps backend workflow vocabulary to product language", () => {
  assert.equal(statusMeta("RUNNING").label, "正在生成");
  assert.equal(stepLabel("game_config_generate"), "游戏配置");
  assert.equal(artifactLabel("GAME_CONCEPT_RESULT"), "游戏概念");
});

test("formats workflow duration compactly", () => {
  assert.equal(formatDuration(900), "900 ms");
  assert.equal(formatDuration(12_000), "12 秒");
  assert.equal(formatDuration(125_000), "2 分 5 秒");
});
