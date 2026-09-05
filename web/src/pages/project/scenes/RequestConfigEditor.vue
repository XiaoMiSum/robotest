<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import KeyValueTable from '@/pages/project/debug/KeyValueTable.vue'
import type { ApiDebugKeyValue } from '@/types'

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
}>()

const emit = defineEmits<{
  (e: 'update:method', value: string): void
  (e: 'update:url', value: string): void
  (e: 'update:headers', value: KvRow[]): void
  (e: 'update:params', value: KvRow[]): void
  (e: 'update:body', value: { type: string; content: unknown }): void
}>()

const activeTab = ref('headers')
const editMethod = ref(props.method ?? 'GET')
const editUrl = ref(props.url ?? '')
const editHeaders = ref<ApiDebugKeyValue[]>(props.headers?.length ? props.headers.map((h) => ({ ...h, description: '' })) : [])
const editParams = ref<ApiDebugKeyValue[]>(props.params?.length ? props.params.map((p) => ({ ...p, description: '' })) : [])
const bodyType = ref(props.body?.type ?? 'none')
const jsonText = ref(typeof props.body?.content === 'string' ? String(props.body.content) : '')
const rawText = ref(typeof props.body?.content === 'string' ? String(props.body.content) : '')
const formRows = ref<ApiDebugKeyValue[]>(Array.isArray(props.body?.content) ? (props.body.content as KvRow[]).map((r) => ({ ...r, description: '' })) : [])

const headersBadge = computed(() => editHeaders.value.filter((h) => h.key.trim()).length || '')
const queryBadge = computed(() => editParams.value.filter((p) => p.key.trim()).length || '')
const bodyBadge = computed(() => {
  if (bodyType.value === 'none') return ''
  if (bodyType.value === 'json' || bodyType.value === 'raw') return jsonText.value.trim() || rawText.value.trim() ? 1 : ''
  if (bodyType.value === 'form') return formRows.value.filter((r) => r.key.trim()).length || ''
  return ''
})

const BODY_TYPES = [
  { value: 'none', label: '无' },
  { value: 'json', label: 'JSON' },
  { value: 'form', label: '表单' },
  { value: 'raw', label: '原始文本' },
]

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

watch(() => props.method, (v) => { editMethod.value = v ?? 'GET' })
watch(() => props.url, (v) => { editUrl.value = v ?? '' })
watch(() => props.headers, (v) => { editHeaders.value = v?.length ? v.map((h) => ({ ...h, description: '' })) : [] }, { deep: true })
watch(() => props.params, (v) => { editParams.value = v?.length ? v.map((p) => ({ ...p, description: '' })) : [] }, { deep: true })
watch(() => props.body, (v) => {
  bodyType.value = v?.type ?? 'none'
  if (v?.type === 'json' && v.content != null && typeof v.content !== 'string') {
    // 接口/组件导入的 JSON body content 为对象，转为文本供编辑器展示
    jsonText.value = JSON.stringify(v.content, null, 2)
  } else if (typeof v?.content === 'string') { jsonText.value = String(v.content); rawText.value = String(v.content) }
  if (Array.isArray(v?.content)) formRows.value = (v.content as KvRow[]).map((r) => ({ ...r, description: '' }))
}, { deep: true })

function emitAll() {
  emit('update:method', editMethod.value)
  emit('update:url', editUrl.value)
  emit('update:headers', editHeaders.value.filter((h) => h.key.trim()).map(({ key, value, enabled }) => ({ key, value, enabled })))
  emit('update:params', editParams.value.filter((p) => p.key.trim()).map(({ key, value, enabled }) => ({ key, value, enabled })))
  const content = bodyType.value === 'json' ? jsonText.value
    : bodyType.value === 'form' ? formRows.value.filter((r) => r.key.trim()).map(({ key, value, enabled }) => ({ key, value, enabled }))
    : bodyType.value === 'raw' ? rawText.value
    : null
  emit('update:body', { type: bodyType.value, content })
}

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
  const headers: ApiDebugKeyValue[] = []
  while ((hm = headerRegex.exec(trimmed)) !== null) {
    const [key, ...rest] = hm[1].split(':')
    if (key) headers.push({ key: key.trim(), value: rest.join(':').trim(), enabled: true, description: '' })
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

    <!-- ==================== Tabs: 请求头 / Query 参数 / 请求体 ==================== -->
    <el-tabs v-model="activeTab" class="req-config-editor__tabs">
      <el-tab-pane name="headers">
        <template #label>
          <span class="req-config-editor__tab-label">
            请求头
            <span v-if="headersBadge" class="req-config-editor__badge">{{ headersBadge }}</span>
          </span>
        </template>
        <KeyValueTable v-model:entries="editHeaders" placeholder-key="Header" @change="emitAll" />
      </el-tab-pane>

      <el-tab-pane name="query">
        <template #label>
          <span class="req-config-editor__tab-label">
            Query 参数
            <span v-if="queryBadge" class="req-config-editor__badge">{{ queryBadge }}</span>
          </span>
        </template>
        <KeyValueTable v-model:entries="editParams" placeholder-key="参数名" @change="emitAll" />
      </el-tab-pane>

      <el-tab-pane name="body">
        <template #label>
          <span class="req-config-editor__tab-label">
            请求体
            <span v-if="bodyBadge" class="req-config-editor__badge">{{ bodyBadge }}</span>
          </span>
        </template>
        <div class="req-config-editor__body">
          <el-radio-group :model-value="bodyType" class="req-config-editor__body-type" @update:model-value="(v) => { bodyType = String(v ?? 'none'); emitAll() }">
            <el-radio-button v-for="bt in BODY_TYPES" :key="bt.value" :value="bt.value">{{ bt.label }}</el-radio-button>
          </el-radio-group>

          <p v-if="bodyType === 'none'" class="req-config-editor__body-hint">该请求不携带请求体。</p>

          <KeyValueTable
            v-else-if="bodyType === 'form'"
            v-model:entries="formRows"
            placeholder-key="字段名"
            @change="emitAll"
          />

          <el-input
            v-else-if="bodyType === 'json'"
            v-model="jsonText"
            type="textarea"
            :rows="6"
            placeholder='{"code": 200}'
            @change="emitAll"
          />

          <el-input
            v-else
            v-model="rawText"
            type="textarea"
            :rows="6"
            placeholder="原始请求体内容"
            @change="emitAll"
          />
        </div>
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
  gap: var(--space-md);
}

.req-config-editor__request-line {
  display: flex;
  gap: var(--space-sm);
}

.req-config-editor__url {
  flex: 1;
}

.req-config-editor__tabs {
  :deep(.el-tabs__header) {
    margin: 0;
  }
}

.req-config-editor__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.req-config-editor__badge {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-primary, #409eff);
  color: #fff;
  font-size: 10px;
  text-align: center;
}

.req-config-editor__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.req-config-editor__body-type {
  margin-bottom: 0;
}

.req-config-editor__body-hint {
  padding: var(--space-lg);
  text-align: center;
  color: var(--color-neutral-400);
  margin: 0;
}
</style>
