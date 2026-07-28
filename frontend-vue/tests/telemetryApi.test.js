import test from "node:test";
import assert from "node:assert/strict";

test("telemetry reporter batches restricted facts with monotonic sequence", async () => {
  global.window={ setTimeout, clearTimeout, sessionStorage:{getItem:()=>""} };
  const { createTelemetryReporter }=await import("../src/shared/api/telemetry.js"); const batches=[];
  const reporter=await createTelemetryReporter("project","version",{createSession:async()=>({sessionUuid:"session"}),ingest:async(_p,_s,b)=>batches.push(b)});
  reporter.emit("SESSION_STARTED",0,{}); reporter.emit("ITEM_COLLECTED",125,{itemId:"item-1"}); reporter.emit("SESSION_ENDED",200,{reason:"USER_EXIT"}); await reporter.flush();
  assert.deepEqual(batches.flatMap(b=>b.events).map(e=>[e.sequence,e.type]),[[1,"SESSION_STARTED"],[2,"ITEM_COLLECTED"],[3,"SESSION_ENDED"]]);
  assert.equal(Object.hasOwn(batches[0].events[1],"token"),false);
});
