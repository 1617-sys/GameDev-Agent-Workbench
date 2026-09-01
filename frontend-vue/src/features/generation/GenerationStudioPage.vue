<template>
  <div class="page-stack generation-page">
    <header class="page-heading with-action generation-heading">
      <div>
        <RouterLink class="back-link" to="/projects"><ArrowLeft :size="16" />返回项目</RouterLink>
        <p class="overline">COCOS GENERATION STUDIO · V5</p>
        <h1>{{ project?.name || "Cocos 游戏生成台" }}</h1>
        <p>编辑受约束 GameSpec，先编译验证，再生成真实 Web Mobile 游戏包。</p>
      </div>
      <div class="generation-heading-actions">
        <RouterLink class="button ghost" :to="`/projects/${projectUuid}/idea-studio`"><Sparkles :size="16" />旧版创意工作流</RouterLink>
        <RouterLink class="button ghost" :to="`/projects/${projectUuid}/versions`"><GitCompareArrows :size="16" />版本与调参</RouterLink>
      </div>
    </header>

    <p v-if="pageError" class="alert danger" role="alert"><CircleAlert :size="18" />{{ pageError }}</p>
    <div v-if="loading" class="empty-panel"><LoaderCircle class="spin" :size="25" /><p>正在读取项目与能力注册表…</p></div>

    <template v-else>
      <section class="capability-bar">
        <div><small>玩法模板</small><strong>{{ capabilities?.archetype || "arcade_collect" }}</strong></div>
        <div><small>GameSpec</small><strong>{{ capabilities?.specVersion || "0.1" }}</strong></div>
        <div><small>Cocos Creator</small><strong>{{ capabilities?.cocosCreatorVersion || "3.8.8" }}</strong></div>
        <div><small>构建目标</small><strong>{{ capabilities?.buildTarget || "web-mobile" }}</strong></div>
        <div class="capability-digest"><small>Registry digest</small><code>{{ shortDigest(capabilities?.digest) }}</code></div>
      </section>

      <div class="generation-layout">
        <section class="generation-panel spec-panel">
          <header class="generation-panel-heading">
            <div><p class="overline">01 · AUTHOR</p><h2>游戏规格</h2></div>
            <button class="button ghost" type="button" @click="resetSpec"><RefreshCw :size="15" />恢复模板</button>
          </header>

          <div class="field-section">
            <h3>Spring AI 规格作者</h3>
            <label><span>用自然语言描述游戏，或要求修复当前规格</span><textarea v-model="idea" rows="3" maxlength="2000" placeholder="例如：做一个森林主题的水晶收集游戏，90 秒内收集全部水晶并到达传送门。"></textarea></label>
            <button class="button ghost" type="button" :disabled="busy || authoring || idea.trim().length < 10" @click="authorSpec">
              <Sparkles :size="16" />{{ authoring ? "Spring AI 生成与修复中…" : "生成 / 修复 GameSpec" }}
            </button>
          </div>

          <form class="form-stack" @submit.prevent="compileSpec">
            <div class="form-grid">
              <label><span>游戏标题</span><input v-model="form.title" maxlength="80" required @input="syncJson" /></label>
              <label><span>确定性种子</span><input v-model.number="form.seed" type="number" min="0" @input="syncJson" /></label>
            </div>
            <label><span>目标描述</span><textarea v-model="form.description" rows="3" maxlength="300" required @input="syncJson"></textarea></label>

            <div class="field-section">
              <h3>世界与玩家</h3>
              <div class="compact-form-grid">
                <label><span>宽度</span><input v-model.number="form.width" type="number" min="640" max="1920" @input="syncJson" /></label>
                <label><span>高度</span><input v-model.number="form.height" type="number" min="360" max="1080" @input="syncJson" /></label>
                <label><span>限时（秒）</span><input v-model.number="form.timeLimitSeconds" type="number" min="30" max="600" @input="syncJson" /></label>
                <label><span>背景颜色</span><input v-model="form.backgroundColor" type="color" @input="syncJson" /></label>
                <label><span>移动速度</span><input v-model.number="form.playerSpeed" type="number" min="80" max="420" @input="syncJson" /></label>
                <label><span>生命值</span><input v-model.number="form.playerHealth" type="number" min="1" max="10" @input="syncJson" /></label>
              </div>
            </div>

            <div class="field-section">
              <h3>关卡实体</h3>
              <div class="entity-controls">
                <label><span>水晶</span><input v-model.number="form.collectibleCount" type="range" min="1" max="6" @input="syncJson" /><strong>{{ form.collectibleCount }}</strong></label>
                <label><span>守卫</span><input v-model.number="form.enemyCount" type="range" min="0" max="4" @input="syncJson" /><strong>{{ form.enemyCount }}</strong></label>
                <label><span>障碍</span><input v-model.number="form.obstacleCount" type="range" min="0" max="6" @input="syncJson" /><strong>{{ form.obstacleCount }}</strong></label>
              </div>
            </div>

            <details class="json-editor">
              <summary><FileText :size="15" />高级：直接编辑 GameSpec JSON</summary>
              <textarea v-model="specText" rows="18" spellcheck="false" @input="markDirty"></textarea>
            </details>

            <p v-if="editorError" class="alert danger" role="alert"><CircleAlert :size="17" />{{ editorError }}</p>
            <div v-if="diagnostics.length" class="diagnostics" aria-live="polite">
              <article v-for="(item, index) in diagnostics" :key="`${item.path}-${item.code}-${index}`" :class="item.severity?.toLowerCase()">
                <CircleAlert :size="16" /><div><strong>{{ item.code }}</strong><code>{{ item.path || "/" }}</code><p>{{ item.message }}</p><small v-if="item.allowedValues?.length">允许值：{{ item.allowedValues.join("、") }}</small></div>
              </article>
            </div>
            <p v-else-if="compilation?.status === 'SUCCEEDED'" class="compile-success"><CheckCircle2 :size="17" />规格合法，已生成确定性 Runtime IR。</p>

            <div class="generation-actions">
              <button class="button ghost large" type="submit" :disabled="busy"><ShieldCheck :size="17" />{{ compiling ? "正在编译…" : "编译并验证" }}</button>
              <button class="button primary large" type="button" :disabled="busy || compilation?.status !== 'SUCCEEDED' || compiledText !== specText" @click="createAndBuild">
                <Rocket :size="17" />{{ building ? "Cocos 构建中…" : "创建并构建游戏" }}
              </button>
            </div>
          </form>
        </section>

        <aside class="generation-side">
          <section class="generation-panel pipeline-panel">
            <header class="generation-panel-heading"><div><p class="overline">02 · BUILD</p><h2>生成流水线</h2></div><span class="status-pill" :class="`tone-${runMeta.tone}`"><span></span>{{ runMeta.label }}</span></header>
            <ol class="generation-pipeline">
              <li v-for="(step, index) in pipeline" :key="step.title" :class="pipelineClass(index + 1)"><span>{{ index + 1 }}</span><div><strong>{{ step.title }}</strong><small>{{ step.note }}</small></div></li>
            </ol>
            <div v-if="!run" class="pipeline-empty"><PackageOpen :size="26" /><p>规格验证通过后即可创建构建任务。</p></div>
            <template v-else>
              <dl class="run-facts">
                <div><dt>Run UUID</dt><dd><code>{{ run.runUuid }}</code></dd></div>
                <div><dt>状态版本</dt><dd>{{ run.stateVersion }}</dd></div>
                <div><dt>GameSpec digest</dt><dd><code>{{ shortDigest(run.sourceDigest) }}</code></dd></div>
                <div><dt>Runtime IR digest</dt><dd><code>{{ shortDigest(run.runtimeIrDigest) }}</code></dd></div>
                <div v-if="run.packageDigest"><dt>Package digest</dt><dd><code>{{ shortDigest(run.packageDigest) }}</code></dd></div>
              </dl>
              <p v-if="run.errorCode" class="alert danger"><CircleAlert :size="16" />{{ run.errorCode }}</p>
              <div class="pipeline-actions">
                <button v-if="canBuild && !building" class="button primary full" type="button" @click="buildRun"><Hammer :size="17" />{{ run.status === 'BUILDING' ? "接管超时构建" : "开始构建" }}</button>
                <button v-if="canPreview" class="button ghost full" type="button" :disabled="downloading" @click="downloadPreview"><Download :size="17" />{{ downloading ? "正在下载…" : "下载内部试玩包" }}</button>
                <template v-if="run.status === 'AWAITING_APPROVAL'">
                  <input v-model="approvalReason" maxlength="500" placeholder="填写人工试玩结论" />
                  <button class="button primary full" type="button" :disabled="approving || !approvalReason.trim()" @click="decide('APPROVED')">批准发布</button>
                  <button class="button ghost full" type="button" :disabled="approving || !approvalReason.trim()" @click="decide('REJECTED')">拒绝发布</button>
                </template>
                <button v-if="run.status === 'APPROVED'" class="button primary full" type="button" :disabled="releasing" @click="releaseRun">{{ releasing ? "发布中…" : "生成正式发布版本" }}</button>
                <button v-if="canDownload" class="button primary full" type="button" :disabled="downloading" @click="downloadArtifact"><Download :size="17" />{{ downloading ? "正在下载…" : "下载正式游戏包" }}</button>
                <button class="button ghost full" type="button" :disabled="refreshing" @click="refreshRun"><RefreshCw :size="16" />刷新任务状态</button>
              </div>
            </template>
          </section>

          <section v-if="compilation?.status === 'SUCCEEDED'" class="generation-panel digest-panel">
            <p class="overline">COMPILE OUTPUT</p>
            <h3>确定性摘要</h3>
            <dl><div><dt>Source</dt><dd><code>{{ shortDigest(compilation.sourceDigest) }}</code></dd></div><div><dt>Runtime IR</dt><dd><code>{{ shortDigest(compilation.runtimeIrDigest) }}</code></dd></div></dl>
            <details><summary>查看 Build Request</summary><pre>{{ pretty(compilation.buildRequest) }}</pre></details>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, CheckCircle2, CircleAlert, Download, FileText, GitCompareArrows, Hammer, LoaderCircle, PackageOpen, RefreshCw, Rocket, ShieldCheck, Sparkles } from "@lucide/vue";
