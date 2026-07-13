# R7 reproducible demo script

This runbook is for a 3–5 minute engineering demo. The default mode is explicitly **DEMO / MOCK**: it uses a deterministic offline Provider and does not claim model quality, online capacity, cost, or production performance. The optional online segment must be introduced aloud and on screen as **REAL PROVIDER**.

## Prepare and preflight

From the repository root, run:

```powershell
.\tools\prepare-demo.ps1
.\tools\verify-demo.ps1
```

Preparation uses Compose project `r7demo07`, user `r7-demo`, namespace `r7-demo-v1`, and a project with the exact marker `R7_DEMO_NAMESPACE:r7-demo-v1`. Generated passwords and service secrets live under the current user's local application-data directory, outside the repository. They are not printed or written to evidence. Repeating preparation reuses the single project, document, and per-mode idempotent workflow.

Before recording, confirm all of the following:

- `verify-demo.ps1` reports PASS, `OFFLINE / MOCK`, four steps, four metrics, four artifacts, RAG references, and a redacted evidence path.
- Docker reports the six `r7demo07` services healthy; ports 5173, 8080, 8000, and 3307 are loopback-only.
- `http://127.0.0.1:5173/` opens in the intended browser and the password from prepare is still on the clipboard.
- The latest evidence summary and this script are available as the backup route. Do not display `compose.env`, `credentials.json`, request payloads, raw document text, Authorization headers, or local absolute paths.
- Close personal tabs, notifications, terminals containing environment variables, password managers, and Provider dashboards before capture.

For the optional real segment, provide credentials only in the current process and run:

```powershell
$env:LLM_API_KEY = '<session-only value>'
.\tools\prepare-demo.ps1 -ProviderMode real
.\tools\verify-demo.ps1
```

Do not record the command that assigns the key. Real results and metrics must remain labelled **REAL PROVIDER**, dated, and separate from the deterministic mock evidence.

## 3–5 minute talk track and shot list

| Time | Screen / action | Talk track and evidence |
| --- | --- | --- |
| 00:00–00:25 | Show the clean login page and terminal PASS line. | “This is DEMO / MOCK, an explicitly labelled offline fixture. Preparation is idempotent and uses a dedicated namespace; it is not a real model-quality or performance claim.” |
| 00:25–01:05 | Sign in as `r7-demo` and open `/workflow-runs/<printed workflowRunUuid>` from prepare. | Explain that prepare submitted `DEMO_GAME_CONFIG` through the public async API with a stable idempotency key; it received a `workflowRunUuid` immediately and then waited on persisted state. Opening the prepared run avoids duplicate demo records. |
| 01:05–01:50 | Show the run summary and four ordered successful steps; refresh once. | Use the persisted run UUID and trace presence as correlation evidence. Explain that refresh/SSE reconnection reads server state rather than replaying work. Do not show raw prompts or logs. |
| 01:50–02:35 | Show the RAG evidence cards and the MOCK badge. | Point to document/chunk UUIDs, versions, ranks, and the explicit MOCK state. Say that these are persisted retrieval references from the controlled document—not fabricated real-provider citations. |
| 02:35–03:15 | Open the available GameConfig artifact and Phaser demo. | Collect both items and reach the exit. Explain that schema/rule/runtime evaluation produced the available artifact; do not call fixed fixture latency a benchmark. |
| 03:15–03:45 | Return to the terminal verification summary or the redacted JSON summary. | Point to four metrics, four artifacts, at least one passing evaluation, trace presence, and the evidence directory. State that secrets, prompts, and document bodies are excluded. |
| 03:45–04:15 | Show the reset command in this document, without running it during the recording. | Explain the double namespace/project marker check, exact user ownership check, storage-reference validation, and `docker compose down` without `--volumes`. |
| 04:15–04:40 | Optional only: show a previously prepared **REAL PROVIDER** run. | Announce “REAL PROVIDER” before switching. Keep its date/model label and results separate; omit this segment if credentials, network, budget, or time are uncertain. |

The core route ends at 4:15; the optional real segment keeps the recording under five minutes.

## Provider/network failure fallback (under 30 seconds)

At the first Provider error, stop waiting. Say: “The optional real Provider is unavailable; I am switching to the pre-verified DEMO / MOCK route.” Within 30 seconds:

1. Close the failing real-run tab without editing data or code.
2. Open the already verified offline run URL recorded by the most recent offline `prepare-demo.ps1` output, or open the redacted `demo/verification-summary.json` from the latest offline evidence directory.
3. Resume at the RAG/Phaser segment and keep the **DEMO / MOCK** badge visible.

If an active stack switch is needed and the offline image is already cached, run `.\tools\prepare-demo.ps1 -ProviderMode offline`, then `.\tools\verify-demo.ps1`. Never relabel a real failure or a cached screenshot as live success. If the switch cannot complete in 30 seconds, use the verified summary/screenshots or stop the demo.

## Recording and screenshot checklist

Safe captures:

- login page with no password visible;
- workflow run UUID, terminal status, ordered step names, and redacted error summary;
- explicit MOCK badge, RAG reference UUID/version/rank/score, and comparison disclaimer;
- GameConfig artifact status/link and Phaser canvas;
- redacted verification summary with counts and boolean trace presence;
- Compose service names and health only, without environment/config output.

Never capture:

- `.env`, `compose.env`, `credentials.json`, tokens, cookies, Authorization or internal-token headers;
- personal accounts, Provider dashboards, billing data, chat history, notifications, or browser autofill;
- raw prompt/request/response bodies, complete uploaded document text, database connection strings, or host absolute paths;
- mock latency/cost as real performance, or mock RAG references as real-provider evidence.

## Safe reset

Run only after confirming the printed namespace, username, and project UUID:

```powershell
.\tools\reset-demo.ps1
```

Invoking this named command is the explicit reset confirmation. It refuses missing/ambiguous state, a mismatched namespace, more than one project, a project without the exact marker, or unexpected storage references. It removes only the demo user/project graph, deletes the generated demo login credential/state files, stops Compose without deleting volumes, and never targets development or production namespaces. The repository-external Compose environment remains so the retained infrastructure volumes can be reused safely on the next prepare.
