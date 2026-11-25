<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { useAiTaskStore } from '@/stores/aiTask'
import JsonResponseView from '@/components/common/JsonResponseView.vue'
import {
  aiTraceEventLabel,
  aiTraceRawRequest,
  aiTraceRawResponse,
  aiTraceResponseForDisplay,
  isAiDecisionEvent,
} from '@/composables/project/ai/taskPresentation'
import type { AiTaskTraceEvent } from '@/types'

const props = defineProps<{
  taskId: string
  /** 最新一次执行 ID，无执行时不渲染轨迹入口 */
  executionId: string | null
}>()

const store = useAiTaskStore()
const expanded = ref(false)
/** 手风琴：同一时刻只展开一条 AI 决策的原始详情，seq 为 null 表示全部收起 */
const openDetailSeq = ref<number | null>(null)

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
    openDetailSeq.value = null
    if (expanded.value) void load(true)
    else store.resetEvents()
  },
  { immediate: true },
)

function toggle(): void {
  expanded.value = !expanded.value
  if (!expanded.value) openDetailSeq.value = null
  if (expanded.value && store.eventsExecutionId !== props.executionId) void load(true)
}

function canExpand(event: AiTaskTraceEvent): boolean {
  return isAiDecisionEvent(event.eventType)
}

function toggleDetail(seq: number): void {
  openDetailSeq.value = openDetailSeq.value === seq ? null : seq
}

function requestOf(event: AiTaskTraceEvent) {
  return aiTraceRawRequest(event.detail?.rawRequest)
}

function responseOf(event: AiTaskTraceEvent) {
  return aiTraceRawResponse(event.detail?.rawResponse)
}

function displayResponseOf(event: AiTaskTraceEvent) {
  const response = responseOf(event)
  return response ? aiTraceResponseForDisplay(response) : null
}

async function copy(section: unknown): Promise<void> {
  try {
    await navigator.clipboard.writeText(JSON.stringify(section, null, 2))
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}
</script>

<template>
  <div v-if="executionId" class="trace-events">
    <div class="trace-events__head">
      <el-button link type="primary" @click="toggle">
        {{ expanded ? '收起轨迹 ▴' : '展开轨迹 ▾' }}
      </el-button>
      <template v-if="expanded">
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
          <el-button :disabled="loading" type="danger" plain size="small" @click="void load(false)">
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
          <div class="trace-events__row">
            <span class="trace-events__seq">#{{ event.seq }}</span>
            <el-tag size="small" effect="plain" class="trace-events__type">
              {{ aiTraceEventLabel(event.eventType) }}
            </el-tag>
            <span class="trace-events__summary">{{ event.summary || '—' }}</span>
            <el-button
              v-if="canExpand(event)"
              link
              type="primary"
              class="trace-events__detail-toggle"
              @click="toggleDetail(event.seq)"
            >
              {{ openDetailSeq === event.seq ? '收起详情 ▴' : '查看详情 ▾' }}
            </el-button>
            <time class="trace-events__time">{{ formatDateTime(event.createdAt) }}</time>
          </div>

          <div v-if="canExpand(event) && openDetailSeq === event.seq" class="trace-events__detail">
            <template v-if="requestOf(event) || responseOf(event)">
              <section class="trace-events__section">
                <div class="trace-events__section-head">
                  <span class="trace-events__section-title">请求摘要</span>
                  <el-button
                    v-if="requestOf(event)"
                    link
                    size="small"
                    :disabled="!requestOf(event)"
                    @click="void copy(requestOf(event))"
                  >
                    复制
                  </el-button>
                </div>
                <div v-if="requestOf(event)" class="trace-events__json">
                  <JsonResponseView
                    :value="requestOf(event)"
                    path="rawRequest"
                    :depth="0"
                    :initially-collapsed="true"
                  />
                </div>
                <div v-else class="trace-events__empty">暂无原始数据</div>
              </section>

              <section class="trace-events__section">
                <div class="trace-events__section-head">
                  <span class="trace-events__section-title">模型响应</span>
                  <el-button
                    v-if="displayResponseOf(event)"
                    link
                    size="small"
                    @click="void copy(displayResponseOf(event))"
                  >
                    复制
                  </el-button>
                </div>
                <div v-if="displayResponseOf(event)" class="trace-events__json">
                  <JsonResponseView
                    :value="displayResponseOf(event)"
                    path="rawResponse"
                    :depth="0"
                    :initially-collapsed="true"
                  />
                </div>
                <div v-else class="trace-events__empty">暂无原始数据</div>
              </section>
            </template>
            <div v-else class="trace-events__empty">暂无原始数据</div>
          </div>
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
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--color-neutral-100, var(--color-neutral-200));
  font-size: var(--font-size-sm);
}

.trace-events__row {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
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

.trace-events__detail-toggle {
  flex-shrink: 0;
  height: auto;
  padding: 0;
}

.trace-events__time {
  flex-shrink: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.trace-events__detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin-top: var(--space-xs);
  padding: var(--space-sm);
  background: var(--color-neutral-50, var(--color-neutral-100));
  border-radius: var(--radius-sm);
}

.trace-events__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trace-events__section-title {
  color: var(--color-neutral-700);
  font-weight: 600;
}

.trace-events__json {
  margin-top: var(--space-xs);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.trace-events__empty {
  padding: var(--space-xs) 0;
  color: var(--color-neutral-400);
}

.trace-events__more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-sm);
}
</style>
