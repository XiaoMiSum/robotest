<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { CascaderOption } from 'element-plus'
import type { ApiComponentListItem, ApiComponentType, ApiDebugKeyValue, ApiDebugRawSubtype, ApiInterfaceDetail, ProjectModule } from '@/types'
import { createInterface, fetchInterfaceDetail, updateInterface } from '@/services/apiInterface'
import { fetchProjectModuleTree } from '@/services/project'
import { fetchComponents } from '@/services/apiComponent'
import KeyValueTable from './debug/KeyValueTable.vue'
import ValidatorForm from '@/components/api-testing/ValidatorForm.vue'
import ExtractorForm from '@/components/api-testing/ExtractorForm.vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import {
  extractorFromComponent,
  validatorFromComponent,
} from '@/components/api-testing/processorFormModel'
import {
  createEditorForm,
  toCreatePayload,
  toSelectableModuleOptions,
  type InterfaceEditorForm,
} from './interfacesModel'

// 多 Tab 并存：渲染于 InterfaceWorkspace「接口管理」Tab 内，单个接口一个编辑器实例（keep-alive 保留状态）
const props = defineProps<{ interfaceId?: string; createMode?: boolean; moduleId?: string }>()
const emit = defineEmits<{
  (e: 'back'): void
  (e: 'title-update', name: string): void
  (e: 'dirty-change', dirty: boolean): void
}>()

const isNew = computed(() => (props.createMode ?? false) || !props.interfaceId)
const interfaceId = computed(() => (isNew.value ? '' : props.interfaceId!))

const detail = ref<ApiInterfaceDetail | null>(null)
const form = ref<InterfaceEditorForm>(createEditorForm())
const loading = ref(false)
const saving = ref(false)
// 新建默认激活「请求头」Tab（对齐 MeterSphere）；编辑态默认展示基本信息
const activeTab = ref(isNew.value ? 'headers' : 'basic')

// ==================== 所属模块（基本信息 Tab 内选择） ====================
const moduleTree = ref<ProjectModule[]>([])
const moduleOptions = computed<CascaderOption[]>(() =>
  toSelectableModuleOptions(moduleTree.value) as CascaderOption[],
)

async function loadModules() {
  try {
    moduleTree.value = await fetchProjectModuleTree('interface')
  } catch {
    // 模块服务不可用时模块下拉为空，不阻塞编辑主流程
    moduleTree.value = []
  }
}

// ==================== 路径：校验 + `?` 自动拆分到 Query（对齐 MeterSphere） ====================
function splitQueryFromPath(raw: string): { path: string; query: ApiDebugKeyValue[] } {
  const qIndex = raw.indexOf('?')
  if (qIndex < 0) return { path: raw, query: [] }
  const query = raw
    .slice(qIndex + 1)
    .split('&')
    .map((kv) => {
      const [k, v] = kv.split('=')
      return { key: (k ?? '').trim(), value: (v ?? '').trim(), enabled: true }
    })
    .filter((entry) => entry.key !== '')
  return { path: raw.slice(0, qIndex), query }
}

function handlePathBlur() {
  const value = form.value.path.trim()
  if (value && !value.startsWith('/')) {
    ElMessage.warning('路径需以 / 开头')
  }
  const { path, query } = splitQueryFromPath(form.value.path)
  if (query.length) {
    form.value.path = path
    const existing = form.value.params.filter((p) => p.key.trim() !== '')
    form.value.params = [...existing, ...query]
    activeTab.value = 'query'
  }
}

