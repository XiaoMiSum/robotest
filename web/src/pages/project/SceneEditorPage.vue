<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type ComponentPublicInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Collection, Plus, Rank } from '@element-plus/icons-vue'
import type { ApiSceneDetail, ApiSceneStepItem, ApiSceneStepDebugResp, ApiSceneVariableItem, ApiExecutionHistoryItem, ApiChangeHistoryItem, ApiEnvironmentListItem, ApiComponentListItem, ApiComponentType, ProjectModule, ApiDataSource, ApiHttpConfig, ApiVariable } from '@/types'
import type { CascaderOption } from 'element-plus'
import {
  createScene,
  fetchSceneDetail,
  updateScene,
  deleteScene,
  debugStep,
  executeScene,
  executeDraftScene,
  fetchExecutionHistory,
  fetchChangeHistory,
  deleteSceneStep,
  reorderSceneSteps,
  updateSceneStep,
  copySceneStep,
} from '@/services/apiScene'
import { fetchEnvironmentDetail, fetchEnvironments } from '@/services/apiEnvironment'
import { fetchComponents } from '@/services/apiComponent'
import ProcessorForm from '@/components/api-testing/ProcessorForm.vue'
import ExtractorAssetPicker from '@/components/api-testing/ExtractorAssetPicker.vue'
import { extractorsFromComponents, processorFromComponent } from '@/components/api-testing/processorFormModel'
import type { ProcessorExtractor } from '@/components/api-testing/processorFormModel'
import { formatDateTime } from '@/utils/format'
import { sortedSteps, emptyStepDraft } from './scenesModel'
import { fetchProjectModuleTree } from '@/services/project'
import { toSelectableModuleOptions } from './interfacesModel'
import StepCanvas from './scenes/StepCanvas.vue'
import SceneStepInlineEditor from './scenes/SceneStepInlineEditor.vue'
import InterfacePickerDialog from './scenes/InterfacePickerDialog.vue'
import StepDebugResultDialog from './scenes/StepDebugResultDialog.vue'
import SceneVariableHelperDialog from './scenes/SceneVariableHelperDialog.vue'
import FunctionHelperDialog from './FunctionHelperDialog.vue'

const props = defineProps<{ sceneId?: string; createMode?: boolean; moduleId?: string }>()
const emit = defineEmits<{ (e: 'back'): void; (e: 'edit', id: string): void; (e: 'title-update', name: string): void; (e: 'dirty-change', dirty: boolean): void }>()

// ==================== 场景数据 ====================
const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const detail = ref<ApiSceneDetail | null>(null)
const dirty = ref(false)
const sceneSection = ref<'steps' | 'variables' | 'pre' | 'post'>('steps')

const autoSaveLabel = ref('')
let autosaveTimer: ReturnType<typeof setTimeout> | null = null
function bumpAutosave() {
  autoSaveLabel.value = `自动保存已开启 ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => { autoSaveLabel.value = '' }, 5000)
}

// ==================== 左右分栏（可拖拽分割条，步骤/处理器各一套独立状态） ====================
function createSplit(initial = 0.42) {
  const ratio = ref(initial)
  const dragging = ref(false)
  let container: HTMLElement | null = null
  // :ref 回调签名须兼容 Vue 的 VNodeRef（Element | ComponentPublicInstance | null）
  const register = (el: Element | ComponentPublicInstance | null): void => {
    container = (el as HTMLElement | null)
  }
  // 分割条按下即标记拖拽，由 watch 挂载全局监听与拖拽光标
  const start = () => { dragging.value = true }
  const move = (e: MouseEvent) => {
    if (!dragging.value || !container) return
    const rect = container.getBoundingClientRect()
    ratio.value = Math.min(0.6, Math.max(0.28, (e.clientX - rect.left) / rect.width))
  }
  const end = () => { dragging.value = false }
  watch(dragging, (val) => {
    document.body.style.cursor = val ? 'col-resize' : ''
    document.body.style.userSelect = val ? 'none' : ''
    if (val) {
      window.addEventListener('mousemove', move)
      window.addEventListener('mouseup', end)
    } else {
      window.removeEventListener('mousemove', move)
      window.removeEventListener('mouseup', end)
    }
  })
  onUnmounted(() => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', end)
  })
  return { ratio, dragging, register, start }
}

const stepSplit = createSplit(0.42)
// el-tabs 默认渲染所有 pane（lazy=false），pre/post 会同时挂载，故各自独立 split 实例避免容器 ref 互抢
const preSplit = createSplit(0.42)
const postSplit = createSplit(0.42)

// ==================== 场景基础信息（顶部导航内联 + 设置弹层） ====================
const editName = ref('')
// why: 描述默认折叠，避免占满顶栏；点 Memo 图标展开编辑
const showDescription = ref(false)
const editDescription = ref('')
const editModuleId = ref<string | null>(null)
const editEnvironmentId = ref<string | null>(null)
const editPriority = ref<string | null>('P2')
// 场景状态：draft（草稿）/ published（已发布），缺省草稿
const editStatus = ref<string>('draft')

const SCENE_PRIORITY_OPTIONS = [
  { value: 'P0', label: 'P0', color: 'var(--color-priority-p0)' },
  { value: 'P1', label: 'P1', color: 'var(--color-priority-p1)' },
  { value: 'P2', label: 'P2', color: 'var(--color-priority-p2)' },
  { value: 'P3', label: 'P3', color: 'var(--color-priority-p3)' },
] as const

const moduleTree = ref<ProjectModule[]>([])
const moduleOptions = computed<CascaderOption[]>(() =>
  toSelectableModuleOptions(moduleTree.value) as CascaderOption[],
)

async function loadModules() {
  try {
    moduleTree.value = await fetchProjectModuleTree('scene')
  } catch {
    moduleTree.value = []
  }
}

const environmentOptions = ref<ApiEnvironmentListItem[]>([])

async function loadEnvironments() {
  try {
    environmentOptions.value = await fetchEnvironments()
    if (isCreateMode.value && editEnvironmentId.value == null) {
      const def = environmentOptions.value.find((env) => env.isDefault)
      if (def) editEnvironmentId.value = def.id
    }
  } catch {
    environmentOptions.value = []
  }
}

watch([editName, editDescription, editEnvironmentId, editPriority], () => {
  dirty.value = true
  emit('dirty-change', true)
})

watch(editName, (name) => emit('title-update', name.trim() || '新场景'))

// ==================== 加载场景 ====================
async function loadDetail() {
  if (!props.sceneId) return
  loading.value = true
  try {
    detail.value = await fetchSceneDetail(props.sceneId)
    editName.value = detail.value.name
    editDescription.value = detail.value.description ?? ''
    editModuleId.value = detail.value.moduleId ?? null
    editEnvironmentId.value = detail.value.environmentId ?? null
    editPriority.value = detail.value.priority ?? null
    editStatus.value = detail.value.status ?? 'draft'
    dirty.value = false
    emit('title-update', detail.value.name)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '场景加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 保存场景 ====================
/** 保存场景；status 传值时以该状态落库（保存为草稿/发布），缺省沿用当前状态（如运行前自动保存） */
async function handleSave(status?: string): Promise<boolean> {
  if (status) editStatus.value = status
  if (!editName.value.trim()) { ElMessage.warning('请填写场景名称'); return false }
  const moduleId = editModuleId.value ?? props.moduleId
  if (!moduleId) { ElMessage.warning('请选择所属模块'); return false }
  saving.value = true
  try {
    if (props.sceneId && detail.value) {
      await updateScene(props.sceneId, {
        name: editName.value.trim(),
        description: editDescription.value.trim() || undefined,
        moduleId: editModuleId.value,
        environmentId: editEnvironmentId.value,
        priority: editPriority.value,
        status: editStatus.value,
        variables: detail.value.variables,
        processors: editProcessors.value,
        steps: detail.value.steps
          .slice()
          .sort((a, b) => a.sortOrder - b.sortOrder)
          .map((s) => ({
            // new- 临时 id 未落库，不传 id 让后端新建；已存在步骤带 id 走局部更新
            id: s.id.startsWith('new-') ? undefined : s.id,
            name: s.name,
            stepType: s.stepType,
            enabled: s.enabled,
            sourceType: s.sourceType,
            sourceId: s.sourceId ?? null,
            requestConfig: s.requestConfig,
            processors: s.processors,
            validators: s.validators,
            extractors: s.extractors,
            sortOrder: s.sortOrder,
          })),
        changeVersion: detail.value.changeVersion,
      })
      ElMessage.success('已保存')
      await loadDetail()
      return true
    } else {
      await createScene({
        name: editName.value.trim(),
        description: editDescription.value.trim() || undefined,
        moduleId: editModuleId.value ?? props.moduleId,
        environmentId: editEnvironmentId.value,
        priority: editPriority.value,
        status: editStatus.value,
        variables: editVariables.value.filter((v) => v.name.trim()),
        processors: editProcessors.value,
        steps: draftSteps.value.map((s) => ({
          name: s.name,
          stepType: s.stepType,
          enabled: s.enabled,
          sourceType: s.sourceType,
          sourceId: s.sourceId,
          requestConfig: s.requestConfig,
          processors: s.processors,
          validators: s.validators,
          extractors: s.extractors,
        })),
      })
      ElMessage.success('已创建')
      emit('back')
      return true
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
    return false
  } finally {
    saving.value = false
  }
}

// ==================== 场景删除 ====================
async function handleDeleteScene() {
  if (!props.sceneId) return
  try {
    await ElMessageBox.confirm('删除场景后不可恢复，确定删除？', '删除场景', { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' })
  } catch { return }
  try {
    await deleteScene(props.sceneId)
    ElMessage.success('已删除')
    emit('back')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

// ==================== 步骤操作 ====================
const showInterfacePicker = ref(false)
const selectedStep = ref<ApiSceneStepItem | null>(null)

/** 构造一个未命名的新步骤（新增后立即内联编辑，故使用临时 id 标记） */
function createDefaultStep(): ApiSceneStepItem {
  const draft = emptyStepDraft()
  return {
    id: `new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    name: '',
    stepType: draft.stepType,
    sortOrder: 0,
    enabled: true,
    sourceType: 'manual',
    sourceId: null,
    requestConfig: draft.requestConfig,
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
  }
}

