<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectListPage } from '@/composables/workspace/useProjectListPage'
import {
  archiveProject,
  createProject,
  deleteProject,
  setDefaultProject,
  updateProject,
} from '@/services/workspace'
import type { Project, ProjectStatus } from '@/types'
import { isWorkspaceAdmin } from '@/utils/workspaceRole'
import { formatDate } from '@/utils/format'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Monitor, Plus, Promotion, Search, StarFilled } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const {
  projects,
  total,
  counts,
  keyword,
  status,
  pageNo,
  pageSize,
  error,
  isInitialLoading,
  isRefreshing,
  isWorkspaceEmpty,
  currentStatusLabel,
  loadProjects,
  handleSearch,
  handleClear,
  clearFilters,
  changeStatus,
  changePage,
  changePageSize,
  retry,
  enterProject,
} = useProjectListPage()

const statusOptions: { value: ProjectStatus; label: string }[] = [
  { value: 'active', label: '活跃' },
  { value: 'archived', label: '已归档' },
]

const isAdmin = computed(() => isWorkspaceAdmin(authStore.activeWorkspace?.workspaceRole ?? ''))
const currentUserId = computed(() => authStore.user?.id ?? '')
const workspaceDescription = computed(() => {
  const workspaceName = authStore.activeWorkspace?.name
  return workspaceName ? `${workspaceName}空间下的全部项目` : '当前工作空间下的全部项目'
})

const formDialogVisible = ref(false)
const formDialogTitle = ref('新建项目')
const editingProject = ref<Project | null>(null)
const formRef = ref<FormInstance>()
const formSubmitting = ref(false)
const form = reactive({
  name: '',
  description: '',
  startTime: '' as string,
  endTime: '' as string,
})
const formRules: FormRules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { max: 100, message: '名称最多 100 个字符', trigger: 'blur' },
  ],
}

function toProject(row: unknown): Project {
  return row as Project
}

function canEdit(row: unknown): boolean {
  const project = toProject(row)
  return isAdmin.value || project.createdBy?.id === currentUserId.value
}

function handleRowClick(row: unknown): void {
  enterProject(toProject(row))
}

function enterProjectRow(row: unknown): void {
  enterProject(toProject(row))
}

function projectRowClassName({ row }: { row: unknown }): string {
  return toProject(row).status === 'archived' ? 'project-list-page__row--archived' : ''
}

function handleSetDefault(row: unknown): void {
  void setDefaultProjectAndRefresh(toProject(row))
}

async function setDefaultProjectAndRefresh(project: Project): Promise<void> {
  try {
    await setDefaultProject(project.id)
    ElMessage.success(`已将「${project.name}」设为默认项目`)
    await loadProjects({ force: true })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '设置默认项目失败')
  }
}

