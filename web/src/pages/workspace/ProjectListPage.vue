<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectListPage } from '@/composables/workspace/useProjectListPage'
import ProjectCard from '@/components/workspace/ProjectCard.vue'
import {
  archiveProject,
  createProject,
  deleteProject,
  setDefaultProject,
  updateProject,
} from '@/services/workspace'
import type { Project, ProjectStatus } from '@/types'
import { isWorkspaceAdmin } from '@/utils/workspaceRole'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Loading, Plus, Promotion, Search } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const {
  projects,
  counts,
  keyword,
  status,
  error,
  isInitialLoading,
  isRefreshing,
  isLoadingMore,
  hasMore,
  isWorkspaceEmpty,
  loadProjects,
  loadMoreProjects,
  handleSearch,
  handleClear,
  clearFilters,
  changeStatus,
  retry,
  enterProject,
} = useProjectListPage()

const statusOptions: { value: ProjectStatus; label: string }[] = [
  { value: 'active', label: '活跃' },
  { value: 'archived', label: '已归档' },
]

const isAdmin = computed(() => isWorkspaceAdmin(authStore.activeWorkspace?.workspaceRole ?? ''))
const canCreateProject = computed(() => authStore.hasPermission('project:create'))
const currentUserId = computed(() => authStore.user?.id ?? '')
const workspaceDescription = computed(() => {
  const workspaceName = authStore.activeWorkspace?.name
  return workspaceName ? `${workspaceName}空间下的全部项目` : '当前工作空间下的全部项目'
})

const loadMoreTarget = ref<HTMLElement | null>(null)
let loadMoreObserver: IntersectionObserver | null = null

function observeLoadMoreTarget(): void {
  if (!loadMoreObserver || !loadMoreTarget.value) return
  loadMoreObserver.disconnect()
  loadMoreObserver.observe(loadMoreTarget.value)
}

onMounted(async () => {
  if (typeof IntersectionObserver === 'undefined') return
  loadMoreObserver = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        void loadMoreProjects()
      }
    },
    { rootMargin: '240px 0px' },
  )
  await nextTick()
  observeLoadMoreTarget()
})

watch([projects, isInitialLoading, isLoadingMore, hasMore], () => {
  void nextTick().then(observeLoadMoreTarget)
})

onBeforeUnmount(() => {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
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

function canEdit(project: Project): boolean {
  return isAdmin.value || project.createdBy?.id === currentUserId.value
}

function handleSetDefault(project: Project): void {
  void setDefaultProjectAndRefresh(project)
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

async function handleArchive(project: Project, archived: boolean): Promise<void> {
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

async function handleDelete(project: Project): Promise<void> {
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
  if (!canCreateProject.value) return
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
      <div v-if="canCreateProject" class="page-head__actions">
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

    <section v-if="isInitialLoading" class="project-list-page__grid" aria-busy="true">
      <el-skeleton v-for="index in 6" :key="index" class="project-list-page__skeleton-card" :rows="5" animated />
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
      <button
        v-if="canCreateProject"
        type="button"
        class="project-list-page__primary-button"
        @click="openCreateDialog"
      >
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
      <section class="project-list-page__grid" aria-label="项目列表">
        <ProjectCard
          v-for="project in projects"
          :key="project.id"
          :project="project"
          :can-edit="canEdit(project)"
          :is-admin="isAdmin"
          @enter="enterProject(project)"
          @set-default="handleSetDefault(project)"
          @edit="openEditDialog(project)"
          @archive="handleArchive(project, true)"
          @unarchive="handleArchive(project, false)"
          @delete="handleDelete(project)"
        />
        <button
          v-if="canCreateProject"
          type="button"
          class="project-list-page__create-card"
          @click="openCreateDialog"
        >
          <span class="project-list-page__create-icon" aria-hidden="true">
            <el-icon><Plus /></el-icon>
          </span>
          <strong>新建项目</strong>
          <span>从项目开始组织测试资产与团队协作</span>
        </button>
      </section>
      <div ref="loadMoreTarget" class="project-list-page__load-more" aria-live="polite">
        <div v-if="isLoadingMore" class="project-list-page__loading-more">
          <el-icon class="is-loading"><Loading /></el-icon>
          正在加载更多项目…
        </div>
        <span v-else-if="hasMore">继续向下滚动加载更多项目</span>
        <span v-else>没有更多项目</span>
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

.project-list-page__toolbar-card {
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

.project-list-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-lg);
}

.project-list-page__skeleton-card {
  min-height: 270px;
  padding: 20px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  background: var(--color-neutral-0);
}

.project-list-page__create-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 270px;
  padding: 20px;
  border: 1px dashed var(--color-primary-300);
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
  cursor: pointer;
  font: inherit;
  text-align: center;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    transform var(--transition-fast);

  &:hover,
  &:focus-visible {
    border-color: var(--color-primary-500);
    background: var(--color-primary-100);
    outline: none;
    transform: translateY(-2px);
  }

  strong {
    margin-top: var(--space-sm);
    font-size: var(--font-size-base);
    font-weight: 600;
  }

  > span:last-child {
    max-width: 220px;
    margin-top: var(--space-xs);
    color: var(--color-primary-600);
    font-size: var(--font-size-xs);
    line-height: 1.5;
  }
}

.project-list-page__create-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--color-primary-200);
  border-radius: 50%;
  background: var(--color-neutral-0);
  color: var(--color-primary-500);
  font-size: 20px;
}

.project-list-page__load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 56px;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
  text-align: center;
}

.project-list-page__loading-more {
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

  .project-list-page__grid {
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  }

  .project-list-page__search {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
}
</style>
