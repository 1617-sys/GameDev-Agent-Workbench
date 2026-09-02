import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { appRoutes } from "../src/app/routes.js";

test("new capability pages use lazy routes with backend capability metadata", () => {
  const expected = [
    "artifacts", "player-runs", "knowledge", "workflow-runs", "admin-dashboard",
    "admin-agent-runs", "admin-prompt-ops", "admin-analytics", "admin-diagnostics"
  ];
  const byName = new Map(appRoutes.map(route => [route.name, route]));

  for (const name of expected) {
    assert.equal(typeof byName.get(name)?.component, "function", `${name} must be lazy loaded`);
    assert.equal(typeof byName.get(name)?.meta?.capability, "string", `${name} must declare a capability`);
  }
});

test("shared page layout has an explicit 375px overflow guard", async () => {
  const css = await readFile(new URL("../src/styles/global.css", import.meta.url), "utf8");
  assert.match(css, /@media\s*\(max-width:\s*480px\)/);
  assert.match(css, /\.app-content[^}]*overflow-x:\s*hidden/s);
  assert.match(css, /\.feature-page[^}]*min-width:\s*0/s);
});
