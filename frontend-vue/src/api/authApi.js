export function createAuthApi(http) {
  return {
    login: (credentials) => http("/api/auth/login", { method: "POST", body: credentials, auth: false })
  };
}
