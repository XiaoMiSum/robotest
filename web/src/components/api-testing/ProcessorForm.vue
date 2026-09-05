<template>
  <div class="processor-form">
    <!-- 类型选择：场景内由头部 radio 控制（show-type-select=false 时隐藏，避免双控件） -->
    <el-form-item v-if="showTypeSelect" label="处理器类型">
      <el-select v-model="state.testclass" placeholder="选择处理器类型">
        <el-option label="发送 HTTP 请求" value="http" />
        <el-option label="执行 SQL" value="jdbc" />
      </el-select>
    </el-form-item>
    <p v-else-if="!state.testclass" class="processor-form__hint">请在上方选择处理器类型</p>

    <!-- 配置信息：HTTP 请求分区（请求行 + Tabs 布局对齐步骤 RequestConfigEditor；path 为相对路径，故不放导入 cURL） -->
    <template v-if="state.testclass === 'http'">
      <section class="processor-form__section">
        <h4 class="processor-form__section-title">HTTP 请求配置</h4>
        <el-form label-position="top">
          <el-form-item label="引用配置 (ref)">
            <el-select v-model="state.http.ref" placeholder="选择环境 HTTP 配置">
              <el-option v-for="opt in httpOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="processor-form__request-line">
          <el-select v-model="state.http.method" class="processor-form__method">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
          <el-input v-model="state.http.path" placeholder="/token" class="processor-form__path" />
        </div>
        <el-tabs v-model="httpTab" class="processor-form__tabs">
          <el-tab-pane name="headers">
            <template #label>
              <span class="processor-form__tab-label">
                请求头
                <span v-if="headersBadge" class="processor-form__badge">{{ headersBadge }}</span>
              </span>
            </template>
            <table class="processor-form__kv-table">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Value</th>
                  <th class="processor-form__kv-op" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in state.http.headerRows" :key="index" class="processor-form__kv-tr">
                  <td><el-input v-model="item.key" size="small" placeholder="Key" /></td>
                  <td><el-input v-model="item.value" size="small" placeholder="Value" /></td>
                  <td class="processor-form__kv-op">
                    <el-button link size="small" type="danger" @click="removeRow(state.http.headerRows, index)">✕</el-button>
                  </td>
                </tr>
              </tbody>
            </table>
            <el-button size="small" @click="state.http.headerRows.push({ key: '', value: '' })">+ 添加请求头</el-button>
          </el-tab-pane>

          <el-tab-pane name="query">
            <template #label>
              <span class="processor-form__tab-label">
                Query 参数
                <span v-if="queryBadge" class="processor-form__badge">{{ queryBadge }}</span>
              </span>
            </template>
            <table class="processor-form__kv-table">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Value</th>
                  <th class="processor-form__kv-op" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in state.http.queryRows" :key="index" class="processor-form__kv-tr">
                  <td><el-input v-model="item.key" size="small" placeholder="参数名" /></td>
                  <td><el-input v-model="item.value" size="small" placeholder="Value" /></td>
                  <td class="processor-form__kv-op">
                    <el-button link size="small" type="danger" @click="removeRow(state.http.queryRows, index)">✕</el-button>
                  </td>
                </tr>
              </tbody>
            </table>
            <el-button size="small" @click="state.http.queryRows.push({ key: '', value: '' })">+ 添加 Query 参数</el-button>
          </el-tab-pane>

          <el-tab-pane name="body">
            <template #label>
              <span class="processor-form__tab-label">
                请求体
                <span v-if="bodyBadge" class="processor-form__badge">{{ bodyBadge }}</span>
              </span>
            </template>
            <div class="processor-form__body">
              <div class="processor-form__body-types">
                <button
                  v-for="t in BODY_KINDS"
                  :key="t.value"
                  class="processor-form__body-type"
                  :class="{ 'is-active': state.http.bodyKind === t.value }"
                  @click="state.http.bodyKind = t.value"
                >
                  {{ t.label }}
                </button>
              </div>

              <p v-if="state.http.bodyKind === 'none'" class="processor-form__body-hint">该请求不携带请求体。</p>

              <table v-else-if="state.http.bodyKind === 'form'" class="processor-form__kv-table">
                <thead>
                  <tr>
                    <th>Key</th>
                    <th>Value</th>
                    <th class="processor-form__kv-op" />
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(item, index) in state.http.formRows" :key="index" class="processor-form__kv-tr">
                    <td><el-input v-model="item.key" size="small" placeholder="Key" /></td>
                    <td><el-input v-model="item.value" size="small" placeholder="Value" /></td>
                    <td class="processor-form__kv-op">
                      <el-button link size="small" type="danger" @click="removeRow(state.http.formRows, index)">✕</el-button>
                    </td>
                  </tr>
                </tbody>
              </table>

              <textarea
                v-else
                v-model="state.http.bodyText"
                class="processor-form__body-editor"
                :placeholder="state.http.bodyKind === 'json' ? '{&quot;key&quot;: &quot;value&quot;}' : '原始文本（支持变量引用）'"
                spellcheck="false"
                :rows="12"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </template>

    <!-- 配置信息：SQL 分区（config 键与 Ryze jdbc 处理器一致） -->
    <template v-if="state.testclass === 'jdbc'">
      <section class="processor-form__section">
        <h4 class="processor-form__section-title">SQL 配置</h4>
        <el-form label-position="top">
          <el-form-item label="数据源 (ref)">
            <el-select v-model="state.jdbc.ref" placeholder="选择环境数据源">
              <el-option v-for="opt in dsOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
            </el-select>
          </el-form-item>
          <el-form-item label="SQL 语句 (sql)">
            <el-input v-model="state.jdbc.sql" type="textarea" :rows="4" placeholder="SELECT * FROM table WHERE id = ?" />
          </el-form-item>
          <el-form-item label="参数 (args)">
            <div v-for="(_, index) in state.jdbc.args" :key="index" class="processor-form__kv-row">
              <el-input v-model="state.jdbc.args[index]" size="small" placeholder="参数值" />
              <el-button link size="small" type="danger" @click="removeRow(state.jdbc.args, index)">✕</el-button>
            </div>
            <el-button size="small" @click="state.jdbc.args.push('')">+ 添加参数</el-button>
          </el-form-item>
        </el-form>
      </section>
    </template>

    <!-- 提取器（可选）：从处理器响应中提取变量供后续步骤使用，行卡片式与步骤断言/提取器一致 -->
    <section class="processor-form__section">
      <h4 class="processor-form__section-title">提取器（可选）</h4>
      <div v-for="(extractor, index) in state.extractors" :key="index" class="processor-form__extractor-card">
        <el-switch v-model="extractor.enabled" size="small" />
        <el-select v-model="extractor.source" size="small" class="processor-form__extractor--source" placeholder="提取来源">
          <el-option label="响应体" value="body" />
          <el-option label="响应头" value="header" />
          <el-option label="状态码" value="status" />
        </el-select>
        <el-input v-model="extractor.expression" size="small" class="processor-form__extractor--flex" placeholder="表达式（如 $.data.token）" />
        <el-input v-model="extractor.variableName" size="small" class="processor-form__extractor--flex" placeholder="目标变量名" />
        <el-input v-model="extractor.description" size="small" class="processor-form__extractor--flex" placeholder="描述" />
        <el-button link size="small" type="danger" @click="removeExtractor(index)">✕</el-button>
      </div>
      <div class="processor-form__extractor-actions">
        <el-button size="small" @click="addExtractor">+ 添加提取器</el-button>
        <el-button size="small" plain @click="emit('import-extractors')">从公共组件获取</el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  HTTP_METHODS,
  parseProcessorElement,
  toProcessorElement,
} from './processorFormModel'
import type { ProcessorElementForm } from './processorFormModel'

