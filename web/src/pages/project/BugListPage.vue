<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type { CSSProperties } from 'vue'
import { useRouter } from 'vue-router'
import { DynamicSizeList, ElMessage, ElMessageBox } from 'element-plus'
import { assignBug, changeBugStatus, confirmBug, fetchBugs } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import { useAuthStore } from '@/stores/auth'
import type { BugListItem, BugPriority, BugResolution, BugSeverity, BugStatus, BugType, WorkspaceMember } from '@/types'
import { formatDateTime } from '@/utils/format'
import {
  BUG_STATUS_LABEL,
  BUG_STATUS_TAG_TYPE,
  BUG_TYPE_LABEL,
  getValidTargetStatuses,
  promptStatusChangeComment,
} from '@/utils/bugStatus'
import BugResolveDialog from '@/components/project/BugResolveDialog.vue'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const bugs = ref<BugListItem[]>([])
const total = ref(0)
const viewMode = ref<'list' | 'board'>('list')

const query = reactive({
  status: '' as BugStatus | '',
  severity: '' as BugSeverity | '',
  priority: '' as BugPriority | '',
  bugType: '' as BugType | '',
  keyword: '',
  pageNo: 1,
  pageSize: 20,
})

// 快捷过滤：与当前登录用户相关的缺陷
type QuickFilter = '' | 'reported' | 'assigned' | 'resolved' | 'closed'
const quickFilter = ref<QuickFilter>('')
const quickFilterOptions: { value: QuickFilter; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'reported', label: '我新建的' },
  { value: 'assigned', label: '指派给我的' },
  { value: 'resolved', label: '我修复的' },
  { value: 'closed', label: '我关闭的' },
]

function quickFilterParams(): { reporterId?: string; assigneeId?: string; resolvedBy?: string; closedBy?: string } {
  const uid = authStore.user?.id
  if (!quickFilter.value || !uid) return {}
  switch (quickFilter.value) {
    case 'reported': return { reporterId: uid }
    case 'assigned': return { assigneeId: uid }
    case 'resolved': return { resolvedBy: uid }
    default: return { closedBy: uid }
  }
}

const severityLabel: Record<string, string> = { fatal: '致命', serious: '严重', general: '一般', minor: '轻微' }
const priorityLabel: Record<string, string> = { high: '高', medium: '中', low: '低' }
const statusLabel = BUG_STATUS_LABEL
const severityType: Record<string, 'danger' | 'warning' | 'success' | 'info'> = { fatal: 'danger', serious: 'warning', general: 'info', minor: 'success' }

