<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatDateTime } from '@/utils/format'
import { useAiTaskStore } from '@/stores/aiTask'
import { aiTraceEventLabel } from '@/composables/project/ai/taskPresentation'

const props = defineProps<{
  taskId: string
  /** 最新一次执行 ID，无执行时不渲染轨迹入口 */
  executionId: string | null
}>()

const store = useAiTaskStore()
const expanded = ref(false)

const events = computed(() => store.events)
const loading = computed(() => store.eventsLoading)
const loadError = computed(() => store.eventsError)
const hasMore = computed(() => store.eventsHasMore)

async function load(reset: boolean): Promise<void> {
  if (!props.executionId) return
  await store.loadEvents(props.taskId, props.executionId, reset)
}

// 换执行实例（再次执行）后旧轨迹作废：展开态重拉，收起态只清缓存
watch(
  () => props.executionId,
  (executionId) => {
    if (!executionId) {
      store.resetEvents()
      return
    }
    if (expanded.value) void load(true)
    else store.resetEvents()
  },
  { immediate: true },
)

function toggle(): void {
  expanded.value = !expanded.value
  if (expanded.value && store.eventsExecutionId !== props.executionId) void load(true)
}
</script>

<template>
  <div v-if="executionId" class="trace-events">
    <div class="trace-events__head">
      <el-button link type="primary" @click="toggle">
        {{ expanded ? '收起轨迹 ▴' : '展开轨迹 ▾' }}
      </el-button>
      <template v-if="expanded">
        <el-button link :disabled="loading" @click="void load(false)">刷新</el-button>
        <span class="trace-events__count">共 {{ events.length }} 条</span>
      </template>
    </div>

    <div v-if="expanded" class="trace-events__body">
      <el-alert
        v-if="loadError"
        type="error"
        title="轨迹加载失败"
        show-icon
        :closable="false"
        class="trace-events__error"
      >
        <template #default>
          <el-button size="small" type="danger" plain :disabled="loading" @click="void load(false)">
            重试
          </el-button>
        </template>
      </el-alert>

      <el-skeleton v-else-if="loading && events.length === 0" :rows="3" animated />

      <el-empty
        v-else-if="events.length === 0"
        description="暂无轨迹事件"
        :image-size="60"
      />

      <ul v-else class="trace-events__list">
        <li v-for="event in events" :key="event.seq" class="trace-events__item">
          <span class="trace-events__seq">#{{ event.seq }}</span>
          <el-tag size="small" effect="plain" class="trace-events__type">
            {{ aiTraceEventLabel(event.eventType) }}
          </el-tag>
          <span class="trace-events__summary">{{ event.summary || '—' }}</span>
          <time class="trace-events__time">{{ formatDateTime(event.createdAt) }}</time>
        </li>
      </ul>

      <div v-if="hasMore && !loadError" class="trace-events__more">
        <el-button
          size="small"
          :loading="loading"
          :disabled="loading"
          @click="void load(false)"
        >
          加载更多
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.trace-events {
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.trace-events__head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.trace-events__count {
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.trace-events__body {
  margin-top: var(--space-sm);
}

.trace-events__error {
  margin-bottom: var(--space-sm);
}

.trace-events__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace-events__item {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
  font-size: var(--font-size-sm);
}

.trace-events__seq {
  flex-shrink: 0;
  color: var(--color-neutral-400);
  font-variant-numeric: tabular-nums;
}

.trace-events__type {
  flex-shrink: 0;
}

.trace-events__summary {
  flex: 1;
  min-width: 0;
  color: var(--color-neutral-700);
  overflow-wrap: anywhere;
}

.trace-events__time {
  flex-shrink: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.trace-events__more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-sm);
}
</style>
