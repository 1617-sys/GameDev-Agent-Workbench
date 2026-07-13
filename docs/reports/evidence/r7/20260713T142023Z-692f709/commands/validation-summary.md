# Validation command summary

No rendered Compose configuration or credential value is stored in this evidence.

| Command | Exit | Result |
| --- | ---: | --- |
| `git diff --check` | 0 | PASS |
| `docker compose config` | 0 | PASS with process-only random credentials; rendered output suppressed |
| `cd backend-java; mvn test` | 0 | PASS; final run 142 tests, 1 environment-conditioned skip |
| `cd python-agent; python -m pytest` | 0 | PASS; 8 tests |
| `cd frontend-vue; npm audit` | 0 | PASS; 0 vulnerabilities after Playwright patch |
| `cd frontend-vue; npm run build` | 0 | PASS; chunk-size warning only |
| `.\tools\verify.ps1 -Profile quick` | 0 | PASS on rerun with process-only random credentials |
| `.\tools\verify.ps1 -Profile integration` | 0 | Script PASS, but all 3 Testcontainers tests skipped because Docker was unavailable |
| Maven OWASP dependency-check | terminated | NOT RUN to completion; external vulnerability data initialization produced no result within the controlled window |
| Docker Scout image scan | not started | NOT RUN; Docker daemon unavailable |
| Python vulnerability scan | not started | NOT RUN; `pip-audit`/`osv-scanner` unavailable; `pip check` passed but is not a CVE scan |

The first `quick` invocation stopped at Compose interpolation because the existing local `.env` was incomplete. No local credential file was overwritten; the required original command was rerun with ephemeral environment variables and passed.
