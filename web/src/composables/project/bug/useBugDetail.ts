import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import {
  assignBug,
  changeBugStatus,
  confirmBug,
  deleteBugAttachment,
  downloadBugAttachment,
  fetchBugAttachments,
  fetchPlans,
  fetchProjectModuleTree,
  getBugDetail,
  getBugLogs,
  getCaseDetail,
  updateBug,
  uploadBugAttachment,
} from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type {
  BugAttachment,
  BugDetail,
  BugLog,
  BugResolution,
  CaseNodeType,
  ProjectModule,
  TestCaseNode,
  TestPlanListItem,
  WorkspaceMember,
} from '@/types'
import {
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  promptStatusChangeComment,
} from '@/composables/project/bug/bugStatus'
import { typeBadge, type Badge } from '@/components/project/functional-testing/minder/badges'

// ==================== Constants ====================

const MARKED_TYPES = new Set<CaseNodeType>(['precondition', 'step', 'expected'])

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
const severityType: Record<string, 'primary' | 'danger' | 'warning' | 'info'> = { fatal: 'danger', serious: 'warning', general: 'primary', minor: 'info' }
const priorityType: Record<string, 'primary' | 'warning' | 'info'> = { high: 'warning', medium: 'primary', low: 'info' }
const statusLabel = BUG_STATUS_LABEL
const MAX_FILE_SIZE = 10 * 1024 * 1024

// ==================== Helpers ====================

function stripDocuments(nodes: ProjectModule[]): ProjectModule[] {
  return nodes
    .filter((n) => n.type === 'directory')
    .map((n) => ({ ...n, children: stripDocuments(n.children) }))
}

function findModuleName(nodes: ProjectModule[], id: string): string | null {
  for (const n of nodes) {
    if (n.id === id) return n.name
    const found = findModuleName(n.children ?? [], id)
    if (found) return found
  }
  return null
}

function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

// ==================== Types ====================

export interface UseBugDetailOptions {
  bugId: string
}

// ==================== Composable ====================

