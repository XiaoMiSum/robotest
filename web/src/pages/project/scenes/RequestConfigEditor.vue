<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import KeyValueTable from '@/pages/project/debug/KeyValueTable.vue'
import type { ApiDebugKeyValue, ApiDebugRawSubtype } from '@/types'
import {
  SCENE_BODY_TYPES,
  SCENE_RAW_SUBTYPES,
  buildBodyFromEditState,
  parseBodyEditState,
  syncBodyContentTypeHeader,
  type SceneBodyEditState,
} from '../scenesModel'

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
  /** 复用该编辑器但入参语义非完整 URL（如处理器相对 path）时隐藏导入 cURL */
  hideCurlImport?: boolean
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
const bodyState = ref<SceneBodyEditState>(parseBodyEditState(props.body))

const headersBadge = computed(() => editHeaders.value.filter((h) => h.key.trim()).length || '')
const queryBadge = computed(() => editParams.value.filter((p) => p.key.trim()).length || '')
const bodyBadge = computed(() => {
  if (bodyState.value.kind === 'none') return ''
  if (bodyState.value.kind === 'urlencoded') {
    return bodyState.value.urlencodedRows.filter((r) => r.key.trim() && r.enabled).length || ''
  }
  return bodyState.value.rawText.trim() ? 1 : ''
})

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

watch(() => props.method, (v) => { editMethod.value = v ?? 'GET' })
watch(() => props.url, (v) => { editUrl.value = v ?? '' })
watch(() => props.headers, (v) => { editHeaders.value = v?.length ? v.map((h) => ({ ...h, description: '' })) : [] }, { deep: true })
watch(() => props.params, (v) => { editParams.value = v?.length ? v.map((p) => ({ ...p, description: '' })) : [] }, { deep: true })
watch(() => props.body, (v) => {
  bodyState.value = parseBodyEditState(v)
}, { deep: true })

function emitBody(): string | undefined {
  const { body, error } = buildBodyFromEditState(bodyState.value)
  if (error) return error
  // 解析失败时保持上一份合法 body 不被覆盖（对齐接口保存行为），仅提示用户修正
  emit('update:body', body ?? { type: 'none', content: null })
  return undefined
}

function emitAll() {
  emit('update:method', editMethod.value)
  emit('update:url', editUrl.value)
  emit('update:headers', editHeaders.value.filter((h) => h.key.trim()).map(({ key, value, enabled }) => ({ key, value, enabled })))
  emit('update:params', editParams.value.filter((p) => p.key.trim()).map(({ key, value, enabled }) => ({ key, value, enabled })))
  const error = emitBody()
  if (error) ElMessage.warning(error)
}

defineExpose({ emitAll })

/** 切换请求体类型：新选 raw 默认 json 子类型，并联动 Content-Type 头（对齐快速调试） */
function pickBodyType(kind: SceneBodyEditState['kind']) {
  if (kind !== bodyState.value.kind && kind === 'raw') bodyState.value.rawSubtype = 'json'
  bodyState.value.kind = kind
  editHeaders.value = syncBodyContentTypeHeader(editHeaders.value, bodyState.value)
  emitAll()
}

/** raw 子类型变更时联动 Content-Type 头 */
function onRawSubtypeChange(subtype: ApiDebugRawSubtype) {
  bodyState.value.rawSubtype = subtype
  editHeaders.value = syncBodyContentTypeHeader(editHeaders.value, bodyState.value)
  emitAll()
}

function formatJsonBody() {
  try {
    const parsed: unknown = JSON.parse(bodyState.value.rawText)
    bodyState.value.rawText = JSON.stringify(parsed, null, 2)
  } catch {
    ElMessage.warning('请求体不是合法 JSON，无法格式化')
  }
}

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
    const text = dataMatch[1]
    bodyState.value = {
      kind: 'raw',
      rawSubtype: /^\s*[{[]/.test(text.trim()) ? 'json' : 'text',
      rawText: text,
      urlencodedRows: [],
    }
    editHeaders.value = syncBodyContentTypeHeader(editHeaders.value, bodyState.value)
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
      <el-button v-if="!props.hideCurlImport" size="small" @click="showCurlImport = true">导入 cURL</el-button>
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
          <div class="req-config-editor__body-bar">
            <div class="req-config-editor__body-types">
              <button
                v-for="t in SCENE_BODY_TYPES"
                :key="t.value"
                class="req-config-editor__body-type"
                :class="{ 'is-active': bodyState.kind === t.value }"
                data-test="scene-body-type"
                @click="pickBodyType(t.value)"
              >
                {{ t.label }}
              </button>
              <template v-if="bodyState.kind === 'raw'">
                <el-select :model-value="bodyState.rawSubtype" class="req-config-editor__raw-select" @update:model-value="(v: ApiDebugRawSubtype) => onRawSubtypeChange(v)">
                  <el-option
                    v-for="s in SCENE_RAW_SUBTYPES"
                    :key="s"
                    :label="s[0].toUpperCase() + s.slice(1)"
                    :value="s"
                  />
                </el-select>
                <el-tooltip v-if="bodyState.rawSubtype === 'json'" content="格式化（修正 JSON 缩进）" placement="top">
                  <button class="req-config-editor__body-icon" @click="formatJsonBody">
                    <el-icon><MagicStick /></el-icon>
                  </button>
                </el-tooltip>
              </template>
            </div>
          </div>

          <p v-if="bodyState.kind === 'none'" class="req-config-editor__body-hint">该请求不携带请求体。</p>

          <KeyValueTable
            v-else-if="bodyState.kind === 'urlencoded'"
            v-model:entries="bodyState.urlencodedRows"
            placeholder-key="Key"
            @change="emitAll"
          />

          <textarea
            v-else
            v-model="bodyState.rawText"
            class="req-config-editor__body-editor"
            placeholder="原始文本（支持变量引用）"
            spellcheck="false"
            :rows="12"
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

.req-config-editor__body-bar {
  display: flex;
  align-items: center;
}

.req-config-editor__body-types {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--color-neutral-50, #fafafa);
  border-radius: 6px;
  padding: 2px;
  width: fit-content;
  flex-wrap: wrap;
}

.req-config-editor__body-type {
  height: 28px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-neutral-500, #909399);
  transition: all 0.15s;

  &:hover {
    color: var(--color-neutral-700, #606266);
  }

  &.is-active {
    background: var(--color-primary-500, #409eff);
    color: #fff;
    font-weight: 500;
    box-shadow: none;
  }
}

.req-config-editor__raw-select {
  width: 120px;
  margin-left: 4px;

  :deep(.el-select__wrapper) {
    min-height: 28px;
    padding: 1px 8px;
  }
}

.req-config-editor__body-icon {
  height: 28px;
  width: 28px;
  margin-left: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-neutral-400, #909399);

  &:hover {
    color: var(--color-primary-500, #409eff);
  }
}

.req-config-editor__body-editor {
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
    border-color: var(--color-primary-500, #409eff);
  }
}

.req-config-editor__body-hint {
  padding: var(--space-lg);
  text-align: center;
  color: var(--color-neutral-400);
  margin: 0;
}
</style>
