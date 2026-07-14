<template>
  <section class="workflow-run-view">
    <div class="button-row">
      <button class="ghost-button" type="button" @click="$emit('back')">返回工作台</button>
      <span class="hint safe-wrap">运行标识：{{ workflowRunUuid }}</span>
    </div>

    <section v-if="run.loading && !run.snapshot" class="content-section" aria-busy="true">
      <p class="empty-state">正在加载服务端运行快照…</p>
    </section>
    <section v-else-if="run.error && !run.snapshot" class="content-section" role="alert">
      <h2>无法加载运行详情</h2>
      <p class="error">{{ run.error.message }}</p>
      <button class="secondary-button" type="button" :disabled="refreshing" @click="refresh">{{ refreshing ? "重新加载中…" : "重新加载" }}</button>
    </section>

    <template v-else-if="run.snapshot">
      <section class="content-section run-summary">
        <div>
          <p class="eyebrow">生成运行</p>
          <h2>{{ run.snapshot.status || "UNKNOWN" }}</h2>
          <p class="hint">第 {{ run.snapshot.attempt ?? 0 }} 次尝试 · {{ duration }}</p>
        </div>
        <div class="button-row">
          <button class="secondary-button" type="button" :disabled="refreshing || run.actionLoading" @click="refresh">{{ refreshing ? "刷新中…" : "刷新状态" }}</button>
          <button v-if="allowed('cancel')" class="secondary-button" type="button" :disabled="run.actionLoading" @click="command('cancel')">{{ run.actionLoading ? "处理中…" : "取消" }}</button>
          <button v-if="allowed('retry')" class="primary-button" type="button" :disabled="run.actionLoading" @click="command('retry')">{{ run.actionLoading ? "处理中…" : "重试" }}</button>
        </div>
      </section>

      <p v-if="visibleError" class="error content-section" role="alert">{{ visibleError }}</p>
      <WorkflowStepper :steps="run.steps" />
      <section v-if="run.snapshot.status === 'SUCCESS'" class="content-section result-summary">
        <h3>生成完成</h3>
        <p class="hint">服务端已确认本次生成完成，共提供 {{ run.artifacts.length }} 个结果产物。</p>
      </section>
      <ArtifactLibrary :artifacts="run.artifacts" />

      <section v-if="run.snapshot.status === 'SUCCESS'" class="content-section">
        <div class="section-heading"><div><span>游戏预览</span><p class="hint">只会使用服务端返回的可用 GameConfig。</p></div></div>
        <p v-if="gameConfigLoading" class="empty-state">正在加载游戏配置…</p>
        <div v-else-if="gameConfigError" class="config-error" role="alert"><strong>无法加载游戏预览</strong><p>{{ gameConfigError }}</p></div>
        <div v-else-if="!gameArtifact" class="empty-state">本次运行没有可用于预览的 GameConfig 产物。</div>
        <div v-else-if="!gameConfigValidation.valid" class="config-error" role="alert">
          <strong>GameConfig 不可用于预览</strong>
          <p v-for="error in gameConfigValidation.errors" :key="error">{{ error }}</p>
        </div>
        <PhaserGamePreview v-else :game-config="gameArtifact.content" />
      </section>
    </template>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import ArtifactLibrary from "../components/ArtifactLibrary.vue";
import PhaserGamePreview from "../components/PhaserGamePreview.vue";
import WorkflowStepper from "../components/WorkflowStepper.vue";
import { validateGameConfig } from "../game/gameConfig";

const props = defineProps({
  store: { type: Object, required: true },
  artifactApi: { type: Function, required: true },
  workflowRunUuid: { type: String, required: true }
});
defineEmits(["back"]);
const run = computed(() => props.store.ensure(props.workflowRunUuid));
const refreshing = ref(false);
const gameArtifact = ref(null);
const gameConfigLoading = ref(false);
const gameConfigError = ref("");
const gameConfigValidation = computed(() => gameArtifact.value ? validateGameConfig(gameArtifact.value.content) : { valid: false, errors: [] });
const visibleError = computed(() => run.value.error?.message || run.value.snapshot?.error?.message || "");
const duration = computed(() => run.value.snapshot?.timeTakenMs != null ? `${run.value.snapshot.timeTakenMs} ms` : "等待服务端完成");
const allowed = (action) => run.value.snapshot?.allowedActions?.includes(action);
let previewRequestVersion = 0;

async function refresh() {
  if (refreshing.value) return;
  refreshing.value = true;
  try {
    const latest = await props.store.open(props.workflowRunUuid);
    loadGameArtifact(previewArtifactUuid(latest.artifacts));
  }
  finally { refreshing.value = false; }
}

async function command(action) {
  try { await props.store[action](props.workflowRunUuid); }
  catch { /* The Store retains the server error for display. */ }
}

async function loadGameArtifact(artifactUuid) {
  const requestVersion = ++previewRequestVersion;
  gameArtifact.value = null;
  gameConfigError.value = "";
  if (!artifactUuid) return;
  gameConfigLoading.value = true;
  try {
    const artifact = await props.artifactApi(artifactUuid);
    if (requestVersion === previewRequestVersion) gameArtifact.value = artifact;
  } catch (error) {
    if (requestVersion === previewRequestVersion) gameConfigError.value = error.message || "无法读取 GameConfig 产物。";
  } finally {
    if (requestVersion === previewRequestVersion) gameConfigLoading.value = false;
  }
}

function previewArtifactUuid(artifacts = []) {
  return artifacts.find((artifact) =>
    artifact.status === "AVAILABLE" && ["GAME_CONFIG", "PHASER_GAME_CONFIG", "GAME_CONFIG_GENERATE_RESULT"].includes(artifact.type)
  )?.artifactUuid || "";
}

watch(() => run.value.artifacts, (artifacts) => {
  loadGameArtifact(previewArtifactUuid(artifacts));
}, { immediate: true, deep: true });
watch(() => run.value.snapshot?.status, (status) => {
  if (status === "SUCCESS") void refresh();
}, { immediate: true });
onMounted(refresh);
onBeforeUnmount(() => {
  previewRequestVersion += 1;
  props.store.disconnect(props.workflowRunUuid);
});
</script>
