import test from "node:test";
import assert from "node:assert/strict";
import { formatPackageSize, waitForExportTerminal } from "../src/features/prototypes/exportState.js";

const pending = { jobUuid: "job", prototypeVersionUuid: "version", status: "PENDING" };

test("polls observable PENDING jobs until COMPLETED", async () => {
  const states = [
    { ...pending },
    { ...pending, status: "COMPLETED", packageSize: 2048 }
  ];
  let calls = 0;
  const result = await waitForExportTerminal(pending, {
    load: async () => states[calls++],
    wait: async () => {},
    maxAttempts: 3
  });
  assert.equal(result.status, "COMPLETED");
  assert.equal(calls, 2);
  assert.equal(formatPackageSize(result.packageSize), "2.0 KiB");
});

test("returns FAILED as a terminal state without hiding its error", async () => {
  const failed = { ...pending, status: "FAILED", errorCode: "EXPORT_SECURITY_REJECTED" };
  const result = await waitForExportTerminal(failed, { load: async () => assert.fail("must not poll") });
  assert.equal(result.errorCode, "EXPORT_SECURITY_REJECTED");
});

test("rejects mismatched jobs and bounded polling exhaustion", async () => {
  await assert.rejects(
    () => waitForExportTerminal(pending, {
      load: async () => ({ ...pending, jobUuid: "other" }),
      wait: async () => {},
      maxAttempts: 1
    }),
    (error) => error.code === "EXPORT_JOB_MISMATCH"
  );
  await assert.rejects(
    () => waitForExportTerminal(pending, { load: async () => pending, wait: async () => {}, maxAttempts: 1 }),
    (error) => error.code === "EXPORT_POLL_TIMEOUT"
  );
});

test("aborts polling before a stale version can be loaded", async () => {
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(
    () => waitForExportTerminal(pending, { load: async () => pending, signal: controller.signal }),
    (error) => error.name === "AbortError"
  );
});
