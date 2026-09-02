import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const page = readFileSync(fileURLToPath(new URL("../src/features/episodes/EpisodeTracePage.vue", import.meta.url)), "utf8");
const api = readFileSync(fileURLToPath(new URL("../src/shared/api/episodes.js", import.meta.url)), "utf8");

test("episode evidence page uses persisted summaries and bounded step pages", () => {
  assert.match(api, /machine-episodes\/\$\{encodeURIComponent\(episodeUuid\)\}\/summary/);
  assert.match(api, /detail:.*machine-episodes\/\$\{encodeURIComponent\(episodeUuid\)\}/);
  assert.match(api, /steps\?page=\$\{page\}&size=\$\{size\}/);
  assert.match(page, /episodesApi\.steps\([^)]*,page,50\)/);
  assert.doesNotMatch(page, /scoreDelta\s*\*|completionRate\s*=|trajectoryDigest\s*=/);
});

test("trace UI displays evidence fields and explicitly labels mock model runs", () => {
  for (const field of ["observationDigest", "previousStateHash", "stateHash", "requestedAction", "personaId", "policyId", "usage"]) assert.match(page, new RegExp(field));
  assert.match(page, /mock 模型/);
  assert.doesNotMatch(page, /prompt(?:Text|Content)|apiKey|internalToken/i);
  assert.match(page, /@media\(max-width:760px\)/);
});
