<script setup lang="ts">
import { useBugDetail } from '@/composables/project/bug/useBugDetail'
import type { BugAttachment } from '@/types'
import { formatDateTime, formatShortId } from '@/utils/format'
import BugResolveDialog from '@/components/project/bug/BugResolveDialog.vue'
import BugSidebar from '@/components/project/bug/BugSidebar.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import MarkdownView from '@/components/common/MarkdownView.vue'

const props = defineProps<{ bugId: string }>()

const {
  loading,
  saving,
  detail,
  logs,
  memberOptions,
  resolveDialogVisible,
  form,
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
  router,
  severityLabel,
  priorityLabel,
  severityType,
  priorityType,
  statusLabel,
  BUG_RESOLUTION_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  formatFileSize,
} = useBugDetail({ bugId: props.bugId })

function handleClearCase() {
  form.relatedCaseId = ''
}

function handleOpenCaseSelector() {
  caseSelectorVisible.value = true
}
</script>

<template>
  <div v-loading="loading" class="bug-detail">
    <div class="bug-detail__topbar">
      <el-link class="bug-detail__back" underline="never" @click="router.push('/workspace/projects/bugs')">
        <el-icon><Back /></el-icon><span>返回</span>
      </el-link>
      <el-divider direction="vertical" />
      <template v-if="detail">
        <el-tag type="info" effect="light" round class="bug-detail__id-tag">{{ formatShortId(detail.id) }}</el-tag>
        <el-input
          v-if="!isClosed"
          v-model="form.title"
          class="bug-detail__title-input"
          placeholder="缺陷标题"
          maxlength="300"
        />
        <el-tooltip v-else :content="detail.title" :disabled="detail.title.length <= 60" placement="bottom-start">
          <span class="bug-detail__title-text">{{ detail.title }}</span>
        </el-tooltip>
        <div class="bug-detail__topbar-right">
          <el-tag :type="BUG_STATUS_TAG_TYPE[detail.status]" size="small" effect="light" round>{{ statusLabel[detail.status] }}</el-tag>
          <el-tag :type="detail.reopenCount > 0 ? 'danger' : 'info'" size="small" effect="plain" round>激活 {{ detail.reopenCount }} 次</el-tag>
          <el-button type="primary" @click="router.push('/workspace/projects/bugs/create')">
            <el-icon><Plus /></el-icon>提交缺陷
          </el-button>
        </div>
      </template>
      <span v-else class="bug-detail__page-title">缺陷详情</span>
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

        <BugSidebar
          v-if="detail"
          :detail="detail"
          :form="form"
          :is-closed="isClosed"
          :is-resolved="isResolved"
          :is-rejected="isRejected"
          :dir-tree="dirTree"
          :plan-options="planOptions"
          :related-plan-name="relatedPlanName"
          :member-options="memberOptions"
          :severity-label="severityLabel"
          :priority-label="priorityLabel"
          :severity-type="severityType"
          :priority-type="priorityType"
          :bug-resolution-label="BUG_RESOLUTION_LABEL"
          :bug-type-label="BUG_TYPE_LABEL"
          :current-related-case-id="currentRelatedCaseId"
          :case-detail="caseDetail"
          :case-detail-loading="caseDetailLoading"
          :case-detail-rows="caseDetailRows"
          :case-doc-name="caseDocName"
          :load-case-detail="loadCaseDetail"
          :open-case-document="openCaseDocument"
          @open-case-selector="handleOpenCaseSelector"
          @clear-case="handleClearCase"
        />
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
      :exclude-bug-id="props.bugId"
      @confirm="handleResolve"
    />

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />
  </div>
</template>

<style scoped lang="scss">
.bug-detail__page-title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.bug-detail__topbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.bug-detail__back {
  flex-shrink: 0;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);

  .el-icon {
    margin-right: var(--space-xs);
  }
}

.bug-detail__topbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
  margin-left: auto;
}

.bug-detail__id-tag {
  flex-shrink: 0;
  font-family: var(--font-mono);
}

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

.bug-detail__main {
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

.bug-detail__timeline {
  padding-left: var(--space-xs);
}

.bug-detail__log-content {
  color: var(--color-neutral-500);
  margin-left: var(--space-sm);
  font-size: var(--font-size-xs);
}

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
