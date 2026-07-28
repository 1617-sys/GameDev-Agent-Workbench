#!/usr/bin/env python3
import argparse, json, math, os, statistics, subprocess, time, urllib.error, urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parent
def call(base,path,token,method="GET",body=None,headers=None):
    data=json.dumps(body,separators=(",",":")).encode() if body is not None else None
    request=urllib.request.Request(base.rstrip("/")+path,data=data,method=method,headers={"Authorization":f"Bearer {token}","Content-Type":"application/json",**(headers or {})})
    with urllib.request.urlopen(request,timeout=150) as response:
        payload=json.load(response)
    if payload.get("code")!=0: raise RuntimeError(f"API failure code={payload.get('code')}")
    return payload["data"]
def percentile(values,p):
    if not values:return None
    ordered=sorted(values);return ordered[max(0,math.ceil(p*len(ordered))-1)]
def failure_reason(value):
    return value.get("terminationReason") or (value.get("error") or {}).get("code") or "NONE"
def format_percent(value):
    return "N/A" if value is None else f"{value:.1%}"
def format_optional(value):
    return "N/A" if value is None else str(value)
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--base-url",default=os.getenv("PLAYER_EVAL_BASE_URL","http://127.0.0.1:8080"));parser.add_argument("--token",default=os.getenv("PLAYER_EVAL_TOKEN"));parser.add_argument("--project",required=True);parser.add_argument("--version",required=True);parser.add_argument("--output",default=str(ROOT/"output"));args=parser.parse_args()
    if not args.token:raise SystemExit("PLAYER_EVAL_TOKEN or --token is required")
    matrix=json.loads((ROOT/"matrix.json").read_text(encoding="utf-8"));episodes=[]
    for cohort in matrix["cohorts"]:
        for seed in matrix["seeds"]:episodes.append({"clientEpisodeKey":f"{cohort['key']}-{seed}","personaId":cohort["personaId"],"policyKind":cohort["policyKind"],"seed":seed,"maxSteps":matrix["budgets"]["maxSteps"],"policySeed":seed^0x5A17,"modelKey":cohort.get("modelKey")})
    key=f"player-eval-{args.version}-{int(time.time())}"[:120];run=call(args.base_url,f"/api/projects/{args.project}/player-runs",args.token,"POST",{"prototypeVersionUuid":args.version,"clientBatchKey":key[-80:],"concurrency":4,"episodes":episodes},{"Idempotency-Key":key})
    deadline=time.time()+900
    while run["status"] in ("PENDING","RUNNING","PERSISTING") and time.time()<deadline:
        time.sleep(2);run=call(args.base_url,f"/api/projects/{args.project}/player-runs/{run['runUuid']}",args.token)
    if not run.get("persistedBatchUuid"):raise RuntimeError(f"run ended without evidence: {run.get('errorCode')}")
    batch=call(args.base_url,f"/api/projects/{args.project}/machine-episodes/batches/{run['persistedBatchUuid']}",args.token)
    results=[call(args.base_url,f"/api/projects/{args.project}/machine-episodes/{item['episodeId']}/summary",args.token) for item in batch["items"]]
    prototype=call(args.base_url,f"/api/projects/{args.project}/prototype-versions/{args.version}",args.token)
    game_config=prototype["gameConfig"] if isinstance(prototype["gameConfig"],dict) else json.loads(prototype["gameConfig"])
    replays=[];evidence_by_episode={}
    for result in results[:min(5,len(results))]:
        pages=[];page=0
        while True:
            evidence=call(args.base_url,f"/api/projects/{args.project}/machine-episodes/{result['episodeId']}/steps?page={page}&size=100",args.token);pages.extend(evidence["items"])
            if len(pages)>=evidence["total"]:break
            page+=1
        evidence_by_episode[result["episodeId"]]=pages
        persona=result["personaId"];observation={"kind":"FULL"} if persona=="baseline-neutral" else {"kind":"PERSONA","visionRadiusPx":{"NOVICE":160,"REGULAR":320,"EXPERT":640}[persona]}
        replay_input={"gameConfig":game_config,"options":{"protocolVersion":"simulation/1.0","episodeId":result["episodeId"],"configDigest":result["configDigest"],"seed":result["seed"],"maxSteps":result["maxSteps"],"observationPolicy":observation},"steps":[{"action":step["decision"]["requestedAction"],"previousStateHash":step["transition"]["previousStateHash"],"stateHash":step["transition"]["stateHash"]} for step in pages]}
        replay_script=ROOT/"replay.mjs";completed=subprocess.run(["node",str(replay_script)],input=json.dumps(replay_input),text=True,capture_output=True,check=False)
        replay=json.loads(completed.stdout or "{}") if completed.stdout else {"verified":False,"error":"NO_OUTPUT"};replay["episodeId"]=result["episodeId"];replays.append(replay)
    cohorts={}
    for result in results:
        key=result["clientEpisodeKey"].rsplit("-",1)[0];cohorts.setdefault(key,[]).append(result)
    summary={"protocolVersion":"player-evaluation/1.0","runUuid":run["runUuid"],"persistedBatchUuid":run["persistedBatchUuid"],"prototypeVersionUuid":args.version,"configDigest":results[0]["configDigest"] if results else None,"sampleSize":len(results),"recordedDecisionReplays":replays,"cohorts":{}}
    for key,items in cohorts.items():
        durations=[x["wallDurationMs"] for x in items if x.get("wallDurationMs") is not None];actions=[x["acceptedActionCount"] for x in items];usage=[x.get("usage") or {} for x in items];provider_latencies=[x["providerLatencyMs"] for x in usage if x.get("providerLatencyMs") is not None];accepted=sum(x["acceptedActionCount"] for x in items);attempted=accepted+sum(x["invalidActionCount"] for x in items)
        summary["cohorts"][key]={"sampleSize":len(items),"completionRate":sum(x.get("outcome")=="WON" for x in items)/len(items),"p50DurationMs":percentile(durations,.5),"p95DurationMs":percentile(durations,.95),"meanAcceptedActions":statistics.mean(actions) if actions else None,"actionEfficiency":accepted/attempted if attempted else None,"invalidActions":sum(x["invalidActionCount"] for x in items),"failureReasons":{reason:sum(failure_reason(x)==reason for x in items) for reason in sorted({failure_reason(x) for x in items if x.get("outcome")!="WON"})},"totalTokens":sum(x.get("totalTokens",0) or 0 for x in usage),"costMicros":sum(x.get("costMicros",0) or 0 for x in usage),"providerLatencyP50Ms":percentile(provider_latencies,.5),"providerLatencyP95Ms":percentile(provider_latencies,.95),"mockCount":sum(bool((x.get("audit") or {}).get("mock")) for x in items)}
    output=Path(args.output);output.mkdir(parents=True,exist_ok=True);(output/"raw.json").write_text(json.dumps({"matrix":matrix,"run":run,"batch":batch,"episodes":results,"representativeStepEvidence":evidence_by_episode},ensure_ascii=False,indent=2),encoding="utf-8");(output/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
    lines=["# V4 Player Evaluation Report","",f"- Persistent batch: `{run['persistedBatchUuid']}`",f"- PrototypeVersion: `{args.version}`",f"- Sample size: {len(results)}","- Confidence: five fixed seeds per cohort; descriptive comparison only, not statistical proof.",f"- Recorded-decision replay: {sum(item.get('verified') is True for item in replays)}/{len(replays)} verified against the frozen Simulation Core without calling a model.","","| Cohort | N | Completion | P50 ms | P95 ms | Action efficiency | Invalid | Provider P95 ms | Tokens | Cost (micros) | Mock | Failures |","|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"]
    for key,value in summary["cohorts"].items():lines.append(f"| {key} | {value['sampleSize']} | {value['completionRate']:.1%} | {value['p50DurationMs']} | {value['p95DurationMs']} | {format_percent(value['actionEfficiency'])} | {value['invalidActions']} | {format_optional(value['providerLatencyP95Ms'])} | {value['totalTokens']} | {value['costMicros']} | {value['mockCount']} | {', '.join(f'{reason}: {count}' for reason,count in value['failureReasons'].items()) or 'none'} |")
    (output/"report.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
if __name__=="__main__":main()
