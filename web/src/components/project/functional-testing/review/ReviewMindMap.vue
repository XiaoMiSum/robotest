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
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import type { Minder, MinderNode } from '@/minder/types'
import { useMinderInstance } from '@/minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from '@/minder/useContextMenu'
import MinderContextMenu from '../minder/MinderContextMenu.vue'
import MinderNavigator from '../minder/MinderNavigator.vue'

const props = defineProps<{ reviewId: string; documentId?: string; removable?: boolean }>()

// 标记成功后通知详情页刷新进度，否则页头进度条需手动刷新才能更新
const emit = defineEmits<{ marked: []; removed: [] }>()

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
})

// 移除按钮可用态：评审未完成（详情页传入）且当前选中为已关联 case 节点
const canRemove = computed(() => props.removable === true && selectedType.value === 'case')

// ==================== 右键菜单 ====================
const {
  visible: menuVisible,
  pos: menuPos,
  onContextMenu,
  close: closeContextMenu,
} = useContextMenu({
  hasSelection: () => !!selectedNodeId.value,
  getSelectedNode: () =>
    getMinder()?.getSelectedNode?.() as ContextMenuAnchorNode | null | undefined,
})

// ==================== Bug 链接跳转 ====================
// 暴露给 kityminder 扩展节点渲染模板通过 ref 调用，避免事件冒泡干扰节点选中
function openBug(bugId: string) {
  window.open(`/workspace/projects/bugs/${bugId}`, '_blank')
}

// reload 供详情页同步快照后刷新画布（reviewId 不变，watch 不会触发）
// locateNode 供 AI 检查面板定位：当前文档含该快照节点则选中高亮，否则返回 false 由调用方提示
function locateNode(snapshotNodeId: string): boolean {
  const raw = getMinder()
  if (!raw) return false
  const m = raw as unknown as Minder
  let found: MinderNode | null = null
  m.getRoot().traverse((node) => {
    if (!found && node.data.id === snapshotNodeId) found = node
  })
  if (!found) return false
  m.select(found, true)
  return true
}

defineExpose({ openBug, reload: initMinder, locateNode })

// ==================== 生命周期 ====================
watch(() => [props.reviewId, props.documentId], initMinder)
onMounted(initMinder)
onBeforeUnmount(() => {
  invalidate()
  destroyMinder()
})
</script>

<template>
  <div v-loading="loading" class="mindmap-container">
    <!-- 评审工具栏 -->
    <div class="mindmap-toolbar">
      <el-button-group size="small">
        <el-button :type="reviewResult==='pass'?'success':''" @click="markReview('pass')">✅通过</el-button>
        <el-button :type="reviewResult==='fail'?'danger':''" @click="markReview('fail')">❌不通过</el-button>
        <el-button :type="reviewResult===null?'info':''" @click="markReview(null)">❓待评审</el-button>
      </el-button-group>
      <el-button size="small" @click="openComments">💬评论</el-button>
      <el-button v-if="removable" size="small" :disabled="!canRemove" @click="removeSelectedCase">🗑移除用例</el-button>
    </div>

    <!-- 脑图画布 -->
    <div
      ref="containerRef"
      class="minder-canvas"
      @contextmenu.prevent="onContextMenu"
    />

    <!-- 导航器：缩放条/定位根节点/抓手/缩略图/全屏 -->
    <MinderNavigator v-if="minder && !loading" :minder="minder" />

    <!-- 右键菜单 -->
    <MinderContextMenu
      v-if="menuVisible"
      :x="menuPos.x"
      :y="menuPos.y"
      @close="closeContextMenu"
    >
      <div class="mindmap-context-menu__subtitle">标记评审结果 ▸</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview('pass')">通过</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview('fail')">不通过</div>
      <div class="mindmap-context-menu__item mindmap-context-menu__item--indent" @click="markReview(null)">待评审</div>
      <div class="mindmap-context-menu__divider" />
      <div class="mindmap-context-menu__item" @click="openComments">添加评论</div>
      <div v-if="removable" class="mindmap-context-menu__item mindmap-context-menu__item--danger" @click="removeSelectedCase">从评审中移除</div>
    </MinderContextMenu>

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

/* 评论抽屉：body 撑满成 flex 列，列表滚动、输入区固定底部；
   drawer 会 teleport 到 body，scoped :deep 命中不了，须用 :global */
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
