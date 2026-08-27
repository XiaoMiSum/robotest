<script setup lang="ts">
import { ref, watch } from 'vue'

interface KvRow {
  key: string
  value: string
  enabled: boolean
}

const props = defineProps<{
  method?: string
  url?: string
  headers?: KvRow[]
  params?: KvRow[]
  body?: { type: string; content: unknown }
  timeout?: number
}>()

const emit = defineEmits<{
  (e: 'update:method', value: string): void
  (e: 'update:url', value: string): void
  (e: 'update:headers', value: KvRow[]): void
  (e: 'update:params', value: KvRow[]): void
  (e: 'update:body', value: { type: string; content: unknown }): void
  (e: 'update:timeout', value: number): void
}>()

const editMethod = ref(props.method ?? 'GET')
const editUrl = ref(props.url ?? '')
const editHeaders = ref<KvRow[]>(props.headers?.length ? [...props.headers] : [])
const editParams = ref<KvRow[]>(props.params?.length ? [...props.params] : [])
const bodyType = ref(props.body?.type ?? 'none')
const jsonText = ref(typeof props.body?.content === 'string' ? String(props.body.content) : '')
const rawText = ref(typeof props.body?.content === 'string' ? String(props.body.content) : '')
const formRows = ref<KvRow[]>(Array.isArray(props.body?.content) ? (props.body.content as KvRow[]) : [])
const editTimeout = ref(props.timeout ?? 30000)

const BODY_TYPES = [
  { value: 'none', label: '无' },
  { value: 'json', label: 'JSON' },
  { value: 'form', label: '表单' },
  { value: 'raw', label: '原始文本' },
]

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

watch(() => props.method, (v) => { editMethod.value = v ?? 'GET' })
watch(() => props.url, (v) => { editUrl.value = v ?? '' })
watch(() => props.headers, (v) => { editHeaders.value = v?.length ? [...v] : [] }, { deep: true })
watch(() => props.params, (v) => { editParams.value = v?.length ? [...v] : [] }, { deep: true })
watch(() => props.body, (v) => {
  bodyType.value = v?.type ?? 'none'
  if (typeof v?.content === 'string') { jsonText.value = String(v.content); rawText.value = String(v.content) }
  if (Array.isArray(v?.content)) formRows.value = v.content as KvRow[]
}, { deep: true })
watch(() => props.timeout, (v) => { editTimeout.value = v ?? 30000 })

function emitAll() {
  emit('update:method', editMethod.value)
  emit('update:url', editUrl.value)
  emit('update:headers', editHeaders.value.filter((h) => h.key.trim()))
  emit('update:params', editParams.value.filter((p) => p.key.trim()))
  const content = bodyType.value === 'json' ? jsonText.value
    : bodyType.value === 'form' ? formRows.value.filter((r) => r.key.trim())
    : bodyType.value === 'raw' ? rawText.value
    : null
  emit('update:body', { type: bodyType.value, content })
  emit('update:timeout', editTimeout.value)
}

function addHeader() { editHeaders.value.push({ key: '', value: '', enabled: true }) }
function removeHeader(i: number) { editHeaders.value.splice(i, 1) }
function addParam() { editParams.value.push({ key: '', value: '', enabled: true }) }
function removeParam(i: number) { editParams.value.splice(i, 1) }
function addFormRow() { formRows.value.push({ key: '', value: '', enabled: true }) }
function removeFormRow(i: number) { formRows.value.splice(i, 1) }

defineExpose({ emitAll })

// ==================== cURL 导入 ====================
const showCurlImport = ref(false)
const curlText = ref('')

