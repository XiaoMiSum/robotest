<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Folder, Document } from '@element-plus/icons-vue'
import type { GitLabFileTreeNode, GitLabExecutableImportResult } from '@/types'
import { fetchGitLabFiles, importGitLabExecutable } from '@/services/gitLabRepo'

const props = defineProps<{ repoId: string; visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', val: boolean): void }>()

const loading = ref(false)
const treeData = ref<GitLabFileTreeNode[]>([])
const selectedPaths = ref<string[]>([])
const importResult = ref<GitLabExecutableImportResult | null>(null)
const importing = ref(false)
const conflictStrategy = ref('skip')
const tree = ref<InstanceType<typeof import('element-plus')['ElTree']> | null>(null)

async function loadTree() {
  loading.value = true
  try {
    treeData.value = await fetchGitLabFiles(props.repoId)
  } catch {
    ElMessage.error('文件树加载失败')
  } finally {
    loading.value = false
  }
}

async function handleImport() {
  if (selectedPaths.value.length === 0) {
    ElMessage.warning('请先勾选要导入的测试类')
    return
  }
  importing.value = true
  try {
    importResult.value = await importGitLabExecutable(props.repoId, {
      scope: 'selected',
      classNames: selectedPaths.value,
      conflictStrategy: conflictStrategy.value,
    })
    ElMessage.success(`导入完成：${importResult.value.scenes.length} 个场景`)
  } catch {
    ElMessage.error('可执行导入失败')
  } finally {
    importing.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    loadTree()
    importResult.value = null
    selectedPaths.value = []
  }
})
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="可执行导入"
    size="680px"
    @update:model-value="emit('update:visible', $event)"
  >
    <template #header>
      <div class="exec-drawer__header">
        <span class="exec-drawer__title">可执行导入</span>
        <div class="exec-drawer__actions">
          <el-select v-model="conflictStrategy" style="width: 120px" size="small">
            <el-option label="跳过冲突" value="skip" />
            <el-option label="覆盖" value="overwrite" />
          </el-select>
          <el-button type="primary" :loading="importing" :disabled="selectedPaths.length === 0" @click="handleImport">
            导入选中 ({{ selectedPaths.length }})
          </el-button>
        </div>
      </div>
    </template>

    <div v-loading="loading">
      <el-tree
        v-if="treeData.length > 0"
        ref="tree"
        :data="treeData"
        show-checkbox
        node-key="path"
        :props="{ label: 'name', children: 'children' }"
        check-strictly
        @check-change="selectedPaths = (tree?.getCheckedKeys(true) as string[]) ?? []"
      >
        <template #default="{ data }">
          <span class="exec-drawer__node">
            <el-icon v-if="(data as GitLabFileTreeNode).type === 'tree'" color="#E6A23C"><Folder /></el-icon>
            <el-icon v-else color="#409EFF"><Document /></el-icon>
            <span>{{ (data as GitLabFileTreeNode).name }}</span>
          </span>
        </template>
      </el-tree>
      <el-empty v-else-if="!loading" description="仓库暂无文件" />
    </div>

    <!-- 导入结果 -->
    <el-result
      v-if="importResult"
      icon="success"
      :title="`成功导入 ${importResult.scenes.length} 个场景`"
      style="margin-top: 16px"
    >
      <template #sub-title>
        <div v-for="scene in importResult.scenes" :key="scene.id" class="exec-drawer__scene-item">
          {{ scene.name }}（{{ scene.stepCount }} 步骤）
        </div>
      </template>
    </el-result>
  </el-drawer>
</template>

<style scoped>
.exec-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.exec-drawer__title {
  font-size: 16px;
  font-weight: 600;
}
.exec-drawer__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.exec-drawer__node {
  display: flex;
  align-items: center;
  gap: 6px;
}
.exec-drawer__scene-item {
  padding: 4px 0;
  color: #606266;
}
</style>
