# Redacted security audit summary

- Tracked secret-pattern scan: 0 candidate files for private-key, common cloud-token, provider-token and JWT shapes. This is a bounded pattern scan, not an absolute guarantee.
- Destructive forward-migration pattern scan: 0 candidate migration files for `DROP TABLE/DATABASE`, `TRUNCATE TABLE` or `DELETE FROM`.
- Authorization review: project/run ownership is enforced before Workflow query, command and SSE replay; dedicated tests cover foreign Workflow, Artifact UUID guessing, Metric user/project arguments, Knowledge Document, RAG evidence, RetrievalRecord and vector project filters.
- Upload review: extension, MIME, magic bytes/text binary check, size and normalized storage path gates are present and tested.
- Untrusted content review: RAG chunks are explicitly delimited as untrusted; model GameConfig is JSON-parsed/validated and is not evaluated as script.
- Configuration fixes: Java-to-Python calls now require an internal token; Redis polymorphic deserialization only permits the cached `SysUser` type; production CORS is explicit, and Java Demo/Swagger are disabled under `prod`.
- Dependency triage: Playwright advisory fixed by pinning `1.55.1`; final npm audit is 0. Maven/Python/container CVE conclusions remain unavailable.
- Blocking facts: Docker-backed attacker/owner integration and image scanning were not executed; the Python service still exposes mock-backed Agent behavior without an end-to-end production profile gate.
