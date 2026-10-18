<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { formatDateTime } from '@/utils/format'
import type { AiGenerationScopeItem } from '@/types'
import type { RequirementRow } from '@/composables/project/requirement/requirementPresentation'
import GenerationConfigDialog from '@/components/project/ai/GenerationConfigDialog.vue'
import RequirementImportDialog from '@/components/project/requirement/RequirementImportDialog.vue'
import SelectionConfigDialog from '@/components/project/ai/SelectionConfigDialog.vue'
import AiTaskEntryButton from '@/components/project/ai/AiTaskEntryButton.vue'
import { useRequirementList } from '@/composables/project/requirement/useRequirementList'

const router = useRouter()

const {
  loading,
  hasLoaded,
  loadError,
  rows,
  total,
  pageNo,
  pageSize,
  filters,
  filterCount,
  moduleTree,
  memberOptions,
  versionOptions,
  canCreate,
  canConfirm,
  canEdit,
  canLaunchAi,
  aiAvailable,
  selectedRows,
  handleSelectionChange,
  load,
  retry,
  search,
  resetFilters,
  changePage,
  changePageSize,
  handleConfirm,
  handleArchive,
  handleUnarchive,
} = useRequirementList()

/** 首屏走骨架、筛选刷新走整卡遮罩，互斥避免骨架上再叠 loading 层（交互 06 §2.1.3） */
const showSkeleton = computed(() => !hasLoaded.value && !loadError.value && loading.value)

// ==================== AI 生成 / 圈选发起 ====================
const generationDialogVisible = ref(false)
const selectionDialogVisible = ref(false)

/** 导入经任务框架执行，AI 总开关关闭或未配模型时入口隐藏（总册 4.5） */
const importDialogVisible = ref(false)
const canImport = computed(() => canCreate.value && aiAvailable.value)

/** 已选行归一为发起范围条目（草稿 / 已变更 / 已归档在对话框内置灰） */
const aiScope = computed<AiGenerationScopeItem[]>(() =>
  selectedRows.value.map((row) => ({
    id: row.id,
    code: row.code,
    title: row.title,
    status: row.status,
  })),
)

function openGeneration(): void {
  if (selectedRows.value.length === 0) return
  generationDialogVisible.value = true
}

function openSelection(): void {
  if (selectedRows.value.length === 0) return
  selectionDialogVisible.value = true
}

const statusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'confirmed', label: '已确认' },
  { value: 'changed', label: '已变更' },
  { value: 'archived', label: '已归档' },
]

function openDetail(id: string): void {
  void router.push(`/workspace/projects/requirements/${id}`)
}

/** 「更多」下拉统一分发：编辑即进详情页，归档 / 取消归档走二次确认（SRS 1.2 行操作） */
function handleRowCommand(command: string, row: unknown): void {
  const target = asRow(row)
  if (command === 'edit') openDetail(target.id)
  if (command === 'archive') void handleArchive(target)
  if (command === 'unarchive') void handleUnarchive(target)
}

/** el-table 插槽行是宽松类型，此处收窄为行视图模型（no-unsafe 后端未知键本就不读取） */
function asRow(row: unknown): RequirementRow {
  return row as RequirementRow
}

// ==================== 新建入口 ====================
/** 新建为独立页面（交互 06 §2.5），列表页只保留跳转入口 */
function openCreate(): void {
  void router.push('/workspace/projects/requirements/create')
}
</script>

