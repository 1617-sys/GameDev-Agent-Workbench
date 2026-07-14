import test from "node:test";
import assert from "node:assert/strict";
import { createAuthApi } from "../src/api/authApi.js";

test("registration is sent through the public auth endpoint", async () => {
  let request;
  const authApi = createAuthApi(async (path, options) => {
    request = { path, options };
    return { id: 7, username: "new-user" };
  });

  const user = await authApi.register({ username: "new-user", password: "SafePassword1" });

  assert.deepEqual(user, { id: 7, username: "new-user" });
  assert.equal(request.path, "/api/auth/register");
  assert.equal(request.options.method, "POST");
  assert.equal(request.options.auth, false);
  assert.deepEqual(request.options.body, { username: "new-user", password: "SafePassword1" });
});

test("current user is requested through the authenticated session endpoint", async () => {
  let request;
  const authApi = createAuthApi(async (path, options) => {
    request = { path, options };
    return { id: 7, username: "new-user", role: "USER" };
  });

  const user = await authApi.me();

  assert.equal(user.username, "new-user");
  assert.equal(request.path, "/api/auth/me");
  assert.equal(request.options, undefined);
});
