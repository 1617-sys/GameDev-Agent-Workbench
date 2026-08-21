import test from "node:test";
import assert from "node:assert/strict";
import { createArcadeCollectSpec, generationStatusMeta, parsePersistedJson } from "../src/shared/presentation/gameSpec.js";

test("builds a closed arcade_collect GameSpec from constrained controls", () => {
  const spec = createArcadeCollectSpec({
    title: "Night Relay",
    width: 1200,
    height: 700,
    collectibleCount: 4,
    enemyCount: 2,
    obstacleCount: 3
  });
  assert.equal(spec.specVersion, "0.1");
  assert.equal(spec.archetype, "arcade_collect");
  assert.equal(spec.metadata.title, "Night Relay");
  assert.equal(spec.entities.filter((item) => item.type === "collectible").length, 4);
  assert.equal(spec.entities.filter((item) => item.type === "enemy").length, 2);
  assert.equal(spec.entities.filter((item) => item.type === "obstacle").length, 3);
  assert.equal(spec.entities.filter((item) => item.type === "exit").length, 1);
  assert.equal(spec.presentation.visualThemeId, "forest-01");
  assert.equal(spec.rules[0].then[0].action, "exit.unlock");
});

test("clamps unsafe form values and presents persisted run state", () => {
  const spec = createArcadeCollectSpec({ width: 20, height: 9000, playerHealth: 0, timeLimitSeconds: 9999 });
  assert.equal(spec.world.width, 640);
  assert.equal(spec.world.height, 1080);
  assert.equal(spec.world.timeLimitSeconds, 600);
  assert.equal(spec.player.health, 1);
  assert.equal(generationStatusMeta("PLAYTESTING").tone, "success");
  assert.deepEqual(parsePersistedJson('[{"code":"BAD"}]'), [{ code: "BAD" }]);
  assert.deepEqual(parsePersistedJson("not-json"), []);
});
