<template>
  <div class="page-stack studio-page">
    <header class="page-heading">
      <RouterLink class="back-link" to="/projects"><ArrowLeft :size="16" />返回项目</RouterLink>
      <p class="overline">GAME STUDIO</p>
      <h1>{{ project?.name || "游戏创作台" }}</h1>
      <p>{{ project?.description || "正在读取项目信息…" }}</p>
      <RouterLink class="button ghost" :to="`/projects/${route.params.projectUuid}/director`"><Route :size="16" />Director 实验</RouterLink>
      <RouterLink class="button ghost" :to="`/projects/${route.params.projectUuid}/versions`"><GitCompareArrows :size="16" />版本与调参</RouterLink>
    </header>

    <p v-if="loadError" class="alert danger" role="alert">{{ loadError }}</p>
    <div v-if="loading" class="empty-panel"><LoaderCircle class="spin" :size="24" /><p>正在准备创作台…</p></div>
    <div v-else-if="project" class="studio-layout">
      <section class="studio-form-panel">
        <div class="section-title"><div><p class="overline">NEW GENERATION</p><h2>描述你的游戏想法</h2></div><Sparkles :size="22" /></div>
        <form class="form-stack" @submit.prevent="submit">
          <label>
            <span>主题与玩法</span>
            <textarea v-model="form.idea" required maxlength="5000" rows="8" placeholder="例如：做一个 90 秒的博物馆夺宝小游戏，玩家需要拿到三件藏品，避开巡逻守卫，然后从出口离开。"></textarea>
            <small>{{ form.idea.length }}/5000</small>
          </label>
          <div class="form-grid">
            <label><span>单局时长</span><select v-model.number="form.durationSeconds"><option :value="60">60 秒</option><option :value="90">90 秒</option><option :value="180">180 秒</option></select></label>
            <label><span>难度</span><select v-model="form.difficulty"><option value="easy">轻松</option><option value="normal">标准</option><option value="hard">挑战</option></select></label>
          </div>
          <label><span>视觉主题</span><input v-model="form.visualTheme" required maxlength="80" placeholder="例如：深色博物馆、扁平几何霓虹风" /></label>
          <label><span>补充要求 <em>可选</em></span><textarea v-model="form.additionalRequirements" maxlength="2000" rows="4" placeholder="可以补充目标玩家、氛围或操作反馈要求。"></textarea><small>{{ form.additionalRequirements.length }}/2000</small></label>
          <p v-if="error" class="alert danger" role="alert">{{ error }}</p>
          <div class="form-actions"><span><ShieldCheck :size="16" />提交后可离开页面，任务将在后台继续执行</span><button class="button primary large" :disabled="submitting"><WandSparkles :size="18" />{{ submitting ? "正在创建任务…" : "开始生成" }}</button></div>
        </form>
      </section>

      <aside class="studio-aside">
        <section class="plain-section">
          <h3>项目设置</h3>
          <dl class="detail-list"><div><dt>游戏类型</dt><dd>俯视角收集</dd></div><div><dt>目标平台</dt><dd>Web / H5</dd></div><div><dt>项目状态</dt><dd>{{ project.status || "可创作" }}</dd></div></dl>
        </section>
        <section id="recent-runs" class="plain-section recent-runs-section">
          <div class="aside-heading">
            <h3>最近生成</h3>
            <button class="icon-button compact" type="button" title="刷新生成记录" :disabled="runsLoading" @click="loadRuns">
              <RefreshCw :size="15" :class="{ spin: runsLoading }" />
            </button>
          </div>
          <p v-if="runsError" class="inline-error">{{ runsError }}</p>
          <p v-else-if="runsLoading && recentRuns.length === 0" class="aside-empty">正在读取生成记录...</p>
          <p v-else-if="recentRuns.length === 0" class="aside-empty">还没有生成任务</p>
          <div v-else class="recent-run-list">
            <RouterLink v-for="item in recentRuns" :key="item.workflowRunUuid" class="recent-run-item" :to="`/runs/${item.workflowRunUuid}`">
              <span class="run-icon"><Clock3 :size="15" /></span>
              <span class="run-copy">
                <strong>{{ statusMeta(item.status).label }}</strong>
                <small>{{ formatRunTime(item.createdAt) }} · 第 {{ item.attempt || 1 }} 次</small>
              </span>
              <ExternalLink :size="14" />
            </RouterLink>
          </div>
        </section>
        <section class="plain-section example-section">
          <h3>快速示例</h3>
          <button v-for="example in examples" :key="example.title" type="button" @click="useExample(example)"><span>{{ example.title }}</span><small>{{ example.note }}</small></button>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, Clock3, ExternalLink, GitCompareArrows, LoaderCircle, RefreshCw, Route, ShieldCheck, Sparkles, WandSparkles } from "@lucide/vue";
