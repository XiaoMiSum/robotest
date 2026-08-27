<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiSceneStepItem, ApiSceneStepVariableItem, ApiSceneStepSaveReq, ApiPublicStepBrowseItem } from '@/types'
import { createSceneStep, updateSceneStep, quickCreateSteps, addPublicStep, fetchStepVariables, updateStepVariables, browsePublicSteps } from '@/services/apiScene'
import { fetchInterfacePage } from '@/services/apiInterface'
import type { ApiInterfaceItem } from '@/types'
import {
  STEP_TYPE_OPTIONS, parseRequestConfig,
  type ValidatorItem, type ExtractorItem,
  createValidator, createExtractor, serializeValidators, serializeExtractors,
  createStepVariable, createExecutionConfig,
  VALIDATOR_TARGETS, VALIDATOR_CONDITIONS, EXTRACTOR_SOURCES,
} from '../scenesModel'
import RequestConfigEditor from './RequestConfigEditor.vue'

const props = defineProps<{ modelValue: boolean; sceneId: string; step: ApiSceneStepItem | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'saved'): void }>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => (visible.value = v))
watch(visible, (v) => emit('update:modelValue', v))

// ==================== 基本信息 ====================
const formName = ref('')
const formStepType = ref('http')
const formMethod = ref('GET')
const formUrl = ref('')
const formEnabled = ref(true)
const activeTab = ref('basic')

// ==================== 请求配置 ====================
const reqHeaders = ref<{ key: string; value: string; enabled: boolean }[]>([])
const reqParams = ref<{ key: string; value: string; enabled: boolean }[]>([])
const reqBody = ref<{ type: string; content: unknown }>({ type: 'none', content: null })
const reqTimeout = ref(30000)

// ==================== 断言 & 提取器 ====================
const validators = ref<ValidatorItem[]>([])
const extractors = ref<ExtractorItem[]>([])

// ==================== 步骤变量 ====================
const stepVariables = ref<ApiSceneStepVariableItem[]>([])
const variablesLoading = ref(false)

// ==================== 执行配置 ====================
const executionConfig = ref(createExecutionConfig())

// ==================== 快速创建 ====================
const createMode = ref<'manual' | 'quick' | 'public'>('manual')
const quickInterfaceId = ref('')
const quickMode = ref('copy')
const publicStepId = ref('')
const publicMode = ref('copy')
const browseVisible = ref(false)
const browseList = ref<ApiPublicStepBrowseItem[]>([])
const browseLoading = ref(false)
const browseSearch = ref('')
const interfaceOptions = ref<ApiInterfaceItem[]>([])
const interfaceSearch = ref('')
const interfaceLoading = ref(false)

// ==================== 初始化 ====================
watch(visible, async (v) => {
  if (!v) return
  activeTab.value = 'basic'
  if (props.step) {
    formName.value = props.step.name
    formStepType.value = props.step.stepType
    formEnabled.value = props.step.enabled
    createMode.value = 'manual'
    const cfg = parseRequestConfig(props.step.requestConfig)
    formMethod.value = String(cfg.method ?? 'GET')
    formUrl.value = String(cfg.url ?? '')
    reqHeaders.value = (cfg.headers ?? []).map((h) => ({ ...h }))
    reqParams.value = (cfg.params ?? []).map((p) => ({ ...p }))
    reqBody.value = cfg.body ?? { type: 'none', content: null }
    reqTimeout.value = cfg.timeout ?? 30000
    validators.value = (props.step.validators ?? []).map((v) => v as unknown as ValidatorItem)
    extractors.value = (props.step.extractors ?? []).map((e) => e as unknown as ExtractorItem)
    const rc = props.step.requestConfig as Record<string, unknown> | undefined
    executionConfig.value = {
      timeout: Number(rc?.executionTimeout ?? 30000),
      retryCount: Number(rc?.retryCount ?? 0),
      conditionExpression: String(rc?.conditionExpression ?? ''),
    }
    await loadStepVariables()
  } else {
    formName.value = ''
    formStepType.value = 'http'
    formMethod.value = 'GET'
    formUrl.value = ''
    formEnabled.value = true
    reqHeaders.value = []
    reqParams.value = []
    reqBody.value = { type: 'none', content: null }
    reqTimeout.value = 30000
    validators.value = []
    extractors.value = []
    stepVariables.value = []
    executionConfig.value = createExecutionConfig()
    createMode.value = 'manual'
  }
})