// 相对最近一次保存是否发生修改（供宿主关闭 Tab 前做离开确认）
let savedStamp = ''
function formStamp() {
  return JSON.stringify({ name: form.value.name, form: form.value, detailVersion: detail.value?.changeVersion })
}
const isDirty = ref(false)
function markSaved() {
  savedStamp = formStamp()
  isDirty.value = false
}
watch(formStamp, () => {
  isDirty.value = formStamp() !== savedStamp
  emit('dirty-change', isDirty.value)
})
watch(
  () => form.value.name,
  (name) => emit('title-update', name.trim() || (isNew.value ? '新接口' : '')),
)

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'CONNECT']
// 协议下拉：当前版本仅 http 可选，jdbc 占位灰示（随场景模块开放后启用）
const PROTOCOL_OPTIONS = [{ value: 'http', label: 'http' }]

// ==================== 请求 Tab 数量徽标（对齐 MeterSphere：仅计有效条目） ====================

function countEnabled(rows: { key: string; enabled: boolean }[]): number {
  return rows.filter((row) => row.key.trim() !== '' && row.enabled).length
}
const headersBadge = computed(() => countEnabled(form.value.headers))
const queryBadge = computed(() => countEnabled(form.value.params))
const bodyBadge = computed(() => (form.value.bodyType === 'none' ? 0 : 1))

// ==================== 请求体（对齐快速调试：none / x-www-form-urlencoded / raw + 子类型 + 深色编辑器） ====================

const BODY_TYPES = [
  { value: 'none', label: 'none' },
  { value: 'urlencoded', label: 'x-www-form-urlencoded' },
  { value: 'raw', label: 'raw' },
] as const satisfies ReadonlyArray<{ value: InterfaceEditorForm['bodyType']; label: string }>
const RAW_SUBTYPES: ApiDebugRawSubtype[] = ['text', 'json', 'xml', 'html', 'javascript']

function pickBodyType(type: InterfaceEditorForm['bodyType']) {
  form.value.bodyType = type
  if (type === 'raw' && !form.value.rawSubtype) form.value.rawSubtype = 'json'
}

function formatJsonBody() {
  try {
    const parsed: unknown = JSON.parse(form.value.rawText)
    form.value.rawText = JSON.stringify(parsed, null, 2)
  } catch {
    ElMessage.warning('请求体不是合法 JSON，无法格式化')
  }
}

// ==================== 接口级配置 Tab（仅定义存储，执行随场景模块；详细设计 6.3） ====================

type ConfigItem = Record<string, unknown>

function addValidator() {
  form.value.validators.push({ target: 'status_code', expression: '', condition: 'equals', expected: '' })
}

function removeValidator(index: number) {
  form.value.validators.splice(index, 1)
}

function updateValidator(index: number, value: ConfigItem) {
  form.value.validators[index] = value
}

function addExtractor() {
  form.value.extractors.push({ source: 'json_field', expression: '', variableName: '' })
}

function removeExtractor(index: number) {
  form.value.extractors.splice(index, 1)
}

function updateExtractor(index: number, value: ConfigItem) {
  form.value.extractors[index] = value
}

// ==================== 从公共组件引入（验证器/提取器，复制语义） ====================

type AssetKind = 'validator' | 'extractor'

/** 接口配置 Tab → 公共组件类型（fetchComponents 过滤） */
const ASSET_TYPE: Record<AssetKind, ApiComponentType> = {
  validator: 'validator',
  extractor: 'extractor',
}

const ASSET_TITLE: Record<AssetKind, string> = {
  validator: '从公共组件引入验证器',
  extractor: '从公共组件引入提取器',
}

const ASSET_NAME: Record<AssetKind, string> = {
  validator: '验证器',
  extractor: '提取器',
}

const assetPickerVisible = ref(false)
const assetPickerLoading = ref(false)
const assetPickerItems = ref<ApiComponentListItem[]>([])
const assetPickerKeyword = ref('')
const assetPickerKind = ref<AssetKind>('validator')

async function loadAssetPicker(): Promise<void> {
  assetPickerLoading.value = true
  try {
    const result = await fetchComponents({
      type: ASSET_TYPE[assetPickerKind.value],
      enabled: true,
      pageNo: 1,
      pageSize: 100,
      keyword: assetPickerKeyword.value.trim() || undefined,
    })
    assetPickerItems.value = result.list
  } catch {
    ElMessage.error('公共组件加载失败')
  } finally {
    assetPickerLoading.value = false
  }
}

