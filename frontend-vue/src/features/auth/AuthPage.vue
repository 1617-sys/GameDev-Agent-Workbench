<template>
  <main class="auth-page">
    <section class="auth-story">
      <div class="brand-mark">GF</div>
      <div class="auth-story-copy">
        <p class="overline">GAMEFLOW STUDIO</p>
        <h1>把游戏规格，构建成真正可玩的 Cocos 游戏。</h1>
        <p>从受约束 GameSpec 到 Web Mobile 游戏包，一条可验证流水线完成。</p>
      </div>
      <div class="workflow-visual" aria-hidden="true">
        <span class="visual-node complete">规格</span><i></i>
        <span class="visual-node complete">校验</span><i></i>
        <span class="visual-node active">构建</span><i></i>
        <span class="visual-node">游戏包</span>
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-form-wrap">
        <div class="segmented-control" aria-label="账号操作">
          <button :class="{ active: mode === 'login' }" type="button" @click="setMode('login')">登录</button>
          <button :class="{ active: mode === 'register' }" type="button" @click="setMode('register')">注册</button>
        </div>
        <div class="page-heading compact">
          <p class="overline">{{ mode === 'login' ? 'WELCOME BACK' : 'CREATE ACCOUNT' }}</p>
          <h2>{{ mode === "login" ? "继续你的游戏项目" : "创建业务账号" }}</h2>
          <p>{{ mode === "login" ? "登录后查看项目与生成进度。" : "这是应用账号，不是数据库连接账号。" }}</p>
        </div>

        <form class="form-stack" @submit.prevent="submit">
          <label>
            <span>用户名</span>
            <input v-model.trim="form.username" autocomplete="username" required :minlength="mode === 'register' ? 4 : undefined" maxlength="20" placeholder="请输入用户名" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="form.password" :autocomplete="mode === 'register' ? 'new-password' : 'current-password'" type="password" required :minlength="mode === 'register' ? 6 : undefined" maxlength="32" placeholder="请输入密码" />
          </label>
          <label v-if="mode === 'register'">
            <span>确认密码</span>
            <input v-model="confirmation" autocomplete="new-password" type="password" required minlength="6" maxlength="32" placeholder="再次输入密码" />
          </label>
          <p v-if="localError || session.error" class="alert danger" role="alert">{{ localError || session.error }}</p>
          <button class="button primary full" :disabled="session.busy">
            {{ session.busy ? "正在处理…" : mode === "login" ? "登录" : "注册并登录" }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "./sessionStore";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const mode = ref("login");
const confirmation = ref("");
const localError = ref("");
const form = reactive({ username: "", password: "" });

function setMode(value) {
  mode.value = value;
  confirmation.value = "";
  localError.value = "";
  session.error = "";
}

async function submit() {
  localError.value = "";
  if (mode.value === "register" && form.password !== confirmation.value) {
    localError.value = "两次输入的密码不一致";
    return;
  }
  try {
    await session.authenticate(mode.value, { username: form.username.trim(), password: form.password });
    await router.replace(typeof route.query.redirect === "string" ? route.query.redirect : "/projects");
  } catch { /* rendered by store */ }
}
</script>
