<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { GitLabMetadataListItem, GitLabSyncConfig, GitLabSyncHistoryItem, GitLabMetadataImportResult } from '@/types'
import {
  fetchGitLabMetadataList,
  fetchGitLabSyncConfig,
  updateGitLabSyncConfig,
  fetchGitLabSyncHistory,
  importGitLabMetadata,
  syncGitLabMetadata,
} from '@/services/gitLabRepo'
import { formatShortDateTime } from '@/utils/format'

const props = defineProps<{ repoId: string; visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', val: boolean): void }>()

const activeTab = ref('metadata')
const loading = ref(false)

// ==================== 元数据列表 ====================
const metaList = ref<GitLabMetadataListItem[]>([])
const metaTotal = ref(0)
const metaPageNo = ref(1)
const metaPageSize = ref(20)
const metaExecutableFilter = ref<boolean | ''>('')
const metaKeyword = ref('')

async function loadMetadata() {
  loading.value = true
  try {
    const result = await fetchGitLabMetadataList(
      props.repoId, metaPageNo.value, metaPageSize.value,
      metaExecutableFilter.value === '' ? undefined : metaExecutableFilter.value,
      metaKeyword.value || undefined,
    )
    metaList.value = result.list
    metaTotal.value = result.total
  } catch {
    ElMessage.error('元数据加载失败')
  } finally {
    loading.value = false
  }
}

// ==================== 同步配置 ====================
const syncConfig = ref<GitLabSyncConfig>({
  autoSyncEnabled: false,
  testSourcePath: null,
  annotationFilter: null,
  onlyWithResourcePath: false,
})

async function loadSyncConfig() {
  try {
    syncConfig.value = await fetchGitLabSyncConfig(props.repoId)
  } catch {
    ElMessage.error('同步配置加载失败')
  }
}

async function saveSyncConfig() {
  try {
    await updateGitLabSyncConfig(props.repoId, syncConfig.value)
    ElMessage.success('同步配置已保存')
  } catch {
    ElMessage.error('同步配置保存失败')
  }
}

// ==================== 同步历史 ====================
const historyList = ref<GitLabSyncHistoryItem[]>([])

async function loadSyncHistory() {
  try {
    historyList.value = await fetchGitLabSyncHistory(props.repoId)
  } catch {
    ElMessage.error('同步历史加载失败')
  }
}

// ==================== 导入/同步操作 ====================
const syncing = ref(false)

async function handleImport() {
  syncing.value = true
  try {
    const result: GitLabMetadataImportResult = await importGitLabMetadata(props.repoId)
    ElMessage.success(`导入完成：${result.classCount} 个类，${result.methodCount} 个方法`)
    loadMetadata()
    loadSyncHistory()
  } catch {
    ElMessage.error('元数据导入失败')
  } finally {
    syncing.value = false
  }
}

async function handleSync() {
  syncing.value = true
  try {
    const result: GitLabMetadataImportResult = await syncGitLabMetadata(props.repoId)
    ElMessage.success(`同步完成：+${result.addCount} ~${result.modifyCount} -${result.removeCount}`)
    loadMetadata()
    loadSyncHistory()
  } catch {
    ElMessage.error('元数据同步失败')
  } finally {
    syncing.value = false
  }
}

function handleTabChange(tab: string | number) {
  const t = String(tab)
  if (t === 'metadata') loadMetadata()
  else if (t === 'config') loadSyncConfig()
  else if (t === 'history') loadSyncHistory()
}

watch(() => props.visible, (val) => {
  if (val) {
    loadMetadata()
    loadSyncConfig()
    loadSyncHistory()
  }
})
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="元数据管理"
    size="720px"
    @update:model-value="emit('update:visible', $event)"
  >
    <template #header>
      <div class="meta-drawer__header">
        <span class="meta-drawer__title">元数据管理</span>
        <div class="meta-drawer__actions">
          <el-button type="primary" :loading="syncing" @click="handleImport">导入元数据</el-button>
          <el-button :loading="syncing" @click="handleSync">同步元数据</el-button>
        </div>
      </div>
    </template>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 元数据列表 -->
      <el-tab-pane label="元数据列表" name="metadata">
        <div class="meta-drawer__filters">
          <el-select v-model="metaExecutableFilter" clearable placeholder="筛选类型" style="width: 140px" @change="loadMetadata()">
            <el-option label="全部" value="" />
            <el-option label="可执行" :value="true" />
            <el-option label="仅定义" :value="false" />
          </el-select>
          <el-input v-model="metaKeyword" clearable placeholder="搜索类名" style="width: 200px" @clear="loadMetadata()" @keyup.enter="loadMetadata()" />
        </div>
        <el-table v-loading="loading" :data="metaList" stripe size="small" max-height="400">
          <el-table-column prop="fullClassName" label="全限定类名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="displayName" label="显示名" width="120" show-overflow-tooltip />
          <el-table-column prop="isExecutable" label="可执行" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="(row as GitLabMetadataListItem).isExecutable" type="success" size="small">是</el-tag>
              <span v-else>否</span>
            </template>
          </el-table-column>
          <el-table-column label="方法数" width="70" align="center">
            <template #default="{ row }">
              {{ (row as GitLabMetadataListItem).methods?.length ?? 0 }}
            </template>
          </el-table-column>
        </el-table>
        <div v-if="metaTotal > metaPageSize" class="meta-drawer__pagination">
          <el-pagination
            v-model:current-page="metaPageNo"
            v-model:page-size="metaPageSize"
            :total="metaTotal"
            :page-sizes="[20, 50]"
            layout="total, sizes, prev, pager, next"
            small
            @current-change="loadMetadata()"
            @size-change="loadMetadata()"
          />
        </div>
      </el-tab-pane>

      <!-- 同步配置 -->
      <el-tab-pane label="同步配置" name="config">
        <el-form :model="syncConfig" label-width="120px" style="max-width: 500px">
          <el-form-item label="自动同步">
            <el-switch v-model="syncConfig.autoSyncEnabled" />
          </el-form-item>
          <el-form-item label="测试源码路径">
            <el-input v-model="syncConfig.testSourcePath" placeholder="如 src/test/java" clearable />
          </el-form-item>
          <el-form-item label="注解过滤">
            <el-input v-model="syncConfig.annotationFilter" placeholder="如 @Test,@ParameterizedTest" clearable />
          </el-form-item>
          <el-form-item label="仅含路径">
            <el-switch v-model="syncConfig.onlyWithResourcePath" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveSyncConfig">保存配置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 同步历史 -->
      <el-tab-pane label="同步历史" name="history">
        <el-table :data="historyList" stripe size="small" max-height="400">
          <el-table-column label="同步时间" width="160">
            <template #default="{ row }">
              {{ formatShortDateTime((row as GitLabSyncHistoryItem).syncAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="classCount" label="类数" width="70" align="center" />
          <el-table-column prop="methodCount" label="方法数" width="70" align="center" />
          <el-table-column prop="commitSha" label="Commit" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ (row as GitLabSyncHistoryItem).commitSha?.slice(0, 8) ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="(row as GitLabSyncHistoryItem).status === 'success' ? 'success' : 'danger'" size="small">
                {{ (row as GitLabSyncHistoryItem).status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<style scoped>
.meta-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.meta-drawer__title {
  font-size: 16px;
  font-weight: 600;
}
.meta-drawer__actions {
  display: flex;
  gap: 8px;
}
.meta-drawer__filters {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.meta-drawer__pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
