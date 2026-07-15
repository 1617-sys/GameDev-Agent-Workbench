import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { configuredImageKeys, RUNTIME_RESOURCE_MANIFEST } from "../src/features/demo/runtime/resourceManifest.js";

const fixturePath = fileURLToPath(new URL("../../docs/requirements/v3/examples/game-config-2.0/valid-minimal.json", import.meta.url));
const fixture = JSON.parse(readFileSync(fixturePath, "utf8"));

test("the runtime manifest contains exactly the RFC resource allow-list", () => {
  assert.deepEqual(Object.keys(RUNTIME_RESOURCE_MANIFEST).sort(), [
    "collectible.artifact", "collectible.core", "collectible.gem", "enemy.drone", "enemy.guard",
    "exit.door", "exit.portal", "obstacle.metal", "obstacle.stone", "obstacle.wood",
    "player.blue", "player.green", "sfx.collect", "sfx.hit", "sfx.lose", "sfx.silent", "sfx.win"
  ]);
});
test("configured images resolve only to repository-local assets", () => {
  const keys = configuredImageKeys(fixture);
  assert.deepEqual(keys, ["player.blue", "obstacle.stone", "collectible.artifact", "collectible.gem", "enemy.guard", "exit.door"]);
  for (const key of keys) {
    const descriptor = RUNTIME_RESOURCE_MANIFEST[key];
    assert.equal(descriptor.kind, "image");
    assert.match(descriptor.url, /^\/runtime-assets\/[a-z-]+\.svg$/);
  }
});