async function loadBugs() {
  loading.value = true
  try {
    const page = await fetchBugs({
      status: query.status || undefined,
      severity: query.severity || undefined,
      priority: query.priority || undefined,
      bugType: query.bugType || undefined,
      keyword: query.keyword || undefined,
      ...quickFilterParams(),
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    bugs.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载缺陷列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 任何显式搜索都以当前关键词为准，取消在途的防抖定时器避免重复请求
  clearKeywordTimer()
  searchedKeyword = query.keyword
  if (viewMode.value === 'board') {
    loadBoard()
    return
  }
  query.pageNo = 1
  loadBugs()
}

function handleReset() {
  query.status = ''
  query.severity = ''
  query.priority = ''
  query.bugType = ''
  query.keyword = ''
  quickFilter.value = ''
  handleSearch()
}

// ==================== 标题搜索与展开筛选 ====================
// 标题输入：回车/失焦立即搜索，停止输入 1 秒后自动搜索

let keywordTimer: ReturnType<typeof setTimeout> | null = null
// 记录最近一次已生效的关键词，失焦/回车与防抖多路触发时跳过重复请求
let searchedKeyword = ''

function clearKeywordTimer() {
  if (keywordTimer) {
    clearTimeout(keywordTimer)
    keywordTimer = null
  }
}

function handleKeywordSearch() {
  clearKeywordTimer()
  if (query.keyword === searchedKeyword) return
  handleSearch()
}

watch(() => query.keyword, () => {
  clearKeywordTimer()
  keywordTimer = setTimeout(handleKeywordSearch, 1000)
})

onUnmounted(clearKeywordTimer)

// 展开筛选浮层：悬浮展示更多条件，不挤占筛选行布局
const filtersExpanded = ref(false)

// 已生效的展开条件数量，收起时通过角标提示用户存在隐藏筛选
const advancedFilterCount = computed(
  () => [query.status, query.bugType, query.severity, query.priority].filter(Boolean).length,
)

function handleAdvancedSearch() {
  filtersExpanded.value = false
  handleSearch()
}

// 看板仅展示核心处理流三列，已拒绝缺陷在列表视图查看
type BoardStatus = Extract<BugStatus, 'active' | 'resolved' | 'closed'>
const boardStatuses: BoardStatus[] = ['active', 'resolved', 'closed']

// ==================== 看板分列分页 ====================
// 看板列固定高度，每列独立按状态分页，滚动到底部追加加载

interface BoardColumn {
  list: BugListItem[]
  total: number
  pageNo: number
  loading: boolean
  finished: boolean
  // 请求版本号：重置列时递增，用于丢弃在途的过期响应
  requestId: number
}

function createBoardColumn(): BoardColumn {
  return { list: [], total: 0, pageNo: 1, loading: false, finished: false, requestId: 0 }
}

const boardColumns = reactive<Record<BoardStatus, BoardColumn>>({
  active: createBoardColumn(),
  resolved: createBoardColumn(),
  closed: createBoardColumn(),
})

// 看板卡片标题单行省略，高度恒定；DynamicSizeList 的 itemSize 必须为函数，且组件不测量 DOM，尺寸完全由此决定
const BOARD_CARD_SIZE = 76
const boardItemSize = () => BOARD_CARD_SIZE
// 虚拟列表 height 必须是数字 px（内部参与偏移运算，传百分比会 NaN），由 ResizeObserver 实测列体高度写入
const boardBodyHeight = ref(400)
const boardRef = ref<HTMLElement>()

async function loadBoardColumn(status: BoardStatus, reset = false) {
  const col = boardColumns[status]
  if (!reset && (col.loading || col.finished)) return
  if (reset) {
    col.requestId += 1
    col.list = []
    col.total = 0
    col.pageNo = 1
    col.finished = false
  }
  // 状态筛选与列不匹配时该列必为空，无需请求
  if (query.status && query.status !== status) {
    col.finished = true
    col.loading = false
    return
  }
  const requestId = col.requestId
  const pageNo = col.pageNo
  col.loading = true
  try {
    const page = await fetchBugs({
      status,
      severity: query.severity || undefined,
      priority: query.priority || undefined,
      bugType: query.bugType || undefined,
      keyword: query.keyword || undefined,
      ...quickFilterParams(),
      pageNo,
      pageSize: query.pageSize,
    })
    if (requestId !== col.requestId) return
    col.list = pageNo === 1 ? page.list : [...col.list, ...page.list]
    col.total = page.total
    col.pageNo = pageNo + 1
    col.finished = col.list.length >= page.total
  } catch (err) {
    if (requestId !== col.requestId) return
    ElMessage.error(err instanceof Error ? err.message : '加载缺陷列表失败')
  } finally {
    if (requestId === col.requestId) {
      col.loading = false
      // 内容未填满视口且仍有数据时自动续拉，兜底超高屏首页不溢出导致 end-reached 不触发
      if (!col.finished && col.list.length * BOARD_CARD_SIZE < boardBodyHeight.value) {
        loadBoardColumn(status)
      }
    }
  }
}

function loadBoard() {
  boardStatuses.forEach((status) => loadBoardColumn(status, true))
}

function handleBoardEndReached(status: BoardStatus, direction: string) {
  // DynamicSizeList 到达边缘瞬间发出该事件，触底即加载下一页
  if (direction === 'bottom') loadBoardColumn(status)
}

// 虚拟列表需要数字像素高度，实测列体高度并随窗口/布局变化更新（rAF 去抖）
let boardResizeObserver: ResizeObserver | null = null
let boardMeasureRaf = 0

function measureBoardBody() {
  const body = boardRef.value?.querySelector('.bug-board__col-body') as HTMLElement | null
  if (body) boardBodyHeight.value = body.clientHeight
}

function scheduleBoardMeasure() {
  cancelAnimationFrame(boardMeasureRaf)
  boardMeasureRaf = requestAnimationFrame(measureBoardBody)
}

function setupBoardResize() {
  if (boardResizeObserver || !boardRef.value) return
  boardResizeObserver = new ResizeObserver(scheduleBoardMeasure)
  boardResizeObserver.observe(boardRef.value)
  measureBoardBody()
}

function teardownBoardResize() {
  boardResizeObserver?.disconnect()
  boardResizeObserver = null
  cancelAnimationFrame(boardMeasureRaf)
}

onUnmounted(teardownBoardResize)

// ==================== 看板拖拽 ====================

const draggingBug = ref<BugListItem | null>(null)
// 拖起卡片时计算的合法目标列，驱动高亮/置灰
const validDropStatuses = ref<Set<BugStatus>>(new Set())

function handleDragStart(bug: BugListItem) {
  draggingBug.value = bug
  validDropStatuses.value = new Set(getValidTargetStatuses(bug.status as BugStatus))
}

function handleDragEnd() {
  draggingBug.value = null
  validDropStatuses.value = new Set()
}

function isValidDropTarget(status: BugStatus): boolean {
  return validDropStatuses.value.has(status)
}

async function handleDrop(targetStatus: BoardStatus) {
  const bug = draggingBug.value
  handleDragEnd()
  if (!bug || bug.status === targetStatus) return
  if (!isValidDropTargetFor(bug, targetStatus)) return

  // 拖到「已解决」列需选择解决方案，弹对话框处理
  if (targetStatus === 'resolved') {
    openResolveDialog(bug)
    return
  }

  const comment = await promptStatusChangeComment(bug.status as BugStatus, targetStatus)
  if (comment === null) return
  try {
    await changeBugStatus(bug.id, { status: targetStatus, comment: comment || undefined })
    ElMessage.success('状态已更新')
    // 只刷新源列与目标列，避免整板重载
    loadBoardColumn(bug.status as BoardStatus, true)
    loadBoardColumn(targetStatus, true)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '状态变更失败')
  }
}

// 看板拖到「已解决」弹出的解决对话框
const resolveDialogVisible = ref(false)
const resolvingBug = ref<BugListItem | null>(null)

async function handleResolveConfirm(payload: {
  resolution: BugResolution
  duplicateOfBugId?: string
  comment: string
}) {
  const bug = resolvingBug.value
  resolvingBug.value = null
  if (!bug) return
  try {
    await changeBugStatus(bug.id, { status: 'resolved', ...payload })
    ElMessage.success('缺陷已解决')
    // 解决对话框可能由列表行操作或看板拖拽触发，按当前视图刷新
    if (viewMode.value === 'board') {
      loadBoardColumn(bug.status as BoardStatus, true)
      loadBoardColumn('resolved', true)
    } else {
      loadBugs()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '解决失败')
  }
}

// drop 时 draggingBug 已清空，用传入的 bug 重新校验合法性
function isValidDropTargetFor(bug: BugListItem, targetStatus: BugStatus): boolean {
  return getValidTargetStatuses(bug.status as BugStatus).includes(targetStatus)
}

// ==================== 列表行操作 ====================

function openResolveDialog(bug: BugListItem) {
  resolvingBug.value = bug
  resolveDialogVisible.value = true
}

async function handleStatusAction(bug: BugListItem, targetStatus: BugStatus, successMsg: string) {
  const comment = await promptStatusChangeComment(bug.status as BugStatus, targetStatus)
  if (comment === null) return
  try {
    await changeBugStatus(bug.id, { status: targetStatus, comment: comment || undefined })
    ElMessage.success(successMsg)
    loadBugs()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '状态变更失败')
  }
}

async function handleConfirmBug(bug: BugListItem) {
  try {
    await ElMessageBox.confirm('确认该缺陷有效并需要处理吗？', '确认缺陷', { type: 'info' })
  } catch {
    return
  }
  try {
    await confirmBug(bug.id)
    ElMessage.success('缺陷已确认')
    loadBugs()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '确认失败')
  }
}

