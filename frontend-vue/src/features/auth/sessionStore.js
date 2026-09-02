import { defineStore } from "pinia";
import { authApi } from "../../shared/api/auth";
import { SESSION_TOKEN_KEY, setUnauthorizedHandler } from "../../shared/api/http";

function savedToken() {
  try { return window.sessionStorage.getItem(SESSION_TOKEN_KEY) || ""; } catch { return ""; }
}

export const useSessionStore = defineStore("session", {
  state: () => ({ token: savedToken(), user: null, initialized: false, busy: false, error: "" }),
  getters: {
    authenticated: (state) => Boolean(state.token),
    capabilityKeys: (state) => Array.isArray(state.user?.capabilities) ? state.user.capabilities : [],
    hasCapability: (state) => (capability) =>
      Array.isArray(state.user?.capabilities) && state.user.capabilities.includes(capability)
  },
  actions: {
    async initialize() {
      if (this.initialized) return;
      this.initialized = true;
      setUnauthorizedHandler(() => this.clear());
      if (!this.token) return;
      try { this.user = await authApi.me(); } catch { this.clear(); }
    },
    async authenticate(mode, credentials) {
      if (this.busy) return;
      this.busy = true;
      this.error = "";
      try {
        if (mode === "register") await authApi.register(credentials);
        const result = await authApi.login(credentials);
        if (!result?.token) throw new Error("登录响应缺少认证信息");
        this.token = result.token;
        this.user = result.user || await authApi.me();
        window.sessionStorage.setItem(SESSION_TOKEN_KEY, result.token);
      } catch (error) {
        this.error = error.message || "登录失败";
        throw error;
      } finally { this.busy = false; }
    },
    clear() {
      this.token = "";
      this.user = null;
      this.error = "";
      try { window.sessionStorage.removeItem(SESSION_TOKEN_KEY); } catch { /* unavailable */ }
    }
  }
});
