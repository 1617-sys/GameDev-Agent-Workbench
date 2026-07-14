export function createAuthApi(http) {
  return {
    register: (credentials) => http("/api/auth/register", { method: "POST", body: credentials, auth: false }),
    login: (credentials) => http("/api/auth/login", { method: "POST", body: credentials, auth: false }),
    me: () => http("/api/auth/me")
  };
}
