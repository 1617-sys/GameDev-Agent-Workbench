import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const apiBase = process.env.FAULT_API_BASE_URL || "http://127.0.0.1:8080";
const evidenceDir = process.env.FAULT_EVIDENCE_DIR;
const containers = {
  mysql: process.env.FAULT_MYSQL_CONTAINER,
  redis: process.env.FAULT_REDIS_CONTAINER,
  rabbitmq: process.env.FAULT_RABBITMQ_CONTAINER,
  python: process.env.FAULT_PYTHON_CONTAINER,
  backend: process.env.FAULT_BACKEND_CONTAINER,
};
const fixtureName = process.env.FAULT_FIXTURE_USERNAME;
const failures = [];
const scenarios = [];
const recoveryActions = [];

function check(value, message) {
  if (!value) throw new Error(message);
}

function docker(args, options = {}) {
  return execFileSync("docker", args, { encoding: "utf8", timeout: 120_000, ...options }).trim();
}

function mysql(sql) {
  const escaped = sql.replaceAll("'", "'\"'\"'");
  return docker(["exec", "-i", containers.mysql, "sh", "-lc",
    `MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql --batch --skip-column-names -uroot -D \"$MYSQL_DATABASE\" -e '${escaped}'`])
    .split(/\r?\n/).filter(Boolean).map((line) => line.split("\t"));
}

async function request(pathname, { token = "", method = "GET", body, key, timeoutMs = 12_000 } = {}) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (key) headers["Idempotency-Key"] = key;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${apiBase}${pathname}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(timeoutMs),
  });
  const payload = await response.json().catch(() => null);
  return { httpStatus: response.status, code: payload?.code, message: payload?.message, data: payload?.data ?? payload };
}

async function api(pathname, options) {
  const result = await request(pathname, options);
  check(result.httpStatus >= 200 && result.httpStatus < 300 && result.code === 0,
    `${options?.method || "GET"} ${pathname} failed: HTTP ${result.httpStatus}, code ${result.code}, ${result.message || "no message"}`);
  return result.data;
}

function setAgentMode(value) {
  check(["normal", "429", "invalid", "delay"].includes(value), `Unsupported fake Agent mode: ${value}`);
  docker(["exec", containers.python, "sh", "-lc", `printf '%s\\n' '${value}' > /tmp/r7-fault-mode`]);
  if (value === "normal") recoveryActions.push({ atUtc: new Date().toISOString(), action: "fake Agent mode restored to normal" });
}

function unpause(container, label) {
  const paused = docker(["inspect", "--format", "{{.State.Paused}}", container]);
  if (paused === "true") {
    docker(["unpause", container]);
    recoveryActions.push({ atUtc: new Date().toISOString(), action: `${label} unpaused` });
  }
}

async function poll(label, fn, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await fn();
    if (last) return last;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`${label} timed out after ${timeoutMs}ms; last=${JSON.stringify(last)}`);
}

async function scenario(name, action) {
  const item = { name, startedAtUtc: new Date().toISOString(), status: "FAIL" };
  scenarios.push(item);
  try {
    item.evidence = await action();
    item.status = "PASS";
  } catch (error) {
    item.error = String(error.message || error).replace(/Bearer\s+\S+/gi, "Bearer [redacted]");
    failures.push(`${name}: ${item.error}`);
  } finally {
    item.completedAtUtc = new Date().toISOString();
  }
}

async function createFixture() {
  const password = `R7fault-${fixtureName.slice(-8)}-A1`;
  await api("/api/auth/register", { method: "POST", body: { username: fixtureName, password } });
  const login = await api("/api/auth/login", { method: "POST", body: { username: fixtureName, password } });
  const project = await api("/api/projects", { token: login.token, method: "POST", body: {
    name: "R7 fault fixture", gameType: "top_down_collect", targetPlatform: "web", description: "Controlled fault-only namespace.",
  }});
  return { token: login.token, projectUuid: project.projectUuid };
}

function submit(fixture, suffix) {
  return request(`/api/v1/projects/${fixture.projectUuid}/workflow-runs`, {
    token: fixture.token, method: "POST", key: `r7-fault-${fixtureName}-${suffix}`,
    body: { workflowKey: "DEMO_GAME_CONFIG", idea: `R7 controlled ${suffix}`, context: "fault fixture" },
  });
}

