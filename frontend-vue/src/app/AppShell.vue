<template>
  <div class="product-shell">
    <aside class="sidebar" :class="{ open: mobileMenuOpen }">
      <div class="sidebar-brand">
        <span class="brand-mark small">GF</span>
        <div><strong>GameFlow</strong><small>Cocos Generation Studio</small></div>
        <button class="icon-button mobile-only" type="button" title="关闭导航" @click="mobileMenuOpen = false"><X :size="18" /></button>
      </div>

      <nav class="primary-nav" aria-label="主导航">
        <RouterLink to="/projects" @click="mobileMenuOpen = false"><LayoutGrid :size="18" />项目中心</RouterLink>
      </nav>

      <div class="sidebar-section">
        <div class="sidebar-section-title"><span>最近项目</span><button class="icon-button" type="button" title="刷新项目" @click="projects.load(true)"><RefreshCw :size="15" /></button></div>
        <p v-if="projects.loading" class="sidebar-note">正在加载…</p>
        <p v-else-if="projects.items.length === 0" class="sidebar-note">还没有项目</p>
        <RouterLink v-for="project in projects.items.slice(0, 6)" :key="project.projectUuid" class="project-link" :to="`/projects/${project.projectUuid}/studio`" @click="mobileMenuOpen = false">
          <span class="project-dot"></span><span>{{ project.name }}</span>
        </RouterLink>
      </div>

      <div class="sidebar-account">
        <div class="avatar">{{ session.user?.username?.slice(0, 1)?.toUpperCase() || "U" }}</div>
        <div><strong>{{ session.user?.username || "用户" }}</strong><small>当前会话</small></div>
        <button class="icon-button" type="button" title="退出登录" @click="logout"><LogOut :size="17" /></button>
      </div>
    </aside>

    <div v-if="mobileMenuOpen" class="sidebar-backdrop" @click="mobileMenuOpen = false"></div>
    <div class="app-column">
      <header class="topbar">
        <button class="icon-button mobile-only" type="button" title="打开导航" @click="mobileMenuOpen = true"><Menu :size="20" /></button>
        <div class="breadcrumb"><span>GameFlow Studio</span><ChevronRight :size="15" /><strong>{{ route.meta.title || "项目" }}</strong></div>
        <div class="service-state"><span></span>服务已连接</div>
      </header>
      <main class="app-content"><slot /></main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ChevronRight, LayoutGrid, LogOut, Menu, RefreshCw, X } from "@lucide/vue";
import { useSessionStore } from "../features/auth/sessionStore";
import { useProjectsStore } from "../features/projects/projectsStore";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const projects = useProjectsStore();
const mobileMenuOpen = ref(false);

onMounted(() => projects.load());

async function logout() {
  session.clear();
  projects.$reset();
  await router.replace("/login");
}
</script>
