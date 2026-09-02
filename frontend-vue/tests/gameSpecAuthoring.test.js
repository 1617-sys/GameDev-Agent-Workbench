import test from "node:test";
import assert from "node:assert/strict";
import { prepareAuthorRequest } from "../src/features/generation/gameSpecAuthoring.js";

const validSpec = JSON.stringify({
  title: "森林收集",
  archetype: "arcade_collect",
  entities: [{ type: "collectible" }, { type: "enemy" }]
});

test("from-scratch mode sends null currentSpec even when the editor contains the default GameSpec", () => {
  const result = prepareAuthorRequest("scratch", "生成森林收集游戏", validSpec);
  assert.equal(result.error, null);
  assert.deepEqual(result.request, { idea: "生成森林收集游戏", currentSpec: null });
});

test("revise mode rejects invalid JSON and sends a valid object with a summary", () => {
  const invalid = prepareAuthorRequest("revise", "修改玩法", "{bad json");
  assert.match(invalid.error, /合法的 JSON 对象/);
  assert.equal(invalid.request, null);

  const valid = prepareAuthorRequest("revise", "修改玩法", validSpec);
  assert.equal(valid.error, null);
  assert.equal(valid.request.currentSpec.title, "森林收集");
  assert.deepEqual(valid.summary, { title: "森林收集", archetype: "arcade_collect", entityCount: 2 });
});

test("switching back to scratch ignores an invalid revise-mode editor", () => {
  const result = prepareAuthorRequest("scratch", "重新生成", "{bad json");
  assert.equal(result.error, null);
  assert.equal(result.request.currentSpec, null);
});
