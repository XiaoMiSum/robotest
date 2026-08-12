<script setup lang="ts">
/**
 * ReviewMindMap 直接调用 services 而非通过 props 接收数据，
 * 因为脑图组件承担"容器组件"角色：需响应用户标记/评论操作并即时提交，
 * 数据流与交互深度耦合，抽到 page 层会导致大量 props/emit 透传。
 * 设计文档第 13 节代码骨架同样在组件内直接调用 API。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getReviewSnapshotTree,
  getReviewPlannedCases,
  submitReviewRecord,
  getNodeReviewRecords,
  updateReviewCases,
} from '@/services/project'
import type { PlannedCases, ReviewMark, ReviewRecord } from '@/types'
import { formatDateTime } from '@/utils/format'
// window.kity / window.kityminder 的类型声明在 minder/types.ts 中统一维护
import { reviewNodeToKm } from './minder/adapter'
import type { Minder, MinderNode } from './minder/types'
import { loadMinderEngine } from './minder/loader'
import { useMinderInstance } from './minder/useMinderInstance'
import { useContextMenu, type ContextMenuAnchorNode } from './minder/useContextMenu'
import MinderContextMenu from './minder/MinderContextMenu.vue'
import MinderNavigator from './minder/MinderNavigator.vue'

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

// 评论抽屉
const commentVisible = ref(false)
const comments = ref<ReviewRecord[]>([])
const newComment = ref('')

// ==================== 初始化 ====================
async function initMinder() {
  if (!containerRef.value || !props.reviewId) return
  const token = beginInit()
  loading.value = true
  destroyMinder()
  try {
    // documentId 限定单文档快照；不传时后端返回多文档多根，仅取首个，页面应始终传入
    const tree = await getReviewSnapshotTree(props.reviewId, props.documentId || undefined)
    const root = tree.length ? reviewNodeToKm(tree[0]) : { data: { text: '空快照' }, children: [] }
    const kmData = { root, template: 'default', theme: 'fresh-green' }

    const km = await loadMinderEngine()
    // 异步等待期间组件可能已卸载或已切换目标，过期结果直接丢弃
    if (isStale(token) || !containerRef.value) return

    // 快照只读展示，裸 minder 即可，无需编辑内核
    const instance: unknown = new km.Minder({ renderTo: containerRef.value })
    minder.value = instance
    const m = instance as Record<string, (...args: unknown[]) => unknown>
    m.importJson(kmData)

    // 禁用画布编辑以防止用户修改快照原始数据
    m.disable?.()

    m.on('selectionchange', updateSelectedState)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载脑图失败')
  } finally {
    loading.value = false
  }
}

// ==================== 评审操作 ====================
async function markReview(mark: ReviewMark | null) {
  if (!props.reviewId || !selectedNodeId.value) return
  // 仅 case 节点可标记
  if (selectedType.value !== 'case' && mark !== null) {
    ElMessage.warning('仅用例节点可标记评审结果')
    return
  }
  try {
    await submitReviewRecord(props.reviewId, {
      snapshotNodeId: selectedNodeId.value,
      operationType: 'mark',
      // 后端以显式 pending 表示重置回待评审（落库 last_mark = null）
      mark: mark ?? 'pending',
    })
    reviewResult.value = mark
    const data = getSelectedNodeData()
    if (data) { data.lastMark = mark; data.reviewStatus = mark ? { result: mark } : null }
    getMinder()?.refresh?.()
    ElMessage.success(mark ? `已标记${mark === 'pass' ? '通过' : '不通过'}` : '已重置为待评审')
    emit('marked')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交标记失败')
  }
}

// 移除选中用例：仅已关联 case 节点可移除，剔除后走全量覆盖接口（后端按新列表重刷快照关联）
async function removeSelectedCase() {
  if (!props.removable) return
  if (selectedType.value !== 'case') {
    ElMessage.warning('仅关联用例节点可移除')
    return
  }
  const data = getSelectedNodeData()
  const originalNodeId = data?.originalNodeId as string | undefined
  // 快照含文档全部节点，仅关联节点才在规划列表中，未关联的 case 无关联可删
  if (!originalNodeId || data?.isAssociated !== true) {
    ElMessage.warning('该用例未关联，无需移除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定从该评审中移除用例「${data.text as string}」吗？移除后该用例及其评审标记将不再展示，历史标记记录保留作审计。`,
      '移除用例',
      { type: 'warning' },
    )
  } catch { return }
  try {
    const planned = await getReviewPlannedCases(props.reviewId)
    const next = planned
      .map((doc) => ({
        documentId: doc.documentId,
        // 剔除目标用例；该文档剩余用例为空时整文档移除（后端删文档快照并清理空目录）
        caseIds: doc.caseIds.filter((id) => id !== originalNodeId),
      }))
      .filter((doc) => doc.caseIds.length > 0) as PlannedCases[]
    await updateReviewCases(props.reviewId, next)
    ElMessage.success('已移除用例')
    emit('removed')
    await initMinder()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移除用例失败')
  }
}

async function openComments() {
  if (!props.reviewId || !selectedNodeId.value) {
    ElMessage.warning('请先选中一个节点')
    return
  }
  commentVisible.value = true
  try {
    comments.value = await getNodeReviewRecords(props.reviewId, selectedNodeId.value)
  } catch {
    comments.value = []
  }
}

async function addCommentFn() {
  if (!newComment.value.trim() || !props.reviewId || !selectedNodeId.value) return
  try {
    await submitReviewRecord(props.reviewId, {
      snapshotNodeId: selectedNodeId.value,
      operationType: 'comment',
      comment: newComment.value.trim(),
    })
    ElMessage.success('评论已发送')
    newComment.value = ''
    comments.value = await getNodeReviewRecords(props.reviewId, selectedNodeId.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '发送评论失败')
  }
}

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
@use './minder/minder-base';

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
  background: #fff;
}

.comment-input__footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