// 指派对话框：成员列表懒加载一次
const assignDialogVisible = ref(false)
const assigningBug = ref<BugListItem | null>(null)
const assigneeId = ref('')
const assigning = ref(false)
const memberOptions = ref<WorkspaceMember[]>([])

async function openAssignDialog(bug: BugListItem) {
  assigningBug.value = bug
  assigneeId.value = bug.assignee?.id ?? ''
  assignDialogVisible.value = true
  if (!memberOptions.value.length) {
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberOptions.value = page.list
    } catch {
      // 加载失败不阻塞，下拉为空时用户可重新打开重试
    }
  }
}

async function handleAssignConfirm() {
  const bug = assigningBug.value
  if (!bug) return
  if (!assigneeId.value) {
    ElMessage.warning('请选择处理人')
    return
  }
  assigning.value = true
  try {
    await assignBug(bug.id, assigneeId.value)
    ElMessage.success('已指派')
    assignDialogVisible.value = false
    loadBugs()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '指派失败')
  } finally {
    assigning.value = false
  }
}

function hasMoreActions(bug: BugListItem): boolean {
  return bug.status !== 'closed'
}

function handleMoreAction(command: string, bug: BugListItem) {
  if (command === 'confirm') handleConfirmBug(bug)
  else if (command === 'reopen') handleStatusAction(bug, 'active', '缺陷已激活')
  else if (command === 'assign') openAssignDialog(bug)
}

