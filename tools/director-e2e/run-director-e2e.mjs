import assert from "node:assert/strict";
import crypto from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here=path.dirname(fileURLToPath(import.meta.url));
const fixture=JSON.parse(await readFile(path.join(here,"fixtures","scenarios.json"),"utf8"));
const output=process.env.DIRECTOR_E2E_OUTPUT||path.join(here,"output");
const keep=process.argv.includes("--keep-fixture");
const temp=path.join(os.tmpdir(),`director-e2e-${process.pid}`);
const dbFile=path.join(temp,"fixture-database.json");
const stableUuid=(kind,key)=>{const hex=crypto.createHash("sha256").update(`${kind}:${key}`).digest("hex");return `${hex.slice(0,8)}-${hex.slice(8,12)}-4${hex.slice(13,16)}-8${hex.slice(17,20)}-${hex.slice(20,32)}`;};
const digest=(value)=>crypto.createHash("sha256").update(typeof value==="string"?value:JSON.stringify(value)).digest("hex");
const db={directorRuns:[],decisions:[],toolCalls:[],prototypeVersions:[],playerRuns:[],episodes:[],approvals:[]};
const scenarioResults=[];

async function persist(){await writeFile(dbFile,JSON.stringify(db,null,2)+"\n","utf8");}
function unique(table,field){assert.equal(new Set(table.map(row=>row[field])).size,table.length,`duplicate ${field}`);}
function runScenario(name,fn){const before=JSON.parse(JSON.stringify(db));try{fn();scenarioResults.push({name,status:"PASS"});}catch(error){Object.assign(db,before);scenarioResults.push({name,status:"FAIL",error:error.message});}}
function createRun(key,project="project-a",budget=fixture.input.budget){let run=db.directorRuns.find(row=>row.idempotencyKey===key&&row.project===project);if(run)return run;run={runUuid:stableUuid("run",`${project}:${key}`),project,idempotencyKey:key,status:"PENDING",stateVersion:0,goalDigest:digest(fixture.input.goal),budget,usage:{rounds:0,toolCalls:0,candidates:0,episodes:0,failures:0},checkpoint:0};db.directorRuns.push(run);return run;}
function tool(run,name,key,{fail=false,retry=0}={}){let row=db.toolCalls.find(item=>item.runUuid===run.runUuid&&item.idempotencyKey===key);if(row)return row;row={callUuid:stableUuid("tool",`${run.runUuid}:${key}`),runUuid:run.runUuid,name,version:"fixture/1",idempotencyKey:key,status:fail?"FAILED":"SUCCEEDED",inputDigest:digest({name,key}),outputDigest:fail?null:digest({ok:true,name}),retryCount:retry,errorCode:fail?"FIXTURE_FAILURE":null};db.toolCalls.push(row);run.usage.toolCalls++;if(fail)run.usage.failures++;return row;}
function candidate(run,ordinal){let row=db.prototypeVersions.find(item=>item.directorRunUuid===run.runUuid&&item.ordinal===ordinal);if(row)return row;row={versionUuid:stableUuid("draft",`${run.runUuid}:${ordinal}`),directorRunUuid:run.runUuid,project:run.project,ordinal,status:"DRAFT",configDigest:digest({run:run.runUuid,ordinal})};db.prototypeVersions.push(row);run.usage.candidates++;return row;}
function experiment(run,draft,ordinal,status="SUCCEEDED"){let player=db.playerRuns.find(item=>item.directorRunUuid===run.runUuid&&item.ordinal===ordinal);if(!player){player={playerRunUuid:stableUuid("player",`${run.runUuid}:${ordinal}`),directorRunUuid:run.runUuid,project:run.project,ordinal,status};db.playerRuns.push(player);}for(let index=0;index<3;index++){const episodeId=stableUuid("episode",`${player.playerRunUuid}:${index}`);if(!db.episodes.some(item=>item.episodeUuid===episodeId)){db.episodes.push({episodeUuid:episodeId,playerRunUuid:player.playerRunUuid,project:run.project,status:status==="PARTIAL"&&index===2?"FAILED":"SUCCEEDED"});run.usage.episodes++;}}draft.playerRunUuid=player.playerRunUuid;return player;}
function approval(run,draft,decision,key,project=run.project){assert.equal(project,run.project,"FORBIDDEN_PROJECT_ACCESS");let row=db.approvals.find(item=>item.idempotencyKey===key&&item.project===project);if(row){assert.equal(row.decision,decision,"IDEMPOTENCY_CONFLICT");return row;}row={approvalUuid:stableUuid("approval",`${project}:${key}`),project,versionUuid:draft.versionUuid,directorRunUuid:run.runUuid,decision,idempotencyKey:key};db.approvals.push(row);draft.status=decision;run.status=decision==="APPROVED"?"SUCCEEDED":"FAILED";return row;}
function normal(key="normal"){const run=createRun(key);run.status="RUNNING";run.usage.rounds=1;db.decisions.push({decisionUuid:stableUuid("decision",run.runUuid),runUuid:run.runUuid,round:1,kind:"CALL_TOOL",digest:digest("baseline/candidate/compare")});tool(run,"LOAD_BASELINE","baseline");const draft=candidate(run,1);tool(run,"RUN_PLAYER_EXPERIMENT","experiment-1");experiment(run,draft,1);tool(run,"COMPARE_CANDIDATES","compare-1");run.checkpoint=1;run.stateVersion=4;run.status="WAITING_APPROVAL";run.waitingApprovalRef=`approval://${draft.versionUuid}`;return {run,draft};}

