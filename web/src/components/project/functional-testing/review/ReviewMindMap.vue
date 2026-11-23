<script setup lang="ts">
import { useReviewMindmapOps } from '@/composables/project/functional-testing/review/useReviewMindmapOps'
/**
 * ReviewMindMap 经本地 composable 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需响应用户标记/评论操作并即时提交，
 * 数据流与交互深度耦合，抽到 page 层会导致大量 props/emit 透传。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { formatDateTime } from '@/utils/format'
import type { ReviewRecord } from '@/types'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import { useMinderInstance } from '@/minder/useMinderInstance'
import { useNodeActionPopover, type NodeActionAnchorNode } from '@/minder/useNodeActionPopover'
import NodeActionPopover from '../minder/NodeActionPopover.vue'
import MinderNavigator from '../minder/MinderNavigator.vue'

const props = defineProps<{ reviewId: string; documentId?: string; removable?: boolean }>()

// 标记成功后通知详情页刷新进度，否则页头进度条需手动刷新才能更新
const emit = defineEmits<{ marked: []; removed: [] }>()

// 原生全屏只渲染全屏元素子树，默认 body 下的 teleport 浮层会消失
const popoverHostRef = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const syncFullscreen = () => {
  isFullscreen.value = Boolean(document.fullscreenElement)
}
onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))
const popoverTeleportTarget = computed(() =>
  isFullscreen.value && popoverHostRef.value ? popoverHostRef.value : 'body',
)

// 基座选中状态（id/type）之上的扩展字段：当前节点的评审标记
const reviewResult = ref<string | null>(null)

const {
  containerRef,
  loading,
  minder,
  selectedNodeId,
  selectedType,
  beginInit,
  isStale,
  invalidate,
  getMinder,
  getSelectedNodeData,
  updateSelectedState,
  destroyMinder,
} = useMinderInstance({
  onSelectionChange(data) {
    reviewResult.value = data ? (data.lastMark as string) || null : null
  },
})

const {
  commentVisible,
  comments,
  newComment,
  markReview,
  removeSelectedCase,
  openComments,
  addCommentFn,
  initMinder,
} = useReviewMindmapOps({
  containerRef,
  loading,
  minder,
  selectedNodeId,
  selectedType,
  beginInit,
  isStale,
  getMinder,
  getSelectedNodeData,
  updateSelectedState,
  destroyMinder,
  reviewResult,
  getReviewId: () => props.reviewId,
  getDocumentId: () => props.documentId,
  getRemovable: () => props.removable,
  onMarked: () => emit('marked'),
  onRemoved: () => emit('removed'),
  onNodeClick: handleNodeClick,
})

// 移除按钮可用态：评审未完成（详情页传入）且当前选中为已关联 case 节点
const canRemove = computed(() => props.removable === true && selectedType.value === 'case')

// 左键单击用例节点展开悬浮窗：按节点渲染框定位，避免浮层遮挡被点击节点
const nodePopover = useNodeActionPopover()

function handleNodeClick(node: NodeActionAnchorNode): void {
  const nodeId = node.data.id
  if (node.data.type !== 'case' || typeof nodeId !== 'string' || !nodeId) {
    nodePopover.close()
    return
  }
  const box = node.getRenderBox?.(undefined, 'screen')
  if (!box) {
    nodePopover.close()
    return
  }
  nodePopover.open(nodeId, { x: Math.round(box.x), y: Math.round(box.y + box.height + 4) })
}

function markFromPopover(mark: Parameters<typeof markReview>[0]): void {
  nodePopover.close()
  void markReview(mark)
}

function openCommentsFromPopover(): void {
  nodePopover.close()
  openComments()
}

function removeFromPopover(): void {
  nodePopover.close()
  void removeSelectedCase()
}

function isAiAdoptedRecord(record: ReviewRecord): boolean {
  return record.comment?.startsWith('[AI 建议]') === true
}

// ==================== Bug 链接跳转 ====================
// 暴露给 kityminder 扩展节点渲染模板通过 ref 调用，避免事件冒泡干扰节点选中
function openBug(bugId: string) {
  window.open(`/workspace/projects/bugs/${bugId}`, '_blank')
}

// reload 供详情页同步快照后刷新画布（reviewId 不变，watch 不会触发）；先收起浮层避免定位到旧节点
function reload(): void {
  nodePopover.close()
  void initMinder()
}
defineExpose({ openBug, reload })

// ==================== 生命周期 ====================
watch(() => [props.reviewId, props.documentId], reload)
onMounted(initMinder)
onBeforeUnmount(() => {
  invalidate()
  destroyMinder()
})
</script>

<template>
  <div ref="popoverHostRef" v-loading="loading" class="mindmap-container">
    <!-- 脑图画布：右键仅抑制浏览器原生菜单，不渲染自定义右键菜单 -->
    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent
    />

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 用例节点悬浮窗：仅点击 case 节点展开，入口水平并排、透明背景、link 按钮 -->
    <NodeActionPopover
      :to="popoverTeleportTarget"
      :visible="nodePopover.visible.value"
      :x="nodePopover.anchor.value.x"
      :y="nodePopover.anchor.value.y"
    >
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--pass': reviewResult === 'pass' }" @click="markFromPopover('pass')">✅通过</el-button>
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--fail': reviewResult === 'fail' }" @click="markFromPopover('fail')">❌不通过</el-button>
      <el-button link class="node-action-btn" :class="{ 'node-action-btn--pending': reviewResult === null }" @click="markFromPopover(null)">❓待评审</el-button>
      <el-button link class="node-action-btn" @click="openCommentsFromPopover">💬评论</el-button>
      <el-button v-if="removable" link class="node-action-btn node-action-btn--danger" :disabled="!canRemove" @click="removeFromPopover">🗑移除用例</el-button>
    </NodeActionPopover>

    <!-- 评论抽屉 -->
    <el-drawer v-model="commentVisible" title="评审记录" :size="380" class="comment-drawer">
      <div class="comment-list">
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-item__avatar">{{ c.reviewerName.charAt(0).toUpperCase() }}</div>
          <div class="comment-item__main">
            <div class="comment-item__header">
              <span class="comment-item__name">{{ c.reviewerName }}</span>
              <el-tag v-if="c.operationType === 'mark'" size="small" effect="light" round :type="c.mark === 'pass' ? 'success' : c.mark === 'fail' ? 'danger' : 'info'">
                {{ c.mark === 'pass' ? '标记通过' : c.mark === 'fail' ? '标记不通过' : '重置待评审' }}
              </el-tag>
              <el-tag v-if="isAiAdoptedRecord(c)" size="small" effect="light" round class="comment-item__ai-tag">AI</el-tag>
            </div>
            <p v-if="c.comment" class="comment-item__body">{{ c.comment }}</p>
            <div class="comment-item__time">{{ formatDateTime(c.createdAt) }}</div>
          </div>
        </div>
        <el-empty v-if="!comments.length" description="暂无评论或标记记录" :image-size="48" />
      </div>
      <div class="comment-input">
        <el-input
          v-model="newComment"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="输入评论，Enter 发送"
          @keydown.enter.prevent="addCommentFn"
        />
        <div class="comment-input__footer">
          <el-button type="primary" size="small" :disabled="!newComment.trim()" @click="addCommentFn">发送</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
@use '../minder/minder-base';

.node-action-btn {
  --el-button-text-color: var(--el-text-color-regular);

  &--pass { --el-button-text-color: var(--color-success); }
  &--fail { --el-button-text-color: var(--color-danger); }
  &--pending { --el-button-text-color: var(--el-color-info); }
  &--danger { --el-button-text-color: var(--color-danger); }
}

/* CODE-008 例外：评论抽屉 Teleport 到 body，scoped :deep 命中不了；
   规则限定在 .comment-drawer，避免污染其他抽屉。
   规则来源：docs/00-spec/10-engineering/01-frontend.md §8。 */
:global(.comment-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.comment-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-md) var(--space-lg);
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;

  & + .comment-item {
    border-top: 1px solid var(--el-border-color-lighter);
  }
}

.comment-item__avatar {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.comment-item__main {
  flex: 1;
  min-width: 0;
}

.comment-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-item__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.comment-item__ai-tag {
  --el-tag-bg-color: var(--el-color-primary-light-9);
  --el-tag-border-color: var(--el-color-primary-light-5);
  --el-tag-text-color: var(--el-color-primary);
  font-weight: 600;
}

.comment-item__body {
  margin: 6px 0 0;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 6px;
  word-break: break-word;
}

.comment-item__time {
  margin-top: 4px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.comment-input {
  flex-shrink: 0;
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--color-neutral-0);
}

.comment-input__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