function openAssetPicker(kind: AssetKind) {
  assetPickerKind.value = kind
  assetPickerKeyword.value = ''
  assetPickerVisible.value = true
  void loadAssetPicker()
}

function handleAssetPicked(rows: ApiComponentListItem[]) {
  if (rows.length === 0) return
  const kind = assetPickerKind.value
  if (kind === 'validator') {
    rows.forEach((r) => form.value.validators.push(validatorFromComponent(r)))
  } else {
    rows.forEach((r) => form.value.extractors.push(extractorFromComponent(r)))
  }
  ElMessage.success(`已引入 ${rows.length} 个${ASSET_NAME[kind]}`)
}

// ==================== 响应示例（独立区，对齐快速调试响应组件） ====================

const responseTab = ref<'body' | 'headers'>('body')

function activeResponseText(): { get: string; set: (v: string) => void } {
  return responseTab.value === 'body'
    ? { get: form.value.responseBodyText, set: (v) => { form.value.responseBodyText = v } }
    : { get: form.value.responseHeadersText, set: (v) => { form.value.responseHeadersText = v } }
}

/** 仅 Raw 编辑：压缩的 JSON 一键格式化便于人工编辑 */
function handleFormatResponse() {
  const ref = activeResponseText()
  const text = ref.get.trim()
  if (!text) return
  try {
    ref.set(JSON.stringify(JSON.parse(text) as unknown, null, 2))
  } catch {
    ElMessage.warning('JSON 格式非法，无法格式化')
  }
}

async function handleCopyResponse() {
  const text = activeResponseText().get
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

async function loadDetail() {
  if (isNew.value) {
    // 新建模式预选模块由父页选中模块/路由 query 传入
    form.value = createEditorForm()
    form.value.moduleId = props.moduleId ?? null
    markSaved()
    return
  }
  loading.value = true
  try {
    detail.value = await fetchInterfaceDetail(interfaceId.value)
    form.value = createEditorForm(detail.value)
    markSaved()
    emit('title-update', form.value.name.trim() || '')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '接口详情加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 保存 ====================

async function save() {
  if (saving.value) return
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写接口名称')
    return
  }
  saving.value = true
  try {
    if (isNew.value) {
      const { req, error } = toCreatePayload(form.value)
      if (error) {
        ElMessage.warning(error)
        return
      }
      await createInterface(req)
      ElMessage.success('接口已创建')
      // 创建完成即关闭该 Tab 并返回列表（宿主监听 back 处理）
      emit('back')
      return
    }
    const payload = toCreatePayload(form.value)
    if (payload.error) {
      ElMessage.warning(payload.error)
      return
    }
    await updateInterface(interfaceId.value, {
      ...payload.req,
      changeVersion: detail.value!.changeVersion,
    })
    ElMessage.success('已保存')
    await loadDetail()
  } catch (err) {
    await handleSaveConflict(err)
  } finally {
    saving.value = false
  }
}

/** 乐观锁冲突（7105）：提示以服务端最新版本为准，确认后重载覆盖本地编辑 */
async function handleSaveConflict(err: unknown) {
  const message = err instanceof Error ? err.message : ''
  if (!message.includes('7105') && !message.includes('版本')) {
    ElMessage.error(message || '保存失败')
    return
  }
  await ElMessageBox.confirm('接口已被他人修改，是否加载最新版本（将丢弃当前未保存的编辑）？', '版本冲突', { type: 'warning' })
  await loadDetail()
}

function handleCtrlS(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void save()
  }
}

// ==================== 请求/响应上下分栏（可拖拽分隔线，对齐快速调试） ====================

const containerRef = ref<HTMLElement>()
const requestHeight = ref(50) // 百分比
const isDragging = ref(false)

function onDividerMouseDown(e: MouseEvent) {
  e.preventDefault()
  isDragging.value = true
  document.addEventListener('mousemove', onDividerMouseMove)
  document.addEventListener('mouseup', onDividerMouseUp)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function onDividerMouseMove(e: MouseEvent) {
  if (!isDragging.value || !containerRef.value) return
  const rect = containerRef.value.getBoundingClientRect()
  const y = e.clientY - rect.top
  const pct = (y / rect.height) * 100
  requestHeight.value = Math.min(Math.max(pct, 20), 80)
}

function onDividerMouseUp() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDividerMouseMove)
  document.removeEventListener('mouseup', onDividerMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  window.addEventListener('keydown', handleCtrlS)
  void loadDetail()
  void loadModules()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleCtrlS)
  document.removeEventListener('mousemove', onDividerMouseMove)
  document.removeEventListener('mouseup', onDividerMouseUp)
})
</script>