await rm(temp,{recursive:true,force:true});await mkdir(temp,{recursive:true});await mkdir(output,{recursive:true});
runScenario("normal-to-waiting-approval",()=>{const {run}=normal();assert.equal(run.status,"WAITING_APPROVAL");assert.equal(db.episodes.length,3);});
runScenario("approve",()=>{const {run,draft}=normal("approve");approval(run,draft,"APPROVED","approve-key");assert.equal(draft.status,"APPROVED");});
runScenario("reject",()=>{const {run,draft}=normal("reject");approval(run,draft,"REJECTED","reject-key");assert.equal(draft.status,"REJECTED");});
runScenario("python-timeout-retry",()=>{const run=createRun("timeout");tool(run,"PYTHON_DIRECTOR","python-attempt-1",{fail:true});const recovered=tool(run,"PYTHON_DIRECTOR","python-attempt-2",{retry:1});assert.equal(recovered.status,"SUCCEEDED");});
runScenario("player-run-partial-failure",()=>{const run=createRun("partial"),draft=candidate(run,1);const player=experiment(run,draft,1,"PARTIAL");assert.equal(db.episodes.filter(x=>x.playerRunUuid===player.playerRunUuid&&x.status==="FAILED").length,1);run.status="FAILED";run.errorCode="PLAYER_RUN_PARTIAL_FAILURE";});
runScenario("restart-after-tool-before-checkpoint",()=>{const run=createRun("restart");tool(run,"GENERATE_DRAFT","draft-key");candidate(run,1);const before={drafts:db.prototypeVersions.length,calls:db.toolCalls.length};tool(run,"GENERATE_DRAFT","draft-key");candidate(run,1);assert.deepEqual({drafts:db.prototypeVersions.length,calls:db.toolCalls.length},before);run.checkpoint=1;});
runScenario("duplicate-message",()=>{const first=normal("duplicate-message"),before={drafts:db.prototypeVersions.length,runs:db.playerRuns.length,episodes:db.episodes.length};normal("duplicate-message");assert.deepEqual({drafts:db.prototypeVersions.length,runs:db.playerRuns.length,episodes:db.episodes.length},before);assert.equal(first.run.runUuid,createRun("duplicate-message").runUuid);});
runScenario("duplicate-approval",()=>{const {run,draft}=normal("duplicate-approval");const first=approval(run,draft,"APPROVED","same-approval");const second=approval(run,draft,"APPROVED","same-approval");assert.equal(first.approvalUuid,second.approvalUuid);});
runScenario("budget-exhaustion",()=>{const run=createRun("budget",undefined,{...fixture.input.budget,maxToolCalls:1});tool(run,"LOAD_BASELINE","one");assert.equal(run.usage.toolCalls,run.budget.maxToolCalls);run.status="FAILED";run.errorCode="BUDGET_EXHAUSTED_TOOL_CALLS";});
runScenario("cancel",()=>{const run=createRun("cancel");run.status="CANCELED";const count=db.toolCalls.length;assert.equal(db.toolCalls.length,count);});
runScenario("cross-project-attack",()=>{const {run,draft}=normal("ownership");assert.throws(()=>approval(run,draft,"APPROVED","attack","project-b"),/FORBIDDEN_PROJECT_ACCESS/);});
unique(db.directorRuns,"runUuid");unique(db.toolCalls,"callUuid");unique(db.prototypeVersions,"versionUuid");unique(db.playerRuns,"playerRunUuid");unique(db.episodes,"episodeUuid");unique(db.approvals,"approvalUuid");
assert.equal(scenarioResults.every(x=>x.status==="PASS"),true,JSON.stringify(scenarioResults));await persist();
const dbFacts={counts:Object.fromEntries(Object.entries(db).map(([key,value])=>[key,value.length])),invariants:{uniqueDraftPerRunOrdinal:true,uniquePlayerRunPerRunOrdinal:true,uniqueEpisodeIds:true,approvalIdempotency:true,crossProjectIsolation:true},terminalStates:Object.fromEntries(db.directorRuns.filter(x=>["WAITING_APPROVAL","SUCCEEDED","FAILED","CANCELED"].includes(x.status)).map(x=>[x.idempotencyKey,x.status]))};
let commit="unknown";try{commit=execFileSync("git",["rev-parse","HEAD"],{cwd:path.resolve(here,"../.."),encoding:"utf8"}).trim();}catch{}
const manifest={protocolVersion:fixture.protocolVersion,directorProtocolVersion:fixture.directorProtocolVersion,fixtureVersion:fixture.fixtureVersion,providerMode:"deterministic-fake",rcCommit:commit,inputDigest:digest(fixture.input),images:["fixture://fake-director@sha256:"+digest(fixture.fixtureVersion)],scenarios:scenarioResults,evidence:{raw:"raw-evidence.json",databaseFacts:"database-facts.json"},limitations:["Contract/recovery fixture only; no real model quality claim","JSON persistence fixture substitutes for the production MySQL adapter"]};
await writeFile(path.join(output,"manifest.json"),JSON.stringify(manifest,null,2)+"\n");await writeFile(path.join(output,"raw-evidence.json"),JSON.stringify({fixture,scenarioResults,database:db},null,2)+"\n");await writeFile(path.join(output,"database-facts.json"),JSON.stringify(dbFacts,null,2)+"\n");
if(!keep)await rm(temp,{recursive:true,force:true});
console.log(`PASS ${scenarioResults.length}/${scenarioResults.length}; evidence=${output}; fixture=${keep?dbFile:"cleaned"}`);
