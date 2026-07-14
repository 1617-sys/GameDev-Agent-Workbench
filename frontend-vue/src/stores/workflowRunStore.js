import { markRaw, reactive } from "vue";

const TERMINAL_STATUSES = new Set(["SUCCESS", "FAILED", "TIMEOUT", "CANCELED"]);
const EVENT_TYPES = ["run.created", "run.status-changed", "step.status-changed", "artifact.available", "run.cancel-requested", "run.retry-requested", "run.recovered", "run.terminal"];
const UNRECOVERABLE_STATUS = new Set([401, 403, 404]);

export function createWorkflowRunStore({
  api,
  eventSourceFactory = null,
  setTimeoutImpl = setTimeout,
  clearTimeoutImpl = clearTimeout,
  reconnect = { maxAttempts: 5, baseDelayMs: 250, maxDelayMs: 5000 }
}) {
  const state = reactive({ runs: {} });
  const ensure = (uuid) => state.runs[uuid] ||= {
    snapshot: null, steps: [], artifacts: [], lastSequence: 0, connectionState: "idle", loading: false,
    actionLoading: false, error: null, source: null, retryTimer: null, retryAttempts: 0, debug: []
  };
  const isTerminal = (run) => TERMINAL_STATUSES.has(run.snapshot?.status);
  const debug = (run, message) => run.debug.push({ at: Date.now(), message });
  const clearRetry = (run) => { if (run.retryTimer !== null) clearTimeoutImpl(run.retryTimer); run.retryTimer = null; };
  const clearProtectedData = (run) => { run.snapshot = null; run.steps = []; run.artifacts = []; run.lastSequence = 0; };

  function applySnapshot(uuid, snapshot) {
    if (!snapshot || snapshot.workflowRunUuid !== uuid) throw new Error("Invalid workflow run snapshot");
    const run = ensure(uuid);
    run.snapshot = snapshot;
    run.steps = Array.isArray(snapshot.steps) ? snapshot.steps : [];
    run.artifacts = Array.isArray(snapshot.artifacts) ? snapshot.artifacts : [];
    run.lastSequence = Number.isSafeInteger(Number(snapshot.lastSequence)) && Number(snapshot.lastSequence) >= 0 ? Number(snapshot.lastSequence) : 0;
    run.error = null;
    return run;
  }

  function applyEvent(uuid, event) {
    const run = ensure(uuid);
    const sequence = Number(event?.sequence);
    if (!event || event.workflowRunUuid !== uuid || !Number.isSafeInteger(sequence) || sequence < 0) {
      debug(run, "Ignored invalid SSE event");
      return "invalid";
    }
    if (sequence <= run.lastSequence) {
      debug(run, `Ignored duplicate or stale event ${sequence}`);
      return "stale";
    }
    if (sequence > run.lastSequence + 1) {
      debug(run, `Detected SSE sequence gap before ${sequence}`);
      return "gap";
    }
    if (!run.snapshot) return "snapshot-required";

    const snapshot = { ...run.snapshot };
    if (event.status && event.eventType?.startsWith("run.")) snapshot.status = event.status;
    if (Number.isSafeInteger(Number(event.attempt))) snapshot.attempt = Number(event.attempt);
    run.lastSequence = sequence;
    snapshot.lastSequence = sequence;
    run.snapshot = snapshot;

    if (event.eventType === "step.status-changed") {
      const index = run.steps.findIndex((step) => step.stepKey === event.stepKey);
      if (index < 0) return "snapshot-required";
      run.steps = run.steps.map((step, position) => position === index ? { ...step, status: event.status, attempt: event.attempt ?? step.attempt } : step);
    }
    if (event.eventType === "artifact.available") return "snapshot-required";
    return event.eventType === "run.terminal" || isTerminal(run) ? "terminal" : "applied";
  }

  async function loadRun(uuid) {
    const run = ensure(uuid);
    run.loading = true;
    try {
      applySnapshot(uuid, await api.getRun(uuid));
      return run;
    } catch (error) {
      run.error = { code: error.code || "NETWORK", message: error.message || "Unable to load workflow run" };
      if (UNRECOVERABLE_STATUS.has(Number(error.status))) {
        disconnect(uuid);
        clearProtectedData(run);
        run.connectionState = "forbidden";
      }
      return run;
    } finally { run.loading = false; }
  }

  function disconnect(uuid) {
    const run = ensure(uuid);
    clearRetry(run);
    run.source?.close();
    run.source = null;
    if (run.connectionState !== "forbidden") run.connectionState = "idle";
  }

  function scheduleReconnect(uuid, reason) {
    const run = ensure(uuid);
    if (isTerminal(run) || run.connectionState === "forbidden" || run.retryTimer !== null) return;
    if (run.retryAttempts >= reconnect.maxAttempts) {
      run.connectionState = "error";
      run.error = { code: "SSE_RECONNECT_EXHAUSTED", message: "实时连接已停止，请刷新后重试。" };
      return;
    }
    const delay = Math.min(reconnect.maxDelayMs, reconnect.baseDelayMs * (2 ** run.retryAttempts));
    run.retryAttempts += 1;
    run.connectionState = "reconnecting";
    debug(run, `${reason}; reconnecting in ${delay}ms`);
    run.retryTimer = setTimeoutImpl(async () => {
      run.retryTimer = null;
      await open(uuid);
    }, delay);
  }

  async function recover(uuid, reason) {
    const run = ensure(uuid);
    if (run.source) { run.source.close(); run.source = null; }
    await loadRun(uuid);
    if (run.connectionState === "forbidden" || isTerminal(run)) { disconnect(uuid); return; }
    scheduleReconnect(uuid, reason);
  }

  function attach(source, uuid) {
    const handle = async (message, expectedType) => {
      const run = ensure(uuid);
      if (run.source !== source) return;
      let event;
      try { event = JSON.parse(message.data); } catch { await recover(uuid, "Invalid SSE payload"); return; }
      if (expectedType && !event.eventType) event.eventType = expectedType;
      const result = applyEvent(uuid, event);
      if (result === "gap" || result === "invalid" || result === "snapshot-required") await recover(uuid, "SSE snapshot correction required");
      else if (result === "terminal") disconnect(uuid);
    };
    source.addEventListener("snapshot", async (message) => {
      const run = ensure(uuid);
      if (run.source !== source) return;
      try {
        applySnapshot(uuid, JSON.parse(message.data));
        run.connectionState = "connected";
        run.retryAttempts = 0;
        if (isTerminal(run)) disconnect(uuid);
      } catch { await recover(uuid, "Invalid SSE snapshot"); }
    });
    EVENT_TYPES.forEach((type) => source.addEventListener(type, (message) => handle(message, type)));
    source.onerror = (error = {}) => {
      const run = ensure(uuid);
      if (run.source !== source) return;
      const status = Number(error.status);
      if (UNRECOVERABLE_STATUS.has(status)) {
        run.error = { code: String(status), message: status === 404 ? "未找到该运行记录。" : "无权订阅该运行记录。" };
        clearProtectedData(run);
        run.connectionState = "forbidden";
        disconnect(uuid);
        return;
      }
      recover(uuid, "SSE transport error");
    };
  }

  function connect(uuid) {
    const run = ensure(uuid);
    disconnect(uuid);
    if (!run.snapshot || isTerminal(run) || run.connectionState === "forbidden") return null;
    const source = eventSourceFactory
      ? eventSourceFactory(api.eventsUrl(uuid), { lastEventId: run.lastSequence })
      : api.openEvents(uuid, run.lastSequence);
    run.source = markRaw(source);
    run.connectionState = "connecting";
    attach(source, uuid);
    return source;
  }

  async function open(uuid) {
    const run = await loadRun(uuid);
    if (!run.snapshot || run.connectionState === "forbidden" || isTerminal(run)) { if (isTerminal(run)) disconnect(uuid); return run; }
    connect(uuid);
    return run;
  }

  function disconnectAll() { Object.keys(state.runs).forEach(disconnect); }
  async function runCommand(uuid, action) {
    const run = ensure(uuid); run.actionLoading = true;
    try {
      await api[action](uuid);
      const latest = await loadRun(uuid);
      if (isTerminal(latest)) disconnect(uuid);
      else if (!latest.source && latest.snapshot) connect(uuid);
      return latest;
    }
    catch (error) { run.error = { code: error.code || "NETWORK", message: error.message }; throw error; }
    finally { run.actionLoading = false; }
  }
  return { state, ensure, open, loadRun, applySnapshot, applyEvent, connect, disconnect, disconnectAll, cancel: (uuid) => runCommand(uuid, "cancel"), retry: (uuid) => runCommand(uuid, "retry") };
}