export function useBugDetail(options: UseBugDetailOptions) {
  const router = useRouter()
  const { bugId } = options

  const loading = ref(false)
  const saving = ref(false)
  const detail = ref<BugDetail | null>(null)
  const logs = ref<BugLog[]>([])
  const memberOptions = ref<WorkspaceMember[]>([])
  const resolveDialogVisible = ref(false)

  const isClosed = computed(() => detail.value?.status === 'closed')
  const isActive = computed(() => detail.value?.status === 'active')
  const isResolved = computed(() => detail.value?.status === 'resolved')
  const isRejected = computed(() => detail.value?.status === 'rejected')

  const form = reactive({
    title: '',
    severity: '' as string,
    priority: '' as string,
    bugType: '' as string,
    moduleId: '' as string,
    keywords: '' as string,
    dueDate: '' as string,
    reproSteps: '' as string,
    assigneeId: '' as string,
    relatedCaseId: '' as string,
    relatedPlanId: '' as string,
  })

  // ==================== Module tree ====================
  const moduleTree = ref<ProjectModule[]>([])
  async function loadModuleTree() {
    try {
      moduleTree.value = await fetchProjectModuleTree('testcase')
    } catch { /* ignore */ }
  }

  const dirTree = computed(() => stripDocuments(moduleTree.value))

  // ==================== Plan options ====================
  const planOptions = ref<TestPlanListItem[]>([])
  async function loadPlanOptions() {
    try {
      const page = await fetchPlans({ pageNo: 1, pageSize: 100 })
      planOptions.value = page.list
    } catch { /* ignore */ }
  }

  const relatedPlanName = computed(() => {
    const id = detail.value?.relatedPlanId
    if (!id) return '-'
    return planOptions.value.find((p) => p.id === id)?.name ?? id
  })

  // ==================== Case selector ====================
  const caseSelectorVisible = ref(false)
  function handleCaseSelected(nodes: { documentId: string; caseIds: string[] }[]) {
    if (nodes.length && nodes[0].caseIds.length) {
      form.relatedCaseId = nodes[0].caseIds[0]
    }
  }

  // ==================== Case detail hover ====================
  const currentRelatedCaseId = computed(() =>
    isClosed.value ? (detail.value?.relatedCaseId ?? '') : form.relatedCaseId,
  )

  const caseDetail = ref<TestCaseNode | null>(null)
  const caseDetailLoading = ref(false)

  async function loadCaseDetail() {
    const id = currentRelatedCaseId.value
    if (!id || caseDetail.value?.id === id || caseDetailLoading.value) return
    caseDetailLoading.value = true
    caseDetail.value = null
    try {
      caseDetail.value = await getCaseDetail(id)
    } catch {
      // 用例可能已被删除，popover 内展示空态兜底
    } finally {
      caseDetailLoading.value = false
    }
  }

  const caseDetailRows = computed(() => {
    const rows: { id: string; depth: number; badge: Badge; title: string }[] = []
    function walk(node: TestCaseNode, depth: number) {
      node.children.forEach((child) => {
        const badge = MARKED_TYPES.has(child.type) ? typeBadge(child.type) : null
        if (badge) rows.push({ id: child.id, depth, badge, title: child.title })
        walk(child, badge ? depth + 1 : depth)
      })
    }
    if (caseDetail.value) walk(caseDetail.value, 0)
    return rows
  })

  const caseDocName = computed(() => {
    const docId = caseDetail.value?.documentId
    if (!docId) return ''
    return findModuleName(moduleTree.value, docId) ?? ''
  })

  function openCaseDocument() {
    const docId = caseDetail.value?.documentId
    if (!docId) return
    router.push({
      path: '/workspace/projects/functional-testing',
      query: { tab: 'cases', documentId: docId },
    })
  }

  // ==================== Load ====================
  async function load() {
    loading.value = true
    try {
      const [bugData, logData, memberData] = await Promise.all([
        getBugDetail(bugId),
        getBugLogs(bugId),
        fetchMembers({ pageNo: 1, pageSize: 100 }),
      ])
      detail.value = bugData
      logs.value = logData
      memberOptions.value = memberData.list
      form.title = bugData.title
      form.severity = bugData.severity
      form.priority = bugData.priority
      form.bugType = bugData.bugType
      form.moduleId = bugData.moduleId ?? ''
      form.keywords = bugData.keywords ?? ''
      form.dueDate = bugData.dueDate ?? ''
      form.reproSteps = bugData.reproSteps ?? ''
      form.assigneeId = bugData.assignee?.id ?? ''
      form.relatedCaseId = bugData.relatedCaseId ?? ''
      form.relatedPlanId = bugData.relatedPlanId ?? ''
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载缺陷详情失败')
    } finally {
      loading.value = false
    }
  }

  // ==================== Save ====================
  async function handleSave() {
    if (!detail.value) return
    saving.value = true
    try {
      const originalRelatedCaseId = detail.value.relatedCaseId ?? ''
      const originalRelatedPlanId = detail.value.relatedPlanId ?? ''
      await updateBug(bugId, {
        title: form.title.trim(),
        severity: form.severity as BugDetail['severity'],
        priority: form.priority as BugDetail['priority'],
        bugType: form.bugType as BugDetail['bugType'],
        moduleId: form.moduleId || undefined,
        keywords: form.keywords.trim() || undefined,
        dueDate: form.dueDate || undefined,
        reproSteps: form.reproSteps.trim() || undefined,
        relatedCaseId: form.relatedCaseId !== originalRelatedCaseId ? form.relatedCaseId : undefined,
        relatedPlanId: form.relatedPlanId !== originalRelatedPlanId ? form.relatedPlanId : undefined,
      })
      const originalAssigneeId = detail.value.assignee?.id ?? ''
      if (form.assigneeId && form.assigneeId !== originalAssigneeId) {
        await assignBug(bugId, form.assigneeId)
      }
      ElMessage.success('已保存')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '保存失败')
    } finally {
      saving.value = false
    }
  }

  // ==================== Status operations ====================
  async function handleConfirm() {
    try {
      await ElMessageBox.confirm('确认该缺陷有效并需要处理吗？', '确认缺陷', { type: 'info' })
    } catch {
      return
    }
    try {
      await confirmBug(bugId)
      ElMessage.success('缺陷已确认')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '确认失败')
    }
  }

  async function handleResolve(payload: {
    resolution: BugResolution
    duplicateOfBugId?: string
    comment?: string
  }) {
    try {
      await changeBugStatus(bugId, { status: 'resolved', ...payload })
      ElMessage.success('缺陷已解决')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '解决失败')
    }
  }

  async function handleReject() {
    const current = detail.value?.status
    if (!current) return
    const comment = await promptStatusChangeComment(current, 'rejected')
    if (comment === null) return
    try {
      await changeBugStatus(bugId, { status: 'rejected', comment: comment || undefined })
      ElMessage.success('缺陷已拒绝')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '拒绝失败')
    }
  }

  async function handleClose() {
    const current = detail.value?.status
    if (!current) return
    const comment = await promptStatusChangeComment(current, 'closed')
    if (comment === null) return
    try {
      await changeBugStatus(bugId, { status: 'closed', comment: comment || undefined })
      ElMessage.success('缺陷已关闭')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '关闭失败')
    }
  }

  async function handleReopen() {
    const current = detail.value?.status
    if (!current) return
    const comment = await promptStatusChangeComment(current, 'active')
    if (comment === null) return
    try {
      await changeBugStatus(bugId, { status: 'active', comment: comment || undefined })
      ElMessage.success('缺陷已激活')
      load()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '激活失败')
    }
  }

  // ==================== Attachments ====================
  const attachments = ref<BugAttachment[]>([])
  const uploading = ref(false)

  async function loadAttachments() {
    try {
      attachments.value = await fetchBugAttachments(bugId)
    } catch {
      // 附件加载失败不阻塞详情展示
    }
  }

  async function handleAttachmentUpload(options: UploadRequestOptions) {
    const file = options.file
    if (file.size > MAX_FILE_SIZE) {
      ElMessage.warning(`「${file.name}」超过 10MB，无法上传`)
      return
    }
    uploading.value = true
    try {
      await uploadBugAttachment(bugId, file)
      ElMessage.success('附件已上传')
      loadAttachments()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '附件上传失败')
    } finally {
      uploading.value = false
    }
  }

  async function handleAttachmentDownload(item: BugAttachment) {
    try {
      await downloadBugAttachment(item.id, item.fileName)
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '附件下载失败')
    }
  }

  async function handleAttachmentDelete(item: BugAttachment) {
    try {
      await ElMessageBox.confirm(`确定删除附件「${item.fileName}」吗？`, '确认', { type: 'warning' })
    } catch {
      return
    }
    try {
      await deleteBugAttachment(item.id)
      ElMessage.success('附件已删除')
      loadAttachments()
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '附件删除失败')
    }
  }

  // ==================== Lifecycle ====================
  onMounted(() => {
    load()
    loadModuleTree()
    loadPlanOptions()
    loadAttachments()
  })

  return {
    // State
    loading,
    saving,
    detail,
    logs,
    memberOptions,
    resolveDialogVisible,
    form,
    // Computed
    isClosed,
    isActive,
    isResolved,
    isRejected,
    dirTree,
    planOptions,
    relatedPlanName,
    currentRelatedCaseId,
    caseDetail,
    caseDetailLoading,
    caseDetailRows,
    caseDocName,
    caseSelectorVisible,
    attachments,
    uploading,
    // Methods
    handleCaseSelected,
    loadCaseDetail,
    openCaseDocument,
    handleSave,
    handleConfirm,
    handleResolve,
    handleReject,
    handleClose,
    handleReopen,
    handleAttachmentUpload,
    handleAttachmentDownload,
    handleAttachmentDelete,
    // Router
    router,
    // Constants
    severityLabel,
    priorityLabel,
    severityType,
    priorityType,
    statusLabel,
    BUG_RESOLUTION_LABEL,
    BUG_STATUS_TAG_TYPE,
    BUG_TYPE_LABEL,
    // Helpers
    formatFileSize,
  }
}
