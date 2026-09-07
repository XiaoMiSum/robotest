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

    <!-- 配置信息：HTTP 请求分区（复用步骤 RequestConfigEditor：请求行 + KeyValueTable + 分段请求体；path 相对语义故隐藏导入 cURL） -->
    <template v-if="state.testclass === 'http'">
      <section class="processor-form__section">
        <el-select v-if="showRefSelect" v-model="state.http.ref" placeholder="选择环境 HTTP 配置" class="processor-form__ref-select" filterable>
          <el-option v-for="opt in httpOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
        </el-select>
        <RequestConfigEditor
          :method="state.http.method"
          :url="state.http.path"
          :headers="editorHeaders"
          :params="editorQuery"
          :body="editorBody"
          @update:method="onMethod"
          @update:url="onUrl"
          @update:headers="onHeaders"
          @update:params="onQuery"
          @update:body="onBody"
        />
      </section>
    </template>

    <!-- 配置信息：SQL 分区（config 键与 Ryze jdbc 处理器一致；数据源 label 与选择器同行） -->
    <template v-if="state.testclass === 'jdbc'">
      <section class="processor-form__section">
        <el-select v-if="showRefSelect" v-model="state.jdbc.ref" placeholder="选择环境数据源" class="processor-form__ref-select" filterable>
          <el-option v-for="opt in dsOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
        </el-select>
        <el-form label-position="top">
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

    <!-- 提取器（可选）：行卡片 + 标题行右侧操作（与步骤同款卡片，操作钮挂标题行避免列表底部留白） -->
    <section class="processor-form__section">
      <div class="processor-form__section-head">
        <h4 class="processor-form__section-title">提取器（可选）</h4>
        <div class="processor-form__extractor-actions">
          <el-button size="small" link type="primary" @click="addExtractor">+ 添加提取器</el-button>
          <el-button size="small" link type="primary" @click="emit('import-extractors')">从公共组件获取</el-button>
        </div>
      </div>
      <div class="processor-form__list">
        <div v-for="(extractor, index) in state.extractors" :key="index" class="processor-form__extractor-card">
          <div class="processor-form__extractor-row">
            <el-switch v-model="extractor.enabled" size="small" />
            <el-select v-model="extractor.source" size="small" class="processor-form__field--source" placeholder="提取来源">
              <el-option v-for="s in EXTRACTOR_SOURCES" :key="s.value" :value="s.value" :label="s.label" />
            </el-select>
            <el-input v-model="extractor.expression" size="small" placeholder="表达式" class="processor-form__field--flex" />
            <el-input v-model="extractor.variableName" size="small" placeholder="变量名" class="processor-form__field--flex" />
            <el-button link size="small" type="danger" @click="removeExtractor(index)">删除</el-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { parseProcessorElement, toProcessorElement } from './processorFormModel'
import type { ProcessorElementForm } from './processorFormModel'
import { EXTRACTOR_SOURCES } from '@/pages/project/scenesModel'
import RequestConfigEditor from '@/pages/project/scenes/RequestConfigEditor.vue'

interface ResultEditorRow {
  key: string
  value: string
  enabled: boolean
}

interface ResultEditorBody {
  type: string
  content: unknown
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
  showRefSelect?: boolean
}>(), {
  httpOptions: () => [],
  dsOptions: () => [],
  showTypeSelect: true,
  showRefSelect: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'import-extractors'): void
}>()

const state = reactive<ProcessorElementForm>(parseProcessorElement(props.modelValue))

// —— RequestConfigEditor 双向绑定映射（headers/query 的启用态与 body 的分段类型收敛于此）——
// 处理器为 Ryze map（无启用态），路径语义为相对 path，故编辑器行统一开启、body 用 bodyKind 直通
const editorHeaders = computed<ResultEditorRow[]>(() => state.http.headerRows.map((row) => ({ key: row.key, value: row.value, enabled: row.enabled ?? true })))
const editorQuery = computed<ResultEditorRow[]>(() => state.http.queryRows.map((row) => ({ key: row.key, value: row.value, enabled: row.enabled ?? true })))
const editorBody = computed<ResultEditorBody>(() => {
  // 复用步骤请求体编辑态的三态映射：form → urlencoded 行三元组，json/raw → 文本直通（content 结构见 buildBodyFromEditState）
  if (state.http.bodyKind === 'form') {
    return { type: 'form', content: state.http.formRows.map((row) => ({ key: row.key, value: row.value, enabled: row.enabled ?? true })) }
  }
  return { type: state.http.bodyKind, content: state.http.bodyText }
})

const onHeaders = (rows: ResultEditorRow[]) => {
  state.http.headerRows = rows.map((row) => ({ key: row.key, value: row.value, enabled: row.enabled ?? true }))
}
const onQuery = (rows: ResultEditorRow[]) => {
  state.http.queryRows = rows.map((row) => ({ key: row.key, value: row.value, enabled: row.enabled ?? true }))
}
const onBody = (body: ResultEditorBody) => {
  const kind = body.type as ProcessorElementForm['http']['bodyKind']
  state.http.bodyKind = kind
  if (kind === 'form') {
    // 步骤编辑器回送的是 urlencoded 三元组，需拆回 formRows（bodyText 在 form 下不参与落库）
    const rows = Array.isArray(body.content) ? body.content : []
    state.http.formRows = rows.map((entry) => {
      const item = entry as Record<string, unknown>
      return { key: String(item.key ?? ''), value: String(item.value ?? ''), enabled: item.enabled !== false }
    })
    state.http.bodyText = ''
    return
  }
  // json 回送的是 JSON.parse 后的对象，raw 回送原始文本；统一还原为可序列化文本
  const content = body.content
  let text: string
  if (kind === 'json' && typeof content === 'object' && content !== null) {
    text = JSON.stringify(content)
  } else if (kind === 'none') {
    text = ''
  } else {
    text = typeof content === 'string' ? content : String(content ?? '')
  }
  state.http.bodyText = text
}

function onMethod(method: string) {
  state.http.method = method
}
function onUrl(url: string) {
  state.http.path = url
}

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

function removeRow(rows: string[], index: number) {
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

// 提取器标题行：标题居左、操作钮居右（水平对齐）
.processor-form__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.processor-form__hint {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

// 引用配置/数据源选择器：整行宽度（无标签，占位符承载语义）
.processor-form__ref-select {
  width: 100%;
}

// 提取器行卡片：对齐步骤断言行（step-inline__card / __card-bottom 同构）
.processor-form__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.processor-form__extractor-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
}

.processor-form__extractor-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: nowrap;
}

.processor-form__field--source {
  flex: 0 0 240px;
}

.processor-form__field--flex {
  flex: 1 1 0;
  min-width: 0;
}

.processor-form__extractor-actions {
  display: flex;
  gap: var(--space-md);
  align-items: center;
}

// JDBC 参数行：小号输入 + ✕ 删除（对齐步骤 arg-row）
.processor-form__kv-row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-xs);

  :deep(.el-input) {
    flex: 1;
  }
}
</style>