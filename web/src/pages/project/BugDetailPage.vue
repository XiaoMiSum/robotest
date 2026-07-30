<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import {
  assignBug,
  changeBugStatus,
  confirmBug,
  deleteBugAttachment,
  downloadBugAttachment,
  fetchBugAttachments,
  fetchModuleTree,
  getBugDetail,
  getBugLogs,
  updateBug,
  uploadBugAttachment,
} from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type {
  BugAttachment,
  BugDetail,
  BugLog,
  BugResolution,
  TestCaseModule,
  WorkspaceMember,
} from '@/types'
import { formatDateTime, formatShortId } from '@/utils/format'
import {
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  promptStatusChangeComment,
} from '@/utils/bugStatus'
import BugResolveDialog from '@/components/project/BugResolveDialog.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import MarkdownView from '@/components/common/MarkdownView.vue'

const route = useRoute()
const router = useRouter()
const bugId = route.params.bugId as string

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
  keywords: '',
  dueDate: '' as string,
  reproSteps: '',
  assigneeId: '' as string,
})

const moduleTree = ref<TestCaseModule[]>([])
async function loadModuleTree() {
  try {
    moduleTree.value = await fetchModuleTree()
  } catch { /* ignore */ }
}

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
const severityType: Record<string, 'danger' | 'warning' | 'success' | 'info'> = { fatal: 'danger', serious: 'warning', general: 'info', minor: 'success' }
const statusLabel = BUG_STATUS_LABEL

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
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载缺陷详情失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!detail.value) return
  saving.value = true
  try {
    await updateBug(bugId, {
      title: form.title.trim(),
      severity: form.severity as BugDetail['severity'],
      priority: form.priority as BugDetail['priority'],
      bugType: form.bugType as BugDetail['bugType'],
      moduleId: form.moduleId || undefined,
      keywords: form.keywords.trim() || undefined,
      dueDate: form.dueDate || undefined,
      reproSteps: form.reproSteps.trim() || undefined,
    })
    // 处理人变更走专用指派接口，后端会校验工作空间成员并写指派日志
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

// ==================== 状态操作 ====================

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

// ==================== 附件 ====================

const MAX_FILE_SIZE = 10 * 1024 * 1024
const attachments = ref<BugAttachment[]>([])
const uploading = ref(false)

async function loadAttachments() {
  try {
    attachments.value = await fetchBugAttachments(bugId)
  } catch {
    // 附件加载失败不阻塞详情展示
  }
}

function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
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

onMounted(() => {
  load()
  loadModuleTree()
  loadAttachments()
})
</script>

<template>
  <div v-loading="loading" class="bug-detail">
    <div class="bug-detail__topbar">
      <el-page-header class="bug-detail__back" @back="router.push('/workspace/projects/bugs')">
        <template #content>
          <div v-if="detail" class="bug-detail__header-bar">
            <el-tag type="info" effect="light" round class="bug-detail__id-tag">{{ formatShortId(detail.id) }}</el-tag>
            <el-input
              v-if="!isClosed"
              v-model="form.title"
              class="bug-detail__title-input"
              placeholder="缺陷标题"
              maxlength="300"
            />
            <span v-else class="bug-detail__title-text">{{ detail.title }}</span>
          </div>
          <span v-else class="bug-detail__page-title">缺陷详情</span>
        </template>
      </el-page-header>
      <div v-if="detail" class="bug-detail__topbar-right">
        <el-tag :type="BUG_STATUS_TAG_TYPE[detail.status]" effect="dark" round>{{ statusLabel[detail.status] }}</el-tag>
        <el-tag :type="detail.reopenCount > 0 ? 'danger' : 'info'" effect="plain" round>激活 {{ detail.reopenCount }} 次</el-tag>
        <el-button type="primary" @click="router.push('/workspace/projects/bugs/create')">
          <el-icon><Plus /></el-icon>新增Bug
        </el-button>
      </div>
    </div>

    <template v-if="detail">
      <div class="bug-detail__layout">
        <div class="bug-detail__main">
          <el-card shadow="never">
            <template #header><span class="bug-detail__section">重现步骤</span></template>
            <MarkdownEditor v-if="!isClosed" v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown）" />
            <MarkdownView v-else-if="detail.reproSteps" :content="detail.reproSteps" />
            <el-empty v-else description="暂无重现步骤" :image-size="48" />
          </el-card>

          <el-card shadow="never">
            <template #header>
              <div class="bug-detail__card-header">
                <span class="bug-detail__section">附件</span>
                <el-upload
                  v-if="!isClosed"
                  :show-file-list="false"
                  :http-request="handleAttachmentUpload"
                >
                  <el-button size="small" :loading="uploading">
                    <el-icon><Upload /></el-icon>上传附件
                  </el-button>
                </el-upload>
              </div>
            </template>
            <el-empty v-if="!attachments.length" description="暂无附件" :image-size="48" />
            <el-table v-else :data="attachments" size="small">
              <el-table-column label="文件名" prop="fileName" min-width="200" show-overflow-tooltip />
              <el-table-column label="大小" width="100">
                <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
              </el-table-column>
              <el-table-column label="上传人" prop="uploaderName" width="120" />
              <el-table-column label="上传时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleAttachmentDownload(row as BugAttachment)">下载</el-button>
                  <el-button v-if="!isClosed" link type="danger" @click="handleAttachmentDelete(row as BugAttachment)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card v-if="logs.length" shadow="never">
            <template #header><span class="bug-detail__section">操作记录</span></template>
            <el-timeline class="bug-detail__timeline">
              <el-timeline-item
                v-for="log in logs"
                :key="log.id"
                :timestamp="formatDateTime(log.createdAt)"
                placement="top"
              >
                <strong>{{ log.operatorName }}</strong> {{ log.operationType }}
                <span v-if="log.content" class="bug-detail__log-content">{{ log.content }}</span>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </div>

        <div class="bug-detail__side">
          <el-card v-if="isResolved || isClosed" shadow="never" class="bug-detail__resolution">
            <template #header><span class="bug-detail__section">解决信息</span></template>
            <el-descriptions :column="1" size="small">
              <el-descriptions-item label="解决方案">
                <el-tag v-if="detail.resolution" size="small" type="success" effect="light" round>
                  {{ BUG_RESOLUTION_LABEL[detail.resolution] }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="detail.duplicateOfBugId" label="重复缺陷">
                <el-link
                  type="primary"
                  :underline="false"
                  @click="router.push(`/workspace/projects/bugs/${detail.duplicateOfBugId}`)"
                >
                  查看原始缺陷
                </el-link>
              </el-descriptions-item>
              <el-descriptions-item label="解决人">
                {{ detail.resolvedBy ? `${detail.resolvedBy.name}（${formatDateTime(detail.resolvedAt!)}）` : '-' }}
              </el-descriptions-item>
              <el-descriptions-item v-if="isClosed" label="关闭人">
                {{ detail.closedBy ? `${detail.closedBy.name}（${formatDateTime(detail.closedAt!)}）` : '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card shadow="never">
            <template #header><span class="bug-detail__section">属性</span></template>
            <el-form label-position="top" class="bug-detail__props">
              <el-form-item label="缺陷类型">
                <el-select v-if="!isClosed" v-model="form.bugType">
                  <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
                </el-select>
                <span v-else class="bug-detail__text">{{ BUG_TYPE_LABEL[detail.bugType] }}</span>
              </el-form-item>
              <el-form-item label="所属模块">
                <el-tree-select
                  v-if="!isClosed"
                  v-model="form.moduleId"
                  :data="moduleTree"
                  :props="{ label: 'name', children: 'children' }"
                  node-key="id"
                  check-strictly
                  placeholder="选择所属模块"
                />
                <span v-else class="bug-detail__text">{{ detail.moduleName ?? '-' }}</span>
              </el-form-item>
              <el-form-item label="严重等级">
                <el-select v-if="!isClosed" v-model="form.severity">
                  <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key">
                    <span class="bug-detail__severity-dot" :class="`bug-detail__severity-dot--${key}`" />{{ label }}
                  </el-option>
                </el-select>
                <el-tag v-else :type="severityType[detail.severity]" size="small" effect="light" round>
                  {{ severityLabel[detail.severity] }}
                </el-tag>
              </el-form-item>
              <el-form-item label="优先级">
                <el-select v-if="!isClosed" v-model="form.priority">
                  <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
                </el-select>
                <span v-else class="bug-detail__text">{{ priorityLabel[detail.priority] }}</span>
              </el-form-item>
              <el-form-item label="截止日期">
                <el-date-picker
                  v-if="!isClosed"
                  v-model="form.dueDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择截止日期"
                  class="bug-detail__date"
                />
                <span v-else class="bug-detail__text">{{ detail.dueDate ?? '-' }}</span>
              </el-form-item>
              <el-form-item label="关键词">
                <el-input v-if="!isClosed" v-model="form.keywords" maxlength="255" placeholder="多个关键词用空格分隔" />
                <span v-else class="bug-detail__text">{{ detail.keywords || '-' }}</span>
              </el-form-item>
              <el-form-item label="指派给">
                <el-select v-if="!isClosed" v-model="form.assigneeId" filterable>
                  <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
                </el-select>
                <span v-else class="bug-detail__text">{{ detail.assignee?.name ?? '-' }}</span>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
      </div>

      <div class="bug-detail__footer">
        <el-button v-if="isActive && !detail.confirmed" @click="handleConfirm">确认</el-button>
        <el-button v-if="isActive" type="success" @click="resolveDialogVisible = true">解决</el-button>
        <el-button v-if="isActive" type="warning" @click="handleReject">拒绝</el-button>
        <el-button v-if="isResolved || isRejected" type="info" @click="handleClose">关闭</el-button>
        <el-button v-if="isResolved || isRejected || isClosed" type="danger" @click="handleReopen">激活</el-button>
        <el-button v-if="!isClosed" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </template>

    <BugResolveDialog
      v-model="resolveDialogVisible"
      :exclude-bug-id="bugId"
      @confirm="handleResolve"
    />
  </div>
</template>

<style scoped lang="scss">
.bug-detail__page-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

// 顶栏自控 flex 布局：page-header 内 content 撑满受组件内部结构影响不可靠，
// 右侧组独立于 page-header 放置，保证状态/激活次数/新增按钮始终贴最右
.bug-detail__topbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.bug-detail__back {
  flex: 1;
  min-width: 0;
}

.bug-detail__back :deep(.el-page-header__content) {
  flex: 1;
  min-width: 0;
}

.bug-detail__topbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.bug-detail__header-bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
}

.bug-detail__id-tag {
  flex-shrink: 0;
  font-family: var(--font-family-mono, monospace);
}

// 标题弱化输入框边框，聚焦时才显现，兼顾展示观感与可编辑性
.bug-detail__title-input {
  flex: 1;
  min-width: 0;
}

.bug-detail__title-input :deep(.el-input__wrapper) {
  box-shadow: none;
  padding-left: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-neutral-900);
  transition: box-shadow var(--transition-fast);

  &:hover,
  &.is-focus {
    box-shadow: 0 0 0 1px var(--color-neutral-300) inset;
    padding-left: 11px;
  }
}

.bug-detail__title-input :deep(.el-input__inner) {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-neutral-900);
}

.bug-detail__title-text {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-neutral-900);
  line-height: 32px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bug-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--space-lg);
  align-items: start;
  margin-top: var(--space-lg);

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.bug-detail__main,
.bug-detail__side {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.bug-detail__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-800);
}

