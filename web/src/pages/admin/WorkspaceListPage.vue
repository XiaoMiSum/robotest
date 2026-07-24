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
  pageNo: 1,
  pageSize: 12,
})

async function loadWorkspaces() {
  loading.value = true
  try {
    const page = await fetchWorkspaces({
      keyword: query.keyword || undefined,
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

function goDetail(id: string) {
  router.push(`/admin/workspaces/${id}`)
}

// --- 新建工作空间 ---
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
    <div class="workspace-list__header">
      <h2 class="workspace-list__title">工作空间管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>新建工作空间
      </el-button>
    </div>

    <el-card shadow="never" class="workspace-list__filters">
      <el-input
        v-model="query.keyword"
        placeholder="搜索工作空间名称"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">
            <el-icon><Search /></el-icon>
          </el-button>
        </template>
      </el-input>
    </el-card>

    <div v-loading="loading" class="workspace-list__body">
      <el-empty v-if="!loading && !workspaces.length" description="暂无工作空间" />
      <el-row v-else :gutter="16">
        <el-col v-for="ws in workspaces" :key="ws.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card shadow="hover" class="ws-card" @click="goDetail(ws.id)">
            <div class="ws-card__name">{{ ws.name }}</div>
            <div class="ws-card__desc">{{ ws.description || '暂无描述' }}</div>
            <div class="ws-card__meta">
              <span>成员 {{ ws.memberCount }}</span>
              <el-divider direction="vertical" />
              <span>项目 {{ ws.projectCount }}</span>
            </div>
            <div class="ws-card__footer">
              <span class="ws-card__date">创建于 {{ formatDate(ws.createdAt) }}</span>
              <el-button link type="primary" @click.stop="goDetail(ws.id)">查看详情</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div class="workspace-list__pager">
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadWorkspaces"
        @size-change="handleSearch"
      />
    </div>

    <!-- 新建工作空间弹窗 -->
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

<style scoped>
.workspace-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.workspace-list__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.workspace-list__filters {
  margin-bottom: 16px;
}

.workspace-list__body {
  min-height: 200px;
}

.ws-card {
  cursor: pointer;
  margin-bottom: 16px;
  transition: transform 0.15s;
}

.ws-card:hover {
  transform: translateY(-2px);
}

.ws-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-card__desc {
  margin-top: 8px;
  height: 40px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ws-card__meta {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.ws-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.ws-card__date {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.workspace-list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
