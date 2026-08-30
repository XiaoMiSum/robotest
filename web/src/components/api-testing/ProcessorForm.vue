<template>
  <div class="processor-form">
    <el-form-item label="处理器类型" prop="testclass">
      <el-select v-model="state.testclass" placeholder="选择处理器类型">
        <el-option label="发送 HTTP 请求" value="http" />
        <el-option label="执行 SQL" value="jdbc" />
      </el-select>
    </el-form-item>

    <!-- 配置信息：HTTP 请求表单（字段与 Ryze http 处理器配置项一一对应） -->
    <template v-if="state.testclass === 'http'">
      <div class="processor-form__row processor-form__row--2">
        <el-form-item label="请求方法" prop="http.method">
          <el-select v-model="state.http.method" placeholder="请求方法">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="基地址 (base_url)" prop="http.baseUrl">
          <el-input v-model="state.http.baseUrl" placeholder="https://api.example.com" />
        </el-form-item>
      </div>
      <div class="processor-form__row processor-form__row--2">
        <el-form-item label="路径 (path)" prop="http.path">
          <el-input v-model="state.http.path" placeholder="/token" />
        </el-form-item>
        <el-form-item label="HTTP/2">
          <el-switch v-model="state.http.http2" />
        </el-form-item>
      </div>
      <el-form-item label="请求头 (headers)">
        <div v-for="(item, index) in state.http.headerRows" :key="index" class="kv-row">
          <el-input v-model="item.key" placeholder="Key" class="kv-input" />
          <el-input v-model="item.value" placeholder="Value" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeRow(state.http.headerRows, index)" />
        </div>
        <el-button type="primary" link @click="state.http.headerRows.push({ key: '', value: '' })">+ 添加请求头</el-button>
      </el-form-item>
      <el-form-item label="Query 参数 (query)">
        <div v-for="(item, index) in state.http.queryRows" :key="index" class="kv-row">
          <el-input v-model="item.key" placeholder="Key" class="kv-input" />
          <el-input v-model="item.value" placeholder="Value" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeRow(state.http.queryRows, index)" />
        </div>
        <el-button type="primary" link @click="state.http.queryRows.push({ key: '', value: '' })">+ 添加 Query 参数</el-button>
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
        <div v-for="(item, index) in state.http.formRows" :key="index" class="kv-row">
          <el-input v-model="item.key" placeholder="Key" class="kv-input" />
          <el-input v-model="item.value" placeholder="Value" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeRow(state.http.formRows, index)" />
        </div>
        <el-button type="primary" link @click="state.http.formRows.push({ key: '', value: '' })">+ 添加表单参数</el-button>
      </el-form-item>
      <el-form-item v-else-if="state.http.bodyKind !== 'none'" label="请求体 (body)">
        <el-input v-model="state.http.bodyText" type="textarea" :rows="4" placeholder='{"key": "value"}' />
      </el-form-item>
    </template>

    <!-- 配置信息：SQL 表单（config 键与 Ryze jdbc 处理器一致） -->
    <template v-if="state.testclass === 'jdbc'">
      <el-form-item label="数据源 (datasource)" prop="jdbc.datasource">
        <el-input v-model="state.jdbc.datasource" placeholder="数据源引用名 (ref_name)" />
      </el-form-item>
      <el-form-item label="SQL 语句 (sql)" prop="jdbc.sql">
        <el-input v-model="state.jdbc.sql" type="textarea" :rows="4" placeholder="SELECT * FROM table WHERE id = ?" />
      </el-form-item>
      <el-form-item label="参数 (args)">
        <div v-for="(_, index) in state.jdbc.args" :key="index" class="kv-row">
          <el-input v-model="state.jdbc.args[index]" placeholder="参数值" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeRow(state.jdbc.args, index)" />
        </div>
        <el-button type="primary" link @click="state.jdbc.args.push('')">+ 添加参数</el-button>
      </el-form-item>
    </template>

    <!-- 提取器（可选）：从处理器响应中提取变量供后续步骤使用 -->
    <el-divider content-position="left">提取器（可选）</el-divider>
    <div v-if="state.extractors.length > 0" class="processor-form__extractors">
      <div class="processor-form__extractors-head">
        <span class="processor-form__extractors-col processor-form__extractors-col--enabled">启用</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--source">提取来源</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--expression">表达式</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--name">目标变量名</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--desc">提取描述</span>
      </div>
      <div v-for="(extractor, index) in state.extractors" :key="index" class="processor-form__extractors-row">
        <span class="processor-form__extractors-col processor-form__extractors-col--enabled">
          <el-switch v-model="extractor.enabled" />
        </span>
        <span class="processor-form__extractors-col processor-form__extractors-col--source">
          <el-select v-model="extractor.source" placeholder="来源">
            <el-option label="响应体" value="body" />
            <el-option label="响应头" value="header" />
            <el-option label="状态码" value="status" />
          </el-select>
        </span>
        <span class="processor-form__extractors-col processor-form__extractors-col--expression">
          <el-input v-model="extractor.expression" placeholder="如: $.data.token 或 X-Token" />
        </span>
        <span class="processor-form__extractors-col processor-form__extractors-col--name">
          <el-input v-model="extractor.variableName" placeholder="变量名" />
        </span>
        <span class="processor-form__extractors-col processor-form__extractors-col--desc">
          <el-input v-model="extractor.description" placeholder="描述" />
        </span>
        <span class="processor-form__extractors-col processor-form__extractors-col--action">
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeExtractor(index)" />
        </span>
      </div>
    </div>
    <div class="processor-form__extractor-actions">
      <el-button type="primary" link @click="addExtractor">+ 添加提取器</el-button>
      <el-button type="primary" link @click="emit('import-extractors')">从公共组件获取</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'
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

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

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

<style scoped>
.processor-form__row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  column-gap: 8px;

  &--2 {
    grid-template-columns: repeat(2, 1fr);
  }
}

.processor-form__extractors {
  margin-bottom: var(--space-md);
}

.processor-form__extractors-head,
.processor-form__extractors-row {
  display: grid;
  grid-template-columns: 48px 104px 1fr 1fr 1fr 36px;
  column-gap: 8px;
  align-items: center;
}

.processor-form__extractors-head {
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  margin-bottom: 4px;
}

.processor-form__extractors-row {
  margin-bottom: 8px;
}

.processor-form__extractor-actions {
  display: flex;
  gap: var(--space-md);
  align-items: center;
}

.kv-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.kv-input {
  flex: 1;
}
</style>