.bug-detail__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bug-detail__resolution :deep(.el-descriptions__label) {
  color: var(--color-neutral-500);
}

.bug-detail__props :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--color-neutral-600);
  margin-bottom: var(--space-xs);
}

.bug-detail__props :deep(.el-form-item) {
  margin-bottom: var(--space-md);
}

.bug-detail__props :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.bug-detail__props :deep(.el-select),
.bug-detail__props :deep(.el-tree-select) {
  width: 100%;
}

// el-date-picker 根节点是 fragment，scoped 的 data-v 属性不会落到控件上，须经 :deep 命中；宽度还受组件级 CSS 变量控制
.bug-detail__props :deep(.bug-detail__date) {
  width: 100%;
  --el-date-editor-width: 100%;
}

.bug-detail__text {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  line-height: 1.6;
}

.bug-detail__severity-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: var(--space-sm);
  vertical-align: middle;

  &--fatal { background: var(--color-bug-fatal); }
  &--serious { background: var(--color-bug-serious); }
  &--general { background: var(--color-bug-general); }
  &--minor { background: var(--color-bug-minor); }
}

.bug-detail__timeline {
  padding-left: var(--space-xs);
}

.bug-detail__log-content {
  color: var(--color-neutral-500);
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
}

// 底部粘性操作栏：透明背景居中悬浮，栏体不拦截点击，仅按钮可交互
.bug-detail__footer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: center;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  background: transparent;
  pointer-events: none;

  .el-button {
    pointer-events: auto;
    box-shadow: var(--shadow-md);
  }
}

</style>
