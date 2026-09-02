import test from "node:test";
import assert from "node:assert/strict";
import { diagnosticActions, safeHealth } from "../src/features/admin/diagnostics.js";

const catalog = [{ method: "POST", path: "/api/demo/game/stream", lifecycle: "non_prod" }, { method: "POST", path: "/internal", lifecycle: "internal" }];
test("production diagnostics never offers non-production demo execution", () => {
  assert.deepEqual(diagnosticActions("prod", catalog), []);
  assert.deepEqual(diagnosticActions("non-prod", catalog), [{ label: "打开非生产 Demo", path: "/demo/play" }]);
});
test("health response uses a minimal whitelist", () => {
  assert.deepEqual(safeHealth({ status: "UP", version: "1", databasePassword: "secret" }), { status: "UP", version: "1" });
});
