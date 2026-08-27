<script setup lang="ts">
import type { ApiSceneStepDebugResp } from '@/types'

defineProps<{ modelValue: boolean; result: ApiSceneStepDebugResp | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

function statusType(status: string): 'success' | 'danger' | 'info' | 'warning' {
  if (status === 'success') return 'success'
  if (status === 'failed' || status === 'error') return 'danger'
  if (status === 'running') return 'warning'
  return 'info'
}

function formatJson(obj: unknown): string {
  if (!obj) return ''
  try { return JSON.stringify(obj, null, 2) } catch { return String(obj) }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="调试结果"
    width="720px"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <template v-if="result?.stepResult">
      <div class="debug-result">
        <div class="debug-result__summary">
          <el-tag :type="statusType(result.stepResult.status)">{{ result.stepResult.status }}</el-tag>
          <span v-if="result.stepResult.durationMs != null" class="debug-result__duration">{{ result.stepResult.durationMs }}ms</span>
        </div>

        <!-- 请求信息 -->
        <div v-if="result.stepResult.request" class="debug-result__section">
          <div class="debug-result__section-title">请求</div>
          <pre class="debug-result__pre">{{ formatJson(result.stepResult.request) }}</pre>
        </div>

        <!-- 响应信息 -->
        <div v-if="result.stepResult.response" class="debug-result__section">
          <div class="debug-result__section-title">响应</div>
          <pre class="debug-result__pre">{{ formatJson(result.stepResult.response) }}</pre>
        </div>

        <!-- 断言结果 -->
        <div v-if="result.stepResult.validatorResults?.length" class="debug-result__section">
          <div class="debug-result__section-title">断言结果</div>
          <el-table :data="result.stepResult.validatorResults" size="small">
            <el-table-column prop="name" label="断言" min-width="160" />
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 提取的变量 -->
        <div v-if="result.stepResult.extractedVariables && Object.keys(result.stepResult.extractedVariables).length" class="debug-result__section">
          <div class="debug-result__section-title">提取的变量</div>
          <el-table :data="Object.entries(result.stepResult.extractedVariables).map(([k, v]) => ({ name: k, value: v }))" size="small">
            <el-table-column prop="name" label="变量名" min-width="160" />
            <el-table-column prop="value" label="值" min-width="200" show-overflow-tooltip />
          </el-table>
        </div>
      </div>
    </template>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.debug-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.debug-result__summary {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.debug-result__duration {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-500);
}

.debug-result__section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.debug-result__section-title {
  padding: var(--space-xs) var(--space-md);
  background: var(--color-neutral-50, #fafafa);
  font-weight: 600;
  font-size: var(--font-size-sm);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.debug-result__pre {
  margin: 0;
  padding: var(--space-md);
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  background: var(--color-neutral-50, #fafafa);
  max-height: 300px;
  overflow-y: auto;
}
</style>
