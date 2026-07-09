<template>
  <section class="content-section">
    <div class="section-heading">
      <span>调试信息</span>
      <small>默认折叠，给开发定位问题用</small>
    </div>
    <div class="debug-grid">
      <article class="debug-card">
        <h3>SSE Events</h3>
        <div v-if="events.length === 0" class="empty-state">暂无事件</div>
        <ol v-else class="event-list">
          <li v-for="event in events" :key="`${event.stage}-${event.eventTime}-${event.message}`">
            <strong>{{ event.stage }}</strong>
            <span :class="['status-pill', event.status?.toLowerCase()]">{{ event.status }}</span>
            <p>{{ event.message }}</p>
          </li>
        </ol>
        <JsonViewer :value="events" label="查看 SSE JSON" />
      </article>
      <article class="debug-card">
        <h3>原始输出</h3>
        <JsonViewer :value="rawOutput" label="查看原始输出" />
        <h3>GameConfig</h3>
        <JsonViewer :value="gameConfig" label="查看 GameConfig" />
      </article>
    </div>
  </section>
</template>

<script setup>
import JsonViewer from "./JsonViewer.vue";

defineProps({
  events: {
    type: Array,
    default: () => []
  },
  rawOutput: {
    type: [Object, Array, String, null],
    default: null
  },
  gameConfig: {
    type: [Object, null],
    default: null
  }
});
</script>
