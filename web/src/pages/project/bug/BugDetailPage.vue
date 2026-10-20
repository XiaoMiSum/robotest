<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useBugDetail } from '@/composables/project/bug/useBugDetail'
import type { BugAttachment } from '@/types'
import { formatDateTime, formatShortId } from '@/utils/format'
import BugResolveDialog from '@/components/project/bug/BugResolveDialog.vue'
import BugSidebar from '@/components/project/bug/BugSidebar.vue'
import CaseSelector from '@/components/project/functional-testing/case/CaseSelector.vue'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import MarkdownView from '@/components/common/MarkdownView.vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const bugId = route.params.bugId as string

const {
  loading,
  saving,
  detail,
  logs,
  memberOptions,
  resolveDialogVisible,
  form,
  isClosed,
  isEditable,
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
} = useBugDetail({ bugId })

function handleClearCase() {
  form.relatedCaseId = ''
}

function handleOpenCaseSelector() {
  caseSelectorVisible.value = true
}

/** 侧栏取剩余可用空间的 90%，底部保留回弹余量（同需求详情页口径） */
const SIDE_HEIGHT_RATIO = 0.9
const MIN_SIDE_HEIGHT = 320

const layoutWrap = ref<HTMLDivElement>()
const sideTop = ref(0)
const viewportHeight = ref(typeof window === 'undefined' ? 0 : window.innerHeight)

function measure(): void {
  sideTop.value = layoutWrap.value?.getBoundingClientRect().top ?? 0
}

function handleResize(): void {
  viewportHeight.value = window.innerHeight
  measure()
}

// 侧栏页签高度只随窗口变化、不随内容变化，超出部分在页签内滚动
const sideHeight = computed(() =>
  Math.max(MIN_SIDE_HEIGHT, Math.floor((viewportHeight.value - sideTop.value) * SIDE_HEIGHT_RATIO)),
)

onMounted(() => {
  window.addEventListener('resize', handleResize)
  void nextTick(measure)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

// 侧栏在详情加载完成后才渲染，出现后补测一次顶距
watch(detail, async () => {
  await nextTick()
  measure()
})
</script>

<template>
  <div v-loading="loading" class="bug-detail">
    <!-- UI-BC-01：两级面包屑（缺陷管理 › 缺陷详情），回退入口是唯一可点击项；状态与操作区同行右对齐 -->
    <div class="bug-detail__header">
      <div class="bug-detail__breadcrumb">
        <router-link to="/workspace/projects/bugs">缺陷管理</router-link>
        <el-icon :size="13"><ArrowRight /></el-icon>
        <span>缺陷详情</span>
      </div>
      <div v-if="detail" class="bug-detail__actions">
        <el-tag :type="BUG_STATUS_TAG_TYPE[detail.status]" size="small" effect="light" round>{{ statusLabel[detail.status] }}</el-tag>
        <el-tag :type="detail.reopenCount > 0 ? 'danger' : 'info'" size="small" effect="plain" round>激活 {{ detail.reopenCount }} 次</el-tag>
        <el-button type="primary" @click="router.push('/workspace/projects/bugs/create')">
          <el-icon><Plus /></el-icon>提交缺陷
        </el-button>
      </div>
    </div>

    <template v-if="detail">
      <div class="bug-detail__title-row">
        <el-tag type="info" effect="light" round class="bug-detail__id-tag">{{ formatShortId(detail.id) }}</el-tag>
        <el-input
          v-if="isEditable"
          v-model="form.title"
          class="bug-detail__title-input"
          placeholder="缺陷标题"
          maxlength="300"
        />
        <el-tooltip v-else :content="detail.title" :disabled="detail.title.length <= 60" placement="bottom-start">
          <span class="bug-detail__title-text">{{ detail.title }}</span>
        </el-tooltip>
      </div>

      <div ref="layoutWrap" class="bug-detail__layout">
        <div class="bug-detail__main">
          <!-- 重现步骤直接放置（无卡片包裹）；resolved / closed 只读渲染 -->
          <MarkdownEditor v-if="isEditable" v-model="form.reproSteps" placeholder="重现步骤（支持 Markdown）" maximizable />
          <MarkdownView v-else-if="detail.reproSteps" :content="detail.reproSteps" />
          <el-empty v-else description="暂无重现步骤" :image-size="48" />

          <!-- 附件独立无 header 边框卡片，紧凑内边距；上传入口右对齐 link 按钮 -->
          <el-card shadow="never" class="bug-detail__attachments">
            <div v-if="isEditable" class="bug-detail__upload">
              <el-upload :show-file-list="false" :http-request="handleAttachmentUpload">
                <el-button size="small" link type="primary" :loading="uploading">
                  <el-icon><Upload /></el-icon>上传附件
                </el-button>
              </el-upload>
            </div>
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
                  <el-button v-if="isEditable" link type="danger" @click="handleAttachmentDelete(row as BugAttachment)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <BugSidebar
          v-if="detail"
          :detail="detail"
          :logs="logs"
          :form="form"
          :height="sideHeight"
          :is-editable="isEditable"
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
        <el-button v-if="isEditable" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </template>

    <BugResolveDialog
      v-model="resolveDialogVisible"
      :exclude-bug-id="bugId"
      @confirm="handleResolve"
    />

    <CaseSelector v-model="caseSelectorVisible" single @confirm="handleCaseSelected" />
  </div>
</template>

<style scoped lang="scss">
.bug-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

// UI-BC-02～04：面包屑排版与文字层级基准；底距与操作区对齐，避免行内错位
.bug-detail__breadcrumb,
.bug-detail__actions {
  margin-bottom: var(--space-sm);
}

.bug-detail__breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);

  a {
    color: var(--color-primary-500);
    font-weight: 500;
    text-decoration: none;
  }

  a:hover {
    color: var(--color-primary-600);
  }

  .el-icon {
    color: var(--color-neutral-400);
  }

  > :last-child {
    color: var(--color-neutral-900);
  }
}

.bug-detail__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.bug-detail__title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-top: var(--space-md);
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
  margin-top: var(--space-md);

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
}

.bug-detail__main {
  display: flex;
  flex-direction: column;
  // 编辑器与附件卡片之间留较大间距，区分两个展示块
  gap: var(--space-xl);
}

/* UI-SC-10：附件卡紧凑内边距，缩小上传入口与卡片顶部的距离 */
.bug-detail__attachments {
  --el-card-padding: var(--space-sm);
}

.bug-detail__upload {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-sm);
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