/** 编辑态：直接追加未命名步骤并选中，右侧内联编辑 */
function handleAddStep() {
  const step = createDefaultStep()
  step.sortOrder = (detail.value?.steps.length ?? 0) + 1
  detail.value?.steps.push(step)
  selectedStep.value = step
}

/** 从已有接口添加步骤：打开接口选择器，选中后客户端填充进步骤列表（保存场景时随 updateScene 一并落库） */
function handleQuickAddStep() { showInterfacePicker.value = true }

/** 接口选择器选中：构造步骤追加到当前列表并选中，不请求后端 */
function handleInterfaceSelected(step: ApiSceneStepItem) {
  const list = isCreateMode.value ? draftSteps.value : (detail.value?.steps ?? [])
  step.sortOrder = list.length + 1
  list.push(step)
  if (!isCreateMode.value && detail.value) detail.value.steps = list
  selectedStep.value = step
  showInterfacePicker.value = false
  bumpAutosave()
}

/** 步骤关键明细是否完整：名称必填；HTTP 需路径，JDBC 需 SQL。不完整时禁止切换到其他步骤 */
function isStepDetailIncomplete(step: ApiSceneStepItem): boolean {
  if (!step.name.trim()) return true
  const cfg = step.requestConfig && typeof step.requestConfig === 'object' ? step.requestConfig : {}
  const value = cfg as Record<string, unknown>
  if (step.stepType === 'jdbc') return !String(value.sql ?? '').trim()
  return !String(value.url ?? '').trim()
}

function handleSelectStep(step: ApiSceneStepItem) {
  const current = selectedStep.value
  if (current && current.id !== step.id && isStepDetailIncomplete(current)) {
    ElMessage.warning('请先填写当前步骤的名称 / HTTP 路径 / SQL 语句')
    return
  }
  selectedStep.value = step
}

function handleDeleteStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  ElMessageBox.confirm(`删除步骤「${step.name}」？`, '删除步骤', { type: 'warning' })
    .then(async () => {
      // 新添加未落库的步骤（new- 前缀）仅本地移除，避免以无效 id 请求后端
      if (step.id.startsWith('new-')) {
        const list = detail.value?.steps ?? []
        const idx = list.findIndex((s) => s.id === step.id)
        if (idx >= 0) list.splice(idx, 1)
        if (selectedStep.value?.id === step.id) selectedStep.value = null
        return
      }
      await deleteSceneStep(props.sceneId ?? '', step.id)
      ElMessage.success('步骤已删除')
      if (selectedStep.value?.id === step.id) selectedStep.value = null
      await loadDetail()
    }).catch(() => {})
}

async function handleToggleStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  try {
    await updateSceneStep(props.sceneId, step.id, {
      name: step.name,
      stepType: step.stepType,
      enabled: !step.enabled,
      requestConfig: step.requestConfig,
      processors: step.processors,
      validators: step.validators,
      extractors: step.extractors,
      sourceType: step.sourceType,
      sourceId: step.sourceId,
    })
    step.enabled = !step.enabled
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleReorderSteps(newSteps: ApiSceneStepItem[]) {
  if (!props.sceneId) return
  try {
    await reorderSceneSteps(props.sceneId, { stepIds: newSteps.map((s) => s.id) })
    if (detail.value) detail.value.steps = newSteps
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '排序失败')
  }
}