async function handleArchive(row: unknown, archived: boolean): Promise<void> {
  const project = toProject(row)
  const action = archived ? '归档' : '启封'
  try {
    await ElMessageBox.confirm(`确定要${action}项目「${project.name}」吗？`, `确认${action}`, {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await archiveProject(project.id, archived)
    ElMessage.success(`已${action}`)
    await loadProjects({ force: true })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : `${action}失败`)
  }
}

async function handleDelete(row: unknown): Promise<void> {
  const project = toProject(row)
  try {
    await ElMessageBox.confirm(
      `确定要删除项目「${project.name}」吗？删除后不可恢复。`,
      '确认删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteProject(project.id)
    ElMessage.success('已删除')
    await loadProjects({ force: true })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

function openCreateDialog(): void {
  editingProject.value = null
  formDialogTitle.value = '新建项目'
  form.name = ''
  form.description = ''
  form.startTime = ''
  form.endTime = ''
  formDialogVisible.value = true
}

function openEditDialog(project: Project): void {
  editingProject.value = project
  formDialogTitle.value = '编辑项目'
  form.name = project.name
  form.description = project.description ?? ''
  form.startTime = project.startTime ?? ''
  form.endTime = project.endTime ?? ''
  formDialogVisible.value = true
}

function openEditRow(row: unknown): void {
  openEditDialog(toProject(row))
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  formSubmitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
    }
    if (editingProject.value) {
      await updateProject(editingProject.value.id, payload)
    } else {
      await createProject(payload)
    }
    ElMessage.success(editingProject.value ? '已保存' : '项目已创建')
    formDialogVisible.value = false
    await loadProjects({ force: true })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    formSubmitting.value = false
  }
}
</script>

<template>
  <main class="project-list-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">项目列表</h1>
        <p class="page-head__desc">{{ workspaceDescription }}</p>
      </div>
      <div class="page-head__actions">
        <button type="button" class="project-list-page__primary-button" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建项目
        </button>
      </div>
    </header>

    <section class="project-list-page__toolbar-card" aria-label="项目筛选">
      <div class="project-list-page__toolbar">
        <div class="project-list-page__filters">
          <div class="project-list-page__segment" role="group" aria-label="项目状态">
            <button
              v-for="option in statusOptions"
              :key="option.value"
              type="button"
              class="project-list-page__segment-button"
              :class="{ 'project-list-page__segment-button--active': status === option.value }"
              :aria-pressed="status === option.value"
              :disabled="isInitialLoading"
              @click="changeStatus(option.value)"
            >
              {{ option.label }}（{{ counts[option.value] }}）
            </button>
          </div>
          <label class="project-list-page__search" for="project-keyword">
            <span class="project-list-page__search-icon" aria-hidden="true">
              <el-icon><Search /></el-icon>
            </span>
            <span class="project-list-page__visually-hidden">搜索项目名称或描述</span>
            <input
              id="project-keyword"
              v-model="keyword"
              type="search"
              placeholder="项目名称 / 描述"
              autocomplete="off"
              :disabled="isInitialLoading"
              @keyup.enter="handleSearch"
              @clear="handleClear"
            />
          </label>
        </div>
        <span class="project-list-page__sort-note">按最近更新排序</span>
      </div>
    </section>

    <div v-if="isRefreshing" class="project-list-page__refresh" role="status" aria-live="polite">
      正在刷新项目列表…
    </div>

    <section v-if="isInitialLoading" class="project-list-page__table-card" aria-busy="true">
      <el-skeleton :rows="6" animated />
    </section>

    <section v-else-if="error && !projects.length" class="project-list-page__state" role="alert">
      <div class="project-list-page__state-icon project-list-page__state-icon--danger">!</div>
      <h2>{{ error }}</h2>
      <p>请检查网络连接后重试</p>
      <button type="button" class="project-list-page__secondary-button" @click="retry">重试</button>
    </section>

    <section v-else-if="isWorkspaceEmpty" class="project-list-page__state">
      <div class="project-list-page__state-icon"><el-icon :size="42"><Promotion /></el-icon></div>
      <h2>欢迎来到「{{ authStore.activeWorkspace?.name || '当前工作空间' }}」</h2>
      <p>当前工作空间还没有任何项目，创建您的第一个项目开始测试管理</p>
      <button type="button" class="project-list-page__primary-button" @click="openCreateDialog">
        创建第一个项目
      </button>
    </section>

    <section v-else-if="!projects.length" class="project-list-page__state">
      <div class="project-list-page__state-icon">?</div>
      <h2>未找到匹配的项目</h2>
      <p>请调整项目名称或描述，或切换项目状态</p>
      <button type="button" class="project-list-page__secondary-button" @click="clearFilters">清除筛选</button>
    </section>

    <template v-else>
      <div v-if="error" class="project-list-page__error-banner" role="alert">
        <span>{{ error }}</span>
        <button type="button" @click="retry">重试</button>
      </div>
      <section class="project-list-page__table-card">
        <el-table
          :data="projects"
          row-key="id"
          class="project-list-page__table"
          :row-class-name="projectRowClassName"
          @row-click="handleRowClick"
        >
          <el-table-column label="项目名称" min-width="230">
            <template #default="{ row }">
              <div class="project-list-page__project-cell">
                <span class="project-list-page__project-icon" aria-hidden="true">
                  <el-icon><Monitor /></el-icon>
                </span>
                <span class="project-list-page__project-main">
                  <span class="project-list-page__project-name">
                    <el-icon v-if="row.isDefault" class="project-list-page__default-icon" title="默认项目">
                      <StarFilled />
                    </el-icon>
                    {{ row.name }}
                  </span>
                  <span class="project-list-page__project-subtitle">
                    {{ row.createdBy?.name || '未知创建人' }} · 创建于 {{ formatDate(row.createdAt) }}
                  </span>
                </span>
                <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light">
                  {{ row.status === 'active' ? '活跃' : '已归档' }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="描述" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.description || '暂无描述' }}
            </template>
          </el-table-column>
          <el-table-column label="开始时间" width="130">
            <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="结束时间" width="130">
            <template #default="{ row }">{{ formatDate(row.endTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div class="project-list-page__actions" @click.stop>
                <el-button v-if="row.status === 'active'" link type="primary" @click="enterProjectRow(row)">
                  进入
                </el-button>
                <el-button
                  v-if="row.status === 'active' && !row.isDefault"
                  link
                  type="primary"
                  @click="handleSetDefault(row)"
                >
                  设为默认
                </el-button>
                <el-button
                  v-if="row.status === 'active' && canEdit(row)"
                  link
                  @click="openEditRow(row)"
                >
                  编辑
                </el-button>
                <el-button v-if="isAdmin && row.status === 'active'" link type="warning" @click="handleArchive(row, true)">
                  归档
                </el-button>
                <el-button v-if="isAdmin && row.status === 'archived'" link @click="handleArchive(row, false)">
                  启封
                </el-button>
                <el-button v-if="isAdmin" link type="danger" @click="handleDelete(row)">
                  删除
                </el-button>
                <span v-if="row.status === 'archived'" class="project-list-page__readonly">只读</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <div v-if="total > pageSize" class="project-list-page__pager">
        <span class="project-list-page__total">共 {{ total }} 个{{ currentStatusLabel }}项目</span>
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="sizes, prev, pager, next"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </template>

    <el-dialog v-model="formDialogVisible" :title="formDialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="项目名称（工作空间内唯一）" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="项目描述（可选）" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="date" placeholder="选择开始日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="date" placeholder="选择结束日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="submitForm">
          {{ editingProject ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
.project-list-page {
  min-width: 0;
}

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
  margin: 4px 0 0;
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.project-list-page__primary-button,
.project-list-page__secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font: inherit;
  font-size: var(--font-size-base);
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.project-list-page__primary-button {
  background: var(--color-primary-500);
  color: var(--color-neutral-0);

  &:hover {
    background: var(--color-primary-600);
  }
}

.project-list-page__secondary-button {
  border-color: var(--color-neutral-300);
  background: var(--color-neutral-0);
  color: var(--color-neutral-700);

  &:hover {
    border-color: var(--color-neutral-400);
    background: var(--color-neutral-50);
  }
}

.project-list-page__toolbar-card,
.project-list-page__table-card {
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
  box-shadow: var(--shadow-card);
}

.project-list-page__toolbar-card {
  margin-bottom: var(--space-lg);
}

.project-list-page__toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-md);
  padding: 14px 20px;
}

.project-list-page__filters {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: var(--space-md);
  flex-wrap: nowrap;
}

.project-list-page__segment {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: var(--radius-md);
  background: var(--color-neutral-100);
}

.project-list-page__segment-button {
  min-height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: calc(var(--radius-md) - 2px);
  background: transparent;
  color: var(--color-neutral-500);
  cursor: pointer;
  font: inherit;
  font-size: var(--font-size-sm);
  white-space: nowrap;

  &:hover:not(:disabled) {
    color: var(--color-neutral-800);
  }

  &--active {
    background: var(--color-neutral-0);
    color: var(--color-primary-600);
    box-shadow: var(--shadow-xs);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.project-list-page__search {
  display: flex;
  align-items: center;
  flex: 0 1 280px;
  width: auto;
  min-width: 180px;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-neutral-300);
  border-radius: var(--radius-md);
  background: var(--color-neutral-0);
  color: var(--color-neutral-400);

  &:focus-within {
    border-color: var(--color-primary-400);
    box-shadow: 0 0 0 3px var(--color-primary-50);
  }

  input {
    width: 100%;
    min-width: 0;
    padding: 0 8px;
    border: 0;
    outline: 0;
    background: transparent;
    color: var(--color-neutral-800);
    font: inherit;
    font-size: var(--font-size-sm);
  }
}

.project-list-page__search-icon {
  display: inline-flex;
  flex-shrink: 0;
}

.project-list-page__visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.project-list-page__sort-note {
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.project-list-page__refresh {
  margin-bottom: var(--space-sm);
  color: var(--color-neutral-500);
  font-size: var(--font-size-xs);
}

.project-list-page__table-card {
  overflow: hidden;
}

.project-list-page__table {
  width: 100%;

  :deep(.el-table__row) {
    cursor: pointer;
  }

  :deep(.project-list-page__row--archived) {
    cursor: default;
    color: var(--color-neutral-400);
  }
}

.project-list-page__project-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.project-list-page__project-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 30px;
  width: 30px;
  height: 30px;
  border: 1px solid var(--color-primary-100);
  border-radius: 8px;
  background: var(--color-primary-50);
  color: var(--color-primary-500);
}

.project-list-page__project-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}

.project-list-page__project-name {
  display: flex;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  color: var(--color-neutral-800);
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-list-page__default-icon {
  flex-shrink: 0;
  margin-right: 4px;
  color: var(--color-warning);
}

.project-list-page__project-subtitle {
  overflow: hidden;
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-list-page__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}

.project-list-page__readonly {
  color: var(--color-neutral-400);
  font-size: var(--font-size-xs);
}

.project-list-page__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-lg);
}

