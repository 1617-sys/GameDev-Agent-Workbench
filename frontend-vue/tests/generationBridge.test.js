import test from "node:test";
import assert from "node:assert/strict";
import { bridgePresentation } from "../src/features/generation/generationBridge.js";

test("incompatible bridge disables creation and exposes structured reasons", () => {
  const view = bridgePresentation({
    compatible: false,
    reasons: [{ code: "PLAYER_BRIDGE_DECLARATION_MISSING", path: "$.playerBridge", message: "契约缺失" }]
  });
  assert.equal(view.disabled, true);
  assert.match(view.label, /不兼容/);
  assert.deepEqual(view.reasons, ["PLAYER_BRIDGE_DECLARATION_MISSING · $.playerBridge · 契约缺失"]);
});

test("compatible and created bridge states provide safe actions", () => {
  assert.equal(bridgePresentation({ compatible: true }).disabled, false);
  const created = bridgePresentation({ compatible: true, prototypeVersionUuid: "version-1", reused: true });
  assert.equal(created.disabled, false);
  assert.match(created.label, /查看/);
  assert.equal(created.versionUuid, "version-1");
});