import { gameGenerationApi, saveGenerationArtifact } from "../../shared/api/gameGeneration.js";
import { projectsApi } from "../../shared/api/projects.js";
import { createArcadeCollectSpec, defaultGameSpecForm, generationStatusMeta, parsePersistedJson } from "../../shared/presentation/gameSpec.js";

const route = useRoute();
const router = useRouter();
const projectUuid = computed(() => String(route.params.projectUuid || ""));
const project = ref(null);
const capabilities = ref(null);
const loading = ref(true);
const pageError = ref("");
const editorError = ref("");
const compiling = ref(false);
const authoring = ref(false);
const building = ref(false);
const refreshing = ref(false);
const downloading = ref(false);
const approving = ref(false);
const releasing = ref(false);
const approvalReason = ref("");
const compilation = ref(null);
const idea = ref("");
const run = ref(null);
const compiledText = ref("");
const form = reactive(defaultGameSpecForm());
const specText = ref(pretty(createArcadeCollectSpec(form)));
const diagnostics = computed(() => compilation.value?.diagnostics?.length
  ? compilation.value.diagnostics
  : parsePersistedJson(run.value?.diagnosticsJson, []));
const runMeta = computed(() => generationStatusMeta(run.value?.status));
const canPreview = computed(() => Boolean(run.value?.packageDigest) && ["AWAITING_APPROVAL", "APPROVED", "RELEASED"].includes(run.value?.status));
const canDownload = computed(() => Boolean(run.value?.packageDigest) && run.value?.status === "RELEASED");
const canBuild = computed(() => run.value?.status === "READY_TO_BUILD"
  || (run.value?.status === "BUILDING" && run.value?.buildClaimExpiresAt
    && Date.parse(run.value.buildClaimExpiresAt) < Date.now()));