function parseCurl(curl: string) {
  const trimmed = curl.trim().replace(/\\\n/g, ' ').replace(/\\/g, ' ')
  const methodMatch = trimmed.match(/-X\s+(\w+)/)
  if (methodMatch) editMethod.value = methodMatch[1].toUpperCase()

  const urlMatch = trimmed.match(/(?:curl\s+)?['"]?(https?:\/\/[^\s'"]+)['"]?/)
  if (urlMatch) editUrl.value = urlMatch[1]

  const headerRegex = /-H\s+['"]([^'"]+)['"]/g
  let hm: RegExpExecArray | null
  const headers: KvRow[] = []
  while ((hm = headerRegex.exec(trimmed)) !== null) {
    const [key, ...rest] = hm[1].split(':')
    if (key) headers.push({ key: key.trim(), value: rest.join(':').trim(), enabled: true })
  }
  if (headers.length) editHeaders.value = headers

  const dataMatch = trimmed.match(/-d\s+['"](.+?)['"]/s) || trimmed.match(/--data\s+['"](.+?)['"]/s)
  if (dataMatch) {
    bodyType.value = 'json'
    jsonText.value = dataMatch[1]
  }

  showCurlImport.value = false
  curlText.value = ''
  emitAll()
}
</script>

<template>
  <div class="req-config-editor">
    <el-tabs type="border-card" class="req-config-editor__tabs">
      <!-- ==================== 请求行 ==================== -->
      <div class="req-config-editor__request-line">
        <el-select :model-value="editMethod" style="width: 120px" @update:model-value="(v: string) => { editMethod = v; emitAll() }">
          <el-option v-for="m in METHODS" :key="m" :value="m" :label="m" />
        </el-select>
        <el-input
          :model-value="editUrl"
          placeholder="/api/endpoint (支持 ${变量} 引用)"
          class="req-config-editor__url"
          @update:model-value="(v: string) => { editUrl = v; emitAll() }"
        />
        <el-button size="small" @click="showCurlImport = true">导入 cURL</el-button>
      </div>

      <!-- ==================== Headers ==================== -->
      <el-tab-pane label="请求头" name="headers">
        <table class="req-config-editor__kv-table">
          <thead>
            <tr><th style="width:40px"></th><th>键</th><th>值</th><th style="width:40px"></th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in editHeaders" :key="i">
              <td><el-checkbox v-model="row.enabled" @change="emitAll" /></td>
              <td><el-input v-model="row.key" size="small" placeholder="Header" @change="emitAll" /></td>
              <td><el-input v-model="row.value" size="small" placeholder="值" @change="emitAll" /></td>
              <td><el-button link size="small" type="danger" @click="removeHeader(i); emitAll()">✕</el-button></td>
            </tr>
          </tbody>
        </table>
        <el-button size="small" link @click="addHeader">+ 添加请求头</el-button>
      </el-tab-pane>

      <!-- ==================== Query Params ==================== -->
      <el-tab-pane label="Query 参数" name="params">
        <table class="req-config-editor__kv-table">
          <thead>
            <tr><th style="width:40px"></th><th>参数名</th><th>值</th><th style="width:40px"></th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in editParams" :key="i">
              <td><el-checkbox v-model="row.enabled" @change="emitAll" /></td>
              <td><el-input v-model="row.key" size="small" placeholder="参数名" @change="emitAll" /></td>
              <td><el-input v-model="row.value" size="small" placeholder="值" @change="emitAll" /></td>
              <td><el-button link size="small" type="danger" @click="removeParam(i); emitAll()">✕</el-button></td>
            </tr>
          </tbody>
        </table>
        <el-button size="small" link @click="addParam">+ 添加参数</el-button>
      </el-tab-pane>

      <!-- ==================== Body ==================== -->
      <el-tab-pane label="请求体" name="body">
        <el-radio-group :model-value="bodyType" class="req-config-editor__body-type" @update:model-value="(v) => { bodyType = String(v ?? 'none'); emitAll() }">
          <el-radio-button v-for="bt in BODY_TYPES" :key="bt.value" :value="bt.value">{{ bt.label }}</el-radio-button>
        </el-radio-group>

        <div v-if="bodyType === 'json'" style="margin-top: 8px">
          <el-input v-model="jsonText" type="textarea" :rows="10" placeholder='{"code": 200}' @change="emitAll" />
        </div>
        <div v-else-if="bodyType === 'form'" style="margin-top: 8px">
          <table class="req-config-editor__kv-table">
            <thead><tr><th style="width:40px"></th><th>键</th><th>值</th><th style="width:40px"></th></tr></thead>
            <tbody>
              <tr v-for="(row, i) in formRows" :key="i">
                <td><el-checkbox v-model="row.enabled" @change="emitAll" /></td>
                <td><el-input v-model="row.key" size="small" placeholder="字段名" @change="emitAll" /></td>
                <td><el-input v-model="row.value" size="small" placeholder="值" @change="emitAll" /></td>
                <td><el-button link size="small" type="danger" @click="removeFormRow(i); emitAll()">✕</el-button></td>
              </tr>
            </tbody>
          </table>
          <el-button size="small" link @click="addFormRow">+ 添加字段</el-button>
        </div>
        <div v-else-if="bodyType === 'raw'" style="margin-top: 8px">
          <el-input v-model="rawText" type="textarea" :rows="10" placeholder="原始请求体内容" @change="emitAll" />
        </div>
        <div v-else class="req-config-editor__body-hint">此请求不包含请求体</div>
      </el-tab-pane>

      <!-- ==================== 超时 ==================== -->
      <el-tab-pane label="超时" name="timeout">
        <el-form-item label="请求超时（毫秒）">
          <el-input-number :model-value="editTimeout" :min="1000" :max="300000" :step="1000" @update:model-value="(v: number | undefined) => { editTimeout = v ?? 30000; emitAll() }" />
        </el-form-item>
      </el-tab-pane>
    </el-tabs>

    <!-- cURL 导入弹窗 -->
    <el-dialog v-model="showCurlImport" title="导入 cURL 命令" width="560px">
      <el-input v-model="curlText" type="textarea" :rows="8" placeholder="粘贴 cURL 命令..." />
      <template #footer>
        <el-button @click="showCurlImport = false">取消</el-button>
        <el-button type="primary" :disabled="!curlText.trim()" @click="parseCurl(curlText)">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.req-config-editor {
  display: flex;
  flex-direction: column;
}

.req-config-editor__tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }
}

.req-config-editor__request-line {
  display: flex;
  gap: var(--space-sm);
  padding: var(--space-md);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.req-config-editor__url {
  flex: 1;
}

.req-config-editor__kv-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: var(--space-sm);

  th {
    text-align: left;
    font-size: var(--font-size-xs);
    color: var(--color-neutral-500);
    padding: 4px 6px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  td {
    padding: 4px 6px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
}

.req-config-editor__body-type {
  margin-bottom: var(--space-sm);
}

.req-config-editor__body-hint {
  padding: var(--space-lg);
  text-align: center;
  color: var(--color-neutral-400);
}
</style>
