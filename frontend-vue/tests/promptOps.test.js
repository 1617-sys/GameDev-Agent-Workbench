import test from "node:test";
import assert from "node:assert/strict";
import { promptDiff, promptUpdatePayload, safePromptTemplate } from "../src/features/prompt-ops/promptOps.js";

test("prompt template response uses an explicit field whitelist", () => {
  const safe = safePromptTemplate({ templateUuid: "t-1", name: "Designer", agentType: "DESIGNER", systemPrompt: "system", userPromptTemplate: "user", version: 1, status: "ACTIVE", apiKey: "secret", internalToken: "secret" });
  assert.deepEqual(Object.keys(safe), ["templateUuid", "name", "agentType", "systemPrompt", "userPromptTemplate", "version", "status", "updatedAt"]);
  assert.equal("apiKey" in safe, false);
});

test("update advances the persisted version without allowing a stale overwrite", () => {
  assert.equal(promptUpdatePayload({ name: "A", agentType: "GAME_CONCEPT", systemPrompt: "s", userPromptTemplate: "u", version: 4, status: "ACTIVE" }).version, 5);
});

test("update diff requires explicit confirmation and preserves backend fields", () => {
  const before = safePromptTemplate({ templateUuid: "t-1", name: "A", agentType: "DESIGNER", systemPrompt: "old", userPromptTemplate: "user", version: 1, status: "ACTIVE" });
  const after = { ...before, systemPrompt: "new", version: 2 };
  assert.deepEqual(promptDiff(before, after).map(item => item.field), ["systemPrompt", "version"]);
});
