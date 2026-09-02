import { readFile, access } from "node:fs/promises";
import { resolve } from "node:path";

const workspace = resolve(import.meta.dirname, "..");
const readJson = async (path) => JSON.parse(await readFile(resolve(workspace, path), "utf8"));
const coverage = await readJson("docs/api-coverage/endpoints.json");
const errors = [];
const keys = new Set();

for (const [index, endpoint] of coverage.endpoints.entries()) {
  const key = `${endpoint.method} ${endpoint.path}`;
  if (!endpoint.method || !endpoint.path || !endpoint.domain || !endpoint.lifecycle || !endpoint.audience?.length) {
    errors.push(`endpoint[${index}] is unclassified: ${key}`);
  }
  if (["internal", "deprecated", "non_prod"].includes(endpoint.lifecycle) && (!endpoint.owner || !endpoint.retentionReason || !endpoint.replacement)) {
    errors.push(`retained endpoint lacks owner/reason/replacement: ${key}`);
  }
  if (keys.has(key)) errors.push(`duplicate endpoint: ${key}`);
  keys.add(key);
}

for (const profile of ["prod", "non-prod"]) {
  const snapshot = await readJson(`docs/api-coverage/openapi-${profile}.json`);
  const actual = new Set(Object.entries(snapshot.paths).flatMap(([path, item]) =>
    Object.keys(item).map(method => `${method.toUpperCase()} ${path}`)));
  const expected = new Set(coverage.endpoints
    .filter(endpoint => endpoint.profiles.includes(profile))
    .map(endpoint => `${endpoint.method} ${endpoint.path}`));
  for (const key of expected) if (!actual.has(key)) errors.push(`${profile} snapshot is missing ${key}`);
  for (const key of actual) if (!expected.has(key)) errors.push(`${profile} snapshot has undeclared ${key}`);
}

const frontendManifest = await readJson("frontend-vue/src/shared/api/endpointManifest.json");
const frontendKeys = new Set();
for (const mapping of frontendManifest.endpoints) {
  const key = `${mapping.method} ${mapping.path}`;
  if (frontendKeys.has(key)) errors.push(`duplicate frontend API mapping: ${key}`);
  if (!mapping.feature || !mapping.adapter || !mapping.page || !["implemented", "planned"].includes(mapping.status)) {
    errors.push(`invalid frontend API mapping: ${key}`);
  }
  frontendKeys.add(key);
}
const frontendExpected = new Set(coverage.endpoints
  .filter(endpoint => endpoint.lifecycle === "active" && !endpoint.audience.includes("internal"))
  .map(endpoint => `${endpoint.method} ${endpoint.path}`));
for (const key of frontendExpected) if (!frontendKeys.has(key)) errors.push(`frontend API mapping is missing ${key}`);
for (const key of frontendKeys) if (!frontendExpected.has(key)) errors.push(`frontend API mapping is stale: ${key}`);
if (process.argv.includes("--strict")) {
  for (const mapping of frontendManifest.endpoints) {
    if (mapping.status !== "implemented" || mapping.adapter === "planned" || mapping.page === "planned") {
      errors.push(`strict mode rejects planned frontend mapping: ${mapping.method} ${mapping.path}`);
    }
    for (const sourcePath of [mapping.adapter, mapping.page]) {
      try { await access(resolve(workspace, sourcePath)); }
      catch { errors.push(`strict mode cannot resolve frontend implementation ${sourcePath}: ${mapping.method} ${mapping.path}`); }
    }
  }
}

if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`API coverage valid: ${keys.size} unique endpoints, prod/non-prod snapshots aligned.`);
}
