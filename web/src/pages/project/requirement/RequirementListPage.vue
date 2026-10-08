<script setup lang="ts">
import { computed, ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createRequirement } from '@/services/project'
import { formatDateTime } from '@/utils/format'
import type { AiGenerationScopeItem, RequirementPriority } from '@/types'
import type { RequirementRow } from '@/composables/project/requirement/requirementPresentation'
import MarkdownEditor from '@/components/common/MarkdownEditor.vue'
import GenerationConfigDialog from '@/components/project/ai/GenerationConfigDialog.vue'
import RequirementImportDialog from '@/components/project/requirement/RequirementImportDialog.vue'
import SelectionConfigDialog from '@/components/project/ai/SelectionConfigDialog.vue'
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
  canViewAiTasks,
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

const priorityOptions: { value: RequirementPriority; label: string }[] = [
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
]

function openDetail(id: string): void {
  void router.push(`/workspace/projects/requirements/${id}`)
}

/** el-table 插槽行是宽松类型，此处收窄为行视图模型（no-unsafe 后端未知键本就不读取） */
function asRow(row: unknown): RequirementRow {
  return row as RequirementRow
}

// ==================== 新建抽屉 ====================
const drawerVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  description: '',
  moduleId: '',
  systemVersion: '',
  priority: '',
  ownerId: '',
  tags: [] as string[],
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入需求标题', trigger: 'blur' },
    { max: 300, message: '标题不能超过 300 字符', trigger: 'blur' },
  ],
  systemVersion: [{ max: 50, message: '版本不能超过 50 字符', trigger: 'blur' }],
}

function openCreate(): void {
  form.title = ''
  form.description = ''
  form.moduleId = ''
  form.systemVersion = ''
  form.priority = ''
  form.ownerId = ''
  form.tags = []
  drawerVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const created = await createRequirement({
      title: form.title.trim(),
      description: form.description,
      moduleId: form.moduleId || undefined,
      systemVersion: form.systemVersion.trim() || undefined,
      priority: (form.priority || undefined) as RequirementPriority | undefined,
      ownerId: form.ownerId || undefined,
      tags: form.tags,
    })
    drawerVisible.value = false
    ElMessage.success('需求已创建')
    void router.push(`/workspace/projects/requirements/${created.id}`)
  } catch (err) {
    ElMessage.error(err instanceof Error && err.message ? err.message : '创建需求失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="requirement-list">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">需求管理</h1>
        <p class="page-head__desc">沉淀需求条目，作为用例设计与评审的输入</p>
      </div>
      <div class="page-head__actions">
        <!-- 任务中心为需求域任务入口（交互 07 §1），按 ai:task 显隐 -->
        <el-button v-if="canViewAiTasks" @click="router.push('/workspace/projects/ai/tasks')">
          <el-icon><List /></el-icon>任务中心
        </el-button>
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
          :content="selectedRows.length > 0 ? '对已选需求发起 AI 测试设计生成' : '请先勾选需求'"
          placement="bottom"
        >
          <span>
            <el-button :disabled="selectedRows.length === 0" @click="openGeneration">
              <el-icon><MagicStick /></el-icon>AI 生成测试设计
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

    <!-- 筛选栏：入口角标显示已设置条件数（交互设计 06 §2.1.2） -->
    <div class="filter-bar">
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
      <el-badge v-if="filterCount > 0" :value="filterCount" class="filter-bar__badge" />
    </div>

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

    <el-skeleton v-if="!hasLoaded && !loadError && loading" :rows="6" animated class="list-skeleton" />

    <el-card v-else shadow="never" class="requirement-list__card">
      <el-table
        v-loading="loading"
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
        <el-table-column label="覆盖状态" width="110">
          <template #default="{ row }">
            <!-- 追溯侧未接入时恒为「—」，不渲染徽标 -->
            <el-tag v-if="row.coverageMeta" :type="row.coverageMeta.tagType" size="small" effect="light">
              {{ row.coverageMeta.label }}
            </el-tag>
            <span v-else class="requirement-list__muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ownerText }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canConfirm || canEdit" label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="canConfirm && row.status !== 'archived'"
              link
              type="primary"
              @click="handleConfirm(asRow(row))"
            >确认</el-button>
            <el-button
              v-if="canConfirm && row.status !== 'archived'"
              link
              type="warning"
              @click="handleArchive(asRow(row))"
            >归档</el-button>
            <el-dropdown
              v-if="canEdit"
              trigger="click"
              @command="(command: string) => (command === 'edit' ? openDetail(row.id) : handleUnarchive(asRow(row)))"
            >
              <el-button link type="primary">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'archived'" command="unarchive">
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

      <div class="requirement-list__pager">
        <span class="requirement-list__pager-total">共 {{ total }} 条</span>
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

    <el-drawer
      v-model="drawerVisible"
      title="新建需求"
      size="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="300" show-word-limit placeholder="请输入需求标题" />
        </el-form-item>
        <el-form-item label="描述">
          <MarkdownEditor v-model="form.description" height="260px" placeholder="需求描述（支持 Markdown）" />
        </el-form-item>
        <el-form-item label="所属模块">
          <el-tree-select
            v-model="form.moduleId"
            :data="moduleTree"
            check-strictly
            clearable
            placeholder="可选"
            style="width: 100%"
          />
        </el-form-item>
        <div class="create-form__row">
          <el-form-item label="版本" prop="systemVersion">
            <el-input v-model="form.systemVersion" maxlength="50" show-word-limit placeholder="可选" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="form.priority" clearable placeholder="可选" style="width: 100%">
              <el-option v-for="option in priorityOptions" :key="option.value" v-bind="option" />
            </el-select>
          </el-form-item>
          <el-form-item label="负责人">
            <el-select v-model="form.ownerId" clearable filterable placeholder="可选" style="width: 100%">
              <el-option
                v-for="member in memberOptions"
                :key="member.userId"
                :value="member.userId"
                :label="member.name || member.username"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="可选，回车创建"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-drawer>
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

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.filter-bar__badge :deep(.el-badge__content) {
  background-color: var(--color-primary-500);
}

.list-error {
  margin-bottom: var(--space-md);
}

.list-skeleton {
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-md);
}

.requirement-list__card {
  margin-bottom: var(--space-md);
}

.requirement-list__code {
  color: var(--color-neutral-500);
  font-family: var(--font-family-mono, monospace);
  font-size: var(--font-size-sm);
}

.requirement-list__muted {
  color: var(--color-neutral-400);
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
  margin-top: var(--space-md);
}

.requirement-list__pager-total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.create-form__row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-sm);
}
</style>
