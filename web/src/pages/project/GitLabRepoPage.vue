<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Platform, MoreFilled, FolderOpened, Document, VideoPlay } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { WORKSPACE_ROLE } from '@/services/admin'
import type { GitLabRepoListItem, GitLabRepoTestConnectionResult } from '@/types'
import {
  createGitLabRepo,
  deleteGitLabRepo,
  fetchGitLabBranches,
  fetchGitLabRepos,
  testGitLabConnection,
  updateGitLabRepo,
} from '@/services/gitLabRepo'
import { formatShortDateTime } from '@/utils/format'
import GitLabMetadataDrawer from './GitLabMetadataDrawer.vue'
import GitLabExecutableImportDrawer from './GitLabExecutableImportDrawer.vue'
import GitLabPipelineDrawer from './GitLabPipelineDrawer.vue'

const authStore = useAuthStore()
const canEdit = computed(() => authStore.activeWorkspace?.workspaceRole === WORKSPACE_ROLE.ADMIN)

// ==================== 列表状态 ====================

const loading = ref(false)
const list = ref<GitLabRepoListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

async function loadList() {
  loading.value = true
  try {
    const result = await fetchGitLabRepos(pageNo.value, pageSize.value)
    list.value = result.list
    total.value = result.total
  } catch {
    ElMessage.error('仓库配置列表加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => void loadList())

// ==================== 新建 / 编辑弹窗 ====================

const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  name: '',
  repoUrl: '',
  accessToken: '',
  branch: 'main',
  testSourcePath: '',
})

const formRules = computed(() => ({
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  repoUrl: [{ required: true, message: '请输入仓库地址', trigger: 'blur' }],
  accessToken: editingId.value
    ? []
    : [{ required: true, message: '请输入访问令牌', trigger: 'blur' }],
  branch: [{ required: true, message: '请选择默认分支', trigger: 'change' }],
}))

// 分支列表（编辑时加载）
const branchList = ref<string[]>([])
const branchLoading = ref(false)

// 测试连接状态
const testing = ref(false)
const connectionResult = ref<GitLabRepoTestConnectionResult | null>(null)

function openCreateDialog() {
  editingId.value = null
  form.name = ''
  form.repoUrl = ''
  form.accessToken = ''
  form.branch = 'main'
  form.testSourcePath = ''
  connectionResult.value = null
  branchList.value = ['main']
  dialogVisible.value = true
}

async function openEditDialog(item: GitLabRepoListItem) {
  editingId.value = item.id
  form.name = item.name
  form.repoUrl = item.repoUrl
  form.accessToken = ''
  form.branch = item.branch
  form.testSourcePath = item.testSourcePath ?? ''
  connectionResult.value = null
  dialogVisible.value = true
  // 加载分支列表
  branchLoading.value = true
  try {
    const branches = await fetchGitLabBranches(item.id)
    branchList.value = branches.length > 0 ? branches : [item.branch]
  } catch {
    branchList.value = [item.branch]
  } finally {
    branchLoading.value = false
  }
}

async function handleTestConnection() {
  if (editingId.value) {
    testing.value = true
    try {
      const result = await testGitLabConnection(editingId.value)
      connectionResult.value = result
    } catch {
      connectionResult.value = { success: false, message: '连接测试失败', repoName: null, defaultBranch: null, commitCount: null }
    } finally {
      testing.value = false
    }
  } else {
    ElMessage.info('请先保存配置后再测试连接')
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      repoUrl: form.repoUrl,
      accessToken: editingId.value && !form.accessToken ? null : form.accessToken,
      branch: form.branch,
      testSourcePath: form.testSourcePath || null,
    }
    if (editingId.value) {
      await updateGitLabRepo(editingId.value, payload)
      ElMessage.success('配置已更新')
    } else {
      await createGitLabRepo(payload)
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    const msg = err instanceof Error ? err.message : '操作失败'
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

// ==================== 删除 ====================

async function handleDelete(item: GitLabRepoListItem) {
  try {
    await ElMessageBox.confirm(
      `删除后基于该仓库的导入场景与元数据将保留，但不可再触发流水线执行。确认删除「${item.name}」？`,
      '删除仓库配置',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteGitLabRepo(item.id)
    ElMessage.success('已删除')
    await loadList()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ==================== 工具 ====================

function importStatusType(status: string | null): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'partial') return 'warning'
  return 'info'
}

function importStatusLabel(status: string | null): string {
  const map: Record<string, string> = { success: '成功', failed: '失败', partial: '部分成功', pending: '待执行' }
  return map[status ?? ''] ?? '未知'
}

// ==================== 功能抽屉状态 ====================

const activeRepoId = ref('')
const metadataDrawerVisible = ref(false)
const executableImportDrawerVisible = ref(false)
const pipelineDrawerVisible = ref(false)

function openMetadataDrawer(row: GitLabRepoListItem) {
  activeRepoId.value = row.id
  metadataDrawerVisible.value = true
}

function openExecutableImportDrawer(row: GitLabRepoListItem) {
  activeRepoId.value = row.id
  executableImportDrawerVisible.value = true
}

function openPipelineDrawer(row: GitLabRepoListItem) {
  activeRepoId.value = row.id
  pipelineDrawerVisible.value = true
}
</script>

<template>
  <div class="gitlab-repo">
    <header class="gitlab-repo__header">
      <div>
        <h3 class="gitlab-repo__title">GitLab 仓库配置</h3>
        <p class="gitlab-repo__subtitle">项目级公共配置，可执行导入与仓库流水线执行均引用此配置</p>
      </div>
      <el-button type="primary" :disabled="!canEdit" @click="openCreateDialog">+ 添加仓库</el-button>
    </header>

    <el-card shadow="never" class="gitlab-repo__card">
      <el-skeleton v-if="loading" :rows="4" animated />

      <div v-else-if="list.length === 0" class="gitlab-repo__empty">
        <el-icon :size="48" color="#c0c4cc"><Platform /></el-icon>
        <p>暂无仓库配置，点击「添加仓库」接入第一个 GitLab 仓库</p>
      </div>

      <el-table v-else :data="list" stripe>
        <el-table-column prop="name" label="配置名称" min-width="120" />
        <el-table-column prop="repoUrl" label="仓库地址" min-width="200">
          <template #default="{ row }">
            <el-text truncated>{{ (row as GitLabRepoListItem).repoUrl }}</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="branch" label="默认分支" width="100" />
        <el-table-column prop="tokenSuffix" label="令牌" width="90">
          <template #default="{ row }">
            <span v-if="(row as GitLabRepoListItem).tokenSuffix">****{{ (row as GitLabRepoListItem).tokenSuffix }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近导入" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as GitLabRepoListItem).lastImportStatus" size="small" :type="importStatusType((row as GitLabRepoListItem).lastImportStatus)">
              {{ importStatusLabel((row as GitLabRepoListItem).lastImportStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近同步" width="110">
          <template #default="{ row }">
            {{ formatShortDateTime((row as GitLabRepoListItem).lastMetadataSyncAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" :disabled="!canEdit" @click="openEditDialog(row as GitLabRepoListItem)">编辑</el-button>
            <el-dropdown trigger="click" :disabled="!canEdit">
              <el-button link size="small" :disabled="!canEdit">
                更多<el-icon class="el-icon--right"><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openMetadataDrawer(row as GitLabRepoListItem)">
                    <el-icon><FolderOpened /></el-icon>元数据管理
                  </el-dropdown-item>
                  <el-dropdown-item @click="openExecutableImportDrawer(row as GitLabRepoListItem)">
                    <el-icon><Document /></el-icon>可执行导入
                  </el-dropdown-item>
                  <el-dropdown-item @click="openPipelineDrawer(row as GitLabRepoListItem)">
                    <el-icon><VideoPlay /></el-icon>流水线触发
                  </el-dropdown-item>
                  <el-dropdown-item divided style="color: #F56C6C" @click="handleDelete(row as GitLabRepoListItem)">
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > pageSize" class="gitlab-repo__pagination">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadList()"
          @size-change="loadList()"
        />
      </div>
    </el-card>

    <!-- 添加/编辑仓库弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑仓库' : '添加仓库'"
      width="640px"
      :close-on-click-modal="false"
      @closed="formRef?.resetFields()"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="form.name" maxlength="100" placeholder="如：主仓库" />
        </el-form-item>
        <el-form-item label="仓库地址" prop="repoUrl">
          <el-input v-model="form.repoUrl" maxlength="500" placeholder="https://gitlab.example.com/team/repo.git" />
        </el-form-item>
        <el-form-item label="访问令牌" prop="accessToken">
          <el-input
            v-model="form.accessToken"
            type="password"
            show-password
            maxlength="1000"
            :placeholder="editingId ? '留空表示不修改令牌' : 'glpat-xxxxxxxxxxxx'"
          />
        </el-form-item>
        <el-form-item label="默认分支" prop="branch">
          <el-select
            v-model="form.branch"
            filterable
            :loading="branchLoading"
            placeholder="请选择默认分支"
            style="width: 100%"
          >
            <el-option v-for="b in branchList" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试源码路径">
          <el-input v-model="form.testSourcePath" maxlength="500" placeholder="src/test/java" />
        </el-form-item>

        <!-- 测试连接结果区 -->
        <el-form-item v-if="connectionResult" label="连接结果">
          <el-alert
            :type="connectionResult.success ? 'success' : 'error'"
            :title="connectionResult.message"
            :description="connectionResult.repoName ? `仓库名：${connectionResult.repoName} · 默认分支：${connectionResult.defaultBranch}` : ''"
            show-icon
            :closable="false"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :loading="testing" :disabled="!editingId" @click="handleTestConnection">测试连接</el-button>
        <div class="gitlab-repo__dialog-footer-right">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 功能抽屉 -->
    <GitLabMetadataDrawer v-model:visible="metadataDrawerVisible" :repo-id="activeRepoId" />
    <GitLabExecutableImportDrawer v-model:visible="executableImportDrawerVisible" :repo-id="activeRepoId" />
    <GitLabPipelineDrawer v-model:visible="pipelineDrawerVisible" :repo-id="activeRepoId" />
  </div>
</template>

<style scoped lang="scss">
.gitlab-repo {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--space-md);
}

.gitlab-repo__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.gitlab-repo__title {
  margin: 0;
  font-size: var(--font-size-lg);
}

.gitlab-repo__subtitle {
  margin: 4px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.gitlab-repo__card {
  flex: 1;
  overflow: auto;
}

.gitlab-repo__empty {
  text-align: center;
  padding: var(--space-xxl, 64px) 0;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);

  p {
    margin-top: var(--space-md);
  }
}

.gitlab-repo__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.gitlab-repo__dialog-footer-right {
  display: inline-flex;
  gap: var(--space-sm);
  margin-left: var(--space-lg);
}
</style>