async function loadStepVariables() {
  if (!props.sceneId || !props.step) return
  variablesLoading.value = true
  try {
    stepVariables.value = await fetchStepVariables(props.sceneId, props.step.id)
  } catch {
    stepVariables.value = []
  } finally {
    variablesLoading.value = false
  }
}

// ==================== 接口选择 ====================
async function loadInterfaces() {
  interfaceLoading.value = true
  try {
    const resp = await fetchInterfacePage({ pageNo: 1, pageSize: 50, search: interfaceSearch.value || undefined })
    interfaceOptions.value = resp.list
  } catch {
    interfaceOptions.value = []
  } finally {
    interfaceLoading.value = false
  }
}

function handleCreateModeChange(mode: 'manual' | 'quick' | 'public') {
  createMode.value = mode
  if (mode === 'quick' && interfaceOptions.value.length === 0) void loadInterfaces()
}

// ==================== 变量操作 ====================
function addStepVariable() { stepVariables.value.push(createStepVariable()) }
function removeStepVariable(i: number) { stepVariables.value.splice(i, 1) }

// ==================== 断言操作 ====================
function addValidator() { validators.value.push(createValidator()) }
function removeValidator(i: number) { validators.value.splice(i, 1) }

// ==================== 提取器操作 ====================
function addExtractor() { extractors.value.push(createExtractor()) }
function removeExtractor(i: number) { extractors.value.splice(i, 1) }

// ==================== 公共步骤浏览 ====================
async function openBrowse() {
  browseVisible.value = true
  await loadBrowse()
}

async function loadBrowse() {
  if (!props.sceneId) return
  browseLoading.value = true
  try {
    browseList.value = await browsePublicSteps(props.sceneId)
  } catch {
    browseList.value = []
  } finally {
    browseLoading.value = false
  }
}

const filteredBrowseList = computed(() => {
  if (!browseSearch.value.trim()) return browseList.value
  const q = browseSearch.value.trim().toLowerCase()
  return browseList.value.filter(item => item.name.toLowerCase().includes(q) || item.interfaceName.toLowerCase().includes(q))
})

function selectPublicStep(item: ApiPublicStepBrowseItem) {
  publicStepId.value = item.id
  browseVisible.value = false
}

// ==================== 保存 ====================
const saving = ref(false)