function persisted(runUuid) {
  const rows = mysql(`select wr.status, coalesce(wr.trace_id,''), wr.retry_count, wr.error_code,
    count(distinct ws.id), count(distinct ar.id), count(distinct m.id), count(distinct aa.id)
    from workflow_run wr left join workflow_step_run ws on ws.workflow_run_id=wr.id
    left join agent_run ar on ar.workflow_run_id=wr.id left join model_call_metric m on m.workflow_run_id=wr.id
    left join agent_artifact aa on aa.step_run_id=ws.id where wr.workflow_run_uuid='${runUuid}' group by wr.id`);
  if (!rows.length) return null;
  const r = rows[0];
  return { status: r[0], traceIdPresent: Boolean(r[1]), retryCount: Number(r[2]), errorCode: r[3] || null,
    stepCount: Number(r[4]), agentRuns: Number(r[5]), metrics: Number(r[6]), artifacts: Number(r[7]) };
}

check(evidenceDir && fixtureName, "Fault evidence directory and fixture username are required.");
check(Object.values(containers).every(Boolean), "All isolated Compose container IDs are required.");
mkdirSync(evidenceDir, { recursive: true });

let fixture;
try {
  fixture = await createFixture();

  await scenario("redis-unavailable-fail-closed", async () => {
    docker(["pause", containers.redis]);
    let response;
    try { response = await submit(fixture, "redis-down"); }
    catch (error) { response = { clientTimeout: true, message: error.name }; }
    finally { unpause(containers.redis, "redis"); }
    await new Promise((resolve) => setTimeout(resolve, 1500));
    const count = Number(mysql(`select count(*) from workflow_run where idempotency_key='r7-fault-${fixtureName}-redis-down'`)[0][0]);
    check(response.httpStatus !== 202 || response.code !== 0, "Redis outage was accepted as a successful async submission.");
    check(count === 0, "Redis outage left a WorkflowRun despite the fail-closed submission gate.");
    return { response, persistedRunCount: count };
  });

  await scenario("redis-lock-expiry-and-wrong-owner", async () => {
    const key = `r7:fault:lock:${fixtureName}`;
    const command = (script) => docker(["exec", containers.redis, "sh", "-lc", script]);
    command(`redis-cli --no-auth-warning -a \"$REDIS_PASSWORD\" set '${key}' owner-a EX 2 >/dev/null`);
    const wrongOwner = command(`redis-cli --no-auth-warning -a \"$REDIS_PASSWORD\" eval \"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end\" 1 '${key}' owner-b`);
    const retainedOwner = command(`redis-cli --no-auth-warning -a \"$REDIS_PASSWORD\" get '${key}'`);
    await new Promise((resolve) => setTimeout(resolve, 2300));
    const expired = command(`redis-cli --no-auth-warning -a \"$REDIS_PASSWORD\" exists '${key}'`);
    check(wrongOwner === "0" && retainedOwner === "owner-a", "Wrong owner removed or changed another owner's lock.");
    check(expired === "0", "Lock did not expire at its bounded TTL.");
    return { wrongOwnerDeleteCount: Number(wrongOwner), retainedOwnerHash: "owner-a", existsAfterTtl: Number(expired) };
  });

  await scenario("rabbitmq-outbox-recovery", async () => {
    docker(["pause", containers.rabbitmq]);
    let submitted;
    try {
      submitted = await submit(fixture, "rabbit-down");
      check(submitted.httpStatus === 202 && submitted.code === 0, `Submission was not durably accepted during broker outage: ${JSON.stringify(submitted)}`);
      await new Promise((resolve) => setTimeout(resolve, 2000));
      const before = mysql(`select status,publish_attempt from outbox_event where workflow_run_uuid='${submitted.data.workflowRunUuid}' order by id`);
      check(before.length > 0 && before.every((row) => row[0] !== "PUBLISHED"), "Outbox was falsely marked PUBLISHED while RabbitMQ was paused.");
      return { workflowRunUuid: submitted.data.workflowRunUuid, beforeRecovery: before };
    } finally {
      unpause(containers.rabbitmq, "rabbitmq");
      if (submitted?.data?.workflowRunUuid) {
        const finalState = await poll("RabbitMQ recovery", async () => {
          const state = persisted(submitted.data.workflowRunUuid);
          return ["SUCCESS", "FAILED"].includes(state?.status) ? state : null;
        }, 90_000);
        scenarios.at(-1).recovery = finalState;
        check(finalState.status === "SUCCESS", "Recovered RabbitMQ workflow did not finish successfully.");
        check(finalState.agentRuns === 4 && finalState.metrics === 4 && finalState.artifacts === 4,
          "Recovered workflow contains duplicate or missing success facts.");
      }
    }
  });

  for (const failureMode of ["429", "invalid"]) {
    await scenario(`python-${failureMode}-limited-retry`, async () => {
      setAgentMode(failureMode);
      const submitted = await submit(fixture, `python-${failureMode}`);
      check(submitted.httpStatus === 202 && submitted.code === 0, `Python ${failureMode} fixture was not accepted for asynchronous execution.`);
      const state = await poll(`Python ${failureMode} retry evidence`, async () => {
        const value = persisted(submitted.data.workflowRunUuid);
        return value?.status === "RETRY_WAIT" || value?.status === "FAILED" ? value : null;
      }, 45_000);
      check(state.status !== "SUCCESS", `Python ${failureMode} produced false SUCCESS.`);
      check(state.retryCount >= 1 || state.status === "FAILED", `Python ${failureMode} produced no bounded retry/terminal evidence.`);
      return { workflowRunUuid: submitted.data.workflowRunUuid, state };
    });
    setAgentMode("normal");
  }

  await scenario("consumer-restart-recovery", async () => {
    setAgentMode("delay");
    const submitted = await submit(fixture, "consumer-restart");
    check(submitted.httpStatus === 202 && submitted.code === 0, "Consumer-restart fixture was not accepted.");
    await poll("consumer RUNNING", async () => persisted(submitted.data.workflowRunUuid)?.status === "RUNNING", 30_000);
    docker(["restart", "--time", "5", containers.backend]);
    recoveryActions.push({ atUtc: new Date().toISOString(), action: "backend consumer restarted" });
    setAgentMode("normal");
    const state = await poll("consumer restart recovery", async () => {
      const value = persisted(submitted.data.workflowRunUuid);
      return ["SUCCESS", "FAILED"].includes(value?.status) ? value : null;
    }, 120_000);
    check(state.status === "SUCCESS", "Restarted consumer did not recover to SUCCESS.");
    check(state.agentRuns === 4 && state.metrics === 4 && state.artifacts === 4,
      "Consumer recovery duplicated or omitted AgentRun/Metric/Artifact facts.");
    const sse = await request(`/api/v1/workflow-runs/${submitted.data.workflowRunUuid}/events`, { token: fixture.token, timeoutMs: 15_000 });
    return { workflowRunUuid: submitted.data.workflowRunUuid, state, sseHttpStatus: sse.httpStatus };
  });
} catch (error) {
  failures.push(`preflight: ${String(error.message || error)}`);
} finally {
  try { setAgentMode("normal"); } catch (error) { failures.push(`cleanup fake Agent: ${error.message}`); }
  for (const [key, label] of [[containers.redis, "redis"], [containers.rabbitmq, "rabbitmq"]]) {
    try { unpause(key, label); } catch (error) { failures.push(`cleanup ${label}: ${error.message}`); }
  }
  const result = {
    providerMode: "fake", fixtureVersion: "r7-fault-fixed-agent-v1", fixtureNamespace: fixtureName,
    completedAtUtc: new Date().toISOString(), scenarios, recoveryActions,
    mysqlTransientFailure: { status: "NOT RUN", reason: "No bounded connection proxy exists; pausing MySQL can strand pooled calls beyond the five-minute scenario contract." },
    contractFailures: failures,
  };
  writeFileSync(path.join(evidenceDir, "fault-matrix.json"), JSON.stringify(result, null, 2) + "\n", "utf8");
}

if (failures.length) {
  console.error(`Fault matrix failed: ${failures.join(" | ")}`);
  process.exit(1);
}
console.log("Fault matrix passed.");