import { projectsApi } from "../../shared/api/projects";
import { workflowsApi } from "../../shared/api/workflows";
import { prepareSubmission } from "../../shared/presentation/submission";
import { statusMeta } from "../../shared/presentation/workflow";

const route = useRoute();
const router = useRouter();
const project = ref(null);
const loading = ref(true);
const loadError = ref("");
const submitting = ref(false);
const error = ref("");
const recentRuns = ref([]);
const runsLoading = ref(false);
const runsError = ref("");
const form = reactive({ idea: "", durationSeconds: 90, difficulty: "normal", visualTheme: "扁平几何霓虹风", additionalRequirements: "" });
let pending = null;
const examples = [
  { title: "博物馆夺宝", note: "潜入与收集", idea: "玩家需要拿到三件藏品，避开巡逻守卫，然后从右下角出口离开。", durationSeconds: 90, difficulty: "normal", visualTheme: "深色博物馆霓虹风" },
  { title: "太空维修", note: "限时与躲避", idea: "玩家收集三个维修零件，避开失控机器人并返回安全舱。", durationSeconds: 60, difficulty: "hard", visualTheme: "冷色太空站扁平风" },
  { title: "森林寻药", note: "轻松探索", idea: "玩家收集三种草药，躲避巡逻野兽，最后回到营地。", durationSeconds: 180, difficulty: "easy", visualTheme: "明亮森林绘本风" }
];

onMounted(async () => {
  await Promise.all([loadProject(), loadRuns()]);
});

async function loadProject() {
  try { project.value = await projectsApi.get(route.params.projectUuid); }
  catch (cause) { loadError.value = cause.message; }
  finally { loading.value = false; }
}

async function loadRuns() {
  runsLoading.value = true;
  runsError.value = "";
  try { recentRuns.value = await workflowsApi.projectRuns(route.params.projectUuid); }
  catch (cause) { runsError.value = cause.message || "无法读取生成记录"; }
  finally { runsLoading.value = false; }
}

function formatRunTime(value) {
  if (!value) return "时间未知";
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function useExample(example) {
  Object.assign(form, {
    idea: example.idea,
    durationSeconds: example.durationSeconds,
    difficulty: example.difficulty,
    visualTheme: example.visualTheme,
    additionalRequirements: ""
  });
}
async function submit() {
  if (submitting.value) return;
  const prepared = prepareSubmission(form, pending);
  if (prepared.error) { error.value = prepared.error; return; }
  pending = prepared.pending;
  submitting.value = true;
  error.value = "";
  try {
    const result = await workflowsApi.submit(project.value.projectUuid, prepared.request, pending.idempotencyKey);
    if (!result?.workflowRunUuid) throw new Error("服务端没有返回运行标识");
    pending = null;
    await router.push(`/runs/${result.workflowRunUuid}`);
  } catch (cause) { error.value = cause.message || "创建生成任务失败"; }
  finally { submitting.value = false; }
}
</script>
