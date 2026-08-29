<template>
  <div class="processor-form">
    <el-form-item label="处理器类型" prop="handlerType">
      <el-select v-model="localConfig.handlerType" placeholder="选择处理器类型">
        <el-option label="发送 HTTP 请求" value="http" />
        <el-option label="执行 SQL" value="sql" />
      </el-select>
    </el-form-item>

    <!-- 配置信息：HTTP 请求表单 -->
    <template v-if="localConfig.handlerType === 'http'">
      <el-form-item label="URL" prop="url">
        <el-input v-model="localConfig.url" placeholder="https://api.example.com/endpoint" />
      </el-form-item>
      <div class="processor-form__row processor-form__row--2">
        <el-form-item label="请求方法" prop="method">
          <el-select v-model="localConfig.method" placeholder="选择请求方法">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="Content-Type" prop="contentType">
          <el-select v-model="localConfig.contentType" placeholder="选择 Content-Type">
            <el-option label="application/json" value="application/json" />
            <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
            <el-option label="multipart/form-data" value="multipart/form-data" />
            <el-option label="text/plain" value="text/plain" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="请求头">
        <div v-for="(item, index) in localConfig.headers" :key="index" class="kv-row">
          <el-input v-model="item.key" placeholder="Key" class="kv-input" />
          <el-input v-model="item.value" placeholder="Value" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeHeader(index)" />
        </div>
        <el-button type="primary" link @click="addHeader">+ 添加请求头</el-button>
      </el-form-item>
      <el-form-item v-if="localConfig.method !== 'GET'" label="请求体">
        <el-input v-model="localConfig.body" type="textarea" :rows="4" placeholder='{"key": "value"}' />
      </el-form-item>
    </template>

    <!-- 配置信息：SQL 表单 -->
    <template v-if="localConfig.handlerType === 'sql'">
      <el-form-item label="数据源" prop="dataSource">
        <el-input v-model="localConfig.dataSource" placeholder="数据源名称" />
      </el-form-item>
      <el-form-item label="SQL 语句" prop="sql">
        <el-input v-model="localConfig.sql" type="textarea" :rows="4" placeholder="SELECT * FROM table WHERE id = ?" />
      </el-form-item>
      <el-form-item label="参数">
        <div v-for="(_, index) in localConfig.args" :key="index" class="kv-row">
          <el-input v-model="localConfig.args[index]" placeholder="参数值" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeArg(index)" />
        </div>
        <el-button type="primary" link @click="addArg">+ 添加参数</el-button>
      </el-form-item>
    </template>

    <!-- 提取器（可选）：从处理器响应中提取变量供后续步骤使用 -->
    <el-divider content-position="left">提取器（可选）</el-divider>
    <div v-if="localConfig.extractors.length > 0" class="processor-form__extractors">
      <div class="processor-form__extractors-head">
        <span class="processor-form__extractors-col processor-form__extractors-col--enabled">启用</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--source">提取来源</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--expression">表达式</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--name">目标变量名</span>
        <span class="processor-form__extractors-col processor-form__extractors-col--desc">提取描述</span>
      </div>
      <div v-for="(extractor, index) in localConfig.extractors" :key="index" class="processor-form__extractors-row">
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
import type { ProcessorExtractor } from './processorFormModel'

interface KvItem {
  key: string
  value: string
}

interface ProcessorConfig {
  handlerType: 'http' | 'sql' | ''
  method: string
  url: string
  contentType: string
  headers: KvItem[]
  body: string
  dataSource: string
  sql: string
  args: string[]
  extractors: ProcessorExtractor[]
}

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'import-extractors'): void
}>()

const defaultConfig: ProcessorConfig = {
  handlerType: '',
  method: 'POST',
  url: '',
  contentType: 'application/json',
  headers: [],
  body: '',
  dataSource: '',
  sql: '',
  args: [],
  extractors: [],
}

const localConfig = reactive<ProcessorConfig>({
  ...defaultConfig,
  ...props.modelValue as Partial<ProcessorConfig>,
})

watch(() => props.modelValue, (val) => {
  Object.assign(localConfig, { ...defaultConfig, ...val as Partial<ProcessorConfig> })
}, { deep: true })

watch(localConfig, (val) => {
  const next = JSON.parse(JSON.stringify(val)) as Record<string, unknown>
  // 内容未变化（父级回写造成）时不再 emit，避免 localConfig⇄父级回声形成递归
  const current = props.modelValue
  if (current && JSON.stringify(next) === JSON.stringify(current)) return
  emit('update:modelValue', next)
}, { deep: true })

const addHeader = () => {
  localConfig.headers.push({ key: '', value: '' })
}

const removeHeader = (index: number) => {
  localConfig.headers.splice(index, 1)
}

const addArg = () => {
  localConfig.args.push('')
}

const removeArg = (index: number) => {
  localConfig.args.splice(index, 1)
}

const addExtractor = () => {
  localConfig.extractors.push({ enabled: true, source: '', expression: '', variableName: '', description: '' })
}

const removeExtractor = (index: number) => {
  localConfig.extractors.splice(index, 1)
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
