<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createModule, deleteModule, fetchModuleTree, updateModule } from '@/services/project'
import type { TestCaseModule } from '@/types'

const emit = defineEmits<{
  selectDocument: [docId: string, docName: string]
}>()

const treeProps = { label: 'name', children: 'children' }
const treeData = ref<TestCaseModule[]>([])
const loading = ref(false)
const currentDocId = ref('')

async function load() {
  loading.value = true
  try {
    treeData.value = await fetchModuleTree()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
  } finally {
    loading.value = false
  }
}

function handleNodeClick(data: TestCaseModule) {
  if (data.type === 'document') {
    currentDocId.value = data.id
    emit('selectDocument', data.id, data.name)
  }
}

async function handleCreate(parent: TestCaseModule | null, type: 'directory' | 'document') {
  const typeLabel = type === 'directory' ? '目录' : '文档'
  try {
    const { value } = await ElMessageBox.prompt(`请输入${typeLabel}名称`, `新建${typeLabel}`, {
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    await createModule({ parentId: parent?.id ?? null, type, name: value.trim() })
    ElMessage.success(`${typeLabel}已创建`)
    load()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  }
}

async function handleRename(node: TestCaseModule) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
      inputValue: node.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    await updateModule(node.id, { name: value.trim() })
    ElMessage.success('已重命名')
    load()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '重命名失败')
  }
}

async function handleDelete(node: TestCaseModule) {
  const typeLabel = node.type === 'directory' ? '目录' : '文档'
  try {
    await ElMessageBox.confirm(
      `确定删除${typeLabel}「${node.name}」吗？${node.type === 'document' ? '文档下所有用例数据将被级联删除。' : '目录必须为空才能删除。'}`,
      `删除${typeLabel}`,
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteModule(node.id)
    ElMessage.success('已删除')
    if (currentDocId.value === node.id) {
      currentDocId.value = ''
    }
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

defineExpose({ reload: load })
onMounted(load)
</script>

<template>
  <div class="module-tree" v-loading="loading">
    <div class="module-tree__toolbar">
      <el-dropdown trigger="click" @command="(cmd: string) => handleCreate(null, cmd as 'directory' | 'document')">
        <el-button size="small" type="primary">
          <el-icon><Plus /></el-icon>新建
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="directory">新建目录</el-dropdown-item>
            <el-dropdown-item command="document">新建文档</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-tree
      :data="treeData"
      :props="treeProps"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
      highlight-current
      :current-node-key="currentDocId"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <div class="module-tree__node">
          <span class="module-tree__label">
            <el-icon v-if="data.type === 'directory'"><Folder /></el-icon>
            <el-icon v-else><Document /></el-icon>
            {{ data.name }}
          </span>
          <span class="module-tree__actions">
            <el-dropdown v-if="data.type === 'directory'" trigger="click" size="small" @command="(cmd: string) => handleCreate(data, cmd as 'directory' | 'document')">
              <el-button link size="small" @click.stop><el-icon><Plus /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="directory">新建子目录</el-dropdown-item>
                  <el-dropdown-item command="document">新建文档</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link size="small" @click.stop="handleRename(data)"><el-icon><EditPen /></el-icon></el-button>
            <el-button link size="small" type="danger" @click.stop="handleDelete(data)"><el-icon><Delete /></el-icon></el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-empty v-if="!loading && !treeData.length" description="暂无模块，点击[新建]创建" :image-size="40" />
  </div>
</template>

<style scoped lang="scss">
.module-tree {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.module-tree__toolbar {
  padding: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.module-tree__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}

.module-tree__label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.module-tree__actions {
  visibility: hidden;
  display: flex;
  align-items: center;
}

.module-tree__node:hover .module-tree__actions {
  visibility: visible;
}
</style>
