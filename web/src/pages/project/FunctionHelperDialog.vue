<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiBuiltinFunctionGroup, ApiCustomFunctionListItem } from '@/types'
import { fetchBuiltinCatalog, fetchCustomFunctions, evaluateFunction } from '@/services/apiFunction'
import {
  buildEvaluateExpression,
  resolveFunctionError,
  type UnifiedFunctionItem,
  unifyFunctionList,
} from './functionModel'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'insert': [expression: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// ==================== 数据加载 ====================

const loading = ref(false)
const builtinGroups = ref<ApiBuiltinFunctionGroup[]>([])
const customList = ref<ApiCustomFunctionListItem[]>([])
const searchKeyword = ref('')

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const [builtin, custom] = await Promise.all([fetchBuiltinCatalog(), fetchCustomFunctions()])
    builtinGroups.value = builtin
    customList.value = custom
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  } finally {
    loading.value = false
  }
}

watch(visible, (v) => {
  if (v) {
    void loadData()
    resetState()
  }
})

// ==================== 函数选择 ====================

const selectedFunctionName = ref('')
const paramValues = reactive<Record<string, string>>({})
const expressionInput = ref('')

const allFunctions = computed<UnifiedFunctionItem[]>(() =>
  unifyFunctionList(builtinGroups.value, customList.value),
)

const filteredFunctions = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return allFunctions.value
  return allFunctions.value.filter(
    (fn) => fn.name.toLowerCase().includes(kw) || fn.description.toLowerCase().includes(kw),
  )
})

const groupedFunctions = computed(() => {
  const map = new Map<string, UnifiedFunctionItem[]>()
  for (const fn of filteredFunctions.value) {
    const key = fn.type === 'builtin' ? '内置函数' : '自定义函数'
    const arr = map.get(key) ?? []
    arr.push(fn)
    map.set(key, arr)
  }
  return map
})

const selectedFunction = computed(() =>
  allFunctions.value.find((fn) => fn.name === selectedFunctionName.value) ?? null,
)

watch(selectedFunctionName, () => {
  // 重置参数值
  for (const key of Object.keys(paramValues)) {
    delete paramValues[key]
  }
  if (selectedFunction.value) {
    for (const param of selectedFunction.value.params) {
      paramValues[param.name] = ''
    }
  }
  expressionInput.value = ''
  trialResult.value = null
})

// ==================== 表达式生成 ====================

const generatedExpression = computed(() => {
  if (!selectedFunction.value) return ''
  return buildEvaluateExpression(selectedFunction.value.name, paramValues)
})

// 选择函数或参数变化时，自动同步到表达式输入框
watch(generatedExpression, (val) => {
  expressionInput.value = val
})

// ==================== 试算 ====================

const trialResult = ref<string | null>(null)
const trialDuration = ref<number | null>(null)
const trialing = ref(false)

async function handleTrial(): Promise<void> {
  const expr = expressionInput.value.trim() || generatedExpression.value
  if (!expr) {
    ElMessage.warning('请输入或生成表达式')
    return
  }
  trialing.value = true
  trialResult.value = null
  trialDuration.value = null
  try {
    const resp = await evaluateFunction(expr)
    trialResult.value = resp.result
    trialDuration.value = resp.durationMs
  } catch (err) {
    ElMessage.error(resolveFunctionError(err))
  } finally {
    trialing.value = false
  }
}

// ==================== 复制 ====================