<template>
  <div v-loading="loading" class="interface-editor">
    <el-card shadow="never" class="interface-editor__card">
      <div class="interface-editor__request-line">
        <el-select v-model="form.protocol" style="width: 96px" data-test="editor-protocol-select">
          <el-option v-for="opt in PROTOCOL_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
        </el-select>
        <el-select v-model="form.method" style="width: 130px" data-test="editor-method-select">
          <el-option v-for="method in METHOD_OPTIONS" :key="method" :value="method" :label="method" />
        </el-select>
        <el-input
          v-model="form.path"
          placeholder="/api/resource"
          style="flex: 1"
          data-test="editor-path-input"
          @blur="handlePathBlur"
        />
        <el-input v-model="form.name" placeholder="接口名称" style="width: 240px" data-test="editor-name-input" />
        <el-button
          type="primary"
          :loading="saving"
          data-test="editor-save-btn"
          class="interface-editor__save"
          @click="save"
        >
          保存
        </el-button>
      </div>

      <div ref="containerRef" class="interface-editor__body">
      <div class="interface-editor__request" :style="{ '--req-h': requestHeight + '%' }">
      <el-tabs v-model="activeTab" class="interface-editor__tabs">
        <el-tab-pane name="basic" label="基本信息">
          <div class="interface-editor__field-row">
            <span class="interface-editor__field-label">所属模块</span>
            <el-cascader
              v-model="form.moduleId"
              :options="moduleOptions"
              :props="{ checkStrictly: true, emitPath: false, value: 'value', label: 'label' }"
              placeholder="选择所属模块"
              clearable
              style="width: 100%"
              data-test="editor-module-cascader"
            />
          </div>
          <div class="interface-editor__field-row interface-editor__field-row--top">
            <span class="interface-editor__field-label">描述</span>
            <el-input v-model="form.description" type="textarea" :rows="3" data-test="editor-description-input" />
          </div>
        </el-tab-pane>

        <el-tab-pane name="headers">
          <template #label>
            <span class="interface-editor__tab-label">
              请求头
              <span v-if="headersBadge" class="interface-editor__badge">{{ headersBadge }}</span>
            </span>
          </template>
          <KeyValueTable v-model:entries="form.headers" placeholder-key="Header" />
        </el-tab-pane>

        <el-tab-pane name="query">
          <template #label>
            <span class="interface-editor__tab-label">
              Query 参数
              <span v-if="queryBadge" class="interface-editor__badge">{{ queryBadge }}</span>
            </span>
          </template>
          <KeyValueTable v-model:entries="form.params" placeholder-key="参数名" />
        </el-tab-pane>

        <el-tab-pane name="body">
          <template #label>
            <span class="interface-editor__tab-label">
              请求体
              <span v-if="bodyBadge" class="interface-editor__badge">{{ bodyBadge }}</span>
            </span>
          </template>
          <div class="interface-editor__body">
            <div class="interface-editor__body-bar">
              <div class="interface-editor__body-types">
                <button
                  v-for="t in BODY_TYPES"
                  :key="t.value"
                  class="interface-editor__body-type"
                  :class="{ 'is-active': form.bodyType === t.value }"
                  data-test="editor-body-type-group"
                  @click="pickBodyType(t.value)"
                >
                  {{ t.label }}
                </button>
                <template v-if="form.bodyType === 'raw'">
                  <el-select v-model="form.rawSubtype" class="interface-editor__raw-select">
                    <el-option
                      v-for="s in RAW_SUBTYPES"
                      :key="s"
                      :label="s[0].toUpperCase() + s.slice(1)"
                      :value="s"
                    />
                  </el-select>
                  <el-tooltip v-if="form.rawSubtype === 'json'" content="格式化（修正 JSON 缩进）" placement="top">
                    <button class="interface-editor__body-icon" @click="formatJsonBody">
                      <el-icon><MagicStick /></el-icon>
                    </button>
                  </el-tooltip>
                </template>
              </div>
            </div>

            <p v-if="form.bodyType === 'none'" class="interface-editor__hint">该请求不携带请求体。</p>

            <KeyValueTable
              v-else-if="form.bodyType === 'urlencoded'"
              v-model:entries="form.urlencodedRows"
              placeholder-key="Key"
            />

            <textarea
              v-else
              v-model="form.rawText"
              class="interface-editor__body-editor"
              placeholder="原始文本（支持变量引用）"
              spellcheck="false"
              :rows="12"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="auth" label="认证">
          <div class="interface-editor__auth">
            <el-form label-width="90px" @submit.prevent>
              <el-form-item label="认证方式">
                <el-select v-model="form.auth.type" data-test="editor-auth-type">
                  <el-option label="No Auth" value="none" />
                  <el-option label="Bearer Token" value="bearer" />
                  <el-option label="API Key" value="apiKey" />
                  <el-option label="Basic Auth" value="basic" />
                  <el-option label="Digest Auth" value="digest" disabled />
                </el-select>
              </el-form-item>
              <template v-if="form.auth.type === 'bearer'">
                <el-form-item label="Token">
                  <el-input v-model="form.auth.token" type="password" show-password placeholder="输入 Bearer Token" />
                </el-form-item>
                <p class="interface-editor__tip">执行时换算为 Authorization: Bearer 头；手工同名头优先</p>
              </template>
              <template v-else-if="form.auth.type === 'apiKey'">
                <el-form-item label="Key 名">
                  <el-input v-model="form.auth.apiKeyName" placeholder="缺省为 X-API-Key" />
                </el-form-item>
                <el-form-item label="Key 值">
                  <el-input v-model="form.auth.apiKeyValue" type="password" show-password />
                </el-form-item>
                <p class="interface-editor__tip">执行时换算为自定义请求头；手工同名头优先</p>
              </template>
              <template v-else-if="form.auth.type === 'basic'">
                <el-form-item label="用户名">
                  <el-input v-model="form.auth.username" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input v-model="form.auth.password" type="password" show-password />
                </el-form-item>
                <p class="interface-editor__tip">执行时换算为 Authorization: Basic 头；手工同名头优先</p>
              </template>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane name="validators" label="验证器">
          <div v-for="(_, i) in form.validators" :key="i" class="interface-editor__config-item">
            <ValidatorForm
              :model-value="form.validators[i]"
              @update:model-value="(v) => updateValidator(i, v)"
            />
            <el-button class="interface-editor__config-delete" type="danger" link @click="removeValidator(i)">删除</el-button>
          </div>
          <div class="interface-editor__add-row">
            <el-button type="primary" link @click="addValidator">+ 添加验证器</el-button>
            <el-button type="primary" link @click="openAssetPicker('validator')">从公共组件引入</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane name="extractors" label="提取器">
          <div v-for="(_, i) in form.extractors" :key="i" class="interface-editor__config-item">
            <ExtractorForm
              :model-value="form.extractors[i]"
              @update:model-value="(v) => updateExtractor(i, v)"
            />
            <el-button class="interface-editor__config-delete" type="danger" link @click="removeExtractor(i)">删除</el-button>
          </div>
          <div class="interface-editor__add-row">
            <el-button type="primary" link @click="addExtractor">+ 添加提取器</el-button>
            <el-button type="primary" link @click="openAssetPicker('extractor')">从公共组件引入</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
      </div>

      <div class="interface-editor__divider" @mousedown="onDividerMouseDown">
        <div class="interface-editor__divider-line" />
      </div>

      <!-- 响应示例：请求区下方、可拖拽分栏内的独立响应区，body/headers 分开展示 -->
      <div class="interface-editor__response" data-test="editor-response-card">
        <div class="interface-editor__response-toolbar">
        <span class="interface-editor__response-title">响应示例</span>
        <div class="interface-editor__response-modes">
          <button
            class="interface-editor__response-mode"
            :class="{ 'is-active': responseTab === 'body' }"
            @click="responseTab = 'body'"
          >Body</button>
          <button
            class="interface-editor__response-mode"
            :class="{ 'is-active': responseTab === 'headers' }"
            @click="responseTab = 'headers'"
          >Headers</button>
        </div>
        <el-tooltip content="格式化（修正 JSON 缩进）" placement="top">
          <button class="interface-editor__response-icon" @click="handleFormatResponse">
            <el-icon><MagicStick /></el-icon>
          </button>
        </el-tooltip>
        <el-tooltip content="复制" placement="top">
          <button class="interface-editor__response-icon" @click="handleCopyResponse">
            <el-icon><CopyDocument /></el-icon>
          </button>
        </el-tooltip>
      </div>

      <div class="interface-editor__response-body">
        <el-input
          v-if="responseTab === 'body'"
          v-model="form.responseBodyText"
          type="textarea"
          :rows="12"
          class="interface-editor__response-editor"
          placeholder='{"code": 200, "data": {...}}'
          data-test="editor-response-body-input"
        />
        <el-input
          v-else
          v-model="form.responseHeadersText"
          type="textarea"
          :rows="12"
          class="interface-editor__response-editor"
          placeholder='{"Content-Type": "application/json", "X-Request-Id": "..."}'
          data-test="editor-response-headers-input"
        />
      </div>
      </div>
      </div>
    </el-card>

    <!-- 从公共组件引入：验证器、提取器一并对齐该选择器，引入为复制 -->
    <ExtractorAssetPicker
      v-model="assetPickerVisible"
      :loading="assetPickerLoading"
      :items="assetPickerItems"
      :keyword="assetPickerKeyword"
      :title="ASSET_TITLE[assetPickerKind]"
      tip="仅展示启用的组件资产；引入为复制，得到独立副本，与源资产无关联。"
      empty-text="暂无可用组件"
      search-placeholder="搜索组件名称..."
      @update:keyword="assetPickerKeyword = $event"
      @search="loadAssetPicker"
      @confirm="handleAssetPicked"
    />
  </div>
