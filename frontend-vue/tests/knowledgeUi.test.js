import test from "node:test";
import assert from "node:assert/strict";

import { createHttpClient } from "../src/api/httpClient.js";
import { createKnowledgeApi } from "../src/api/knowledgeApi.js";
import { comparisonStateLabel, ragStateLabel } from "../src/utils/ragPresentation.js";

test("knowledge upload preserves multipart body and lets the browser set its boundary", async () => {
  let request;
  const http = createHttpClient({
    baseUrl: "http://test",
    fetchImpl: async (_url, options) => {
      request = options;
      return new Response(JSON.stringify({ code: 0, data: { status: "UPLOADED" } }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }
  });
  const api = createKnowledgeApi(http);

  await api.upload("project/one", new Blob(["safe knowledge"], { type: "text/plain" }));

  assert.ok(request.body instanceof FormData);
  assert.equal(request.headers["Content-Type"], undefined);
});

test("rag presentation keeps disabled, empty, failed and mock states explicit", () => {
  assert.equal(ragStateLabel({ ragEnabled: false }), "RAG 已关闭");
  assert.equal(ragStateLabel({ ragEnabled: true, ragStatus: "EMPTY" }), "空候选：未注入来源");
  assert.equal(ragStateLabel({ ragEnabled: true, ragStatus: "UNAVAILABLE" }), "检索失败：已降级运行");
  assert.match(ragStateLabel({ ragEnabled: true, ragStatus: "AVAILABLE", mock: true }), /^Mock/);
  assert.equal(comparisonStateLabel("INSUFFICIENT_SAMPLES"), "样本不足");
  assert.equal(comparisonStateLabel("INCOMPARABLE_VERSION_MIX"), "版本不一致，禁止比较");
});