// 切换视图时刷新对应数据源，看板数据与列表分页相互独立
watch(viewMode, (mode) => {
  if (mode === 'board') {
    loadBoard()
    // 列体元素需渲染到 DOM 后才能测量高度
    nextTick(setupBoardResize)
  } else {
    teardownBoardResize()
    loadBugs()
  }
})

onMounted(loadBugs)
</script>

<template>
  <div class="bug-page">

    <el-card shadow="never" class="bug-page__filters">
      <el-form :inline="true" class="bug-page__filter-form" @submit.prevent>
        <el-form-item>
          <el-radio-group v-model="quickFilter" size="small" @change="handleSearch">
            <el-radio-button v-for="opt in quickFilterOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="query.keyword"
            placeholder="搜索标题"
            clearable
            style="width: 360px"
            @keyup.enter="handleKeywordSearch"
            @blur="handleKeywordSearch"
            @clear="handleKeywordSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-popover v-model:visible="filtersExpanded" placement="bottom-start" :width="300" trigger="click">
            <template #reference>
              <el-badge :value="advancedFilterCount" :hidden="!advancedFilterCount" type="primary">
                <el-button link type="primary">
                  更多筛选<el-icon><component :is="filtersExpanded ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <el-form label-width="70px" class="bug-page__advanced-form" @submit.prevent>
              <el-form-item label="状态">
                <el-select v-model="query.status" placeholder="全部" clearable>
                  <el-option v-for="(label, key) in statusLabel" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="query.bugType" placeholder="全部" clearable>
                  <el-option v-for="(label, key) in BUG_TYPE_LABEL" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="严重等级">
                <el-select v-model="query.severity" placeholder="全部" clearable>
                  <el-option v-for="(label, key) in severityLabel" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="优先级">
                <el-select v-model="query.priority" placeholder="全部" clearable>
                  <el-option v-for="(label, key) in priorityLabel" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <div class="bug-page__advanced-actions">
                <el-button type="primary" @click="handleAdvancedSearch">
                  <el-icon><Search /></el-icon>查询
                </el-button>
                <el-button @click="handleReset">重置</el-button>
              </div>
            </el-form>
          </el-popover>
        </el-form-item>
        <el-form-item class="bug-page__filter-spacer" />
        <el-form-item>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="list">列表</el-radio-button>
            <el-radio-button value="board">看板</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="router.push('/workspace/projects/bugs/create')">
            <el-icon><Plus /></el-icon>提交缺陷
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="viewMode === 'list'" v-loading="loading" shadow="never">
      <el-table :data="bugs" row-key="id">
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="router.push(`/workspace/projects/bugs/${row.id}`)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ BUG_TYPE_LABEL[row.bugType as BugType] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="严重等级" width="100">
          <template #default="{ row }">
            <el-tag :type="severityType[row.severity]" size="small" effect="light" round>{{ severityLabel[row.severity] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">{{ priorityLabel[row.priority] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="BUG_STATUS_TAG_TYPE[row.status as BugStatus]" size="small" effect="light" round>{{ statusLabel[row.status as BugStatus] }}</el-tag>
            <el-tag v-if="row.confirmed" size="small" type="warning" effect="plain" class="bug-page__confirmed-tag">已确认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" width="100">
          <template #default="{ row }">{{ row.reporter?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="处理人" width="100">
          <template #default="{ row }">{{ row.assignee?.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/workspace/projects/bugs/${row.id}`)">详情</el-button>
            <el-button v-if="row.status === 'active'" link type="success" @click="openResolveDialog(row as BugListItem)">解决</el-button>
            <el-button v-if="row.status === 'active'" link type="warning" @click="handleStatusAction(row as BugListItem, 'rejected', '缺陷已拒绝')">拒绝</el-button>
            <el-button v-if="row.status === 'resolved' || row.status === 'rejected'" link type="info" @click="handleStatusAction(row as BugListItem, 'closed', '缺陷已关闭')">关闭</el-button>
            <el-button v-if="row.status === 'closed'" link type="danger" @click="handleStatusAction(row as BugListItem, 'active', '缺陷已激活')">激活</el-button>
            <el-dropdown
              v-if="hasMoreActions(row as BugListItem)"
              class="bug-page__more"
              @command="(cmd: string) => handleMoreAction(cmd, row as BugListItem)"
            >
              <el-button link type="primary">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status === 'active' && !row.confirmed" command="confirm">确认</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'resolved' || row.status === 'rejected'" command="reopen">激活</el-dropdown-item>
                  <el-dropdown-item command="assign">指派</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <div class="bug-page__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadBugs"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <div v-else ref="boardRef" class="bug-board">
      <div
        v-for="status in boardStatuses"
        :key="status"
        class="bug-board__column"
        :class="{
          'bug-board__column--valid': draggingBug && isValidDropTarget(status),
          'bug-board__column--invalid': draggingBug && !isValidDropTarget(status) && draggingBug.status !== status,
        }"
        @dragover.prevent
        @drop="handleDrop(status)"
      >
        <div class="bug-board__col-header">
          <span class="bug-board__col-title">{{ statusLabel[status] }}</span>
          <span class="bug-board__col-count">{{ boardColumns[status].total }}</span>
        </div>
        <div class="bug-board__col-body" @scroll.passive="handleBoardScroll(status, $event)">
          <div
            v-for="bug in boardColumns[status].list"
            :key="bug.id"
            class="bug-board__card"
            draggable="true"
            @dragstart="handleDragStart(bug)"
            @dragend="handleDragEnd"
            @click="router.push(`/workspace/projects/bugs/${bug.id}`)"
          >
            <div class="bug-board__card-title">{{ bug.title }}</div>
            <div class="bug-board__card-meta">
              <el-tag :type="severityType[bug.severity]" size="small" effect="light" round>{{ severityLabel[bug.severity] }}</el-tag>
              <span v-if="bug.assignee" class="bug-board__card-assignee">{{ bug.assignee.name }}</span>
            </div>
          </div>
          <div v-if="boardColumns[status].loading" class="bug-board__col-loading">加载中…</div>
          <el-empty v-if="!boardColumns[status].list.length && !boardColumns[status].loading" description="" :image-size="30" />
        </div>
      </div>
    </div>

    <BugResolveDialog
      v-model="resolveDialogVisible"
      :exclude-bug-id="resolvingBug?.id"
      @confirm="handleResolveConfirm"
    />

    <el-dialog v-model="assignDialogVisible" title="指派处理人" width="420px">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="处理人" required>
          <el-select v-model="assigneeId" filterable placeholder="选择处理人" class="bug-page__assign-select">
            <el-option v-for="m in memberOptions" :key="m.userId" :label="m.username" :value="m.userId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssignConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.bug-page__filters {
  margin-bottom: var(--space-lg);
}

