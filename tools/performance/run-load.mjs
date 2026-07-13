import { execFile, execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required.`);
  return value;
}

function integer(name, fallback) {
  const parsed = Number(process.env[name] ?? fallback);
  if (!Number.isInteger(parsed)) throw new Error(`${name} must be an integer.`);
  return parsed;
}

const config = {
  apiBaseUrl: required("PERF_API_BASE_URL"),
  agentBaseUrl: required("PERF_AGENT_BASE_URL"),
  rabbitApiBaseUrl: required("PERF_RABBITMQ_API_BASE_URL"),
  rabbitUsername: required("PERF_RABBITMQ_USERNAME"),
  rabbitPassword: required("PERF_RABBITMQ_PASSWORD"),
  composeProject: required("PERF_COMPOSE_PROJECT"),
  mysqlContainer: required("PERF_MYSQL_CONTAINER"),
  fixturePrefix: required("PERF_FIXTURE_PREFIX"),
  outputDir: required("PERF_OUTPUT_DIR"),
  warmupSeconds: integer("PERF_WARMUP_SECONDS", 60),
  measurementSeconds: integer("PERF_MEASUREMENT_SECONDS", 300),
  maxRequests: integer("PERF_MAX_REQUESTS", 1000),
  referenceEligible: process.env.PERF_REFERENCE_ELIGIBLE === "true",
  hardDeadlineMs: Date.parse(required("PERF_HARD_DEADLINE_UTC")),
};

if (!/^r7perf-[a-z0-9]{8}$/.test(config.fixturePrefix)) {
  throw new Error("PERF_FIXTURE_PREFIX is outside the controlled R7 performance namespace.");
}

mkdirSync(config.outputDir, { recursive: true });

const raw = {
  providerMode: "fake",
  fixtureVersion: "r7-performance-fixed-agent-v1",
  uniqueRequests: [],
  resourceSamples: [],
  queueSamples: [],
  monitorErrors: [],
};

const summary = {
  providerMode: "fake",
  fixtureVersion: "r7-performance-fixed-agent-v1",
  fixedAgentLatencyMs: 300,
  configuration: {
    uniqueConcurrency: 20,
    sameKeyConcurrency: 10,
    queryConnections: 20,
    sseConnections: 20,
    consumerProcesses: 2,
    warmupSeconds: config.warmupSeconds,
    measurementSeconds: config.measurementSeconds,
    maxRequests: config.maxRequests,
    percentileMethod: "nearest-rank",
    referenceEligible: config.referenceEligible,
  },
  startedAtUtc: new Date().toISOString(),
  phases: {},
  checks: {},
  conclusion: "BLOCKED",
};

class ContractBlocker extends Error {
  constructor(message, details) {
    super(message);
    this.name = "ContractBlocker";
    this.details = details;
  }
}

function writeJson(filename, value) {
  writeFileSync(path.join(config.outputDir, filename), `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function boundedTimeout(requestedMs) {
  const remaining = config.hardDeadlineMs - Date.now();
  if (remaining <= 0) throw new ContractBlocker("The 15-minute performance harness deadline was reached.", { owner: "R7", timeout: true });
  return Math.max(1, Math.min(requestedMs, remaining));
}

function percentile(values, percentileValue) {
  if (!values.length) return null;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil((percentileValue / 100) * sorted.length) - 1)];
}

function distribution(values) {
  return {
    samples: values.length,
    p50: percentile(values, 50),
    p95: percentile(values, 95),
    p99: percentile(values, 99),
    max: values.length ? Math.max(...values) : null,
  };
}

