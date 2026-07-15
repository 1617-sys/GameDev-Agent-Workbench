const TERMINAL = new Set(["COMPLETED", "FAILED"]);
const ALLOWED = new Set(["PENDING", ...TERMINAL]);

export async function waitForExportTerminal(initialJob, {
  load,
  signal,
  delays = [300, 500, 1000, 1500],
  maxAttempts = 30,
  wait = abortableWait
} = {}) {
  if (typeof load !== "function") throw new TypeError("Export job loader is required");
  const expectedJobUuid = initialJob?.jobUuid;
  const expectedVersionUuid = initialJob?.prototypeVersionUuid;
  let job = initialJob;
  for (let attempt = 0; attempt <= maxAttempts; attempt += 1) {
    assertJob(job, expectedJobUuid, expectedVersionUuid);
    if (TERMINAL.has(job.status)) return job;
    if (attempt === maxAttempts) break;
    await wait(delays[Math.min(attempt, delays.length - 1)], signal);
    job = await load(expectedJobUuid);
  }
  const error = new Error("导出仍在处理中，请稍后重新查询");
  error.code = "EXPORT_POLL_TIMEOUT";
  throw error;
}

function assertJob(job, jobUuid, versionUuid) {
  if (!job || !ALLOWED.has(job.status)) {
    const error = new Error("导出服务返回了未知状态");
    error.code = "EXPORT_STATUS_UNKNOWN";
    throw error;
  }
  if (job.jobUuid !== jobUuid || job.prototypeVersionUuid !== versionUuid) {
    const error = new Error("导出作业身份发生变化，已停止自动处理");
    error.code = "EXPORT_JOB_MISMATCH";
    throw error;
  }
}

function abortableWait(milliseconds, signal) {
  if (signal?.aborted) return Promise.reject(signal.reason || new DOMException("Aborted", "AbortError"));
  return new Promise((resolve, reject) => {
    const onAbort = () => {
      clearTimeout(timeout);
      reject(signal.reason || new DOMException("Aborted", "AbortError"));
    };
    const timeout = setTimeout(() => {
      signal?.removeEventListener("abort", onAbort);
      resolve();
    }, milliseconds);
    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

export function formatPackageSize(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) return "--";
  if (value < 1024) return `${value} B`;
  return `${(value / 1024).toFixed(1)} KiB`;
}