async function handleCopyStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  try {
    await copySceneStep(props.sceneId, step.id)
    ElMessage.success('已复制步骤')
    await loadDetail()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}

// ==================== 创建态草稿步骤 ====================
const draftSteps = ref<ApiSceneStepItem[]>([])

function handleDraftAddStep() {
  const step = createDefaultStep()
  step.sortOrder = draftSteps.value.length + 1
  draftSteps.value.push(step)
  selectedStep.value = step
}
function handleDraftDeleteStep(step: ApiSceneStepItem) {
  const idx = draftSteps.value.findIndex((s) => s.id === step.id)
  if (idx >= 0) draftSteps.value.splice(idx, 1)
  if (selectedStep.value?.id === step.id) selectedStep.value = null
}
function handleDraftToggleStep(step: ApiSceneStepItem) { step.enabled = !step.enabled; bumpAutosave() }
function handleDraftReorderSteps(newSteps: ApiSceneStepItem[]) {
  newSteps.forEach((s, i) => { s.sortOrder = i + 1 })
  draftSteps.value = newSteps
}
function handleDraftCopyStep(step: ApiSceneStepItem) {
  draftSteps.value.push({ ...step, id: `draft-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, sortOrder: draftSteps.value.length + 1 })
}

// ==================== 单步调试 ====================
const debugResult = ref<ApiSceneStepDebugResp | null>(null)
const showDebugResult = ref(false)
const debugStepId = ref<string | null>(null)

async function handleDebugStep(step: ApiSceneStepItem) {
  if (!props.sceneId) return
  debugStepId.value = step.id
  try {
    const resp = await debugStep(props.sceneId, step.id)
    debugResult.value = resp
    showDebugResult.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '调试失败')
  } finally {
    debugStepId.value = null
  }
}

function handleDraftDebugDisabled(_step: ApiSceneStepItem) { ElMessage.info('创建成功后可在编辑页单步调试') }

// ==================== 运行场景（编辑页 [运行]，3.6） ====================
async function handleRun() {
  if (isCreateMode.value) {
    if (draftSteps.value.length === 0) { ElMessage.warning('请先添加步骤'); return }
    running.value = true
    try {
      const resp = await executeDraftScene({
        name: editName.value.trim() || undefined,
        environmentId: editEnvironmentId.value,
        sceneVariables: editVariables.value.filter((v) => v.name.trim()),
        steps: draftSteps.value.map((s) => ({
          name: s.name,
          stepType: s.stepType,
          sourceType: s.sourceType,
          sourceId: s.sourceId,
          enabled: s.enabled,
          requestConfig: s.requestConfig,
          validators: s.validators,
          extractors: s.extractors,
          stepVariables: s.variables ?? [],
        })),
      })
      ElMessage.success(`草稿运行完成：通过 ${resp.passed} · 失败 ${resp.failed} · 跳过 ${resp.skipped}`)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '运行失败')
    } finally {
      running.value = false
    }
    return
  }
  if (!props.sceneId) return
  if (dirty.value) {
    const ok = await handleSave()
    if (!ok) return
  }
  running.value = true
  try {
    const resp = await executeScene(props.sceneId, { environmentId: editEnvironmentId.value })
    ElMessage.success(`场景已触发执行（${resp.executionId}）`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '运行失败')
  } finally {
    running.value = false
  }
}

// ==================== 变量管理 ====================
const editVariables = ref<{ name: string; value: string; description: string }[]>([])

watch(detail, (d) => {
  if (d) editVariables.value = d.variables.map((v: ApiSceneVariableItem) => ({ ...v, value: v.value ?? '', description: v.description ?? '' }))
})

function addVariable() { editVariables.value.push({ name: '', value: '', description: '' }) }
function removeVariable(index: number) { editVariables.value.splice(index, 1) }

// ==================== Tabs 头部：函数助手 / 变量助手 ====================

const showFunctionHelper = ref(false)

const showVariableHelper = ref(false)
const envVariables = ref<ApiVariable[]>([])
const envVariablesName = ref('')

/** 打开变量助手：展示场景关联环境（未选则取默认环境）的变量 + 场景变量（含未保存） */
async function openVariableHelper(): Promise<void> {
  envVariables.value = []
  let envId = editEnvironmentId.value
  if (!envId) {
    const def = environmentOptions.value.find((env) => env.isDefault)
    envId = def?.id ?? null
    envVariablesName.value = def?.name ?? ''
  } else {
    const env = environmentOptions.value.find((e) => e.id === envId)
    envVariablesName.value = env?.name ?? ''
  }
  if (envId) {
    try {
      const envDetail = await fetchEnvironmentDetail(envId)
      envVariables.value = envDetail.variables
    } catch {
      ElMessage.error('加载环境变量失败')
    }
  }
  showVariableHelper.value = true
}

// ==================== 处理器 ref 下拉：取场景关联环境的 http/数据源 ====================

const httpRefOptions = ref<ApiHttpConfig[]>([])
const dsRefOptions = ref<ApiDataSource[]>([])

/** 按场景关联环境加载 http 配置与数据源，供处理器 ref 下拉使用；未选环境时清空 */
async function loadSceneRefOptions(environmentId: string | null | undefined): Promise<void> {
  if (!environmentId) {
    httpRefOptions.value = []
    dsRefOptions.value = []
    return
  }
  try {
    const envDetail = await fetchEnvironmentDetail(environmentId)
    httpRefOptions.value = envDetail.httpConfigs
    dsRefOptions.value = envDetail.dataSources
  } catch (err) {
    httpRefOptions.value = []
    dsRefOptions.value = []
    ElMessage.error(err instanceof Error ? err.message : '加载环境配置失败')
  }
}

watch(() => detail.value?.environmentId, (id) => { void loadSceneRefOptions(id) })

// ==================== 场景处理器 ====================
/** 处理器元素（Ryze 结构 + 场景级 name/type），直接作为 JSONB 片段随场景保存 */
type SceneProcessorElement = Record<string, unknown> & {
  name?: string
  enabled?: boolean
}

const editProcessors = ref<SceneProcessorElement[]>([] as SceneProcessorElement[])

watch(detail, (d) => {
  if (d?.processors) {
    editProcessors.value = d.processors.map((p) => ({ ...(p as SceneProcessorElement) }))
    const cur = selectedProcessorIdx.value
    if (cur === null || cur >= editProcessors.value.length) selectedProcessorIdx.value = null
  }
})

/** 前置/后置处理器在 editProcessors 扁平数组中的下标（缺省 type 视为前置） */
function processorIndexes(type: 'pre' | 'post'): number[] {
  return editProcessors.value
    .map((p, i) => ((p.type === 'post') === (type === 'post') ? i : -1))
    .filter((i) => i >= 0)
}

function addProcessor(type: 'pre' | 'post') {
  editProcessors.value.push({ type, name: '', enabled: true, testclass: '', config: {}, extractors: [] })
  selectedProcessorIdx.value = editProcessors.value.length - 1
}

function removeProcessor(type: 'pre' | 'post', position: number) {
  const idx = processorIndexes(type)[position]
  if (idx >= 0) {
    editProcessors.value.splice(idx, 1)
    if (selectedProcessorIdx.value === idx) selectedProcessorIdx.value = null
  }
}

function updateProcessor(idx: number, value: Record<string, unknown>) {
  editProcessors.value[idx] = value as SceneProcessorElement
}

/** 处理器类型 → 摘要标签（ProcessorForm 的 http/jdbc） */
function processorTypeLabel(testclass: string): string {
  return testclass === 'jdbc' ? 'SQL' : testclass === 'http' ? 'HTTP' : '待配置'
}

/** 左列表展示名：无名称时回退「处理器 N」 */
function procDisplayName(idx: number): string {
  const name = (editProcessors.value[idx] as SceneProcessorElement).name
  return name && name.trim() ? name : `处理器 ${idx + 1}`
}

/** 右侧明细当前选中处理器（editProcessors 全局下标） */
const selectedProcessorIdx = ref<number | null>(null)

function selectProcessor(idx: number) {
  selectedProcessorIdx.value = idx
}

// 切换场景部分时，把选中处理器对齐到当前类型的第一个，避免跨类型失配
watch(sceneSection, (section) => {
  if (section !== 'pre' && section !== 'post') return
  const list = processorIndexes(section)
  const cur = selectedProcessorIdx.value
  if (cur === null || !list.includes(cur)) {
    selectedProcessorIdx.value = list.length > 0 ? list[0] : null
  }
})

/** 删除右侧明细当前选中的处理器（type 为当前查看的处理器类型） */
function removeSelectedProcessor(type: 'pre' | 'post') {
  const pos = processorIndexes(type).indexOf(selectedProcessorIdx.value ?? -1)
  if (pos >= 0) removeProcessor(type, pos)
}

/** 处理器上移/下移（JSONB 数组顺序即执行顺序，直接交换元素） */
function moveProcessor(type: 'pre' | 'post', position: number, dir: -1 | 1) {
  const list = processorIndexes(type)
  const from = list[position]
  const to = list[position + dir]
  if (from === undefined || to === undefined) return
  const arr = editProcessors.value
  ;[arr[from], arr[to]] = [arr[to], arr[from]]
  selectedProcessorIdx.value = to
}

// ==================== 处理器拖拽排序（对齐步骤 StepCanvas 的 DnD） ====================
const procDrag = ref<{ type: 'pre' | 'post'; flat: number } | null>(null)

function procOnDragStart(type: 'pre' | 'post', flat: number, e: DragEvent) {
  procDrag.value = { type, flat }
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

function procOnDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}

/** 把拖拽中的处理器移动到 type 列表的 position 位置（position 为放入点，0..length） */
function procOnDrop(type: 'pre' | 'post', position: number) {
  const drag = procDrag.value
  procDrag.value = null
  if (!drag || drag.type !== type) return
  const list = processorIndexes(type)
  const fromPos = list.indexOf(drag.flat)
  if (fromPos === -1 || fromPos === position) return
  const arr = editProcessors.value
  const moved = arr[drag.flat]
  arr.splice(drag.flat, 1)
  const newList = processorIndexes(type)
  const insertAt = position >= newList.length ? newList[newList.length - 1] + 1 : newList[position]
  arr.splice(insertAt, 0, moved)
  selectedProcessorIdx.value = arr.indexOf(moved)
}

function copyProcessor(idx: number) {
  const copy = JSON.parse(JSON.stringify(editProcessors.value[idx])) as SceneProcessorElement
  editProcessors.value.splice(idx + 1, 0, copy)
  selectedProcessorIdx.value = idx + 1
}

function procTestclass(idx: number): string {
  const v = editProcessors.value[idx]?.testclass
  return typeof v === 'string' ? v : ''
}

// ==================== 处理器 / 提取器：从公共组件引入 ====================

type AssetKind = 'pre' | 'post' | 'extractor'

const ASSET_TYPE: Record<AssetKind, ApiComponentType> = {
  pre: 'preprocessor',
  post: 'postprocessor',
  extractor: 'extractor',
}

const ASSET_TITLE: Record<AssetKind, string> = {
  pre: '从公共组件引入前置处理器',
  post: '从公共组件引入后置处理器',
  extractor: '从公共组件引入提取器',
}

const ASSET_NAME: Record<AssetKind, string> = {
  pre: '处理器',
  post: '处理器',
  extractor: '提取器',
}

const assetPickerVisible = ref(false)
const assetPickerLoading = ref(false)
const assetPickerItems = ref<ApiComponentListItem[]>([])
const assetPickerKeyword = ref('')
const assetPickerKind = ref<AssetKind>('pre')
/** 提取器引入目标处理器下标；处理器引入场景根时为空 */
const assetTargetIdx = ref<number | null>(null)

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
    ElMessage.error('加载公共组件失败')
  } finally {
    assetPickerLoading.value = false
  }
}

function openAssetPicker(kind: AssetKind) {
  assetPickerKind.value = kind
  assetPickerKeyword.value = ''
  assetTargetIdx.value = null
  assetPickerVisible.value = true
  void loadAssetPicker()
}

/** 打开针对某条处理器的提取器引入（ProcessorForm 的从公共组件获取） */
function openExtractorPickerForProcessor(idx: number) {
  assetPickerKind.value = 'extractor'
  assetPickerKeyword.value = ''
  assetTargetIdx.value = idx
  assetPickerVisible.value = true
  void loadAssetPicker()
}

function handleAssetPicked(rows: ApiComponentListItem[]) {
  if (rows.length === 0) return
  const kind = assetPickerKind.value
  if (kind === 'extractor') {
    const idx = assetTargetIdx.value
    if (idx === null || !editProcessors.value[idx]) return
    const incoming = extractorsFromComponents(rows)
    const existing = Array.isArray(editProcessors.value[idx].extractors)
      ? editProcessors.value[idx].extractors as ProcessorExtractor[]
      : []
    editProcessors.value[idx] = {
      ...editProcessors.value[idx],
      extractors: [...existing, ...incoming],
    }
  } else {
    rows.forEach((r) => editProcessors.value.push(processorFromComponent(r, kind) as SceneProcessorElement))
  }
  ElMessage.success(`已引入 ${rows.length} 个${ASSET_NAME[kind]}`)
}

// ==================== 历史 ====================
const executionHistory = ref<ApiExecutionHistoryItem[]>([])
const executionHistoryTotal = ref(0)
const executionHistoryPage = ref(1)
const changeHistory = ref<ApiChangeHistoryItem[]>([])
const changeHistoryTotal = ref(0)
const changeHistoryPage = ref(1)
const historyLoading = ref(false)

async function loadHistory() {
  if (!props.sceneId) return
  historyLoading.value = true
  try {
    const [execResp, changeResp] = await Promise.all([
      fetchExecutionHistory(props.sceneId, executionHistoryPage.value, 20),
      fetchChangeHistory(props.sceneId, changeHistoryPage.value, 20),
    ])
    executionHistory.value = execResp.list
    executionHistoryTotal.value = execResp.total
    changeHistory.value = changeResp.list
    changeHistoryTotal.value = changeResp.total
  } catch { /* 静默 */ } finally { historyLoading.value = false }
}

const showHistory = ref(false)

watch(showHistory, (v) => { if (v) void loadHistory() })

function handleViewReport(reportId: string) {
  emit('edit', `report:${reportId}`)
}

// ==================== 计算属性 ====================
const sorted = computed(() => (detail.value ? sortedSteps(detail.value.steps) : []))
const isCreateMode = computed(() => props.createMode || !props.sceneId)
const currentPriorityColor = computed(() =>
  SCENE_PRIORITY_OPTIONS.find((o) => o.value === editPriority.value)?.color,
)

onMounted(async () => {
  void loadModules()
  void loadEnvironments()
  if (props.sceneId) await loadDetail()
})
</script>

<template>
  <div v-loading="loading" class="scene-editor">
    <!-- ==================== 顶部导航栏 ==================== -->
    <header v-if="detail || isCreateMode" class="scene-editor__nav">
      <!-- 场景基础信息（内联直接修改） -->
      <div class="scene-editor__nav-field">
        <span class="scene-editor__required">*</span>
        <el-input
          v-model="editName"
          class="scene-editor__nav-name-input"
          placeholder="场景名称"
          size="small"
          maxlength="100"
        />
      </div>

      <div class="scene-editor__nav-field">
        <span class="scene-editor__required">*</span>
        <el-cascader
          v-model="editModuleId"
          :options="moduleOptions"
          :props="{ checkStrictly: true, emitPath: false, value: 'value', label: 'label' }"
          class="scene-editor__nav-module"
          placeholder="所属模块"
          size="small"
          clearable
        />
      </div>

      <span
        class="scene-editor__status-tag"
        :class="editStatus === 'published' ? 'is-published' : 'is-draft'"
      >
        {{ editStatus === 'published' ? '已发布' : '草稿' }}
      </span>

      <el-popover placement="bottom-start" :width="120" trigger="click">
        <template #reference>
          <button
            class="scene-editor__priority-tag"
            :style="{ background: currentPriorityColor }"
            type="button"
          >
            {{ editPriority ?? '优先级' }}
          </button>
        </template>
        <div class="scene-editor__priority-menu">
          <button
            v-for="opt in SCENE_PRIORITY_OPTIONS"
            :key="opt.value"
            class="scene-editor__priority-option"
            :class="{ 'is-active': editPriority === opt.value }"
            type="button"
            @click="editPriority = opt.value"
          >
            <span class="scene-editor__priority-dot" :style="{ background: opt.color }" />
            {{ opt.label }}
            <span v-if="opt.value === 'P2' && !editPriority">（默认）</span>
          </button>
        </div>
      </el-popover>

      <el-tooltip content="场景描述" placement="bottom">
        <el-button link size="small" @click="showDescription = !showDescription">
          <el-icon><Memo /></el-icon>
        </el-button>
      </el-tooltip>

      <div v-if="!isCreateMode" style="display: inline-flex">
        <el-popover placement="bottom-start" :width="360" trigger="click">
          <template #reference>
            <el-button link size="small" @click="showHistory = true">
              执行历史
            </el-button>
          </template>
          <div v-loading="historyLoading" style="max-height: 360px; overflow-y: auto">
            <div class="scene-editor__settings-section">
              <div class="scene-editor__settings-head">执行历史</div>
              <div v-for="h in executionHistory" :key="h.id" class="scene-editor__history-row">
                <span class="scene-editor__history-status" :class="`is-${h.status}`">{{ h.status }}</span>
                <span>{{ formatDateTime(h.executedAt) }}</span>
                <el-button v-if="h.reportId" link size="small" type="primary" @click="handleViewReport(h.reportId)">报告</el-button>
              </div>
              <div v-if="!executionHistory.length" class="scene-editor__empty-text">暂无执行记录</div>
            </div>
          </div>
        </el-popover>
      </div>

      <div class="scene-editor__nav-spacer" />

      <!-- 默认环境 -->
      <el-select v-model="editEnvironmentId" size="small" placeholder="默认环境" clearable style="width: 180px">
        <el-option v-for="env in environmentOptions" :key="env.id" :value="env.id" :label="env.name" />
      </el-select>

      <!-- 全局操作 -->
      <template v-if="isCreateMode">
        <el-button size="small" :loading="running" @click="handleRun">▶ 运行场景</el-button>
        <el-button size="small" @click="handleSave('draft')">保存为草稿</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave('published')">发布</el-button>
      </template>
      <template v-else>
        <el-button size="small" :loading="running" type="primary" plain @click="handleRun">▶ 运行场景</el-button>
        <el-button size="small" @click="handleSave('draft')">保存为草稿</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave('published')">发布</el-button>
        <el-button size="small" type="danger" plain @click="handleDeleteScene">删除</el-button>
      </template>

      <el-input
        v-if="showDescription"
        v-model="editDescription"
        type="textarea"
        :rows="2"
        class="scene-editor__nav-desc"
        placeholder="场景描述（可选）"
      />
    </header>

    <!-- ==================== 主体 ==================== -->
    <div v-if="detail || isCreateMode" class="scene-editor__body">
      <div class="scene-editor__tabs-wrap">
        <el-tabs v-model="sceneSection" class="scene-editor__left-tabs">
        <!-- 步骤 tab：左右双列（步骤列表 + 内联详情） -->
        <el-tab-pane label="步骤" name="steps">
          <div
            :ref="stepSplit.register"
            class="scene-editor__steps-layout"
            :class="{ 'is-dragging': stepSplit.dragging.value }"
          >
            <div class="scene-editor__steps-left" :style="{ flexBasis: `${stepSplit.ratio.value * 100}%` }">
              <div class="scene-editor__section-head">
                <span>{{ (isCreateMode ? draftSteps : sorted).length }} 个步骤</span>
                <div class="scene-editor__section-actions">
                  <el-button size="small" @click="handleQuickAddStep">从接口添加</el-button>
                  <el-button size="small" :type="isCreateMode ? 'primary' : 'default'" @click="isCreateMode ? handleDraftAddStep() : handleAddStep()">+ 添加步骤</el-button>
                </div>
              </div>
              <StepCanvas
                v-if="detail || isCreateMode"
                :steps="isCreateMode ? draftSteps : sorted"
                :selected-id="selectedStep?.id ?? null"
                :is-executing="false"
                @add="isCreateMode ? handleDraftAddStep() : handleAddStep()"
                @edit="handleSelectStep"
                @delete="(s) => isCreateMode ? handleDraftDeleteStep(s) : handleDeleteStep(s)"
                @toggle="(s) => isCreateMode ? handleDraftToggleStep(s) : handleToggleStep(s)"
                @reorder="(s) => isCreateMode ? handleDraftReorderSteps(s) : handleReorderSteps(s)"
                @copy="(s) => isCreateMode ? handleDraftCopyStep(s) : handleCopyStep(s)"
                @debug="(s) => isCreateMode ? handleDraftDebugDisabled(s) : handleDebugStep(s)"
                @insert-before="handleAddStep"
              />
            </div>

            <div
              class="scene-editor__splitter"
              :class="{ 'is-dragging': stepSplit.dragging.value }"
              @mousedown.prevent="stepSplit.start"
            >
              <div class="scene-editor__splitter-line" />
            </div>

            <div class="scene-editor__steps-right">
              <SceneStepInlineEditor
                v-if="selectedStep"
                :key="selectedStep.id"
                :step="selectedStep"
                :draft="isCreateMode"
                :environment-id="editEnvironmentId"
              />
              <div v-else class="scene-editor__right-empty">
                <p>选中左侧步骤卡片后在右侧编辑</p>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 场景变量 / 前置处理器 / 后置处理器：单列 -->
        <el-tab-pane label="场景变量" name="variables">
          <div class="scene-editor__section-head">
            <span>场景变量</span>
            <el-button size="small" @click="addVariable">+ 添加变量</el-button>
          </div>
          <div v-for="(v, i) in editVariables" :key="i" class="scene-editor__variable-row">
            <el-input v-model="v.name" placeholder="变量名" />
            <el-input v-model="v.value" placeholder="值（支持 ${} 引用）" />
            <el-input v-model="v.description" placeholder="描述" />
            <el-button link size="small" type="danger" @click="removeVariable(i)">✕</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="前置处理器" name="pre">
          <div
            :ref="preSplit.register"
            class="scene-editor__proc-layout"
            :class="{ 'is-dragging': preSplit.dragging.value }"
          >
            <div class="scene-editor__proc-left" :style="{ flexBasis: `${preSplit.ratio.value * 100}%` }">
              <div class="scene-editor__section-head">
                <span>前置处理器（{{ processorIndexes('pre').length }}）</span>
                <div class="scene-editor__section-actions">
                  <el-button size="small" @click="openAssetPicker('pre')">从公共组件引入</el-button>
                  <el-button size="small" type="primary" @click="addProcessor('pre')">+ 添加处理器</el-button>
                </div>
              </div>
              <template v-for="(idx, i) in processorIndexes('pre')" :key="idx">
                <div class="scene-editor__proc-insert-zone" @dragover.prevent="procOnDragOver($event)" @drop="procOnDrop('pre', i)">
                  <div class="scene-editor__proc-insert-line" />
                </div>
                <div
                  class="scene-editor__proc-item"
                  :class="{ 'is-selected': selectedProcessorIdx === idx, 'is-disabled': !(editProcessors[idx] as SceneProcessorElement).enabled, 'is-dragging': procDrag?.flat === idx }"
                  draggable="true"
                  @click="selectProcessor(idx)"
                  @dragstart="procOnDragStart('pre', idx, $event)"
                  @dragover.prevent="procOnDragOver($event)"
                  @drop="procOnDrop('pre', i)"
                >
                  <div class="scene-editor__proc-item-header">
                    <el-icon class="scene-editor__proc-drag-handle" title="拖拽排序"><Rank /></el-icon>
                    <span class="scene-editor__proc-index">{{ i + 1 }}</span>
                    <el-tag v-if="procTestclass(idx)" size="small" type="info">{{ processorTypeLabel(procTestclass(idx)) }}</el-tag>
                    <div class="scene-editor__proc-header-spacer" />
                    <el-switch v-model="(editProcessors[idx] as SceneProcessorElement).enabled" size="small" @click.stop />
                    <el-dropdown trigger="click" @click.stop>
                      <el-button link size="small">操作</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item @click="selectProcessor(idx)">编辑</el-dropdown-item>
                          <el-dropdown-item :disabled="i === 0" @click="moveProcessor('pre', i, -1)">上移</el-dropdown-item>
                          <el-dropdown-item :disabled="i === processorIndexes('pre').length - 1" @click="moveProcessor('pre', i, 1)">下移</el-dropdown-item>
                          <el-dropdown-item divided @click="copyProcessor(idx)">复制</el-dropdown-item>
                          <el-dropdown-item divided style="color: var(--el-color-danger)" @click="removeProcessor('pre', i)">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                  <div class="scene-editor__proc-item-name">{{ procDisplayName(idx) }}</div>
                </div>
              </template>
              <div class="scene-editor__proc-insert-zone" @dragover.prevent="procOnDragOver($event)" @drop="procOnDrop('pre', processorIndexes('pre').length)">
                <div class="scene-editor__proc-insert-line" />
              </div>
              <el-empty v-if="processorIndexes('pre').length === 0" description="暂无前置处理器" :image-size="60" />
              <el-button v-if="processorIndexes('pre').length === 0" size="small" class="scene-editor__proc-add" @click="addProcessor('pre')">
                <el-icon><Plus /></el-icon> 添加处理器
              </el-button>
            </div>

            <div
              class="scene-editor__splitter"
              :class="{ 'is-dragging': preSplit.dragging.value }"
              @mousedown.prevent="preSplit.start"
            >
              <div class="scene-editor__splitter-line" />
            </div>

            <div class="scene-editor__proc-right">
              <template v-if="selectedProcessorIdx !== null">
                <div class="scene-editor__proc-inline">
                  <header class="scene-editor__proc-inline-head">
                    <el-input v-model="(editProcessors[selectedProcessorIdx] as SceneProcessorElement).name" placeholder="处理器名称" class="scene-editor__proc-inline-name" />
                    <el-switch v-model="(editProcessors[selectedProcessorIdx] as SceneProcessorElement).enabled" active-text="启用" />
                    <el-divider direction="vertical" />
                    <el-button link size="small" type="danger" @click="removeSelectedProcessor('pre')">删除</el-button>
                  </header>
                  <div class="scene-editor__proc-inline-body">
                    <ProcessorForm
                      :model-value="editProcessors[selectedProcessorIdx]"
                      :http-options="httpRefOptions"
                      :ds-options="dsRefOptions"
                      @update:model-value="(v) => updateProcessor(selectedProcessorIdx!, v)"
                      @import-extractors="openExtractorPickerForProcessor(selectedProcessorIdx!)"
                    />
                  </div>
                </div>
              </template>
              <div v-else class="scene-editor__right-empty">
                <p>选中左侧处理器后在右侧编辑</p>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="后置处理器" name="post">
          <div
            :ref="postSplit.register"
            class="scene-editor__proc-layout"
            :class="{ 'is-dragging': postSplit.dragging.value }"
          >
            <div class="scene-editor__proc-left" :style="{ flexBasis: `${postSplit.ratio.value * 100}%` }">
              <div class="scene-editor__section-head">
                <span>后置处理器（{{ processorIndexes('post').length }}）</span>
                <div class="scene-editor__section-actions">
                  <el-button size="small" @click="openAssetPicker('post')">从公共组件引入</el-button>
                  <el-button size="small" type="primary" @click="addProcessor('post')">+ 添加处理器</el-button>
                </div>
              </div>
              <template v-for="(idx, i) in processorIndexes('post')" :key="idx">
                <div class="scene-editor__proc-insert-zone" @dragover.prevent="procOnDragOver($event)" @drop="procOnDrop('post', i)">
                  <div class="scene-editor__proc-insert-line" />
                </div>
                <div
                  class="scene-editor__proc-item"
                  :class="{ 'is-selected': selectedProcessorIdx === idx, 'is-disabled': !(editProcessors[idx] as SceneProcessorElement).enabled, 'is-dragging': procDrag?.flat === idx }"
                  draggable="true"
                  @click="selectProcessor(idx)"
                  @dragstart="procOnDragStart('post', idx, $event)"
                  @dragover.prevent="procOnDragOver($event)"
                  @drop="procOnDrop('post', i)"
                >
                  <div class="scene-editor__proc-item-header">
                    <el-icon class="scene-editor__proc-drag-handle" title="拖拽排序"><Rank /></el-icon>
                    <span class="scene-editor__proc-index">{{ i + 1 }}</span>
                    <el-tag v-if="procTestclass(idx)" size="small" type="info">{{ processorTypeLabel(procTestclass(idx)) }}</el-tag>
                    <div class="scene-editor__proc-header-spacer" />
                    <el-switch v-model="(editProcessors[idx] as SceneProcessorElement).enabled" size="small" @click.stop />
                    <el-dropdown trigger="click" @click.stop>
                      <el-button link size="small">操作</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item @click="selectProcessor(idx)">编辑</el-dropdown-item>
                          <el-dropdown-item :disabled="i === 0" @click="moveProcessor('post', i, -1)">上移</el-dropdown-item>
                          <el-dropdown-item :disabled="i === processorIndexes('post').length - 1" @click="moveProcessor('post', i, 1)">下移</el-dropdown-item>
                          <el-dropdown-item divided @click="copyProcessor(idx)">复制</el-dropdown-item>
                          <el-dropdown-item divided style="color: var(--el-color-danger)" @click="removeProcessor('post', i)">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                  <div class="scene-editor__proc-item-name">{{ procDisplayName(idx) }}</div>
                </div>
              </template>
              <div class="scene-editor__proc-insert-zone" @dragover.prevent="procOnDragOver($event)" @drop="procOnDrop('post', processorIndexes('post').length)">
                <div class="scene-editor__proc-insert-line" />
              </div>
              <el-empty v-if="processorIndexes('post').length === 0" description="暂无后置处理器" :image-size="60" />
              <el-button v-if="processorIndexes('post').length === 0" size="small" class="scene-editor__proc-add" @click="addProcessor('post')">
                <el-icon><Plus /></el-icon> 添加处理器
              </el-button>
            </div>

            <div
              class="scene-editor__splitter"
              :class="{ 'is-dragging': postSplit.dragging.value }"
              @mousedown.prevent="postSplit.start"
            >
              <div class="scene-editor__splitter-line" />
            </div>

            <div class="scene-editor__proc-right">
              <template v-if="selectedProcessorIdx !== null">
                <div class="scene-editor__proc-inline">
                  <header class="scene-editor__proc-inline-head">
                    <el-input v-model="(editProcessors[selectedProcessorIdx] as SceneProcessorElement).name" placeholder="处理器名称" class="scene-editor__proc-inline-name" />
                    <el-switch v-model="(editProcessors[selectedProcessorIdx] as SceneProcessorElement).enabled" active-text="启用" />
                    <el-divider direction="vertical" />
                    <el-button link size="small" type="danger" @click="removeSelectedProcessor('post')">删除</el-button>
                  </header>
                  <div class="scene-editor__proc-inline-body">
                    <ProcessorForm
                      :model-value="editProcessors[selectedProcessorIdx]"
                      :http-options="httpRefOptions"
                      :ds-options="dsRefOptions"
                      @update:model-value="(v) => updateProcessor(selectedProcessorIdx!, v)"
                      @import-extractors="openExtractorPickerForProcessor(selectedProcessorIdx!)"
                    />
                  </div>
                </div>
              </template>
              <div v-else class="scene-editor__right-empty">
                <p>选中左侧处理器后在右侧编辑</p>
              </div>
            </div>
          </div>
        </el-tab-pane>
        </el-tabs>
        <!-- Tabs 头部右侧：函数助手 / 变量助手 -->
        <div class="scene-editor__tabs-extra">
          <el-button link size="small" @click="showFunctionHelper = true">
            <el-icon><MagicStick /></el-icon> 函数助手
          </el-button>
          <el-button link size="small" @click="openVariableHelper">
            <el-icon><Collection /></el-icon> 变量助手
          </el-button>
        </div>
      </div>
    </div>

    <!-- ==================== 底部状态栏 ==================== -->
    <footer v-if="detail || isCreateMode" class="scene-editor__footer">
      <span class="scene-editor__autosave">{{ autoSaveLabel || (isCreateMode ? '草稿未保存' : '自动保存已开启') }}</span>
    </footer>

    <!-- 从公共组件引入处理器 / 提取器 -->
    <ExtractorAssetPicker
      v-model="assetPickerVisible"
      :loading="assetPickerLoading"
      :items="assetPickerItems"
      :keyword="assetPickerKeyword"
      :title="ASSET_TITLE[assetPickerKind]"
      @update:keyword="assetPickerKeyword = $event"
      @search="loadAssetPicker"
      @confirm="handleAssetPicked"
    />

    <!-- 从接口添加步骤 -->
    <InterfacePickerDialog v-model="showInterfacePicker" @select="handleInterfaceSelected" />

    <!-- 单步调试结果弹窗 -->
    <StepDebugResultDialog v-model="showDebugResult" :result="debugResult" />

    <!-- Tabs 头部函数助手 / 变量助手 -->
    <FunctionHelperDialog v-model="showFunctionHelper" />
    <SceneVariableHelperDialog
      v-model="showVariableHelper"
      :environment-variables="envVariables"
      :environment-name="envVariablesName"
      :scene-variables="editVariables"
    />
  </div>
</template>

<style scoped lang="scss">
.scene-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
  overflow: hidden;
}

// ==================== 顶部导航 ====================
.scene-editor__nav {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-lg);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: #fff;
}

.scene-editor__nav-name-input {
  width: 200px;
  :deep(.el-input__wrapper) { box-shadow: none; }
}

.scene-editor__nav-field {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.scene-editor__required {
  color: var(--el-color-danger);
  font-size: 14px;
  line-height: 1;
}

.scene-editor__nav-module { width: 180px; }

.scene-editor__status-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  user-select: none;
  &.is-draft { background: var(--color-neutral-100); color: var(--color-neutral-600); }
  &.is-published { background: rgba(var(--el-color-success-rgb), 0.1); color: var(--el-color-success); }
}

.scene-editor__priority-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  padding: 1px 8px;
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  line-height: 20px;
}

.scene-editor__priority-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.scene-editor__priority-option {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 4px 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  font-size: var(--font-size-sm);

  &:hover { background: var(--color-neutral-100); }

  &.is-active { background: var(--color-primary-50, #eff6ff); }
}

.scene-editor__priority-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

// 描述换行到顶栏第二行
.scene-editor__nav-desc {
  flex-basis: 100%;
}

.scene-editor__nav-spacer { flex: 1; }

.scene-editor__history-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
}

.scene-editor__history-status {
  width: 26px;
  text-align: center;
  font-size: 11px;
  padding: 1px 4px;
  border-radius: var(--radius-sm);
  color: #fff;

  &.is-success { background: var(--el-color-success); }
  &.is-failed,
  &.is-error { background: var(--el-color-danger); }
  &.is-skipped,
  &.is-pending { background: var(--color-neutral-400); }
}

.scene-editor__empty-text { color: var(--color-neutral-400); }

// ==================== 主体 ====================
.scene-editor__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

// ==================== 步骤 tab：左右双列 ====================
.scene-editor__steps-layout {
  // 父级 el-tab-pane 使高度 100%（非 flex 容器），此处必须用确定高度而非 flex:1，左右栏的 overflow 才能触发
  height: 100%;
  display: flex;
  align-items: stretch;
}

.scene-editor__steps-left {
  flex: 0 1 auto;
  min-width: 260px;
  min-height: 0;
  padding: var(--space-md);
  overflow-y: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.scene-editor__steps-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-md);
  border-left: 1px solid var(--color-neutral-100);
  overflow: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.scene-editor__right-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}

// ==================== 分割条 ====================
.scene-editor__splitter {
  flex: 0 0 8px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-neutral-100);

  .scene-editor__splitter-line {
    width: 1px;
    height: 100%;
    background: var(--color-neutral-300);
  }

  &:hover,
  &.is-dragging {
    background: var(--color-primary-50, #eff6ff);
    .scene-editor__splitter-line { background: var(--color-primary-400, #60a5fa); }
  }
}

// ==================== 底部状态栏 ====================
.scene-editor__footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: var(--space-xs) var(--space-lg);
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.scene-editor__autosave { opacity: 0.85; }

.scene-editor__assoc-panel { margin-top: var(--space-lg); }

// ==================== 设置弹层 ====================
.scene-editor__settings-section { margin-bottom: var(--space-md); }

.scene-editor__settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-sm);
  font-weight: 600;
}

// ==================== 左栏分栏 ====================
.scene-editor__tabs-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.scene-editor__left-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) { margin-bottom: 0; flex-shrink: 0; }
  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
  :deep(.el-tab-pane) {
    height: 100%;
    overflow-y: auto;
  }
}

// Tabs 头部右侧操作入口，绝对定位到头部同一行；覆盖背景避免看到头部下边框
.scene-editor__tabs-extra {
  position: absolute;
  top: 1px;
  right: 8px;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  padding: 7px 8px;
  background: #fff;
}

.scene-editor__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  font-weight: 600;
}

.scene-editor__section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.scene-editor__variable-row {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-xs);

  :deep(.el-input) {
    flex: 1;
  }
}

// ==================== 处理器：左列表 + 右明细（对齐步骤 tab） ====================
// 布局宽度规则沿用 steps-layout：左栏 flexBasis 42%（可拖拽），不再写死像素
.scene-editor__proc-layout {
  height: 100%;
  display: flex;
  align-items: stretch;
}

.scene-editor__proc-left {
  flex: 0 1 auto;
  min-width: 260px;
  min-height: 0;
  padding: var(--space-md);
  overflow-y: auto;
  scrollbar-width: none;

  &::-webkit-scrollbar { display: none; }
}

.scene-editor__proc-right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: var(--space-md);
  border-left: 1px solid var(--color-neutral-100);
  display: flex;
  flex-direction: column;
}

// 左侧列表卡片：对齐 StepCanvas 步骤卡片
.scene-editor__proc-item {
  display: flex;
  flex-direction: column;
  height: 76px;
  padding: var(--space-md);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  cursor: pointer;
  margin-bottom: var(--space-xs);
  overflow: hidden;

  &:hover {
    border-color: var(--color-primary-300);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }

  &.is-selected {
    border-color: var(--color-primary-400);
    background: var(--color-primary-50, #eff6ff);
    box-shadow: 0 0 0 1px var(--color-primary-300);
  }

  &.is-disabled {
    opacity: 0.5;
  }

  &.is-dragging {
    opacity: 0.4;
    border-style: dashed;
  }
}

.scene-editor__proc-insert-zone {
  height: 4px;
  transition: height var(--transition-fast);

  &:hover,
  &.is-over {
    height: 24px;
    background: var(--color-primary-50, #eff6ff);
    border-radius: var(--radius-sm);
  }
}

.scene-editor__proc-insert-line {
  display: none;
}

.scene-editor__proc-drag-handle {
  color: var(--color-neutral-400);
  cursor: grab;
  flex-shrink: 0;

  &:active {
    cursor: grabbing;
  }
}

.scene-editor__proc-item-header {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
}

.scene-editor__proc-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-neutral-100);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-600);
  flex-shrink: 0;
}

.scene-editor__proc-header-spacer {
  flex: 1;
}

.scene-editor__proc-item-name {
  padding: var(--space-xs) 0 0 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-editor__proc-add {
  border-style: dashed;
  width: 100%;
  margin-top: var(--space-sm);
}

// 右侧明细卡片：对齐 SceneStepInlineEditor
.scene-editor__proc-inline {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.scene-editor__proc-inline-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-neutral-100);
  background: var(--color-neutral-50);
}

.scene-editor__proc-inline-name {
  flex: 1;
  max-width: 320px;
}

.scene-editor__proc-inline-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: none;
  padding: var(--space-lg);

  &::-webkit-scrollbar { display: none; }
}
</style>