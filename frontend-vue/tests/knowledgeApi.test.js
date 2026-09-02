import test from "node:test";
import assert from "node:assert/strict";
import { validateKnowledgeFile, shouldPollKnowledge } from "../src/shared/api/knowledge.js";

test("knowledge upload validates allowed files, size and malicious names", () => {
  assert.equal(validateKnowledgeFile({ name: "rules.md", size: 100, type: "text/markdown" }).name, "rules.md");
  assert.throws(() => validateKnowledgeFile({ name: "large.pdf", size: 10 * 1024 * 1024 + 1, type: "application/pdf" }), /10 MiB/);
  assert.throws(() => validateKnowledgeFile({ name: "../secret.md", size: 100, type: "text/markdown" }), /文件名/);
  assert.throws(() => validateKnowledgeFile({ name: "image.exe", size: 100, type: "application/octet-stream" }), /PDF|Markdown|文本/);
});

test("safe polling only continues for non-terminal indexing states", () => {
  assert.equal(shouldPollKnowledge([{ status: "UPLOADED" }, { status: "READY" }]), true);
  assert.equal(shouldPollKnowledge([{ status: "INDEXING" }]), true);
  assert.equal(shouldPollKnowledge([{ status: "READY" }, { status: "FAILED" }]), false);
  assert.equal(shouldPollKnowledge([]), false);
});
