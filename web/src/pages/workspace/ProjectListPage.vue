<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  archiveProject,
  createProject,
  deleteProject,
  fetchProjects,
  setDefaultProject,
  updateProject,
} from '@/services/workspace'
import type { Project, ProjectStatus } from '@/types'
import { formatDate } from '@/utils/format'

const authStore = useAuthStore()
const router = useRouter()

const canEditProject = computed(() => authStore.hasPermission('project:edit'))
const canDeleteProject = computed(() => authStore.hasPermission('project:delete'))
const canArchiveProject = computed(() => authStore.hasPermission('project:archive'))
const canSetDefaultProject = computed(() => authStore.hasPermission('project:set-default'))
const currentUserId = computed(() => authStore.user?.id ?? '')

const loading = ref(false)
const projects = ref<Project[]>([])
const total = ref(0)
const query = reactive({
  keyword: '',
  status: '' as ProjectStatus | '',
  pageNo: 1,
  pageSize: 12,
})

async function loadProjects() {
  loading.value = true
  try {
    const page = await fetchProjects({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    projects.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载项目列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadProjects()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.pageNo = 1
  loadProjects()
}

async function handleSetDefault(project: Project) {
  try {
    await setDefaultProject(project.id)
    ElMessage.success(`已将「${project.name}」设为默认项目`)
    loadProjects()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '设置默认项目失败')
  }
}

async function handleArchive(project: Project, archived: boolean) {
  const action = archived ? '归档' : '启封'
  try {
    await ElMessageBox.confirm(
      `确定要${action}项目「${project.name}」吗？`,
      `确认${action}`,
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await archiveProject(project.id, archived)
    ElMessage.success(`已${action}`)
    loadProjects()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : `${action}失败`)
  }
}

async function handleDelete(project: Project) {
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
    loadProjects()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

function handleEnterProject(project: Project) {
  authStore.setActiveProject(project.id)
  router.push('/workspace/projects/dashboard')
}

const formDialogVisible = ref(false)
const formDialogTitle = ref('创建项目')
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

function openCreateDialog() {
  editingProject.value = null
  formDialogTitle.value = '创建项目'
  form.name = ''
  form.description = ''
  form.startTime = ''
  form.endTime = ''
  formDialogVisible.value = true
}

function openEditDialog(project: Project) {
  editingProject.value = project
  formDialogTitle.value = '编辑项目'
  form.name = project.name
  form.description = project.description ?? ''
  form.startTime = project.startTime ?? ''
  form.endTime = project.endTime ?? ''
  formDialogVisible.value = true
}

async function submitForm() {
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
    loadProjects()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    formSubmitting.value = false
  }
}

function timeRange(project: Project): string {
  const s = project.startTime ? formatDate(project.startTime) : ''
  const e = project.endTime ? formatDate(project.endTime) : ''
  if (!s && !e) return ''
  return `${s || '?'} ~ ${e || '?'}`
}

function canEdit(project: Project): boolean {
  return canEditProject.value || project.createdBy.id === currentUserId.value
}

onMounted(loadProjects)
</script>

<template>
  <div class="project-page">
    <template v-if="!loading && total === 0 && !query.keyword && !query.status">
      <div class="project-page__onboarding">
        <div class="project-page__onboarding-icon">
          <el-icon :size="48"><Rocket /></el-icon>
        </div>
        <h2>欢迎来到「{{ authStore.activeWorkspace?.name }}」</h2>
        <p>当前工作空间还没有任何项目，创建您的第一个项目开始测试管理</p>
        <el-button type="primary" size="large" @click="openCreateDialog">创建第一个项目</el-button>
      </div>
    </template>

    <template v-else>

      <el-card shadow="never" class="project-page__filters">
        <el-form :inline="true" class="project-page__filter-form" @submit.prevent>
          <el-form-item>
            <el-select
              v-model="query.status"
              placeholder="状态"
              clearable
              style="width: 120px"
              @change="handleSearch"
            >
              <el-option label="活跃" value="active" />
              <el-option label="已归档" value="archived" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="query.keyword"
              placeholder="搜索项目名称"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>搜索
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
          <el-form-item class="project-page__filter-spacer" />
          <el-form-item>
            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon>创建项目
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div v-loading="loading" class="project-page__body">
        <el-empty v-if="!loading && !projects.length" description="未找到匹配的项目" />
        <el-row v-else :gutter="16">
          <el-col v-for="p in projects" :key="p.id" :xs="24" :sm="12" :md="8" :lg="6">
            <div class="proj-card" @click="handleEnterProject(p)">
              <div class="proj-card__header">
                <el-icon v-if="p.isDefault" class="proj-card__star"><StarFilled /></el-icon>
                <span class="proj-card__name">{{ p.name }}</span>
                <el-tag :type="p.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
                  {{ p.status === 'active' ? '活跃' : '已归档' }}
                </el-tag>
              </div>
              <div class="proj-card__desc">{{ p.description || '暂无描述' }}</div>
              <div class="proj-card__meta">
                <span v-if="timeRange(p)" class="proj-card__time">{{ timeRange(p) }}</span>
                <span class="proj-card__creator">{{ p.createdBy.name }}</span>
              </div>
              <div class="proj-card__actions" @click.stop>
                <el-button
                  v-if="canSetDefaultProject && !p.isDefault && p.status === 'active'"
                  link
                  size="small"
                  @click="handleSetDefault(p)"
                >
                  设为默认
                </el-button>
                <el-button link size="small" type="primary" @click="handleEnterProject(p)">进入</el-button>
                <el-button v-if="canEdit(p) && p.status === 'active'" link size="small" @click="openEditDialog(p)">编辑</el-button>
                <el-button
                  v-if="canArchiveProject && p.status === 'active'"
                  link
                  size="small"
                  type="warning"
                  @click="handleArchive(p, true)"
                >
                  归档
                </el-button>
                <el-button
                  v-if="canArchiveProject && p.status === 'archived'"
                  link
                  size="small"
                  @click="handleArchive(p, false)"
                >
                  启封
                </el-button>
                <el-button v-if="canDeleteProject" link size="small" type="danger" @click="handleDelete(p)">删除</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <div v-if="total > query.pageSize" class="project-page__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[12, 24, 48]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadProjects"
          @size-change="handleSearch"
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
  </div>
</template>

<style scoped lang="scss">
.project-page__filters {
  margin-bottom: var(--space-lg);
}

.project-page__filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.project-page__filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

.project-page__filter-spacer {
  flex: 1;
}

.project-page__body {
  min-height: 200px;
}

.project-page__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--space-xl);
}

.project-page__onboarding {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
}

.project-page__onboarding-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-xl);
  background: var(--color-primary-50);
  color: var(--color-primary-500);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-lg);
}

.project-page__onboarding h2 {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--color-neutral-800);
}

.project-page__onboarding p {
  color: var(--color-neutral-500);
  margin: 0 0 var(--space-xl);
  font-size: var(--font-size-sm);
}

.proj-card {
  cursor: pointer;
  margin-bottom: var(--space-md);
  padding: var(--space-md);
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-lg);
  transition: all var(--transition-base);
  position: relative;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
    border-color: var(--color-primary-200);

    .proj-card__actions {
      opacity: 1;
      visibility: visible;
    }
  }
}

.proj-card__header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.proj-card__star {
  color: var(--color-warning-500);
  font-size: 14px;
}

.proj-card__name {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.proj-card__desc {
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
  height: 32px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.6;
  margin-bottom: 4px;
}

.proj-card__meta {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-2xs);
  color: var(--color-neutral-400);
}

.proj-card__creator::before {
  content: '';
}

.proj-card__actions {
  margin-top: var(--space-sm);
  padding-top: var(--space-xs);
  border-top: 1px solid var(--color-neutral-100);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s ease, visibility 0.2s ease;
}
</style>
