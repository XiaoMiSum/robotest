<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useNavStore } from '@/stores/nav'
import { fetchMyWorkspaces, setActiveWorkspacePreference } from '@/services/workspace'
import { WORKSPACE_ROLE, workspaceRoleLabel } from '@/services/admin'
import type { WorkspaceItem } from '@/types'

const router = useRouter()
const authStore = useAuthStore()
const navStore = useNavStore()

const loading = ref(false)
const workspaces = ref<WorkspaceItem[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 12 })

async function loadWorkspaces() {
  loading.value = true
  try {
    const page = await fetchMyWorkspaces({ pageNo: query.pageNo, pageSize: query.pageSize })
    workspaces.value = page.list
    total.value = page.total
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载工作空间列表失败')
  } finally {
    loading.value = false
  }
}

async function enterWorkspace(ws: WorkspaceItem) {
  authStore.setActiveWorkspace({ id: ws.id, name: ws.name, workspaceRole: ws.workspaceRole })
  navStore.setMode('workspace')
  try {
    await setActiveWorkspacePreference(ws.id)
  } catch {
    // 偏好保存失败不阻塞进入
  }
  router.push('/workspace/projects')
}

function roleTagType(role: string): 'warning' | 'info' {
  return role === WORKSPACE_ROLE.ADMIN ? 'warning' : 'info'
}

onMounted(loadWorkspaces)
</script>

<template>
  <div class="my-workspaces">
    <h2 class="my-workspaces__title">我的工作空间</h2>

    <div v-loading="loading" class="my-workspaces__body">
      <el-empty v-if="!loading && !workspaces.length" description="暂无归属的工作空间，请联系管理员" />
      <el-row v-else :gutter="16">
        <el-col v-for="ws in workspaces" :key="ws.id" :xs="24" :sm="12" :md="8" :lg="6">
          <el-card shadow="hover" class="ws-card" @click="enterWorkspace(ws)">
            <div class="ws-card__header">
              <span class="ws-card__name">{{ ws.name }}</span>
              <el-tag :type="roleTagType(ws.workspaceRole)" size="small">
                {{ workspaceRoleLabel(ws.workspaceRole) }}
              </el-tag>
            </div>
            <div class="ws-card__desc">{{ ws.description || '暂无描述' }}</div>
            <div class="ws-card__info">
              <span v-if="ws.defaultProjectName" class="ws-card__default">
                默认项目: {{ ws.defaultProjectName }}
              </span>
              <span v-else class="ws-card__default ws-card__muted">默认项目: 无</span>
            </div>
            <div class="ws-card__meta">
              <span>成员 {{ ws.memberCount }}</span>
              <el-divider direction="vertical" />
              <span>项目 {{ ws.projectCount }}</span>
            </div>
            <div class="ws-card__footer">
              <el-button type="primary" size="small" @click.stop="enterWorkspace(ws)">
                进入工作空间
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div v-if="total > query.pageSize" class="my-workspaces__pager">
      <el-pagination
        v-model:current-page="query.pageNo"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, prev, pager, next"
        @current-change="loadWorkspaces"
        @size-change="loadWorkspaces"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.my-workspaces__title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 16px;
}

.my-workspaces__body {
  min-height: 200px;
}

.my-workspaces__pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.ws-card {
  cursor: pointer;
  margin-bottom: 16px;
  transition: transform 0.15s;
}

.ws-card:hover {
  transform: translateY(-2px);
}

.ws-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
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
  font-size: 13px;
  color: var(--el-text-color-secondary);
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ws-card__info {
  margin-top: 10px;
  font-size: 12px;
}

.ws-card__default {
  color: var(--el-text-color-regular);
}

.ws-card__muted {
  color: var(--el-text-color-secondary);
}

.ws-card__meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.ws-card__footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