interface KvRow {
  key: string
  value: string
}

/** ref 下拉选项：环境 http 配置 / 数据源的最小结构（结构性满足 ApiHttpConfig / ApiDataSource） */
interface RefOption {
  name: string
  refName?: string
  isDefault?: boolean
}

const props = withDefaults(defineProps<{
  modelValue: Record<string, unknown>
  httpOptions?: RefOption[]
  dsOptions?: RefOption[]
  showTypeSelect?: boolean
}>(), {
  httpOptions: () => [],
  dsOptions: () => [],
  showTypeSelect: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'import-extractors'): void
}>()

type BodyKind = ProcessorElementForm['http']['bodyKind']
const BODY_KINDS: { value: BodyKind; label: string }[] = [
  { value: 'none', label: '无' },
  { value: 'json', label: 'JSON' },
  { value: 'form', label: '表单' },
  { value: 'raw', label: '原始文本' },
]

// HTTP 请求行 + Tabs 布局（对齐步骤 RequestConfigEditor）；Tabs 内容按需惰性渲染
const httpTab = ref<'headers' | 'query' | 'body'>('headers')

function nonEmptyKeyCount(rows: { key: string }[]): number {
  return rows.filter((row) => row.key.trim()).length
}

const headersBadge = computed<number | string>(() => nonEmptyKeyCount(state.http.headerRows))
const queryBadge = computed<number | string>(() => nonEmptyKeyCount(state.http.queryRows))
const bodyBadge = computed<number | string>(() => {
  if (state.http.bodyKind === 'none') return ''
  if (state.http.bodyKind === 'form') return nonEmptyKeyCount(state.http.formRows) || ''
  return state.http.bodyText.trim() ? 1 : ''
})

const state = reactive<ProcessorElementForm>(parseProcessorElement(props.modelValue))

