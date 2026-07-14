import test from "node:test";
import assert from "node:assert/strict";
import { createProjectApi } from "../src/api/projectApi.js";

test("project API maps list and create requests to the GameController contract", async () => {
  const requests = [];
  const api = createProjectApi(async (path, options) => {
    requests.push({ path, options });
    return options?.method === "POST" ? { projectUuid: "created" } : [];
  });
  const project = { name: "Star Scavenger", gameType: "top_down_collect", targetPlatform: "web", description: "Collect lost signals." };

  await api.list();
  const created = await api.create(project);

  assert.equal(requests[0].path, "/api/projects");
  assert.equal(requests[0].options, undefined);
  assert.deepEqual(requests[1], { path: "/api/projects", options: { method: "POST", body: project } });
  assert.equal(created.projectUuid, "created");
});

test("project detail and update preserve server-issued UUIDs in encoded paths", async () => {
  const requests = [];
  const api = createProjectApi(async (path, options) => {
    requests.push({ path, options });
    return { projectUuid: "server/id" };
  });
  const project = { name: "Updated", gameType: "puzzle", targetPlatform: "web", description: "Updated project." };

  await api.get("server/id");
  await api.update("server/id", project);

  assert.deepEqual(requests, [
    { path: "/api/projects/server%2Fid", options: undefined },
    { path: "/api/projects/server%2Fid", options: { method: "PUT", body: project } }
  ]);
});
