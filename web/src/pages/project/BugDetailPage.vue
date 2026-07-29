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
  WorkspaceMember,
} from '@/types'
import { formatDateTime } from '@/utils/format'
import {
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_LABEL,
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

const form = reactive({
  title: '',
  severity: '' as string,
  priority: '' as string,
  bugType: '' as string,
  keywords: '',
  dueDate: '' as string,
  reproSteps: '',
  assigneeId: '' as string,
})

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
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
  loadAttachments()
})
</script>

<template>
  <div v-loading="loading" class="bug-detail">
    <el-page-header @back="router.push('/workspace/projects/bugs')">
      <template #content><span class="bug-detail__title">缺陷详情</span></template>
    </el-page-header>

    <el-card v-if="detail" shadow="never" class="bug-detail__card">
      <div class="bug-detail__status-bar">
        <el-tag :type="isActive ? 'danger' : isResolved ? 'success' : 'info'" effect="light">
          {{ statusLabel[detail.status] }}
        </el-tag>
        <el-tag v-if="detail.confirmed" size="small" type="warning" effect="plain">已确认</el-tag>
        <el-tag v-if="detail.reopenCount > 0" size="small" type="danger" effect="plain">
          重开 {{ detail.reopenCount }} 次
        </el-tag>
        <span class="bug-detail__status-spacer" />
        <el-button v-if="isActive && !detail.confirmed" size="small" @click="handleConfirm">确认</el-button>
        <el-button v-if="isActive" size="small" type="success" @click="resolveDialogVisible = true">解决</el-button>
        <el-button v-if="isResolved" size="small" type="info" @click="handleClose">关闭</el-button>
        <el-button v-if="isResolved || isClosed" size="small" type="danger" @click="handleReopen">激活</el-button>
      </div>

      <el-descriptions v-if="isResolved || isClosed" :column="2" size="small" border class="bug-detail__resolution">
        <el-descriptions-item label="解决方案">
          {{ detail.resolution ? BUG_RESOLUTION_LABEL[detail.resolution] : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="重复缺陷">
          <el-link
            v-if="detail.duplicateOfBugId"
            type="primary"
            :underline="false"
            @click="router.push(`/workspace/projects/bugs/${detail.duplicateOfBugId}`)"
          >
            查看原始缺陷
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="解决人">
          {{ detail.resolvedBy ? `${detail.resolvedBy.name}（${formatDateTime(detail.resolvedAt!)}）` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="关闭人">
          {{ detail.closedBy ? `${detail.closedBy.name}（${formatDateTime(detail.closedAt!)}）` : '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="96px" class="bug-detail__form">
        <el-form-item label="标题">
          <el-input v-if="!isClosed" v-model="form.title" />
          <span v-else class="bug-detail__text">{{ detail.title }}</span>
        </el-form-item>
        <el-form-item label="缺陷类型">
          <el-select v-if="!isClosed" v-model="form.bugType" style="width: 160px">
            <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
          <span v-else class="bug-detail__text">{{ BUG_TYPE_LABEL[detail.bugType] }}</span>
        </el-form-item>
        <el-form-item label="所属模块">
          <span class="bug-detail__text">{{ detail.moduleName ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-select v-if="!isClosed" v-model="form.severity" style="width: 160px">
            <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <el-tag v-else size="small" effect="light" round>{{ severityLabel[detail.severity] }}</el-tag>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-if="!isClosed" v-model="form.priority" style="width: 160px">
            <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
          </el-select>
          <span v-else class="bug-detail__text">{{ priorityLabel[detail.priority] }}</span>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-if="!isClosed" v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
          <span v-else class="bug-detail__text">{{ detail.dueDate ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-if="!isClosed" v-model="form.keywords" maxlength="255" />
          <span v-else class="bug-detail__text">{{ detail.keywords || '-' }}</span>
        </el-form-item>
        <el-form-item label="重现步骤">
          <MarkdownEditor v-if="!isClosed" v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown）" />
          <MarkdownView v-else-if="detail.reproSteps" :content="detail.reproSteps" />
          <span v-else class="bug-detail__text">-</span>
        </el-form-item>
        <el-form-item label="指派给">
          <el-select v-if="!isClosed" v-model="form.assigneeId" filterable style="width: 240px">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
          <span v-else class="bug-detail__text">{{ detail.assignee?.name ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="报告人">
          <span class="bug-detail__text">{{ detail.reporter.name }}</span>
        </el-form-item>
        <el-form-item v-if="!isClosed">
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.push('/workspace/projects/bugs')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="detail" shadow="never" class="bug-detail__logs">
      <template #header>
        <div class="bug-detail__attachment-header">
          <span class="bug-detail__section">附件</span>
          <el-upload
            v-if="!isClosed"
            :show-file-list="false"
            :http-request="handleAttachmentUpload"
          >
            <el-button size="small" :loading="uploading">上传附件</el-button>
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

    <el-card v-if="logs.length" shadow="never" class="bug-detail__logs">
      <template #header><span class="bug-detail__section">操作记录</span></template>
      <el-timeline>
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

    <BugResolveDialog
      v-model="resolveDialogVisible"
      :exclude-bug-id="bugId"
      @confirm="handleResolve"
    />
  </div>
</template>

<style scoped lang="scss">
.bug-detail__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.bug-detail__card {
  margin-top: var(--space-lg);
}

.bug-detail__status-bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}

.bug-detail__status-spacer {
  flex: 1;
}

.bug-detail__resolution {
  margin-bottom: var(--space-lg);
}

.bug-detail__form {
  max-width: 640px;
}

.bug-detail__text {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  line-height: 1.6;
}

.bug-detail__logs {
  margin-top: var(--space-lg);
}

.bug-detail__attachment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bug-detail__section {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.bug-detail__log-content {
  color: var(--color-neutral-500);
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
}
</style>