watch(() => props.modelValue, (val) => {
  const incoming = val ?? {}
  // 父级回写本组件刚 emit 的内容时跳过，避免光标跳回与循环触发
  if (JSON.stringify(incoming) === JSON.stringify(toProcessorElement(props.modelValue, state))) return
  Object.assign(state, parseProcessorElement(incoming))
}, { deep: true })

watch(state, () => {
  const next = toProcessorElement(props.modelValue, state)
  const current = props.modelValue
  if (current && JSON.stringify(next) === JSON.stringify(current)) return
  emit('update:modelValue', next)
}, { deep: true })

// 下拉数据异步到达后，仅当用户尚未选择时自动预选默认项，避免覆盖已有 ref
watch(() => props.httpOptions, (opts) => {
  if (state.http.ref) return
  const def = opts?.find((o) => o.isDefault)
  if (def?.refName) state.http.ref = def.refName
})
watch(() => props.dsOptions, (opts) => {
  if (state.jdbc.ref) return
  const def = opts?.find((o) => o.isDefault)
  if (def?.refName) state.jdbc.ref = def.refName
})

/** 下拉文案：名称 + 引用名，便于区分同名配置 */
function optionLabel(name: string, refName?: string): string {
  return refName ? `${name}（${refName}）` : name
}

function removeRow(rows: KvRow[] | string[], index: number) {
  rows.splice(index, 1)
}

const addExtractor = () => {
  state.extractors.push({ enabled: true, source: '', expression: '', variableName: '', description: '' })
}

const removeExtractor = (index: number) => {
  state.extractors.splice(index, 1)
}
</script>

<style scoped lang="scss">
// 分区布局对齐步骤内联编辑器的 step-inline__section 结构
.processor-form__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.processor-form__section-title {
  margin: 0;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-600);
}

.processor-form__hint {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

// 请求行：方法与 path 同行（对齐步骤 RequestConfigEditor）
.processor-form__request-line {
  display: flex;
  gap: var(--space-sm);
}

.processor-form__method {
  width: 120px;
}

.processor-form__path {
  flex: 1;
}

.processor-form__tabs {
  :deep(.el-tabs__header) {
    margin: 0;
  }
}

.processor-form__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.processor-form__badge {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-primary);
  color: #fff;
  font-size: 10px;
  text-align: center;
}

// 键值表格：对齐步骤 KeyValueTable，处理器为 Ryze map（无行启用态）故省略 enabled 列
.processor-form__kv-table {
  width: 100%;
  border-collapse: collapse;

  th {
    text-align: left;
    font-weight: 500;
    font-size: 11px;
    color: var(--color-neutral-400);
    text-transform: uppercase;
    padding: 0 6px 8px 0;
    border-bottom: 1px solid var(--color-neutral-100);
  }
}

.processor-form__kv-op {
  width: 30px;
  text-align: center;
  vertical-align: middle;
}

.processor-form__kv-tr {
  transition: background 0.1s;

  td {
    padding: 4px 6px 4px 0;
  }

  &:hover {
    background: var(--color-neutral-50);
  }
}

// 请求体：分段类型按钮 + 表单表格 / 深色文本编辑器（镜像 RequestConfigEditor）
.processor-form__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.processor-form__body-types {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--color-neutral-50);
  border-radius: 6px;
  padding: 2px;
  width: fit-content;
  flex-wrap: wrap;
}

.processor-form__body-type {
  height: 28px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-neutral-500);
  transition: all 0.15s;

  &:hover {
    color: var(--color-neutral-700);
  }

  &.is-active {
    background: var(--color-primary);
    color: #fff;
    font-weight: 500;
  }
}

.processor-form__body-hint {
  padding: var(--space-lg);
  text-align: center;
  color: var(--color-neutral-400);
  margin: 0;
}

.processor-form__body-editor {
  width: 100%;
  min-height: 160px;
  max-height: 400px;
  resize: vertical;
  padding: 12px;
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #d4d4d4;
  background: #1e1e1e;
  border: 1px solid #333;
  border-radius: 6px;
  outline: none;
  tab-size: 2;

  &::placeholder {
    color: #555;
  }

  &:focus {
    border-color: var(--color-primary);
  }
}

// 键值/参数行：对齐步骤编辑器 arg-row（小号输入 + ✕ 删除）
.processor-form__kv-row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-xs);

  :deep(.el-input) {
    flex: 1;
  }
}

// 提取器行卡片：对齐步骤断言行（step-inline__card）
.processor-form__extractor-card {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: nowrap;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-xs);
}

.processor-form__extractor--source {
  flex: 0 0 240px;
}

.processor-form__extractor--flex {
  flex: 1 1 0;
  min-width: 0;
}

.processor-form__extractor-actions {
  display: flex;
  gap: var(--space-md);
  align-items: center;
}
</style>