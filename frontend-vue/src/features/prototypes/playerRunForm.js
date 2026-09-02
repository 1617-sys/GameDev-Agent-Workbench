export function buildPlayerRunRequest(form, versionUuid, batchKey) {
  const episodeKey = `${batchKey}-1`.slice(0, 80);
  return {
    prototypeVersionUuid: versionUuid,
    clientBatchKey: batchKey,
    concurrency: Number(form.concurrency),
    episodes: [{
      clientEpisodeKey: episodeKey,
      personaId: form.personaId,
      policyKind: form.policyKind,
      seed: Number(form.seed),
      maxSteps: Number(form.maxSteps),
      policySeed: Number(form.seed),
      modelKey: "default"
    }]
  };
}

export function canStartPlayerRun({ capabilities, confirmed, busy }) {
  return capabilities.includes("player-runs.create") && confirmed && !busy;
}

export function createSingleFlightSubmitter(operation) {
  let inFlight = null;
  return () => {
    if (!inFlight) inFlight = Promise.resolve().then(operation).finally(() => { inFlight = null; });
    return inFlight;
  };
}
