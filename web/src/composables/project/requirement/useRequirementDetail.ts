import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveRequirement,
  confirmRequirement,
  fetchProjectModuleTree,
  fetchRequirementChangeLogs,
  getRequirement,
  splitRequirement,
  unarchiveRequirement,
  updateRequirement,
} from '@/services/project'
import { fetchAiStatus } from '@/services/ai'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import { useRequirementStore } from '@/stores/requirement'
import type {
  ProjectModule,
  RequirementChangeLog,
  RequirementDetail,
  RequirementUpdatePayload,
  WorkspaceMember,
} from '@/types'
import { canSplitRequirement, isRequirementReadonly } from '@/composables/project/requirement/requirementPresentation'

const LOG_PAGE_SIZE = 20
const VERSION_MAX_LENGTH = 50
// 需求不存在或越权：页面切 404 分支（错误码 1000018001）
const REQUIREMENT_NOT_FOUND = 1000018001
/** 已有进行中拆分任务（1000018013）：提示引导去任务中心 */
const REQUIREMENT_TASK_IN_PROGRESS = 1000018013

function stripDocuments(nodes: ProjectModule[]): ProjectModule[] {
  return nodes
    .filter((node) => node.type === 'directory')
    .map((node) => ({ ...node, children: stripDocuments(node.children) }))
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) return error.message
  return fallback
}

function errorCode(error: unknown): number | undefined {
  if (error instanceof Error && 'code' in error) {
    const code = (error as Error & { code?: number }).code
    return typeof code === 'number' ? code : undefined
  }
  return undefined
}

