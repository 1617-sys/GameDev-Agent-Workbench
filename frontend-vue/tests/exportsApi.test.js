import test from "node:test";
import assert from "node:assert/strict";

global.window = { setTimeout, clearTimeout, sessionStorage: { getItem: () => "jwt" } };

test("export API binds create, get and retry to encoded project scope", async () => {
  const calls = [];
  global.fetch = async (url, options = {}) => {
    calls.push([url, options]);
    return { ok: true, status: 200, json: async () => ({ code: 0, data: { jobUuid: "job", status: "COMPLETED" } }) };
  };
  const { exportsApi } = await import("../src/shared/api/exports.js");
  await exportsApi.create("project one", "version one", "same-key");
  await exportsApi.get("project one", "job one");
  await exportsApi.retry("project one", "job one");
  assert.match(calls[0][0], /projects\/project%20one\/prototype-versions\/version%20one\/exports$/);
  assert.equal(calls[0][1].headers["Idempotency-Key"], "same-key");
  assert.match(calls[1][0], /projects\/project%20one\/exports\/job%20one$/);
  assert.match(calls[2][0], /projects\/project%20one\/exports\/job%20one\/retry$/);
});

test("ZIP download carries JWT and decodes a safe UTF-8 filename", async () => {
  let captured;
  global.fetch = async (url, options = {}) => {
    captured = [url, options];
    return {
      ok: true,
      status: 200,
      headers: new Headers({
        "content-type": "application/zip",
        "content-disposition": "attachment; filename*=UTF-8''prototype-%E7%89%88%E6%9C%AC.zip"
      }),
      blob: async () => new Blob(["PK"])
    };
  };
  const { exportsApi } = await import("../src/shared/api/exports.js");
  const result = await exportsApi.download("project", "job");
  assert.equal(captured[1].headers.Authorization, "Bearer jwt");
  assert.equal(captured[1].headers.Accept, "application/zip");
  assert.equal(result.filename, "prototype-版本.zip");
  assert.equal(result.blob.size, 2);
});

test("download preserves backend JSON errors instead of saving them as ZIP", async () => {
  global.fetch = async () => ({
    ok: false,
    status: 409,
    headers: new Headers({ "content-type": "application/json" }),
    json: async () => ({ code: 40907, message: "Prototype export is not ready" })
  });
  const { exportsApi } = await import("../src/shared/api/exports.js");
  await assert.rejects(
    () => exportsApi.download("project", "job"),
    (error) => error.code === 40907 && error.status === 409
  );
});

test("download uses the job package name when CORS hides Content-Disposition", async () => {
  let captured;
  global.fetch = async (url, options = {}) => {
    captured = [url, options];
    return {
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/zip" }),
      blob: async () => new Blob(["PK"])
    };
  };
  const { exportsApi } = await import("../src/shared/api/exports.js");
  const result = await exportsApi.download("project", "job", "prototype-v2-safe.zip");
  assert.equal(result.filename, "prototype-v2-safe.zip");
  assert.equal("fallbackFilename" in captured[1], false);
});