async function handleSave() {
  if (!props.sceneId) return
  saving.value = true
  try {
    if (createMode.value === 'quick') {
      if (!quickInterfaceId.value) { ElMessage.warning('请选择接口'); return }
      await quickCreateSteps(props.sceneId, { interfaceId: quickInterfaceId.value, mode: quickMode.value })
      ElMessage.success('步骤已创建')
    } else if (createMode.value === 'public') {
      if (!publicStepId.value) { ElMessage.warning('请填写公共步骤 ID'); return }
      await addPublicStep(props.sceneId, { publicStepId: publicStepId.value, mode: publicMode.value })
      ElMessage.success('公共步骤已添加')
    } else {
      if (!formName.value.trim()) { ElMessage.warning('请填写步骤名称'); return }
      const requestConfig: Record<string, unknown> = {
        method: formMethod.value,
        url: formUrl.value,
        headers: reqHeaders.value.filter((h) => h.key.trim()),
        params: reqParams.value.filter((p) => p.key.trim()),
        body: reqBody.value,
        timeout: reqTimeout.value,
        executionTimeout: executionConfig.value.timeout,
        retryCount: executionConfig.value.retryCount,
        conditionExpression: executionConfig.value.conditionExpression,
      }
      const payload: ApiSceneStepSaveReq = {
        name: formName.value.trim(),
        stepType: formStepType.value,
        enabled: formEnabled.value,
        requestConfig,
        validators: serializeValidators(validators.value),
        extractors: serializeExtractors(extractors.value),
      }
      if (props.step) {
        await updateSceneStep(props.sceneId, props.step.id, payload)
        ElMessage.success('步骤已更新')
        if (stepVariables.value.length > 0) {
          await updateStepVariables(props.sceneId, props.step.id, { variables: stepVariables.value.filter((v) => v.name.trim()) })
        }
      } else {
        await createSceneStep(props.sceneId, { ...payload, sourceType: 'custom' })
        ElMessage.success('步骤已创建')
      }
    }
    emit('saved')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="step ? '编辑步骤' : '添加步骤'"
    size="680px"
    data-test="step-editor-drawer"
  >
    <!-- 创建模式切换（仅新建时显示） -->
    <div v-if="!step" class="step-editor__mode-switch">
      <el-radio-group :model-value="createMode" @update:model-value="(v) => handleCreateModeChange(v as 'manual' | 'quick' | 'public')">
        <el-radio-button value="manual">手动创建</el-radio-button>
        <el-radio-button value="quick">通过接口快速创建</el-radio-button>
        <el-radio-button value="public">添加公共步骤</el-radio-button>
      </el-radio-group>
    </div>

    <!-- ==================== 手动创建 / 编辑：标签页 ==================== -->
    <template v-if="createMode === 'manual'">
      <el-tabs v-model="activeTab" type="border-card" class="step-editor__tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-form label-position="top">
            <el-form-item label="步骤名称" required>
              <el-input v-model="formName" placeholder="如：发送登录请求" data-test="step-name" />
            </el-form-item>
            <el-form-item label="步骤类型">
              <el-select v-model="formStepType" style="width: 100%">
                <el-option v-for="opt in STEP_TYPE_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="formEnabled" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 请求配置 -->
        <el-tab-pane label="请求配置" name="request">
          <RequestConfigEditor
            :method="formMethod"
            :url="formUrl"
            :headers="reqHeaders"
            :params="reqParams"
            :body="reqBody"
            :timeout="reqTimeout"
            @update:method="(v: string) => { formMethod = v }"
            @update:url="(v: string) => { formUrl = v }"
            @update:headers="(v: typeof reqHeaders) => { reqHeaders = v }"
            @update:params="(v: typeof reqParams) => { reqParams = v }"
            @update:body="(v: typeof reqBody) => { reqBody = v }"
            @update:timeout="(v: number) => { reqTimeout = v }"
          />
        </el-tab-pane>

        <!-- 断言 -->
        <el-tab-pane label="断言" name="validators">
          <div class="step-editor__list-section">
            <div v-for="(v, i) in validators" :key="v.id" class="step-editor__validator-card">
              <div class="step-editor__card-top">
                <el-switch v-model="v.enabled" size="small" />
                <el-input v-model="v.name" size="small" placeholder="断言名称" style="flex:1" />
                <el-button link size="small" type="danger" @click="removeValidator(i)">删除</el-button>
              </div>
              <div class="step-editor__card-fields">
                <el-select v-model="v.target" size="small" style="width:130px" placeholder="目标">
                  <el-option v-for="t in VALIDATOR_TARGETS" :key="t.value" :value="t.value" :label="t.label" />
                </el-select>
                <el-select v-model="v.condition" size="small" style="width:130px" placeholder="条件">
                  <el-option v-for="c in VALIDATOR_CONDITIONS" :key="c.value" :value="c.value" :label="c.label" />
                </el-select>
                <el-input v-model="v.expression" size="small" placeholder="表达式（如 $.code）" style="flex:1" />
                <el-input v-model="v.expected" size="small" placeholder="期望值" style="flex:1" />
              </div>
            </div>
            <el-button size="small" @click="addValidator">+ 添加断言</el-button>
          </div>
        </el-tab-pane>

        <!-- 提取器 -->
        <el-tab-pane label="提取器" name="extractors">
          <div class="step-editor__list-section">
            <div v-for="(e, i) in extractors" :key="e.id" class="step-editor__validator-card">
              <div class="step-editor__card-top">
                <el-switch v-model="e.enabled" size="small" />
                <el-input v-model="e.name" size="small" placeholder="提取器名称" style="flex:1" />
                <el-button link size="small" type="danger" @click="removeExtractor(i)">删除</el-button>
              </div>
              <div class="step-editor__card-fields">
                <el-select v-model="e.source" size="small" style="width:130px" placeholder="来源">
                  <el-option v-for="s in EXTRACTOR_SOURCES" :key="s.value" :value="s.value" :label="s.label" />
                </el-select>
                <el-input v-model="e.expression" size="small" placeholder="表达式" style="flex:1" />
                <el-input v-model="e.variableName" size="small" placeholder="变量名" style="flex:1" />
              </div>
            </div>
            <el-button size="small" @click="addExtractor">+ 添加提取器</el-button>
          </div>
        </el-tab-pane>

        <!-- 步骤变量 -->
        <el-tab-pane v-if="step" label="变量" name="variables">
          <div v-loading="variablesLoading" class="step-editor__list-section">
            <table v-if="stepVariables.length" class="step-editor__kv-table">
              <thead>
                <tr><th>变量名</th><th>值</th><th>来源</th><th>描述</th><th style="width:40px"></th></tr>
              </thead>
              <tbody>
                <tr v-for="(sv, i) in stepVariables" :key="sv.id">
                  <td><el-input v-model="sv.name" size="small" placeholder="变量名" /></td>
                  <td><el-input v-model="sv.value" size="small" placeholder="值（支持 ${} 引用）" /></td>
                  <td><el-tag size="small" :type="sv.source === 'interface' ? 'warning' : 'info'">{{ sv.source === 'interface' ? '接口' : '自定义' }}</el-tag></td>
                  <td><el-input v-model="sv.description" size="small" placeholder="描述" /></td>
                  <td><el-button link size="small" type="danger" @click="removeStepVariable(i)">✕</el-button></td>
                </tr>
              </tbody>
            </table>
            <div v-else class="step-editor__empty-text">暂无变量</div>
            <el-button size="small" @click="addStepVariable">+ 添加变量</el-button>
          </div>
        </el-tab-pane>

        <!-- 执行配置 -->
        <el-tab-pane label="执行配置" name="execution">
          <el-form label-position="top">
            <el-form-item label="超时时间（毫秒）">
              <el-input-number v-model="executionConfig.timeout" :min="1000" :max="300000" :step="1000" />
            </el-form-item>
            <el-form-item label="重试次数">
              <el-input-number v-model="executionConfig.retryCount" :min="0" :max="5" />
            </el-form-item>
            <el-form-item label="条件表达式（为空则始终执行）">
              <el-input v-model="executionConfig.conditionExpression" type="textarea" :rows="3" placeholder="如：${status} == 'success'" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- ==================== 通过接口快速创建 ==================== -->
    <template v-if="createMode === 'quick'">
      <el-form label-position="top">
        <el-form-item label="选择接口" required>
          <el-select
            v-model="quickInterfaceId"
            filterable
            remote
            :remote-method="(q: string) => { interfaceSearch = q; loadInterfaces() }"
            :loading="interfaceLoading"
            placeholder="搜索接口名称"
            style="width: 100%"
          >
            <el-option
              v-for="item in interfaceOptions"
              :key="item.id"
              :value="item.id"
              :label="`${item.method} ${item.path} - ${item.name}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="同步模式">
          <el-radio-group v-model="quickMode">
            <el-radio value="copy">复制（独立副本）</el-radio>
            <el-radio value="link">链接（跟随源变更）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </template>

    <!-- ==================== 添加公共步骤 ==================== -->
    <template v-if="createMode === 'public'">
      <el-form label-position="top">
        <el-form-item label="公共步骤" required>
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="publicStepId" placeholder="选择公共步骤" readonly style="flex: 1" />
            <el-button @click="openBrowse">浏览</el-button>
          </div>
        </el-form-item>
        <el-form-item label="同步模式">
          <el-radio-group v-model="publicMode">
            <el-radio value="copy">复制</el-radio>
            <el-radio value="link">链接</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" data-test="step-save-btn" @click="handleSave">保存</el-button>
    </template>
  </el-drawer>

  <!-- 公共步骤浏览弹窗 -->
  <el-dialog v-model="browseVisible" title="选择公共步骤" width="640px" destroy-on-close>
    <el-input v-model="browseSearch" placeholder="搜索步骤或接口名称" clearable style="margin-bottom: 12px" />
    <el-table v-loading="browseLoading" :data="filteredBrowseList" height="400" @row-click="selectPublicStep">
      <el-table-column prop="name" label="步骤名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="interfaceName" label="所属接口" min-width="140" show-overflow-tooltip />
      <el-table-column prop="method" label="方法" width="80" align="center" />
      <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
      <template #empty>
        <el-empty description="当前场景未关联接口，无公共步骤可选" />
      </template>
    </el-table>
    <template #footer>
      <el-button @click="browseVisible = false">取消</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.step-editor__mode-switch {
  margin-bottom: var(--space-lg);
}

.step-editor__tabs {
  :deep(.el-tabs__content) {
    padding: var(--space-md);
    max-height: calc(100vh - 220px);
    overflow-y: auto;
  }
}

.step-editor__list-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.step-editor__validator-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
}

.step-editor__card-top {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}

.step-editor__card-fields {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.step-editor__empty-text {
  padding: var(--space-md);
  text-align: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

.step-editor__kv-table {
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
</style>