const busy = computed(() => compiling.value || building.value || authoring.value);
const pipeline = [
  { title: "GameSpec 校验", note: "封闭字段、范围与终局检查" },
  { title: "Runtime IR 编译", note: "生成 canonical spec 与摘要" },
  { title: "Cocos Web Mobile 构建", note: "隔离工作区执行 Creator 3.8.8" },
  { title: "本地可玩包", note: "封装 manifest、来源与启动器" },
  { title: "人工审批", note: "试玩结论独立留痕" },
  { title: "正式发布", note: "审批通过后显式发布" }
];

onMounted(async () => {
  try {
    [project.value, capabilities.value] = await Promise.all([
      projectsApi.get(projectUuid.value),
      gameGenerationApi.capabilities()
    ]);
    if (route.query.run) await loadRun(String(route.query.run));
  } catch (cause) { pageError.value = cause.message || "无法打开 Cocos 生成台"; }
  finally { loading.value = false; }
});

function pretty(value) { return JSON.stringify(value, null, 2); }
function shortDigest(value) { return value ? `${value.slice(0, 12)}…${value.slice(-8)}` : "—"; }
function syncJson() { specText.value = pretty(createArcadeCollectSpec(form)); markDirty(); }
function markDirty() { editorError.value = ""; if (compiledText.value !== specText.value) compilation.value = null; }
function resetSpec() { Object.assign(form, defaultGameSpecForm()); syncJson(); run.value = null; void router.replace({ query: {} }); }

function parsedSpec() {
  try {
    const value = JSON.parse(specText.value);
    if (!value || Array.isArray(value) || typeof value !== "object") throw new Error();
    editorError.value = "";
    return value;
  } catch {
    editorError.value = "GameSpec 必须是合法的 JSON 对象";
    return null;
  }
}

