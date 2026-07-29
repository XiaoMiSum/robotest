<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createWorkspace, fetchWorkspaces } from '@/services/admin'
import type { AdminWorkspace } from '@/types'
import { formatDate } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const workspaces = ref<AdminWorkspace[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  status: '' as string,
  pageNo: 1,
  pageSize: 20,
})

async function loadWorkspaces() {
  loading.value = true
  try {
    const page = await fetchWorkspaces({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    })
    workspaces.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载工作空间列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNo = 1
  loadWorkspaces()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.pageNo = 1
  loadWorkspaces()
}

function goDetail(id: string) {
  router.push(`/admin/workspaces/${id}`)
}

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createSubmitting = ref(false)
const createForm = reactive({
  name: '',
  description: '',
})
const createRules: FormRules = {
  name: [
    { required: true, message: '请输入工作空间名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度需在 2-50 字符之间', trigger: 'blur' },
  ],
}

function openCreateDialog() {
  createForm.name = ''
  createForm.description = ''
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  createSubmitting.value = true
  try {
    const id = await createWorkspace({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
    })
    ElMessage.success('工作空间已创建')
    createDialogVisible.value = false
    goDetail(id)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建工作空间失败')
  } finally {
    createSubmitting.value = false
  }
}

onMounted(loadWorkspaces)
</script>

<template>
  <div class="workspace-list">
    <el-card shadow="never" class="workspace-list__filters">
      <el-form :inline="true" class="workspace-list__filter-form" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="query.keyword"
            placeholder="搜索工作空间名称"
            clearable
            :prefix-icon="'Search'"
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
            <el-option label="活跃" value="active" />
            <el-option label="已解散" value="dissolved" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="workspace-list__filter-spacer" />
        <el-form-item>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>新建工作空间
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="workspaces" row-key="id">
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goDetail(row.id)">
              {{ row.name }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '活跃' : '已解散' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成员" width="80" align="center">
          <template #default="{ row }">{{ row.memberCount }}</template>
        </el-table-column>
        <el-table-column label="项目" width="80" align="center">
          <template #default="{ row }">{{ row.projectCount }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="workspace-list__pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadWorkspaces"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建工作空间" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="createForm.name"
            placeholder="请输入工作空间名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入工作空间描述（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.workspace-list__filters {
  margin-bottom: var(--space-lg);
}

.workspace-list__filters :deep(.el-form-item) {
  margin-bottom: 0;
}

.workspace-list__filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

.workspace-list__filter-spacer {
  flex: 1;
}

.workspace-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-lg);
  padding-top: var(--space-lg);
  border-top: 1px solid var(--color-neutral-100);
}
</style>