async function handleCopy(expression: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(expression)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

// ==================== 插入 ====================

function handleInsert(): void {
  const expr = expressionInput.value.trim() || generatedExpression.value
  if (!expr) {
    ElMessage.warning('请先选择函数并填写参数')
    return
  }
  emit('insert', expr)
  visible.value = false
}

// ==================== 重置 ====================

function resetState(): void {
  selectedFunctionName.value = ''
  searchKeyword.value = ''
  expressionInput.value = ''
  trialResult.value = null
  trialDuration.value = null
  for (const key of Object.keys(paramValues)) {
    delete paramValues[key]
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="函数助手"
    width="680px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="fn-helper">
      <!-- 函数选择 -->
      <div class="fn-helper__section">
        <label class="fn-helper__label">选择函数</label>
        <el-select
          v-model="selectedFunctionName"
          filterable
          :filter-method="(val: string) => (searchKeyword = val)"
          placeholder="搜索函数名称..."
          class="fn-helper__select"
        >
          <el-option-group v-for="[group, fns] in groupedFunctions" :key="group" :label="group">
            <el-option
              v-for="fn in fns"
              :key="fn.name"
              :label="`${fn.name} — ${fn.description}`"
              :value="fn.name"
            />
          </el-option-group>
        </el-select>
      </div>

      <!-- 函数说明 -->
      <div v-if="selectedFunction" class="fn-helper__section fn-helper__info">
        <div class="fn-helper__info-row">
          <span class="fn-helper__info-label">签名</span>
          <code class="fn-helper__info-value fn-helper__code">{{ selectedFunction.signature }}</code>
        </div>
        <div class="fn-helper__info-row">
          <span class="fn-helper__info-label">描述</span>
          <span class="fn-helper__info-value">{{ selectedFunction.description }}</span>
        </div>
        <div v-if="selectedFunction.params.length > 0" class="fn-helper__info-row">
          <span class="fn-helper__info-label">参数</span>
          <div class="fn-helper__info-value">
            <div v-for="p in selectedFunction.params" :key="p.name" class="fn-helper__param-desc">
              <code class="fn-helper__code">{{ p.name }}</code>
              <el-tag v-if="p.required" size="small" type="warning" effect="light">必填</el-tag>
              <span class="fn-helper__param-desc-text">{{ p.description }}</span>
            </div>
          </div>
        </div>
        <div class="fn-helper__info-row">
          <span class="fn-helper__info-label">示例</span>
          <code class="fn-helper__info-value fn-helper__code">{{ selectedFunction.example }}</code>
        </div>
      </div>

      <!-- 参数填写 -->
      <div v-if="selectedFunction && selectedFunction.params.length > 0" class="fn-helper__section">
        <label class="fn-helper__label">参数填写</label>
        <el-form label-position="top" class="fn-helper__params">
          <el-form-item
            v-for="p in selectedFunction.params"
            :key="p.name"
            :label="p.name"
            :required="p.required"
          >
            <el-input v-model="paramValues[p.name]" :placeholder="p.description" />
          </el-form-item>
        </el-form>
      </div>

      <!-- 生成表达式 + 试算（合并） -->
      <div class="fn-helper__section">
        <label class="fn-helper__label">表达式</label>
        <div class="fn-helper__expr-row">
          <el-input
            v-model="expressionInput"
            :placeholder="generatedExpression || '选择函数并填写参数后自动生成，或直接输入表达式'"
            class="fn-helper__expr-input"
          />
          <el-button :loading="trialing" @click="handleTrial">试算</el-button>
          <el-button @click="handleCopy(expressionInput)">复制</el-button>
        </div>
        <div v-if="trialResult !== null" class="fn-helper__trial-result">
          <span class="fn-helper__trial-label">试算结果</span>
          <code class="fn-helper__code">{{ trialResult }}</code>
          <span v-if="trialDuration !== null" class="fn-helper__trial-duration">
            （{{ trialDuration }}ms）
          </span>
          <el-button link size="small" @click="handleCopy(trialResult)">复制结果</el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedFunction" @click="handleInsert">插入表达式</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.fn-helper {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.fn-helper__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.fn-helper__label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-700);
}

.fn-helper__select {
  width: 100%;
}

.fn-helper__info {
  background: var(--color-neutral-50, #f9fafb);
  border: 1px solid var(--color-neutral-100, #f3f4f6);
  border-radius: var(--radius-md);
  padding: var(--space-md);
}

.fn-helper__info-row {
  display: flex;
  gap: var(--space-md);
  padding: 4px 0;
  font-size: var(--font-size-sm);

  & + & {
    border-top: 1px solid var(--color-neutral-100, #f3f4f6);
    padding-top: 8px;
    margin-top: 4px;
  }
}

.fn-helper__info-label {
  width: 48px;
  flex-shrink: 0;
  color: var(--color-neutral-400);
  font-weight: 500;
}

.fn-helper__info-value {
  flex: 1;
  min-width: 0;
}

.fn-helper__code {
  background: var(--color-neutral-100, #f3f4f6);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-family: monospace;
  font-size: 13px;
}

.fn-helper__param-desc {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 2px 0;
}

.fn-helper__param-desc-text {
  color: var(--color-neutral-500);
}

.fn-helper__params {
  :deep(.el-form-item) {
    margin-bottom: 8px;
  }
  :deep(.el-form-item__label) {
    font-family: monospace;
    font-size: 13px;
  }
}

.fn-helper__expr-row,
.fn-helper__trial {
  display: flex;
  gap: var(--space-sm);
  align-items: center;
}

.fn-helper__expr-input,
.fn-helper__trial-input {
  flex: 1;
}

.fn-helper__trial-result {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-50, #f9fafb);
  border: 1px solid var(--color-neutral-100, #f3f4f6);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
}

.fn-helper__trial-label {
  color: var(--color-neutral-400);
  flex-shrink: 0;
}

.fn-helper__trial-duration {
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}
</style>