function sqlLiteral(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function mysqlRows(sql) {
  const encoded = Buffer.from(sql, "utf8").toString("base64");
  const command = `printf %s '${encoded}' | base64 -d | mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE"`;
  const output = execFileSync("docker", ["exec", "-i", config.mysqlContainer, "sh", "-lc", command], {
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
    timeout: boundedTimeout(10_000),
    windowsHide: true,
  }).trim();
  if (!output) return [];
  return output.split(/\r?\n/).map((line) => line.split("\t"));
}

async function apiRequest(urlPath, { token, method = "GET", body, headers = {}, timeoutMs = 10_000 } = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), boundedTimeout(timeoutMs));
  const startedAt = performance.now();
  try {
    const response = await fetch(`${config.apiBaseUrl}${urlPath}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
    const text = await response.text();
    let payload = null;
    try {
      payload = text ? JSON.parse(text) : null;
    } catch {
      payload = null;
    }
    return {
      httpStatus: response.status,
      code: payload?.code ?? null,
      message: payload?.message ?? null,
      data: payload?.data ?? null,
      latencyMs: Math.round((performance.now() - startedAt) * 100) / 100,
    };
  } catch (error) {
    return {
      httpStatus: null,
      code: null,
      message: error.name === "AbortError" ? "client timeout" : error.message,
      data: null,
      latencyMs: Math.round((performance.now() - startedAt) * 100) / 100,
    };
  } finally {
    clearTimeout(timeout);
  }
}

function requireSuccess(response, operation) {
  if (response.httpStatus === null || response.httpStatus >= 400 || response.code !== 0) {
    throw new Error(`${operation} failed (HTTP ${response.httpStatus ?? "timeout"}, code ${response.code ?? "missing"}, message ${response.message ?? "missing"}).`);
  }
  return response.data;
}

async function createFixture(index) {
  const suffix = config.fixturePrefix.slice(-8);
  const username = `${config.fixturePrefix}-u${String(index).padStart(2, "0")}`;
  const password = `R7p-${suffix}-A1!`;
  requireSuccess(await apiRequest("/api/auth/register", { method: "POST", body: { username, password } }), `register user ${index}`);
  const login = requireSuccess(await apiRequest("/api/auth/login", { method: "POST", body: { username, password } }), `login user ${index}`);
  const project = requireSuccess(await apiRequest("/api/projects", {
    token: login.token,
    method: "POST",
    body: {
      name: `R7 performance ${index}`,
      gameType: "top_down_collect",
      targetPlatform: "web",
      description: "Controlled R7 performance fixture.",
    },
  }), `create project ${index}`);
  return { index, username, token: login.token, projectUuid: project.projectUuid };
}

async function submit(fixture, idempotencyKey, idea) {
  return apiRequest(`/api/v1/projects/${encodeURIComponent(fixture.projectUuid)}/workflow-runs`, {
    token: fixture.token,
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: { workflowKey: "DEMO_GAME_CONFIG", idea, context: "R7 controlled performance load" },
    timeoutMs: 5_000,
  });
}

async function waitForTerminal(fixture, workflowRunUuid, timeoutMs = 30_000) {
  const deadline = Math.min(Date.now() + timeoutMs, config.hardDeadlineMs);
  while (Date.now() < deadline) {
    const response = await apiRequest(`/api/v1/workflow-runs/${encodeURIComponent(workflowRunUuid)}`, {
      token: fixture.token,
      timeoutMs: 5_000,
    });
    if (response.code === 0 && ["SUCCESS", "FAILED", "TIMEOUT", "CANCELED"].includes(response.data?.status)) {
      return response.data;
    }
    await sleep(250);
  }
  throw new Error(`Workflow ${workflowRunUuid} did not reach a terminal state within ${timeoutMs} ms.`);
}

async function preflight(fixture) {
  const key = `preflight-${config.fixturePrefix.slice(-8)}`;
  const startedAt = performance.now();
  const response = await submit(fixture, key, "R7 performance preflight");
  summary.phases.preflight = {
    httpStatus: response.httpStatus,
    responseCode: response.code,
    responseMessage: response.message,
    apiLatencyMs: response.latencyMs,
    workflowRunUuid: response.data?.workflowRunUuid ?? null,
  };
  if (response.httpStatus !== 202 || response.code !== 0 || !response.data?.workflowRunUuid) {
    throw new ContractBlocker("The asynchronous submission prerequisite rejected the performance preflight.", {
      owner: "R3",
      httpStatus: response.httpStatus,
      responseCode: response.code,
      responseMessage: response.message,
      workflowRunUuid: null,
    });
  }
  const run = await waitForTerminal(fixture, response.data.workflowRunUuid);
  const completionMs = Math.round((performance.now() - startedAt) * 100) / 100;
  Object.assign(summary.phases.preflight, { status: run.status, completionMs });
  if (run.status !== "SUCCESS") {
    throw new ContractBlocker("The asynchronous workflow prerequisite did not complete successfully.", {
      owner: "R2/R3",
      workflowRunUuid: response.data.workflowRunUuid,
      status: run.status,
    });
  }
  return response.data.workflowRunUuid;
}

function workflowFactSnapshot(workflowRunUuid) {
  const uuid = sqlLiteral(workflowRunUuid);
  const rows = mysqlRows(`
    select
      (select count(*) from workflow_run where workflow_run_uuid=${uuid}),
      (select count(*) from workflow_step_run where workflow_run_uuid=${uuid} and status='SUCCESS'),
      (select count(distinct agent_run_id) from workflow_step_run where workflow_run_uuid=${uuid} and agent_run_id is not null),
      (select count(*) from agent_artifact a join workflow_step_run s on s.id=a.step_run_id where s.workflow_run_uuid=${uuid}),
      (select count(*) from model_call_metric m join workflow_step_run s on s.agent_run_id=m.agent_run_id where s.workflow_run_uuid=${uuid});
  `);
  const row = rows[0] ?? ["0", "0", "0", "0", "0"];
  return {
    workflowRuns: Number(row[0]),
    successfulSteps: Number(row[1]),
    linkedAgentRuns: Number(row[2]),
    artifacts: Number(row[3]),
    metrics: Number(row[4]),
  };
}

async function sameKeyCohort(fixture) {
  const key = `same-${config.fixturePrefix.slice(-8)}`;
  const responses = await Promise.all(Array.from({ length: 10 }, () =>
    submit(fixture, key, "R7 identical same-key workload")));
  const workflowRunUuids = [...new Set(responses.map((response) => response.data?.workflowRunUuid).filter(Boolean))];
  const accepted = responses.filter((response) => response.httpStatus === 202 && response.code === 0).length;
  if (accepted !== 10 || workflowRunUuids.length !== 1) {
    throw new ContractBlocker("The 10-way same-key cohort did not return one reusable WorkflowRun.", {
      owner: "R3",
      accepted,
      distinctWorkflowRunUuids: workflowRunUuids.length,
      responseCodes: [...new Set(responses.map((response) => response.code))],
    });
  }
  const workflowRunUuid = workflowRunUuids[0];
  const terminal = await waitForTerminal(fixture, workflowRunUuid);
  const persisted = Number(mysqlRows(`
    select count(*) from workflow_run wr
    join game_project gp on gp.id=wr.project_id
    join sys_user su on su.id=wr.user_id
    where su.username=${sqlLiteral(fixture.username)} and gp.project_uuid=${sqlLiteral(fixture.projectUuid)}
      and wr.workflow_type='DEMO_GAME_CONFIG' and wr.idempotency_key=${sqlLiteral(key)};
  `)[0]?.[0] ?? 0);
  summary.phases.sameKey = {
    concurrency: 10,
    accepted,
    distinctWorkflowRunUuids: workflowRunUuids.length,
    persistedWorkflowRuns: persisted,
    status: terminal.status,
    apiLatencyMs: distribution(responses.map((response) => response.latencyMs)),
    workflowRunUuid,
  };
  return workflowRunUuid;
}

async function rabbitRequest(urlPath, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), boundedTimeout(5_000));
  try {
    const response = await fetch(`${config.rabbitApiBaseUrl}${urlPath}`, {
      ...options,
      headers: {
        Authorization: `Basic ${Buffer.from(`${config.rabbitUsername}:${config.rabbitPassword}`).toString("base64")}`,
        "Content-Type": "application/json",
        ...(options.headers ?? {}),
      },
      signal: controller.signal,
    });
    const body = await response.json();
    if (!response.ok) throw new Error(`RabbitMQ management request failed with HTTP ${response.status}.`);
    return body;
  } finally {
    clearTimeout(timeout);
  }
}

async function fakeAgentMetrics() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), boundedTimeout(5_000));
  try {
    const response = await fetch(`${config.agentBaseUrl}/metrics`, { signal: controller.signal });
    if (!response.ok) throw new Error(`Fake Agent metrics returned HTTP ${response.status}.`);
    return response.json();
  } finally {
    clearTimeout(timeout);
  }
}

async function queueSnapshot() {
  const queue = await rabbitRequest("/api/queues/%2F/workflow.run.execute");
  return {
    sampledAtUtc: new Date().toISOString(),
    messages: Number(queue.messages ?? 0),
    ready: Number(queue.messages_ready ?? 0),
    unacknowledged: Number(queue.messages_unacknowledged ?? 0),
    consumers: Number(queue.consumers ?? 0),
  };
}

async function waitForQueueDrain(timeoutMs = 90_000) {
  const startedAt = Date.now();
  const deadline = Math.min(startedAt + timeoutMs, config.hardDeadlineMs);
  while (Date.now() < deadline) {
    const snapshot = await queueSnapshot();
    raw.queueSamples.push(snapshot);
    if (snapshot.messages === 0) return { drained: true, elapsedMs: Date.now() - startedAt, ...snapshot };
    await sleep(1_000);
  }
  const snapshot = await queueSnapshot();
  return { drained: false, elapsedMs: Date.now() - startedAt, ...snapshot };
}

async function duplicateDelivery(workflowRunUuid) {
  const eventRows = mysqlRows(`
    select event_uuid, workflow_run_uuid, publish_attempt, trace_id,
           date_format(created_at, '%Y-%m-%dT%H:%i:%s')
    from outbox_event where workflow_run_uuid=${sqlLiteral(workflowRunUuid)} limit 1;
  `);
  if (eventRows.length !== 1) {
    throw new ContractBlocker("No committed Outbox event was available for the duplicate-delivery assertion.", {
      owner: "R3",
      workflowRunUuid,
      outboxRows: eventRows.length,
    });
  }
  const [eventId, runUuid, publishAttempt, traceId, createdAt] = eventRows[0];
  const message = {
    schemaVersion: 1,
    messageId: eventId,
    eventId,
    workflowRunUuid: runUuid,
    attempt: Math.max(1, Number(publishAttempt)),
    traceId,
    createdAt,
  };
  const before = workflowFactSnapshot(workflowRunUuid);
  const publish = async () => rabbitRequest("/api/exchanges/%2F/workflow.events/publish", {
    method: "POST",
    body: JSON.stringify({
      properties: { content_type: "application/json", headers: { "x-r7-fixture": "duplicate-delivery" } },
      routing_key: "workflow.run.requested",
      payload: JSON.stringify(message),
      payload_encoding: "string",
    }),
  });
  const published = await Promise.all([publish(), publish()]);
  if (published.some((item) => item.routed !== true)) throw new Error("RabbitMQ did not route both controlled duplicate deliveries.");
  const drain = await waitForQueueDrain();
  await sleep(1_000);
  const after = workflowFactSnapshot(workflowRunUuid);
  summary.phases.duplicateDelivery = {
    messageId: eventId,
    workflowRunUuid,
    deliveries: 2,
    before,
    after,
    queueDrain: drain,
    factsUnchanged: JSON.stringify(before) === JSON.stringify(after),
  };
}

async function runUniquePhase(name, fixtures, durationSeconds, budget) {
  const deadline = Math.min(Date.now() + durationSeconds * 1_000, config.hardDeadlineMs);
  const records = [];
  let failureCount = 0;
  let stop = false;
  const workers = fixtures.slice(0, 20).map(async (fixture) => {
    while (!stop && Date.now() < deadline && budget.sent < config.maxRequests) {
      const sequence = budget.sent;
      budget.sent += 1;
      const key = `${name}-${config.fixturePrefix.slice(-8)}-${sequence}`;
      const startedAt = performance.now();
      const response = await submit(fixture, key, `R7 ${name} unique workload ${sequence}`);
      const record = {
        phase: name,
        sequence,
        httpStatus: response.httpStatus,
        responseCode: response.code,
        apiLatencyMs: response.latencyMs,
        workflowRunUuid: response.data?.workflowRunUuid ?? null,
        status: null,
        completionMs: null,
        error: null,
      };
      if (response.httpStatus !== 202 || response.code !== 0 || !record.workflowRunUuid) {
        record.error = response.message ?? "submission rejected";
        failureCount += 1;
      } else {
        try {
          const terminal = await waitForTerminal(fixture, record.workflowRunUuid);
          record.status = terminal.status;
          record.completionMs = Math.round((performance.now() - startedAt) * 100) / 100;
          if (terminal.status !== "SUCCESS") {
            record.error = `terminal status ${terminal.status}`;
            failureCount += 1;
          }
        } catch (error) {
          record.error = error.message;
          failureCount += 1;
        }
      }
      records.push(record);
      raw.uniqueRequests.push(record);
      if (failureCount >= 20) stop = true;
    }
  });
  await Promise.all(workers);
  const accepted = records.filter((record) => record.httpStatus === 202 && record.responseCode === 0).length;
  const completed = records.filter((record) => record.status === "SUCCESS").length;
  const errors = records.filter((record) => record.error).length;
  return {
    durationSeconds,
    sent: records.length,
    accepted,
    completed,
    errors,
    errorRate: records.length ? errors / records.length : 1,
    throughputPerSecond: completed / durationSeconds,
    apiLatencyMs: distribution(records.map((record) => record.apiLatencyMs)),
    completionLatencyMs: distribution(records.filter((record) => record.completionMs !== null).map((record) => record.completionMs)),
  };
}

async function readLoad(fixture, workflowRunUuid) {
  const queries = await Promise.all(Array.from({ length: 20 }, () =>
    apiRequest(`/api/v1/workflow-runs/${encodeURIComponent(workflowRunUuid)}`, { token: fixture.token, timeoutMs: 5_000 })));
  const sse = await Promise.all(Array.from({ length: 20 }, async () => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), boundedTimeout(5_000));
    const startedAt = performance.now();
    try {
      const response = await fetch(`${config.apiBaseUrl}/api/v1/workflow-runs/${encodeURIComponent(workflowRunUuid)}/events`, {
        headers: { Accept: "text/event-stream", Authorization: `Bearer ${fixture.token}`, "Last-Event-ID": "0" },
        signal: controller.signal,
      });
      const latencyMs = Math.round((performance.now() - startedAt) * 100) / 100;
      await response.body?.cancel();
      return { httpStatus: response.status, latencyMs, error: response.ok ? null : `HTTP ${response.status}` };
    } catch (error) {
      return { httpStatus: null, latencyMs: Math.round((performance.now() - startedAt) * 100) / 100, error: error.name === "AbortError" ? "client timeout" : error.message };
    } finally {
      clearTimeout(timeout);
    }
  }));
  summary.phases.readLoad = {
    query: {
      connections: queries.length,
      errors: queries.filter((item) => item.httpStatus !== 200 || item.code !== 0).length,
      latencyMs: distribution(queries.map((item) => item.latencyMs)),
    },
    sse: {
      connections: sse.length,
      errors: sse.filter((item) => item.error).length,
      latencyMs: distribution(sse.map((item) => item.latencyMs)),
    },
  };
}

function databaseSummary(sameKey) {
  const prefix = sqlLiteral(`${config.fixturePrefix}%`);
  const rows = mysqlRows(`
    select
      (select count(*) from workflow_run wr join sys_user su on su.id=wr.user_id where su.username like ${prefix}),
      (select count(*) from agent_run ar join sys_user su on su.id=ar.user_id where su.username like ${prefix}),
      (select count(*) from model_call_metric m join agent_run ar on ar.id=m.agent_run_id join sys_user su on su.id=ar.user_id where su.username like ${prefix}),
      (select count(*) from agent_artifact a join game_project gp on gp.id=a.project_id join sys_user su on su.id=gp.user_id where su.username like ${prefix}),
      (select count(*) from (select wr.user_id, wr.project_id, wr.workflow_type, wr.idempotency_key from workflow_run wr join sys_user su on su.id=wr.user_id where su.username like ${prefix} group by wr.user_id, wr.project_id, wr.workflow_type, wr.idempotency_key having count(*) > 1) duplicate_keys),
      (select count(*) from (select s.id from workflow_step_run s join workflow_run wr on wr.id=s.workflow_run_id join sys_user su on su.id=wr.user_id left join agent_artifact a on a.step_run_id=s.id left join model_call_metric m on m.agent_run_id=s.agent_run_id where su.username like ${prefix} and s.status='SUCCESS' group by s.id having count(distinct a.id)>1 or count(distinct m.id)>1) duplicate_facts);
  `);
  const row = rows[0] ?? ["0", "0", "0", "0", "0", "0"];
  return {
    workflowRuns: Number(row[0]),
    agentRuns: Number(row[1]),
    modelCallMetrics: Number(row[2]),
    artifacts: Number(row[3]),
    duplicateIdempotencyGroups: Number(row[4]),
    successfulStepsWithDuplicateFacts: Number(row[5]),
    sameKeyWorkflowRuns: sameKey.persistedWorkflowRuns,
  };
}

function parseBytes(value) {
  const match = String(value).trim().match(/^([0-9.]+)\s*([KMGT]?i?B)$/i);
  if (!match) return null;
  const power = { B: 0, KB: 1, KIB: 1, MB: 2, MIB: 2, GB: 3, GIB: 3, TB: 4, TIB: 4 }[match[2].toUpperCase()];
  return Number(match[1]) * (1024 ** power);
}

async function sampleResources() {
  const format = '{{.ID}}\t{{.Names}}\t{{.Label "com.docker.compose.service"}}';
  const { stdout } = await execFileAsync("docker", ["ps", "--filter", `label=com.docker.compose.project=${config.composeProject}`, "--format", format], { timeout: boundedTimeout(5_000), windowsHide: true });
  const containers = stdout.trim().split(/\r?\n/).filter(Boolean).map((line) => {
    const [id, name, service] = line.split("\t");
    return { id, name, service };
  });
  if (!containers.length) return;
  const stats = await execFileAsync("docker", ["stats", "--no-stream", "--format", "{{json .}}", ...containers.map((item) => item.id)], { maxBuffer: 8 * 1024 * 1024, timeout: boundedTimeout(5_000), windowsHide: true });
  const sampledAtUtc = new Date().toISOString();
  const byName = new Map(containers.map((item) => [item.name, item]));
  for (const line of stats.stdout.trim().split(/\r?\n/).filter(Boolean)) {
    const item = JSON.parse(line);
    const container = byName.get(item.Name) ?? containers.find((entry) => item.Container?.startsWith(entry.id));
    raw.resourceSamples.push({
      sampledAtUtc,
      service: container?.service ?? "unknown",
      container: item.Name,
      cpuPercent: Number(String(item.CPUPerc ?? "0").replace("%", "")),
      memoryBytes: parseBytes(String(item.MemUsage ?? "0B").split("/")[0]),
      memoryPercent: Number(String(item.MemPerc ?? "0").replace("%", "")),
      pids: Number(item.PIDs ?? 0),
    });
  }
  raw.queueSamples.push(await queueSnapshot());
}

function startMonitor() {
  let busy = false;
  const tick = async () => {
    if (busy) return;
    busy = true;
    try {
      await sampleResources();
    } catch (error) {
      raw.monitorErrors.push({ sampledAtUtc: new Date().toISOString(), message: error.message });
    } finally {
      busy = false;
    }
  };
  void tick();
  const timer = setInterval(tick, 1_000);
  return async () => {
    clearInterval(timer);
    while (busy) await sleep(50);
  };
}

function resourceSummary() {
  const services = {};
  for (const sample of raw.resourceSamples) {
    services[sample.service] ??= { cpuPercent: [], memoryBytes: [], maxPids: 0 };
    services[sample.service].cpuPercent.push(sample.cpuPercent);
    if (sample.memoryBytes !== null) services[sample.service].memoryBytes.push(sample.memoryBytes);
    services[sample.service].maxPids = Math.max(services[sample.service].maxPids, sample.pids);
  }
  return Object.fromEntries(Object.entries(services).map(([service, values]) => [service, {
    cpuPercent: distribution(values.cpuPercent),
    memoryBytes: distribution(values.memoryBytes),
    maxPids: values.maxPids,
  }]));
}

async function containerStates() {
  const { stdout } = await execFileAsync("docker", ["ps", "-aq", "--filter", `label=com.docker.compose.project=${config.composeProject}`], { timeout: boundedTimeout(5_000), windowsHide: true });
  const ids = stdout.trim().split(/\r?\n/).filter(Boolean);
  if (!ids.length) return [];
  const result = await execFileAsync("docker", ["inspect", "--format", '{{.Name}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.RestartCount}}|{{.State.OOMKilled}}|{{.State.Status}}', ...ids], { timeout: boundedTimeout(5_000), windowsHide: true });
  return result.stdout.trim().split(/\r?\n/).filter(Boolean).map((line) => {
    const [name, service, restartCount, oomKilled, status] = line.split("|");
    return { name: name.replace(/^\//, ""), service, restartCount: Number(restartCount), oomKilled: oomKilled === "true", status };
  });
}

let stopMonitor = null;
let exitCode = 1;
try {
  stopMonitor = startMonitor();
  const fixtures = await Promise.all(Array.from({ length: 20 }, (_, index) => createFixture(index)));
  summary.fixture = { userCount: fixtures.length, projectCount: fixtures.length, namespace: config.fixturePrefix };

  await preflight(fixtures[0]);
  const sameKeyWorkflowRunUuid = await sameKeyCohort(fixtures[0]);
  await duplicateDelivery(sameKeyWorkflowRunUuid);

  const budget = { sent: 0 };
  summary.phases.warmup = await runUniquePhase("warmup", fixtures, config.warmupSeconds, budget);
  summary.phases.measurement = await runUniquePhase("measurement", fixtures, config.measurementSeconds, budget);
  const representativeRun = [...raw.uniqueRequests].reverse().find((record) => record.status === "SUCCESS")?.workflowRunUuid ?? sameKeyWorkflowRunUuid;
  await readLoad(fixtures[0], representativeRun);
  summary.queueDrain = await waitForQueueDrain();
  summary.database = databaseSummary(summary.phases.sameKey);
  summary.fakeAgent = await fakeAgentMetrics();
  summary.resources = resourceSummary();
  summary.applicationMetrics = {
    jvm: "NOT_EXPOSED",
    connectionPool: "NOT_EXPOSED",
    note: "The candidate exposes health probes only; the performance harness does not widen production Actuator exposure.",
  };
  summary.containerStates = await containerStates();
  summary.queue = {
    samples: raw.queueSamples.length,
    peakMessages: raw.queueSamples.length ? Math.max(...raw.queueSamples.map((item) => item.messages)) : null,
    peakReady: raw.queueSamples.length ? Math.max(...raw.queueSamples.map((item) => item.ready)) : null,
    peakUnacknowledged: raw.queueSamples.length ? Math.max(...raw.queueSamples.map((item) => item.unacknowledged)) : null,
    maxConsumers: raw.queueSamples.length ? Math.max(...raw.queueSamples.map((item) => item.consumers)) : null,
  };

  const measurement = summary.phases.measurement;
  const readLoadSummary = summary.phases.readLoad;
  summary.checks = {
    api202P95AtMost1000Ms: measurement.apiLatencyMs.p95 !== null && measurement.apiLatencyMs.p95 <= 1_000,
    errorRateAtMostOnePercent: measurement.errorRate <= 0.01,
    completionP95AtMost20000Ms: measurement.completionLatencyMs.p95 !== null && measurement.completionLatencyMs.p95 <= 20_000,
    sameKeyExactlyOneRun: summary.phases.sameKey.persistedWorkflowRuns === 1 && summary.phases.sameKey.distinctWorkflowRunUuids === 1,
    duplicateDeliveryFactsUnchanged: summary.phases.duplicateDelivery.factsUnchanged,
    noDuplicatePersistentFacts: summary.database.duplicateIdempotencyGroups === 0 && summary.database.successfulStepsWithDuplicateFacts === 0,
    queryP95AtMost1000Ms: readLoadSummary.query.errors === 0 && readLoadSummary.query.latencyMs.p95 <= 1_000,
    sseP95AtMost1000Ms: readLoadSummary.sse.errors === 0 && readLoadSummary.sse.latencyMs.p95 <= 1_000,
    queueDrainedWithin90Seconds: summary.queueDrain.drained && summary.queueDrain.elapsedMs <= 90_000,
    noContainerOomOrRestart: summary.containerStates.every((item) => !item.oomKilled && item.restartCount === 0),
    fakeAgentFixedAt300Ms: summary.fakeAgent.fixedLatencyMs === 300 && summary.fakeAgent.providerMode === "fake",
  };
  const thresholdsPassed = Object.values(summary.checks).every(Boolean);
  summary.conclusion = thresholdsPassed ? (config.referenceEligible ? "PASS" : "NON_BASELINE") : "FAIL";
  exitCode = summary.conclusion === "PASS" ? 0 : 1;
} catch (error) {
  summary.conclusion = error instanceof ContractBlocker ? "BLOCKED" : "FAIL";
  summary.blocker = {
    type: error.name,
    message: error.message,
    ...(error.details ?? {}),
  };
  try {
    summary.resources = resourceSummary();
    summary.containerStates = await containerStates();
  } catch (diagnosticError) {
    summary.diagnosticError = diagnosticError.message;
  }
  exitCode = 1;
} finally {
  if (stopMonitor) await stopMonitor();
  summary.completedAtUtc = new Date().toISOString();
  writeJson("raw-samples.json", raw);
  writeJson("summary.json", summary);
}

console.log(JSON.stringify({ conclusion: summary.conclusion, evidence: config.outputDir, blocker: summary.blocker ?? null }));
process.exit(exitCode);