.project-list-page__total {
  color: var(--color-neutral-500);
  font-size: var(--font-size-sm);
}

.project-list-page__error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-sm);
  padding: 10px 14px;
  border: 1px solid var(--color-danger-200);
  border-radius: var(--radius-md);
  background: var(--color-danger-50);
  color: var(--color-danger-700);
  font-size: var(--font-size-sm);

  button {
    border: 0;
    background: transparent;
    color: inherit;
    cursor: pointer;
    font: inherit;
    text-decoration: underline;
  }
}

.project-list-page__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: var(--space-xl);
  text-align: center;

  h2 {
    margin: var(--space-md) 0 0;
    color: var(--color-neutral-800);
    font-size: var(--font-size-xl);
  }

  p {
    margin: var(--space-sm) 0 var(--space-lg);
    color: var(--color-neutral-500);
    font-size: var(--font-size-sm);
  }
}

.project-list-page__state-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--color-primary-50);
  color: var(--color-primary-500);
  font-size: 30px;

  &--danger {
    background: var(--color-danger-50);
    color: var(--color-danger-600);
  }
}

@media (max-width: 900px) {
  .page-head {
    flex-direction: column;
  }

  .project-list-page__toolbar {
    display: flex;
    align-items: stretch;
    flex-direction: column;
  }

  .project-list-page__filters {
    flex-wrap: wrap;
  }

  .project-list-page__pager {
    align-items: flex-start;
    flex-direction: column;
  }

  .project-list-page__search {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
}
</style>
