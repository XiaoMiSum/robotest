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

    <!-- 配置信息：HTTP 请求分区（字段与 Ryze http 处理器配置项一一对应） -->
    <template v-if="state.testclass === 'http'">
      <section class="processor-form__section">
        <h4 class="processor-form__section-title">HTTP 请求配置</h4>
        <el-form label-position="top">
          <div class="processor-form__row processor-form__row--2">
            <el-form-item label="请求方法">
              <el-select v-model="state.http.method" placeholder="请求方法">
                <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="HTTP/2">
              <el-switch v-model="state.http.http2" />
            </el-form-item>
          </div>
          <div class="processor-form__row processor-form__row--2">
            <el-form-item label="路径 (path)">
              <el-input v-model="state.http.path" placeholder="/token" />
            </el-form-item>
            <el-form-item label="ref 引用">
              <el-select v-model="state.http.ref" placeholder="选择环境 HTTP 配置">
                <el-option v-for="opt in httpOptions" :key="opt.refName ?? opt.name" :label="optionLabel(opt.name, opt.refName)" :value="opt.refName ?? ''" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="请求头 (headers)">
            <div v-for="(item, index) in state.http.headerRows" :key="index" class="processor-form__kv-row">
              <el-input v-model="item.key" size="small" placeholder="Key" />
              <el-input v-model="item.value" size="small" placeholder="Value" />
              <el-button link size="small" type="danger" @click="removeRow(state.http.headerRows, index)">✕</el-button>
            </div>
            <el-button size="small" @click="state.http.headerRows.push({ key: '', value: '' })">+ 添加请求头</el-button>
          </el-form-item>
          <el-form-item label="Query 参数 (query)">
            <div v-for="(item, index) in state.http.queryRows" :key="index" class="processor-form__kv-row">
              <el-input v-model="item.key" size="small" placeholder="Key" />
              <el-input v-model="item.value" size="small" placeholder="Value" />
              <el-button link size="small" type="danger" @click="removeRow(state.http.queryRows, index)">✕</el-button>
            </div>
            <el-button size="small" @click="state.http.queryRows.push({ key: '', value: '' })">+ 添加 Query 参数</el-button>
          </el-form-item>
          <el-form-item label="请求体类型">
            <el-select v-model="state.http.bodyKind" style="width: 100%">
              <el-option label="无" value="none" />
              <el-option label="JSON (body)" value="json" />
              <el-option label="表单 (data)" value="form" />
              <el-option label="原始文本 (body)" value="raw" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="state.http.bodyKind === 'form'" label="表单参数 (data)">
            <div v-for="(item, index) in state.http.formRows" :key="index" class="processor-form__kv-row">
              <el-input v-model="item.key" size="small" placeholder="Key" />
              <el-input v-model="item.value" size="small" placeholder="Value" />
              <el-button link size="small" type="danger" @click="removeRow(state.http.formRows, index)">✕</el-button>
            </div>
            <el-button size="small" @click="state.http.formRows.push({ key: '', value: '' })">+ 添加表单参数</el-button>
          </el-form-item>
          <el-form-item v-else-if="state.http.bodyKind !== 'none'" label="请求体 (body)">
            <el-input v-model="state.http.bodyText" type="textarea" :rows="4" placeholder='{"key": "value"}' />
          </el-form-item>
        </el-form>
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
import { reactive, watch } from 'vue'
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

.processor-form__row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  column-gap: 8px;

  &--2 {
    grid-template-columns: repeat(2, 1fr);
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