export function useRequirementDetail() {
  const route = useRoute()
  const router = useRouter()
  const authStore = useAuthStore()
  const requirementStore = useRequirementStore()

  const requirementId = String(route.params.requirementId ?? '')

  const loading = ref(true)
  const loadError = ref('')
  const notFound = ref(false)
  const detail = ref<RequirementDetail | null>(null)

  // ==================== 变更记录 ====================
  const logs = ref<RequirementChangeLog[]>([])
  const logsTotal = ref(0)
  const logsLoading = ref(false)

  async function loadLogs(): Promise<void> {
    logsLoading.value = true
    try {
      const page = await fetchRequirementChangeLogs(requirementId, {
        pageNo: 1,
        pageSize: LOG_PAGE_SIZE,
      })
      logs.value = page.list
      logsTotal.value = page.total
    } catch (err) {
      ElMessage.error(errorMessage(err, '加载变更记录失败'))
    } finally {
      logsLoading.value = false
    }
  }

  // ==================== 属性面板选项 ====================
  const moduleTree = ref<ProjectModule[]>([])
  const memberOptions = ref<WorkspaceMember[]>([])

  async function loadOptions(): Promise<void> {
    try {
      moduleTree.value = stripDocuments(await fetchProjectModuleTree())
    } catch {
      // 选项树失败不阻塞详情：展示已有值，选择能力降级
      moduleTree.value = []
    }
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberOptions.value = page.list
    } catch {
      memberOptions.value = []
    }
  }

  // ==================== 详情加载 ====================
  async function load(): Promise<void> {
    loading.value = true
    loadError.value = ''
    notFound.value = false
    try {
      const cached = requirementStore.takeCachedDetail(requirementId)
      if (cached) detail.value = cached
      const data = await getRequirement(requirementId)
      detail.value = data
      requirementStore.cacheDetail(data)
      syncEditable(data)
      void loadLogs()
    } catch (err) {
      if (errorCode(err) === REQUIREMENT_NOT_FOUND) {
        notFound.value = true
        return
      }
      loadError.value = errorMessage(err, '加载需求详情失败')
    } finally {
      loading.value = false
    }
  }

  function retry(): void {
    void load()
  }

  // ==================== 标题内联编辑 ====================
  const titleEditing = ref(false)
  const titleDraft = ref('')

  function startEditTitle(): void {
    if (isReadonly.value) return
    titleDraft.value = detail.value?.title ?? ''
    titleEditing.value = true
  }

  function cancelEditTitle(): void {
    titleEditing.value = false
    titleDraft.value = ''
  }

  // ==================== 属性面板（部分更新） ====================
  const form = reactive({
    moduleId: '',
    systemVersion: '',
    priority: '',
    ownerId: '',
    tags: [] as string[],
  })
  const saving = ref(false)

  function syncEditable(data: RequirementDetail): void {
    form.moduleId = data.moduleId ?? ''
    form.systemVersion = data.systemVersion ?? ''
    form.priority = data.priority ?? ''
    form.ownerId = data.ownerId ?? ''
    form.tags = [...(data.tags ?? [])]
    titleDraft.value = data.title ?? ''
    descriptionDraft.value = data.description ?? ''
    descriptionEditing.value = false
  }

  const versionError = computed(() =>
    form.systemVersion.length > VERSION_MAX_LENGTH
      ? `版本不能超过 ${VERSION_MAX_LENGTH} 字符（错误码 1000018010）`
      : '',
  )

  const attributeDirty = computed(() => {
    const data = detail.value
    if (!data) return false
    return (
      form.moduleId !== (data.moduleId ?? '') ||
      form.systemVersion !== (data.systemVersion ?? '') ||
      form.priority !== (data.priority ?? '') ||
      form.ownerId !== (data.ownerId ?? '') ||
      form.tags.join('\n') !== (data.tags ?? []).join('\n')
    )
  })

  /** 三态：非空白且与原值不同 → 传新值；空白且原值非空 → 传空串清空；否则不传（C11） */
  function threeState(next: string, original: string | null): string | undefined {
    const trimmed = next.trim()
    const prev = original ?? ''
    if (trimmed === '' && prev !== '') return ''
    if (trimmed !== '' && trimmed !== prev) return trimmed
    return undefined
  }

  function buildAttributePayload(): RequirementUpdatePayload {
    const data = detail.value
    const payload: RequirementUpdatePayload = {}
    if (!data) return payload
    // 模块/负责人后端仅支持改为非空值（null 视为不修改），空值不入载荷
    if (form.moduleId && form.moduleId !== (data.moduleId ?? '')) {
      payload.moduleId = form.moduleId
    }
    if (form.ownerId && form.ownerId !== (data.ownerId ?? '')) {
      payload.ownerId = form.ownerId
    }
    const version = threeState(form.systemVersion, data.systemVersion)
    if (version !== undefined) payload.systemVersion = version
    const priority = threeState(form.priority, data.priority)
    if (priority !== undefined) payload.priority = priority as RequirementUpdatePayload['priority']
    if (form.tags.join('\n') !== (data.tags ?? []).join('\n')) payload.tags = [...form.tags]
    return payload
  }

  // ==================== 描述编辑 ====================
  const descriptionEditing = ref(false)
  const descriptionDraft = ref('')

  function startEditDescription(): void {
    if (isReadonly.value) return
    descriptionDraft.value = detail.value?.description ?? ''
    descriptionEditing.value = true
  }

  function cancelEditDescription(): void {
    descriptionEditing.value = false
    descriptionDraft.value = detail.value?.description ?? ''
  }

  // ==================== 影响确认 ====================

  /**
   * confirmed 态改题/描述/模块会流转为「已变更」并触发影响分析（详设 4.4），
   * 保存前显式确认；受影响数量待追溯侧接入，本期不展示。
   */
  async function confirmImpactIfConfirmed(needsImpact: boolean, apply: () => Promise<void>): Promise<void> {
    if (!needsImpact || detail.value?.status !== 'confirmed') {
      await apply()
      return
    }
    try {
      await ElMessageBox.confirm(
        '该需求已处于「已确认」状态，修改标题、描述或所属模块会将其流转为「已变更」，并沿追溯边标记可能受影响的用例与接口。是否继续保存？',
        '确认保存变更',
        { type: 'warning', confirmButtonText: '保存并流转', cancelButtonText: '放弃修改' },
      )
    } catch {
      // 用户放弃：恢复为服务端当前值，不提交任何字段
      if (detail.value) syncEditable(detail.value)
      return
    }
    await apply()
  }

  async function putDetail(payload: RequirementUpdatePayload): Promise<void> {
    saving.value = true
    try {
      const data = await updateRequirement(requirementId, payload)
      detail.value = data
      requirementStore.cacheDetail(data)
      syncEditable(data)
      ElMessage.success('需求已更新')
      void loadLogs()
    } catch (err) {
      ElMessage.error(errorMessage(err, '保存需求失败'))
    } finally {
      saving.value = false
    }
  }

  function saveTitle(): Promise<void> {
    const title = titleDraft.value.trim()
    const original = detail.value?.title ?? ''
    if (!title || title === original) {
      cancelEditTitle()
      return Promise.resolve()
    }
    const apply = async () => {
      titleEditing.value = false
      await putDetail({ title })
    }
    return confirmImpactIfConfirmed(true, apply)
  }

  function saveDescription(): Promise<void> {
    const data = detail.value
    if (!data) return Promise.resolve()
    const description = descriptionDraft.value
    if (description === (data.description ?? '')) {
      cancelEditDescription()
      return Promise.resolve()
    }
    const apply = async () => {
      descriptionEditing.value = false
      await putDetail({ description })
    }
    return confirmImpactIfConfirmed(true, apply)
  }

  async function saveAttributes(): Promise<void> {
    if (versionError.value) {
      ElMessage.error(versionError.value)
      return
    }
    const payload = buildAttributePayload()
    if (Object.keys(payload).length === 0) {
      // 本地改动无法表达为后端字段（如清空模块），回滚到服务端当前值避免误导
      if (detail.value) syncEditable(detail.value)
      ElMessage.info('属性未发生变化')
      return
    }
    await confirmImpactIfConfirmed('moduleId' in payload, () => putDetail(payload))
  }

  // ==================== 状态操作 ====================
  async function runStatusAction(
    action: () => Promise<RequirementDetail>,
    successText: string,
  ): Promise<void> {
    try {
      const data = await action()
      detail.value = data
      requirementStore.cacheDetail(data)
      syncEditable(data)
      ElMessage.success(successText)
      void loadLogs()
    } catch (err) {
      ElMessage.error(errorMessage(err, '操作失败'))
    }
  }

  function handleConfirm(): Promise<void> {
    return runStatusAction(() => confirmRequirement(requirementId), '需求已确认')
  }

  async function handleArchive(): Promise<void> {
    const title = detail.value?.title ?? ''
    try {
      await ElMessageBox.confirm(
        `归档后「${title}」只读且不可拆分，可在本页取消归档（条目将回到草稿状态）。`,
        '确认归档',
        { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    await runStatusAction(() => archiveRequirement(requirementId), '需求已归档')
  }

  async function handleUnarchive(): Promise<void> {
    try {
      await ElMessageBox.confirm(
        '取消归档后条目将回到「草稿」状态，需重新确认才会恢复为已确认。',
        '确认取消归档',
        { type: 'warning', confirmButtonText: '取消归档', cancelButtonText: '返回' },
      )
    } catch {
      return
    }
    await runStatusAction(() => unarchiveRequirement(requirementId), '已取消归档，条目回到草稿状态')
  }

  // ==================== AI 拆分 ====================
  const aiAvailable = ref(false)
  const splitting = ref(false)

  async function loadAiStatus(): Promise<void> {
    try {
      const status = await fetchAiStatus()
      aiAvailable.value = status.available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏，不阻塞页面
      aiAvailable.value = false
    }
  }

  const canSplit = computed(
    () =>
      aiAvailable.value &&
      authStore.hasPermission('requirement:edit') &&
      detail.value !== null &&
      canSplitRequirement(detail.value),
  )

  /** 发起 AI 生成入口（交互 04 §1）：AI 可用且具备任务提交权限，状态条件由入口置灰 */
  const canLaunchGeneration = computed(
    () => aiAvailable.value && authStore.hasPermission('ai:task'),
  )

  async function handleSplit(): Promise<void> {
    const title = detail.value?.title ?? ''
    try {
      await ElMessageBox.confirm(
        `将以「${title}」为输入提交 AI 拆分任务，提交后跳转任务详情页跟踪进度并审核拆分建议。`,
        '提交 AI 拆分',
        { type: 'info', confirmButtonText: '提交拆分', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    splitting.value = true
    try {
      const result = await splitRequirement(requirementId)
      ElMessage.success('拆分任务已提交')
      void router.push(`/workspace/projects/ai/tasks/${result.taskId}`)
    } catch (err) {
      // 已有进行中任务：引导去任务中心看该条目的进行中任务（交互 06 §2.3）
      if (errorCode(err) === REQUIREMENT_TASK_IN_PROGRESS) {
        try {
          await ElMessageBox.confirm(
            errorMessage(err, '已存在进行中的导入或拆分任务'),
            '无法提交拆分',
            {
              type: 'warning',
              confirmButtonText: '前往任务中心',
              cancelButtonText: '留在本页',
            },
          )
        } catch {
          return
        }
        void router.push('/workspace/projects/ai/tasks')
        return
      }
      ElMessage.error(errorMessage(err, '提交拆分失败'))
    } finally {
      splitting.value = false
    }
  }

  // ==================== 派生状态 ====================
  const isReadonly = computed(() => (detail.value ? isRequirementReadonly(detail.value) : false))
  const canConfirm = computed(
    () =>
      authStore.hasPermission('requirement:confirm') &&
      detail.value !== null &&
      (detail.value.status === 'draft' || detail.value.status === 'changed'),
  )
  const canArchive = computed(
    () =>
      authStore.hasPermission('requirement:confirm') &&
      detail.value !== null &&
      detail.value.status !== 'archived',
  )
  const canUnarchive = computed(
    () =>
      authStore.hasPermission('requirement:edit') && detail.value?.status === 'archived',
  )
  const canEdit = computed(() => authStore.hasPermission('requirement:edit'))
  const canLoadMoreLogs = computed(() => logs.value.length < logsTotal.value)

  onMounted(() => {
    void load()
    void loadOptions()
    void loadAiStatus()
  })

  return {
    requirementId,
    loading,
    loadError,
    notFound,
    detail,
    logs,
    logsTotal,
    logsLoading,
    canLoadMoreLogs,
    loadLogs,
    moduleTree,
    memberOptions,
    titleEditing,
    titleDraft,
    startEditTitle,
    cancelEditTitle,
    saveTitle,
    descriptionEditing,
    descriptionDraft,
    startEditDescription,
    cancelEditDescription,
    saveDescription,
    form,
    saving,
    versionError,
    attributeDirty,
    saveAttributes,
    isReadonly,
    canConfirm,
    canArchive,
    canUnarchive,
    canEdit,
    handleConfirm,
    handleArchive,
    handleUnarchive,
    aiAvailable,
    splitting,
    canSplit,
    canLaunchGeneration,
    handleSplit,
    loadOptions,
    loadAiStatus,
    load,
    retry,
    router,
  }
}
