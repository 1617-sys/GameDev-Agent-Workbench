import test from "node:test";
import assert from "node:assert/strict";
import { asyncStateCopy, dangerConfirmation } from "../src/shared/ui/safeStates.js";

test("shared async states use safe actionable copy", () => {
  assert.equal(asyncStateCopy("loading").busy, true);
  assert.match(asyncStateCopy("empty").message, /暂无/);
  assert.match(asyncStateCopy("forbidden").message, /权限/);
  assert.match(asyncStateCopy("network-error").message, /网络/);
  assert.match(asyncStateCopy("partial").message, /部分/);
});

test("dangerous operation stays disabled until exact confirmation", () => {
  const initial = dangerConfirmation("RELEASE", "");
  assert.equal(initial.disabled, true);
  assert.match(initial.message, /不可撤销|影响/);
  assert.equal(dangerConfirmation("RELEASE", "release").disabled, true);
  assert.equal(dangerConfirmation("RELEASE", "RELEASE").disabled, false);
});
