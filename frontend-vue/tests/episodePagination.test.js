import test from "node:test";
import assert from "node:assert/strict";
import { episodePagination, batchPresentation } from "../src/features/episodes/episodePresentation.js";

test("long trajectories preserve server pagination boundaries", () => {
  assert.deepEqual(episodePagination({ page: 0, size: 50, total: 121 }), { page: 0, totalPages: 3, hasPrevious: false, hasNext: true });
  assert.deepEqual(episodePagination({ page: 2, size: 50, total: 121 }), { page: 2, totalPages: 3, hasPrevious: true, hasNext: false });
});

test("empty persisted batches are not presented as completed evidence", () => {
  assert.deepEqual(batchPresentation({ status: "COMPLETED", items: [] }), { empty: true, message: "批次已持久化，但没有 Episode 项。" });
  assert.equal(batchPresentation({ status: "COMPLETED", items: [{ episodeId: "e-1" }] }).empty, false);
});
