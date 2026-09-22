<template>
  <div class="processor-form">
    <!-- 类型选择：与测试场景处理器头部一致（radio，无 label）；引用选择与之水平对齐 -->
    <div v-if="showTypeSelect || showRefSelect" class="processor-form__type-row">
      <el-radio-group v-if="showTypeSelect" v-model="state.testclass" size="small">
        <el-radio-button value="http">HTTP</el-radio-button>
        <el-radio-button value="jdbc">JDBC</el-radio-button>
      </el-radio-group>
      <el-select v-if="showRefSelect && state.testclass === 'http'" v-model="state.http.ref" placeholder="选择环境 HTTP 配置" class="processor-form__ref-select" filterable>
        <el-option v-for="opt in httpOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
      </el-select>
      <el-select v-else-if="showRefSelect && state.testclass === 'jdbc'" v-model="state.jdbc.ref" placeholder="选择环境数据源" class="processor-form__ref-select" filterable>
        <el-option v-for="opt in dsOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
      </el-select>
    </div>
    <p v-if="!showTypeSelect && !state.testclass" class="processor-form__hint">请在上方选择处理器类型</p>

    <!-- 配置 / 提取器：http 直接并入请求配置 tabs，jdbc 用同构 tabs（SQL / 提取器） -->
    <template v-if="state.testclass === 'http'">
      <section class="processor-form__section">
        <RequestConfigEditor
          :method="state.http.method"
          :url="state.http.path"
          :headers="editorHeaders"
          :params="editorQuery"
          :body="editorBody"
          :extractors="state.extractors"
          @update:method="onMethod"
          @update:url="onUrl"
          @update:headers="onHeaders"
          @update:params="onQuery"
          @update:body="onBody"
          @update:extractors="(v) => (state.extractors = v as ProcessorExtractor[])"
          @add-extractor="addExtractor"
          @import-extractors="() => emit('import-extractors')"
        />
      </section>
    </template>

    <template v-if="state.testclass === 'jdbc'">
      <el-tabs class="processor-form__tabs">
        <el-tab-pane label="SQL" name="sql">
          <section class="processor-form__section">
            <el-form label-position="top">
              <el-form-item label="SQL 语句 (sql)">
                <el-input v-model="state.jdbc.sql" type="textarea" :rows="4" placeholder="SELECT * FROM table WHERE id = ?" />
              </el-form-item>
              <el-form-item class="processor-form__args-item">
                <template #label>
                  参数 (args)
                  <el-button link type="primary" size="small" class="processor-form__args-add" @click="state.jdbc.args.push('')">+ 添加参数</el-button>
                </template>
                <div v-for="(_, index) in state.jdbc.args" :key="index" class="processor-form__kv-row">
                  <el-input v-model="state.jdbc.args[index]" placeholder="参数值" />
                  <el-button link size="small" type="danger" @click="removeRow(state.jdbc.args, index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </el-form-item>
            </el-form>
          </section>
        </el-tab-pane>

        <ValidatorsExtractorsPanes
          :extractors="state.extractors"
          @update:extractors="(v) => (state.extractors = v as ProcessorExtractor[])"
          @add-extractor="addExtractor"
          @import-extractors="() => emit('import-extractors')"
        />
      </el-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { parseProcessorElement, toProcessorElement } from '@/composables/project/api-testing/processorFormModel'
import type { ProcessorElementForm, ProcessorExtractor } from '@/composables/project/api-testing/processorFormModel'
import RequestConfigEditor from '@/components/project/api-testing/RequestConfigEditor.vue'
import ValidatorsExtractorsPanes from '@/components/project/api-testing/ValidatorsExtractorsPanes.vue'

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
  // config 可选：未配置的处理器（环境/组件新建态）可能缺失元素对象
  modelValue?: Record<string, unknown>
  httpOptions?: RefOption[]
  dsOptions?: RefOption[]
  showTypeSelect?: boolean
  showRefSelect?: boolean
}>(), {
  modelValue: () => ({}),
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

// 提取器空态默认行：字段全空，编译时被过滤，仅提供即时输入起点
if (state.extractors.length === 0) {
  state.extractors.push({ enabled: true, source: '', expression: '', variableName: '', description: '' })
}

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
</script>

<style scoped lang="scss">
// 纵向分区间距：类型行 / 提示 / 配置区 之间的呼吸感（请求方法·path 行与上方组件留白即由此产生）
.processor-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

// 分区布局对齐步骤内联编辑器的 step-inline__section 结构
.processor-form__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

// tabs 统一 http/jdbc 处理器分区结构；收窄默认间距，内容区零内边距
.processor-form__tabs {
  :deep(.el-tabs__header) {
    margin-bottom: var(--space-sm);
  }

  :deep(.el-tabs__content) {
    padding: 0;
  }
}

.processor-form__hint {
  margin: 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

// 类型 radio + 引用选择器水平对齐行（与测试场景处理器头部同构）
.processor-form__type-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

// 引用配置/数据源选择器：定宽不收缩（标签过长溢出省略）
.processor-form__ref-select {
  width: 240px;
  flex-shrink: 0;
}

// 参数标签行：label 本身作为整行 flex，按钮推到最右侧
.processor-form__args-item {
  :deep(.el-form-item__label) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }
}

// JDBC 参数行：小号输入 + 删除图标（对齐步骤 arg-row）
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