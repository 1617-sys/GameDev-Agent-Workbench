import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { defaultGameConfig, sha256Hex } from "../src/features/demo/runtime/gameConfig.js";
import { selectPlayableGameConfigSummary, validatedPlayableConfig } from "../src/features/runs/playableArtifact.js";

const summary = {
  artifactUuid: "11111111-1111-1111-1111-111111111111",
  type: "GAME_CONFIG",
  runtimeEligible: true,
  schemaKey: "game-config",
  schemaVersion: "2.0",
  contentDigest: "a".repeat(64)
};

describe("playable artifact gate", () => {
  it("selects only an eligible v2 config from a successful run", () => {
    assert.deepEqual(selectPlayableGameConfigSummary([summary], "SUCCESS"), summary);
    assert.equal(selectPlayableGameConfigSummary([{ ...summary, runtimeEligible: false }], "SUCCESS"), null);
    assert.equal(selectPlayableGameConfigSummary([summary], "FAILED"), null);
    assert.equal(selectPlayableGameConfigSummary([{ ...summary, schemaVersion: "1.0" }], "SUCCESS"), null);
    const retried = { ...summary, artifactUuid: "33333333-3333-3333-3333-333333333333", sourceAttempt: 2 };
    assert.deepEqual(selectPlayableGameConfigSummary([{ ...summary, sourceAttempt: 1 }, retried], "SUCCESS"), retried);
  });

  it("revalidates exact artifact content before mounting runtime", () => {
    const content = JSON.stringify(defaultGameConfig);
    const eligibleSummary = { ...summary, contentDigest: sha256Hex(content) };
    const detail = { ...eligibleSummary, artifactType: summary.type, content };
    assert.equal(validatedPlayableConfig(detail, eligibleSummary)?.metadata.schemaVersion, "2.0");
    assert.equal(validatedPlayableConfig({ ...detail, runtimeEligible: false }, eligibleSummary), null);
    assert.equal(validatedPlayableConfig({ ...detail, content: "{}" }, eligibleSummary), null);
    assert.equal(validatedPlayableConfig(detail, { ...eligibleSummary, artifactUuid: "22222222-2222-2222-2222-222222222222" }), null);
  });
});
