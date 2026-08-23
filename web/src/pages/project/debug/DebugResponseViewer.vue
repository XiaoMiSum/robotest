<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ApiDebugExecuteResp } from '@/types'

const props = defineProps<{
  response: ApiDebugExecuteResp | null
}>()

type ViewTab = 'body' | 'headers' | 'raw'

const activeTab = ref<ViewTab>('body')
const prettyFailed = ref(false)

watch(
  () => props.response,
  () => {
    activeTab.value = 'body'
    prettyFailed.value = false
  },
)

const STATUS_COLORS: Record<'2' | '3' | '4' | '5', string> = {
  '2': 'var(--color-success-500, #67c23a)',
  '3': 'var(--color-primary-500, #409eff)',
  '4': 'var(--color-warning-500, #e6a23c)',
  '5': 'var(--color-danger-500, #f56c6c)',
}

const statusText = computed(() => {
  if (props.response?.status === 'error') return '请求失败'
  const code = props.response?.responseStatus
  return code ? String(code) : '—'
})

const statusColor = computed(() => {
  if (props.response?.status === 'error') return STATUS_COLORS['5']
  const code = props.response?.responseStatus
  if (!code) return undefined
  return STATUS_COLORS[String(Math.floor(code / 100)) as '2' | '3' | '4' | '5'] ?? undefined
})

const bodyText = computed(() => {
  const body = props.response?.responseBody
  if (body === null || body === undefined) return ''
  if (typeof body === 'string') return body
  try {
    return JSON.stringify(body, null, 2)
  } catch {
    // 循环引用等极端情况降级为字符串拼接
    return String(body)
  }
})

const headerEntries = computed(() => Object.entries(props.response?.responseHeaders ?? {}))

function formatSize(size?: number): string {
  if (!size && size !== 0) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}
</script>

<template>
  <div class="resp-view">
    <template v-if="!response">
      <div class="resp-view__empty">点击「发送」查看响应</div>
    </template>

    <template v-else>
      <div class="resp-view__meta">
        <span class="resp-view__status" :style="{ color: statusColor }">{{ statusText }}</span>
        <span>{{ response.durationMs != null ? `${response.durationMs} ms` : '—' }}</span>
        <span>{{ formatSize(response.size) }}</span>
        <span v-if="response.errorMessage" class="resp-view__error" :title="response.errorMessage">
          {{ response.errorMessage }}
        </span>
      </div>

      <el-tabs v-model="activeTab" class="resp-view__tabs">
        <el-tab-pane label="响应体" name="body" />
        <el-tab-pane :label="`响应头 (${headerEntries.length})`" name="headers" />
        <el-tab-pane label="原始报文" name="raw" />
      </el-tabs>

      <pre v-if="activeTab === 'body'" class="resp-view__body">{{ bodyText || '（空响应体）' }}</pre>

      <table v-else-if="activeTab === 'headers'" class="resp-view__headers">
        <tbody>
          <tr v-for="[name, value] in headerEntries" :key="name + value">
            <td class="resp-view__header-name">{{ name }}</td>
            <td>{{ value }}</td>
          </tr>
          <tr v-if="!headerEntries.length">
            <td colspan="2" class="resp-view__empty resp-view__empty--inline">无响应头</td>
          </tr>
        </tbody>
      </table>

      <pre v-else class="resp-view__body">{{ JSON.stringify(response.responseBody ?? '', null, 2) }}</pre>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.resp-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  min-height: 0;

  &__empty {
    margin: auto;
    color: var(--color-neutral-300);
    font-size: var(--font-size-sm);

    &--inline {
      padding: 8px 0;
      text-align: center;
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--space-md);
    font-size: var(--font-size-xs);
    color: var(--color-neutral-500);
  }

  &__status {
    font-size: var(--font-size-md);
    font-weight: 700;
    font-family: ui-monospace, monospace;
  }

  &__error {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--color-danger-500, #f56c6c);
  }

  &__tabs {
    :deep(.el-tabs__header) {
      margin-bottom: var(--space-sm);
    }
  }

  &__body {
    flex: 1;
    min-height: 0;
    overflow: auto;
    margin: 0;
    padding: var(--space-sm);
    background: var(--color-neutral-50, #fafafa);
    border-radius: var(--radius-sm, 4px);
    font-family: ui-monospace, SFMono-Regular, monospace;
    font-size: var(--font-size-xs);
    white-space: pre-wrap;
    word-break: break-all;
  }

  &__headers {
    width: 100%;
    border-collapse: collapse;
    font-size: var(--font-size-xs);

    td {
      padding: 4px 8px 4px 0;
      vertical-align: top;
      word-break: break-all;
    }
  }

  &__header-name {
    color: var(--color-neutral-500);
    white-space: nowrap;
    font-family: ui-monospace, monospace;
  }
}
</style>
