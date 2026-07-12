import { reactive } from "vue";

export function createWorkflowRunStore({ api, eventSourceFactory = (url) => new EventSource(url) }) {
  const state = reactive({ runs: {} });
  const ensure = (uuid) => state.runs[uuid] ||= { snapshot: null, steps: [], artifacts: [], lastSequence: 0, connectionState: "idle", loading: false, actionLoading: false, error: null, source: null };
  const applySnapshot = (uuid, snapshot) => { const run = ensure(uuid); run.snapshot = snapshot; run.steps = snapshot.steps || []; run.artifacts = snapshot.artifacts || []; run.lastSequence = Number(snapshot.lastSequence || 0); run.error = null; return run; };
  const applyEvent = (uuid, event) => { const run = ensure(uuid); const sequence = Number(event.sequence); if (!Number.isFinite(sequence) || sequence <= run.lastSequence) return false; run.lastSequence = sequence; return true; };
  async function loadRun(uuid) { const run = ensure(uuid); run.loading = true; try { applySnapshot(uuid, await api.getRun(uuid)); } catch (error) { run.error = { code: error.code || "NETWORK", message: error.message }; } finally { run.loading = false; } return run; }
  function disconnect(uuid) { const run = ensure(uuid); run.source?.close(); run.source = null; run.connectionState = "idle"; }
  function disconnectAll() { Object.keys(state.runs).forEach(disconnect); }
  async function runCommand(uuid, action) { const run = ensure(uuid); run.actionLoading = true; try { const snapshot = await api[action](uuid); return snapshot?.workflowRunUuid ? applySnapshot(uuid, snapshot) : await loadRun(uuid); } catch (error) { run.error = { code: error.code || "NETWORK", message: error.message }; throw error; } finally { run.actionLoading = false; } }
  function connect(uuid) { const run = ensure(uuid); disconnect(uuid); const source = eventSourceFactory(api.eventsUrl(uuid)); run.source = source; run.connectionState = "connecting"; source.addEventListener("snapshot", (message) => applySnapshot(uuid, JSON.parse(message.data))); source.onmessage = (message) => applyEvent(uuid, JSON.parse(message.data)); source.onerror = () => { run.connectionState = "error"; }; return source; }
  return { state, ensure, loadRun, applySnapshot, applyEvent, connect, disconnect, disconnectAll, cancel: (uuid) => runCommand(uuid, "cancel"), retry: (uuid) => runCommand(uuid, "retry") };
}