</template>

<style scoped lang="scss">
.interface-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  height: 100%;
}

.interface-editor__request-line {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.interface-editor__save {
  flex-shrink: 0;
}

.interface-editor__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.interface-editor__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 11px;
  line-height: 1;
}

.interface-editor__tabs {
  :deep(.el-tabs__content) {
    padding-top: var(--space-sm);
  }
}

.interface-editor__field-row {
  display: flex;
  gap: var(--space-md);
  align-items: center;

  &--top {
    align-items: flex-start;
  }

  & + & {
    margin-top: var(--space-md);
  }
}

.interface-editor__field-label {
  width: 80px;
  flex-shrink: 0;
  color: var(--color-neutral-600);
  font-size: var(--font-size-sm);
  line-height: 32px;
}

.interface-editor__hint {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

// ==================== 请求体（对齐快速调试） ====================

.interface-editor__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.interface-editor__body-bar {
  display: flex;
  align-items: center;
}

.interface-editor__body-types {
  display: flex;
  align-items: center;
  gap: 2px;
  background: var(--color-neutral-50, #fafafa);
  border-radius: 6px;
  padding: 2px;
  width: fit-content;
  flex-wrap: wrap;
}

.interface-editor__body-type {
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

.interface-editor__raw-select {
  width: 120px;
  margin-left: 4px;

  :deep(.el-select__wrapper) {
    min-height: 28px;
    padding: 1px 8px;
  }
}

.interface-editor__body-editor {
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

// ==================== 接口级配置项（处理器/验证器/提取器） ====================

.interface-editor__auth {
  max-width: 420px;
}

.interface-editor__tip {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--color-neutral-400, #909399);
}

.interface-editor__config-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
  padding: var(--space-sm);
  border: 1px solid var(--color-neutral-100, #e8e8e8);
  border-radius: 6px;
}

.interface-editor__config-delete {
  flex-shrink: 0;
  white-space: nowrap;
}

.interface-editor__add-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.interface-editor__card {
  display: flex;
  flex-direction: column;
  height: 100%;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
    padding: var(--space-md);
    gap: var(--space-md);
  }
}

// 请求/响应上下分栏（对齐快速调试）：请求区占 --req-h，分隔线可拖拽，响应区填满剩余
.interface-editor__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.interface-editor__request {
  height: var(--req-h, 50%);
  min-height: 80px;
  overflow: auto;
  flex-shrink: 0;
}

.interface-editor__divider {
  height: 6px;
  cursor: row-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 1;

  &:hover .interface-editor__divider-line,
  &:active .interface-editor__divider-line {
    background: var(--color-primary-300, #a0cfff);
  }
}

.interface-editor__divider-line {
  width: 75%;
  height: 2px;
  border-radius: 1px;
  background: var(--color-neutral-200, #dcdfe6);
  transition: background 0.15s;
}

.interface-editor__response {
  flex: 1;
  min-height: 80px;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.interface-editor__response-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 8px 12px;
  border-bottom: 1px solid var(--color-neutral-100, #e8e8e8);
}

.interface-editor__response-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
}

.interface-editor__response-modes {
  display: flex;
  gap: 2px;
}

.interface-editor__response-mode {
  padding: 4px 10px;
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
    background: var(--color-neutral-100, #e8e8e8);
    color: var(--color-neutral-800, #303133);
    font-weight: 500;
  }
}

.interface-editor__body-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: none;
  border-radius: 4px;
  color: var(--color-neutral-400, #909399);
  cursor: pointer;
  font-size: 14px;
  transition: color 0.15s, background 0.15s;

  &:hover {
    color: var(--color-neutral-700, #606266);
    background: var(--color-neutral-100, #e8e8e8);
  }
}

.interface-editor__response-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: none;
  border-radius: 4px;
  color: var(--color-neutral-400, #909399);
  cursor: pointer;
  font-size: 14px;
  transition: color 0.15s, background 0.15s;

  &:hover {
    color: var(--color-neutral-700, #606266);
    background: var(--color-neutral-100, #e8e8e8);
  }
}

.interface-editor__response-error {
  margin-left: auto;
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--color-danger-500, #f56c6c);
  cursor: help;
}

.interface-editor__response-body {
  min-height: 0;
}

.interface-editor__response-tree {
  padding: 8px 10px;
  background: #1e1e1e;
  max-height: 320px;
  overflow: auto;
}

.interface-editor__response-empty {
  color: var(--color-neutral-400, #909399);
  font-size: 12px;
}

.interface-editor__response-editor {
  :deep(.el-textarea__inner) {
    font-family: ui-monospace, SFMono-Regular, monospace;
  }
}
</style>
