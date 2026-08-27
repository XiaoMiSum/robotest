<template>
  <div class="processor-form">
    <el-form-item label="启用" prop="enabled">
      <el-switch v-model="localConfig.enabled" />
    </el-form-item>
    <el-form-item label="处理器类型" prop="handlerType">
      <el-select v-model="localConfig.handlerType" placeholder="选择处理器类型">
        <el-option label="发送 HTTP 请求" value="http" />
        <el-option label="执行 SQL" value="sql" />
      </el-select>
    </el-form-item>

    <!-- HTTP 请求表单 -->
    <template v-if="localConfig.handlerType === 'http'">
      <el-form-item label="请求方法" prop="method">
        <el-select v-model="localConfig.method" placeholder="选择请求方法">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
          <el-option label="PATCH" value="PATCH" />
        </el-select>
      </el-form-item>
      <el-form-item label="URL" prop="url">
        <el-input v-model="localConfig.url" placeholder="https://api.example.com/endpoint" />
      </el-form-item>
      <el-form-item label="Content-Type" prop="contentType">
        <el-select v-model="localConfig.contentType" placeholder="选择 Content-Type">
          <el-option label="application/json" value="application/json" />
          <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
          <el-option label="multipart/form-data" value="multipart/form-data" />
          <el-option label="text/plain" value="text/plain" />
        </el-select>
      </el-form-item>
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

    <!-- SQL 表单 -->
    <template v-if="localConfig.handlerType === 'sql'">
      <el-form-item label="数据源" prop="dataSource">
        <el-input v-model="localConfig.dataSource" placeholder="数据源名称" />
      </el-form-item>
      <el-form-item label="SQL 语句" prop="sql">
        <el-input v-model="localConfig.sql" type="textarea" :rows="4" placeholder="SELECT * FROM table WHERE id = ?" />
      </el-form-item>
      <el-form-item label="参数">
        <div v-for="(item, index) in localConfig.params" :key="index" class="kv-row">
          <el-input v-model="item.key" placeholder="参数名" class="kv-input" />
          <el-input v-model="item.value" placeholder="值" class="kv-input" />
          <el-button type="danger" :icon="Delete" circle size="small" @click="removeParam(index)" />
        </div>
        <el-button type="primary" link @click="addParam">+ 添加参数</el-button>
      </el-form-item>
    </template>

    <el-form-item label="异步执行" prop="async">
      <el-switch v-model="localConfig.async" />
    </el-form-item>
    <el-form-item label="执行条件" prop="condition">
      <el-input v-model="localConfig.condition" placeholder="留空表示无条件执行" />
    </el-form-item>
    <el-form-item label="排序号" prop="sortOrder">
      <el-input-number v-model="localConfig.sortOrder" :min="0" :max="9999" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'

interface KvItem {
  key: string
  value: string
}

interface ProcessorConfig {
  enabled: boolean
  handlerType: 'http' | 'sql' | ''
  method: string
  url: string
  contentType: string
  headers: KvItem[]
  body: string
  dataSource: string
  sql: string
  params: KvItem[]
  async: boolean
  condition: string
  sortOrder: number
}

const props = defineProps<{
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
}>()

const defaultConfig: ProcessorConfig = {
  enabled: true,
  handlerType: '',
  method: 'POST',
  url: '',
  contentType: 'application/json',
  headers: [],
  body: '',
  dataSource: '',
  sql: '',
  params: [],
  async: false,
  condition: '',
  sortOrder: 0,
}

const localConfig = reactive<ProcessorConfig>({
  ...defaultConfig,
  ...props.modelValue as Partial<ProcessorConfig>,
})

watch(() => props.modelValue, (val) => {
  Object.assign(localConfig, { ...defaultConfig, ...val as Partial<ProcessorConfig> })
}, { deep: true })

watch(localConfig, (val) => {
  emit('update:modelValue', JSON.parse(JSON.stringify(val)))
}, { deep: true })

const addHeader = () => {
  localConfig.headers.push({ key: '', value: '' })
}

const removeHeader = (index: number) => {
  localConfig.headers.splice(index, 1)
}

const addParam = () => {
  localConfig.params.push({ key: '', value: '' })
}

const removeParam = (index: number) => {
  localConfig.params.splice(index, 1)
}
</script>

<style scoped>
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