<template>
  <main class="requirement-list">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">需求列表</h1>
        <p class="page-head__desc">沉淀项目需求，驱动用例设计与 AI 生成</p>
      </div>
      <div class="page-head__actions">
        <!-- 任务中心就近入口（交互 07 §1）：组件内按 ai:task 显隐 -->
        <AiTaskEntryButton />
        <el-tooltip
          v-if="canLaunchAi"
          :content="selectedRows.length > 0 ? '对已选需求发起 AI 圈选建议' : '请先勾选需求'"
          placement="bottom"
        >
          <span>
            <el-button :disabled="selectedRows.length === 0" @click="openSelection">
              <el-icon><Files /></el-icon>AI 圈选建议
            </el-button>
          </span>
        </el-tooltip>
        <el-tooltip
          v-if="canLaunchAi"
          :content="selectedRows.length > 0 ? '对已选需求生成模块、脑图用例文档与测试用例' : '请先勾选需求'"
          placement="bottom"
        >
          <span>
            <el-button :disabled="selectedRows.length === 0" @click="openGeneration">
              <el-icon><MagicStick /></el-icon>AI 生成用例
            </el-button>
          </span>
        </el-tooltip>
        <el-button v-if="canImport" @click="importDialogVisible = true">
          <el-icon><Upload /></el-icon>导入需求
        </el-button>
        <el-button v-if="canCreate" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建需求
        </el-button>
      </div>
    </header>

    <!-- 列表错误：提示条 + 重试，不展示空态以免误导（UI-PAGE-11） -->
    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="list-error"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="retry">重试</el-button>
      </template>
    </el-alert>

    <!-- UI-SC-10：卡片内边距归零使表格贴边，刷新走整卡遮罩（交互 06 §2.1.3） -->
    <el-card v-loading="loading && hasLoaded" shadow="never" class="requirement-list__card">
      <!-- 筛选栏：入卡片工具栏，入口角标显示已设置条件数（交互 06 §2.1.2） -->
      <div class="requirement-list__toolbar">
        <el-select
          v-model="filters.status"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          placeholder="状态"
          style="width: 150px"
          @change="search"
        >
          <el-option v-for="option in statusOptions" :key="option.value" v-bind="option" />
        </el-select>
        <el-tree-select
          v-model="filters.moduleIds"
          :data="moduleTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          multiple
          check-strictly
          show-checkbox
          clearable
          check-on-click-node
          placeholder="所属模块"
          style="width: 180px"
          @change="search"
        />
        <el-select
          v-model="filters.ownerId"
          clearable
          filterable
          placeholder="负责人"
          style="width: 150px"
          @change="search"
        >
          <el-option
            v-for="member in memberOptions"
            :key="member.userId"
            :value="member.userId"
            :label="member.name || member.username"
          />
        </el-select>
        <el-select
          v-model="filters.systemVersion"
          filterable
          allow-create
          clearable
          placeholder="版本"
          style="width: 150px"
          @change="search"
        >
          <el-option v-for="version in versionOptions" :key="version" :value="version" :label="version" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="编号或标题"
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>查询
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-badge v-if="filterCount > 0" :value="filterCount" class="requirement-list__badge" />
        <span class="requirement-list__count">共 {{ total }} 条</span>
      </div>

      <el-skeleton v-if="showSkeleton" :rows="6" animated class="requirement-list__skeleton" />

      <el-table
        v-else
        :data="rows"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <!-- 选择列服务 AI 发起（交互 2.1），入口隐藏时无消费方不渲染 -->
        <el-table-column v-if="canLaunchAi" type="selection" width="36" />
        <el-table-column label="编号" width="110">
          <template #default="{ row }">
            <span class="requirement-list__code">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click.stop="openDetail(row.id)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="所属模块" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.moduleText }}</template>
        </el-table-column>
        <el-table-column label="版本" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.versionText }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.statusMeta.tagType"
              size="small"
              effect="light"
              :class="{ 'status-tag--archived': row.statusMeta.archived }"
            >
              {{ row.statusMeta.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ownerText }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <!-- 归档入口随 requirement:confirm，故下拉显隐由 edit / confirm 任一权限决定（SRS 1.2 行操作） -->
        <el-table-column v-if="canConfirm || canEdit" label="操作" width="200" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="canConfirm && row.status !== 'archived'"
              link
              type="primary"
              @click="handleConfirm(asRow(row))"
            >确认</el-button>
            <el-dropdown
              v-if="canEdit || (canConfirm && row.status !== 'archived')"
              trigger="click"
              class="requirement-list__more"
              @command="(command: string) => handleRowCommand(command, row)"
            >
              <el-button link type="primary">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canEdit" command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item
                    v-if="canConfirm && row.status !== 'archived'"
                    command="archive"
                    style="color: var(--el-color-warning)"
                  >归档</el-dropdown-item>
                  <el-dropdown-item v-if="canEdit && row.status === 'archived'" command="unarchive">
                    取消归档
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            v-if="filterCount > 0"
            description="无匹配结果"
            :image-size="80"
          >
            <el-button @click="resetFilters">重置筛选</el-button>
          </el-empty>
          <el-empty v-else description="暂无需求" :image-size="80">
            <el-button v-if="canCreate" type="primary" @click="openCreate">新建需求</el-button>
            <el-button v-if="canImport" @click="importDialogVisible = true">导入需求</el-button>
          </el-empty>
        </template>
      </el-table>

      <div v-if="!showSkeleton" class="requirement-list__pager">
        <span class="requirement-list__pager-total">共 {{ total }} 条 · 每页 {{ pageSize }} 条</span>
        <el-pagination
          layout="prev, pager, next, sizes"
          :total="total"
          :current-page="pageNo"
          :page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </el-card>

    <GenerationConfigDialog
      v-model="generationDialogVisible"
      :requirements="aiScope"
      :module-tree="moduleTree"
      @stale="load"
    />
    <SelectionConfigDialog
      v-model="selectionDialogVisible"
      :requirements="aiScope"
    />
    <RequirementImportDialog v-model="importDialogVisible" />
  </main>
</template>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--block-gap);
}

.page-head__title {
  margin: 0;
  color: var(--color-neutral-900);
  font-size: var(--font-size-2xl);
  font-weight: 650;
  letter-spacing: -0.01em;
}

.page-head__desc {
  margin: var(--space-xs) 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.page-head__actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.list-error {
  margin-bottom: var(--space-md);
}

/* UI-SC-10：body 内边距归零，留白由工具栏/分页条承担 */
.requirement-list__card {
  --el-card-padding: 0;
}

/* UI-SC-10：筛选工具栏是固定控件，独立留白并与表格以分割线分隔 */
.requirement-list__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.requirement-list__badge :deep(.el-badge__content) {
  background-color: var(--color-primary-500);
}

.requirement-list__count {
  margin-left: auto;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.requirement-list__skeleton {
  padding: 14px 20px;
}

.requirement-list__code {
  color: var(--color-neutral-500);
  font-family: var(--font-family-mono, monospace);
  font-size: var(--font-size-sm);
}

.requirement-list__muted {
  color: var(--color-neutral-400);
}

/* 下拉根节点包了一层，既不匹配 EP 按钮的 vertical-align:middle 也不匹配 `.el-button + .el-button` 的 12px 间距，逐项补齐 */
.requirement-list__more {
  vertical-align: middle;

  & + .el-button,
  .el-button + & {
    margin-left: 12px;
  }
}

// archived 徽标取中性色（视觉设计 4.1），经组件 CSS 变量覆盖（UI-DS-09）
.status-tag--archived {
  --el-tag-text-color: var(--color-neutral-400);
  --el-tag-bg-color: var(--color-neutral-50);
  --el-tag-border-color: var(--color-neutral-300);
}

.requirement-list__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 14px 20px;
  border-top: 1px solid var(--color-neutral-100);
}

.requirement-list__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}
</style>
