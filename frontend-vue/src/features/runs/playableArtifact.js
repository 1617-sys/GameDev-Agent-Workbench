import { sha256Hex, validateGameConfig } from "../demo/runtime/gameConfig.js";

export function selectPlayableGameConfigSummary(artifacts = [], runStatus = "") {
  if (runStatus !== "SUCCESS") return null;
  return artifacts.filter((artifact) =>
    (artifact.type || artifact.artifactType) === "GAME_CONFIG"
    && artifact.runtimeEligible === true
    && artifact.schemaKey === "game-config"
    && artifact.schemaVersion === "2.0"
    && /^[0-9a-f]{64}$/.test(artifact.contentDigest || "")
  ).sort((left, right) => (right.sourceAttempt || 0) - (left.sourceAttempt || 0))[0] || null;
}

export function validatedPlayableConfig(detail, expected = {}) {
  if (!detail || detail.artifactType !== "GAME_CONFIG"
    || detail.runtimeEligible !== true
    || detail.schemaKey !== "game-config"
    || detail.schemaVersion !== "2.0"
    || typeof detail.content !== "string"
    || !/^[0-9a-f]{64}$/.test(detail.contentDigest || "")
    || detail.contentDigest !== expected.contentDigest
    || detail.artifactUuid !== expected.artifactUuid
    || sha256Hex(detail.content) !== detail.contentDigest) return null;
  const result = validateGameConfig(detail.content);
  return result.valid && !result.migrated ? result.config : null;
}
