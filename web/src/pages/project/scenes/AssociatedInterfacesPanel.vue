<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiSceneAssociationItem } from '@/types'
import { fetchSceneAssociations, unassociateInterface, switchSyncMode } from '@/services/apiScene'

const props = defineProps<{ sceneId: string }>()
const emit = defineEmits<{ (e: 'refresh'): void }>()

const rows = ref<ApiSceneAssociationItem[]>([])
const loading = ref(false)

async function load() {
  if (!props.sceneId) return
  loading.value = true
  try {
    rows.value = await fetchSceneAssociations(props.sceneId)
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.sceneId, () => void load())
onMounted(() => void load())

async function handleRemove(item: ApiSceneAssociationItem) {
  await ElMessageBox.confirm(`取消关联接口「${item.interfaceName}」？已导入的步骤不受影响。`, '取消关联', { type: 'warning' })
  try {
    await unassociateInterface(props.sceneId, item.id)
    ElMessage.success('已取消关联')
    await load()
    emit('refresh')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleToggleMode(item: ApiSceneAssociationItem) {
  const newMode = item.syncMode === 'copy' ? 'link' : 'copy'
  try {
    await switchSyncMode(props.sceneId, item.id, { syncMode: newMode })
    ElMessage.success(`已切换为${newMode === 'link' ? '链接' : '复制'}模式`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}
</script>

<template>
  <div class="assoc-panel">
    <div class="assoc-panel__header">
      <span class="assoc-panel__title">已关联接口</span>
    </div>
    <el-table v-loading="loading" :data="rows" size="small" class="assoc-panel__table">
      <el-table-column prop="interfaceName" label="接口名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="method" label="方法" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.method === 'GET' ? 'success' : row.method === 'POST' ? 'primary' : 'info'">{{ row.method }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />
      <el-table-column label="同步模式" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.syncMode === 'link' ? 'warning' : 'info'" class="assoc-panel__mode-tag">
            {{ row.syncMode === 'link' ? '链接' : '复制' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publicStepCount" label="步骤数" width="70" align="center" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="handleToggleMode(row as ApiSceneAssociationItem)">{{ row.syncMode === 'link' ? '切复制' : '切链接' }}</el-button>
          <el-button link size="small" type="danger" @click="handleRemove(row as ApiSceneAssociationItem)">取消关联</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="assoc-panel__empty">关联接口后可快速创建步骤</div>
      </template>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.assoc-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.assoc-panel__header {
  display: flex;
  align-items: center;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-neutral-50, #fafafa);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.assoc-panel__title {
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.assoc-panel__mode-tag {
  cursor: pointer;
}

.assoc-panel__empty {
  padding: var(--space-lg);
  text-align: center;
  color: var(--color-neutral-400);
  font-size: var(--font-size-sm);
}
</style>
