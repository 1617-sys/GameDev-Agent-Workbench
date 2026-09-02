import test from "node:test";
import assert from "node:assert/strict";
import { createRouteGuard } from "../src/app/routeGuard.js";

function session(overrides = {}) {
  return {
    authenticated: true,
    initialize: async () => {},
    hasCapability: capability => ["projects.read"].includes(capability),
    ...overrides
  };
}

test("direct admin URL navigation returns the 403 route for an ordinary user", async () => {
  const guard = createRouteGuard(session());
  const result = await guard({
    name: "admin-dashboard",
    fullPath: "/admin/dashboard",
    meta: { capability: "admin.dashboard" }
  });

  assert.deepEqual(result, {
    name: "forbidden",
    query: { from: "/admin/dashboard" }
  });
});

test("authorized capability is accepted and unauthenticated private route goes to login", async () => {
  const guard = createRouteGuard(session());
  assert.equal(await guard({ name: "projects", fullPath: "/projects", meta: { capability: "projects.read" } }), true);

  const anonymousGuard = createRouteGuard(session({ authenticated: false }));
  assert.deepEqual(
    await anonymousGuard({ name: "projects", fullPath: "/projects", meta: {} }),
    { name: "auth", query: { redirect: "/projects" } }
  );
});
