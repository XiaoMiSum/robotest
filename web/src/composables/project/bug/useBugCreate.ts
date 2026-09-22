import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadUserFile } from 'element-plus'
import { changeBugStatus, createBug, fetchPlans, fetchProjectModuleTree, getBugDetail, uploadBugAttachment } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import { useAiStore } from '@/stores/ai'
import type {
  AiBugDedupItem,
  BugPriority,
  BugSeverity,
  BugType,
  ProjectModule,
  TestPlanListItem,
  WorkspaceMember,
} from '@/types'
import { BUG_STATUS_LABEL, BUG_STATUS_TAG_TYPE, BUG_TYPE_LABEL } from '@/composables/project/bug/bugStatus'

// 以 expose 契约替代组件类型导入，避免组合式函数反向依赖 components
interface BugAiSuggestRef {
  requestSuggestion: () => Promise<void>
  loading: boolean
}
// ==================== Constants ====================

const severityLabel: Record<BugSeverity, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<BugPriority, string> = { high: '高', medium: '中', low: '低' }
const MAX_FILE_SIZE = 10 * 1024 * 1024

// ==================== Composable ====================

export function useBugCreate() {
  const route = useRoute()
  const router = useRouter()
  const aiStore = useAiStore()

  const aiEnabled = aiStore.aiEnabled
  const formRef = ref<FormInstance>()
  const submitting = ref(false)
  const aiSuggestRef = ref<BugAiSuggestRef>()

  const dedupItems = ref<AiBugDedupItem[]>([])
  const dedupConfirmVisible = ref(false)
  const dedupSubmitting = ref(false)
  const dedupTargetId = ref('')

  function applyTitle(title: string): void {
    form.title = title
  }
  function applySeverity(severity: BugSeverity): void {
    form.severity = severity
  }
  function applyPriority(priority: BugPriority): void {
    form.priority = priority
  }

  const form = reactive({
    title: '',
    bugType: 'code_error' as BugType,
    moduleId: '' as string,
    severity: 'general' as BugSeverity,
    priority: 'medium' as BugPriority,
    dueDate: '' as string,
    keywords: '',
    reproSteps: '',
    assigneeId: '' as string,
    relatedCaseId: '' as string,
    relatedPlanId: '' as string,
  })

  const moduleTree = ref<ProjectModule[]>([])
  async function loadModuleTree() {
    try {
      moduleTree.value = await fetchProjectModuleTree()
    } catch { /* ignore */ }
  }
  loadModuleTree()

  const caseSelectorVisible = ref(false)
  const selectedCaseTitle = ref('')
  function handleCaseSelected(nodes: { documentId: string; caseIds: string[] }[]) {
    if (nodes.length && nodes[0].caseIds.length) {
      form.relatedCaseId = nodes[0].caseIds[0]
      selectedCaseTitle.value = '已选 1 个用例'
    }
  }

  const planOptions = ref<TestPlanListItem[]>([])
  async function loadPlanOptions() {
    try {
      const page = await fetchPlans({ status: 'in_progress', pageNo: 1, pageSize: 50 })
      planOptions.value = page.list
    } catch { /* ignore */ }
  }

  async function applyCopySource() {
    const copyFrom = String(route.query.copyFrom ?? '')
    if (!copyFrom) return
    try {
      const src = await getBugDetail(copyFrom)
      form.title = src.title
      form.bugType = src.bugType
      form.moduleId = src.moduleId ?? ''
      form.severity = src.severity
      form.priority = src.priority
      form.dueDate = src.dueDate ?? ''
      form.keywords = src.keywords ?? ''
      form.reproSteps = src.reproSteps ?? ''
      form.relatedCaseId = src.relatedCaseId ?? ''
      if (form.relatedCaseId) selectedCaseTitle.value = '已选 1 个用例'
      const planId = src.relatedPlanId ?? ''
      form.relatedPlanId = planOptions.value.some((p) => p.id === planId) ? planId : ''
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载源缺陷信息失败')
    }
  }
  loadPlanOptions().then(applyCopySource)

  const rules: FormRules = {
    title: [{ required: true, message: '请输入缺陷标题', trigger: 'blur' }],
    bugType: [{ required: true, message: '请选择缺陷类型', trigger: 'change' }],
    severity: [{ required: true, message: '请选择严重等级', trigger: 'change' }],
    priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
    assigneeId: [{ required: true, message: '请选择处理人', trigger: 'change' }],
  }

  const memberOptions = ref<WorkspaceMember[]>([])
  async function loadMembers() {
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberOptions.value = page.list
    } catch {
      // 加载失败不阻塞
    }
  }
  loadMembers()

  const attachmentFiles = ref<UploadUserFile[]>([])
  function handleAttachmentChange(_file: UploadUserFile, files: UploadUserFile[]) {
    attachmentFiles.value = files.filter((f) => {
      if (f.size && f.size > MAX_FILE_SIZE) {
        ElMessage.warning(`「${f.name}」超过 10MB，已忽略`)
        return false
      }
      return true
    })
  }
  function handleAttachmentRemove(_file: UploadUserFile, files: UploadUserFile[]) {
    attachmentFiles.value = files
  }

  function handleSelectDuplicate(item: AiBugDedupItem | null): void {
    dedupTargetId.value = item ? item.bugId : ''
  }

  function handleAbandonSubmit(): void {
    router.push('/workspace/projects/bugs')
  }

  async function runCreate(duplicateOfBugId?: string): Promise<void> {
    submitting.value = true
    try {
      const bugId = await createBug({
        title: form.title.trim(),
        severity: form.severity,
        priority: form.priority,
        bugType: form.bugType,
        reproSteps: form.reproSteps.trim() || undefined,
        moduleId: form.moduleId || undefined,
        keywords: form.keywords.trim() || undefined,
        dueDate: form.dueDate || undefined,
        assigneeId: form.assigneeId,
        relatedCaseId: form.relatedCaseId || undefined,
        relatedPlanId: form.relatedPlanId || undefined,
      })
      for (const item of attachmentFiles.value) {
        if (item.raw) {
          await uploadBugAttachment(bugId, item.raw)
        }
      }
      if (duplicateOfBugId) {
        await changeBugStatus(bugId, {
          status: 'resolved',
          resolution: 'duplicate',
          duplicateOfBugId,
          comment: '创建时标记为重复缺陷',
        })
      }
      ElMessage.success(duplicateOfBugId ? '缺陷已提交并标记为重复' : '缺陷已提交')
      router.push('/workspace/projects/bugs')
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '提交失败')
    } finally {
      submitting.value = false
    }
  }

  async function handleSubmit() {
    if (!formRef.value) return
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    if (dedupItems.value.length > 0) {
      dedupConfirmVisible.value = true
      return
    }
    await runCreate()
  }

  function handleDedupAbandon(): void {
    if (dedupSubmitting.value) return
    dedupConfirmVisible.value = false
    router.push('/workspace/projects/bugs')
  }

  async function handleDedupContinue(): Promise<void> {
    if (dedupSubmitting.value) return
    dedupSubmitting.value = true
    try {
      await runCreate()
    } finally {
      dedupSubmitting.value = false
    }
  }

  async function handleDedupMarkDuplicate(): Promise<void> {
    if (dedupSubmitting.value) return
    if (!dedupTargetId.value) {
      ElMessage.warning('请选择要标记为重复所对应的原始缺陷')
      return
    }
    dedupSubmitting.value = true
    try {
      await runCreate(dedupTargetId.value)
    } finally {
      dedupSubmitting.value = false
    }
  }

  return {
    aiEnabled,
    formRef,
    submitting,
    aiSuggestRef,
    dedupItems,
    dedupConfirmVisible,
    dedupSubmitting,
    dedupTargetId,
    applyTitle,
    applySeverity,
    applyPriority,
    form,
    moduleTree,
    caseSelectorVisible,
    selectedCaseTitle,
    handleCaseSelected,
    planOptions,
    rules,
    memberOptions,
    attachmentFiles,
    handleAttachmentChange,
    handleAttachmentRemove,
    handleSelectDuplicate,
    handleAbandonSubmit,
    handleSubmit,
    handleDedupAbandon,
    handleDedupContinue,
    handleDedupMarkDuplicate,
    router,
    severityLabel,
    priorityLabel,
    BUG_STATUS_LABEL,
    BUG_STATUS_TAG_TYPE,
    BUG_TYPE_LABEL,
  }
}
