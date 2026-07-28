import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { approvalVersion, budgetRows, candidateEvidence, waitReason } from "../src/features/director/directorEvidence.js";

test("derives display-only budget and approval facts from persisted state", () => {
  const run={status:"WAITING_APPROVAL",waitingApprovalRef:"approval://11111111-1111-4111-8111-111111111111",budgetJson:JSON.stringify({maxRounds:4}),checkpointJson:JSON.stringify({usage:{rounds:2}})};
  assert.equal(approvalVersion(run.waitingApprovalRef),"11111111-1111-4111-8111-111111111111");
  assert.match(waitReason(run),/真实用户审批/);
  assert.deepEqual(budgetRows(run)[0],{label:"轮次",used:2,maximum:4,remaining:2});
  assert.deepEqual(candidateEvidence({tuningJson:'{"playerSpeed":240}',evidenceJson:'{"personaMetrics":{"NOVICE":0.8}}'}).metrics,{NOVICE:0.8});
});

test("page uses persisted API, bounded history and protected approval endpoint", () => {
  const page=readFileSync(fileURLToPath(new URL("../src/features/director/DirectorRunPage.vue",import.meta.url)),"utf8");
  const api=readFileSync(fileURLToPath(new URL("../src/shared/api/director.js",import.meta.url)),"utf8");
  assert.match(page,/PAGE_SIZE=10/);assert.match(page,/directorApi\.get/);assert.match(page,/directorApi\.approve/);assert.match(api,/Idempotency-Key/);assert.match(api,/prototype-versions.*approval/s);assert.doesNotMatch(page,/modelEvidenceJson|payloadJson/);
});