async function compileSpec() {
  // 高级 JSON 编辑器可能包含语法错误；只把合法对象发送给后端权威编译器。
  const spec = parsedSpec();
  if (!spec) return null;
  compiling.value = true;
  pageError.value = "";
  try {
    compilation.value = await gameGenerationApi.compile(projectUuid.value, spec);
    compiledText.value = specText.value;
    return compilation.value;
  } catch (cause) { pageError.value = cause.message || "GameSpec 编译失败"; return null; }
  finally { compiling.value = false; }
}

async function authorSpec() {
  authoring.value = true;
  pageError.value = "";
  try {
    // 把当前编辑内容一并发送：有内容时是“修改/修复”，没有内容时是“从创意生成”。
    const currentSpec = parsedSpec();
    const result = await gameGenerationApi.author(projectUuid.value, idea.value.trim(), currentSpec);
    specText.value = pretty(result.spec);
    compilation.value = result.compilation;
    compiledText.value = result.status === "SUCCEEDED" ? specText.value : "";
    editorError.value = "";
  } catch (cause) { pageError.value = cause.message || "Spring AI 无法生成有效 GameSpec"; }
  finally { authoring.value = false; }
}

async function createAndBuild() {
  // 规格自上次编译后被修改过时禁止构建，避免页面展示和实际构建的内容不一致。
  if (compiledText.value !== specText.value || compilation.value?.status !== "SUCCEEDED") return;
  const spec = parsedSpec();
  if (!spec) return;
  building.value = true;
  pageError.value = "";
  try {
    const key = globalThis.crypto?.randomUUID?.() || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    run.value = await gameGenerationApi.create(projectUuid.value, spec, key);
    await router.replace({ query: { run: run.value.runUuid } });
    if (run.value.status === "READY_TO_BUILD") await buildRun();
  } catch (cause) { pageError.value = cause.message || "创建 Cocos 构建任务失败"; }
  finally { building.value = false; }
}

async function buildRun() {
  if (!run.value) return;
  building.value = true;
  pageError.value = "";
  try {
    // stateVersion 是乐观锁：若另一个请求已改变状态，后端会拒绝这个旧版本请求。
    await gameGenerationApi.build(projectUuid.value, run.value.runUuid, run.value.stateVersion);
    await loadRun(run.value.runUuid);
  } catch (cause) { pageError.value = cause.message || "Cocos 构建失败"; }
  finally { building.value = false; }
}

async function loadRun(uuid) { run.value = await gameGenerationApi.get(projectUuid.value, uuid); }
async function refreshRun() {
  if (!run.value) return;
  refreshing.value = true;
  try { await loadRun(run.value.runUuid); }
  catch (cause) { pageError.value = cause.message || "刷新任务失败"; }
  finally { refreshing.value = false; }
}
async function downloadArtifact() {
  downloading.value = true;
  try { saveGenerationArtifact(await gameGenerationApi.download(projectUuid.value, run.value.runUuid)); }
  catch (cause) { pageError.value = cause.message || "下载游戏包失败"; }
  finally { downloading.value = false; }
}
async function downloadPreview() {
  downloading.value = true;
  try { saveGenerationArtifact(await gameGenerationApi.downloadPreview(projectUuid.value, run.value.runUuid)); }
  catch (cause) { pageError.value = cause.message || "下载内部试玩包失败"; }
  finally { downloading.value = false; }
}
async function decide(decision) {
  approving.value = true;
  pageError.value = "";
  try {
    // 审批使用独立幂等键，网络重试不会重复写入两条人工决定。
    const key = globalThis.crypto?.randomUUID?.() || `approval-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    await gameGenerationApi.approve(projectUuid.value, run.value.runUuid, decision, approvalReason.value.trim(), key);
    await loadRun(run.value.runUuid);
  } catch (cause) { pageError.value = cause.message || "提交审批失败"; }
  finally { approving.value = false; }
}
async function releaseRun() {
  releasing.value = true;
  pageError.value = "";
  try {
    // APPROVED 只代表试玩通过；release 是第二个显式门禁，成功后才开放正式包下载。
    await gameGenerationApi.release(projectUuid.value, run.value.runUuid, run.value.stateVersion);
    await loadRun(run.value.runUuid);
  } catch (cause) { pageError.value = cause.message || "正式发布失败"; }
  finally { releasing.value = false; }
}
function pipelineClass(step) {
  if (run.value?.status === "FAILED" && step >= runMeta.value.step) return "failed";
  if (step < runMeta.value.step || (canDownload.value && step <= 6)) return "complete";
  if (step === runMeta.value.step || (!run.value && compilation.value?.status === "SUCCEEDED" && step <= 2)) return "active";
  return "";
}
</script>