.bug-page__filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.bug-page__filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

.bug-page__filter-spacer {
  flex: 1;
}

// 浮层表单元素统一撑满，动作按钮右对齐
.bug-page__advanced-form :deep(.el-select) {
  width: 100%;
}

.bug-page__advanced-actions {
  display: flex;
  justify-content: flex-end;
}

.bug-page__confirmed-tag {
  margin-left: 4px;
}

// 与前一个 link 按钮保持间距，对齐基线
.bug-page__more {
  margin-left: 12px;
  vertical-align: middle;
}

// 弹窗表单元素统一撑满
.bug-page__assign-select {
  width: 100%;
}

.bug-page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}

.bug-board {
  display: flex;
  gap: var(--space-md);
  overflow-x: auto;
  // 固定看板高度（视口减去顶栏、内容区边距与筛选卡片），列体在约束内滚动触底加载
  height: calc(100vh - var(--header-height) - 138px);
  min-height: 400px;
}

.bug-board__column {
  flex: 1;
  min-width: 200px;
  background: var(--color-neutral-50);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-neutral-200);
  transition: all var(--transition-fast);
}

.bug-board__column--valid {
  border-color: var(--color-primary-400);
  background: var(--color-primary-50);
}

.bug-board__column--invalid {
  opacity: 0.5;
}

.bug-board__col-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-neutral-200);
}

.bug-board__col-title {
  font-weight: 600;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-700);
}

.bug-board__col-count {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  background: var(--color-neutral-200);
  border-radius: var(--radius-full);
  padding: 1px 6px;
  font-weight: 500;
}

.bug-board__col-body {
  padding: var(--space-sm);
  flex: 1;
  overflow-y: auto;
}

.bug-board__col-loading {
  text-align: center;
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
  padding: var(--space-xs) 0;
}

.bug-board__card {
  background: var(--color-neutral-0);
  border-radius: var(--radius-md);
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-sm);
  cursor: grab;
  border: 1px solid var(--color-neutral-200);
  transition: all var(--transition-fast);

  &:hover {
    box-shadow: var(--shadow-sm);
    border-color: var(--color-primary-200);
  }

  &:active {
    cursor: grabbing;
  }
}

.bug-board__card-title {
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--color-neutral-800);
  margin-bottom: var(--space-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-board__card-meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.bug-board__card-assignee {
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-500);
}
</style>
