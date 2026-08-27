<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ApiMockDebugResponse } from '@/types'
import { debugMock } from '@/services/apiMock'

const props = defineProps<{
  modelValue: boolean
  mockId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(props.modelValue)
const executing = ref(false)
const requestHeaders = ref('')
const requestBody = ref('')
const result = ref<ApiMockDebugResponse | null>(null)

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) {
      result.value = null
      requestHeaders.value = ''
      requestBody.value = ''
    }
  },
)

watch(visible, (val) => emit('update:modelValue', val))

async function handleDebug() {
  if (!props.mockId) return
  let headers: Record<string, string> | undefined
  let body: unknown
  if (requestHeaders.value.trim()) {
    try {
      headers = JSON.parse(requestHeaders.value)
    } catch {
      headers = {}
      for (const line of requestHeaders.value.split('\n')) {
        const colon = line.indexOf(':')
        if (colon > 0) {
          headers[line.slice(0, colon).trim()] = line.slice(colon + 1).trim()
        }
      }
    }
  }
  if (requestBody.value.trim()) {
    try {
      body = JSON.parse(requestBody.value)
    } catch {
      body = requestBody.value
    }
  }
  executing.value = true
  try {
    result.value = await debugMock(props.mockId, { headers, body })
  } finally {
    executing.value = false
  }
}

function formatJson(obj: unknown): string {
  if (obj == null) return ''
  if (typeof obj === 'string') return obj
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="Mock 调试"
    size="560px"
    :close-on-click-modal="false"
  >
    <div class="mock-debug">
      <el-form label-position="top">
        <el-form-item label="请求头 (JSON 或 Header: Value 格式)">
          <el-input
            v-model="requestHeaders"
            type="textarea"
            :rows="3"
            placeholder='{"Content-Type": "application/json"}'
            class="mock-debug__mono"
          />
        </el-form-item>
        <el-form-item label="请求体 (JSON)">
          <el-input
            v-model="requestBody"
            type="textarea"
            :rows="5"
            placeholder='{"username": "admin", "password": "123456"}'
            class="mock-debug__mono"
          />
        </el-form-item>
      </el-form>

      <el-button type="primary" :loading="executing" @click="handleDebug">
        发送调试请求
      </el-button>

      <div v-if="result" class="mock-debug__result">
        <el-divider content-position="left">响应结果</el-divider>
        <div class="mock-debug__status">
          <el-tag :type="result.status < 400 ? 'success' : 'danger'" size="large">
            {{ result.status }}
          </el-tag>
          <span class="mock-debug__duration">{{ result.durationMs }}ms</span>
        </div>
        <el-form-item label="响应头">
          <pre class="mock-debug__pre">{{ formatJson(result.headers) }}</pre>
        </el-form-item>
        <el-form-item label="响应体">
          <pre class="mock-debug__pre">{{ formatJson(result.body) }}</pre>
        </el-form-item>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.mock-debug {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mock-debug__mono {
  :deep(textarea) {
    font-family: 'JetBrains Mono', 'Fira Code', monospace;
    font-size: 13px;
  }
}

.mock-debug__result {
  margin-top: 16px;
}

.mock-debug__status {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.mock-debug__duration {
  font-size: 13px;
  color: var(--color-neutral-400);
}

.mock-debug__pre {
  background: var(--color-neutral-50, #f5f5f5);
  border: 1px solid var(--color-neutral-200, #e5e5e5);
  border-radius: var(--radius-md);
  padding: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 300px;
  overflow-y: auto;
}
</style>
