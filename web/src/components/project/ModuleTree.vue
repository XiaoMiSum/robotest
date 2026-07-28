<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import { createModule, deleteModule, fetchModuleTree, updateModule } from '@/services/project'
import type { TestCaseModule } from '@/types'

const emit = defineEmits<{
  selectDocument: [docId: string, docName: string]
}>()

const treeProps = { label: 'name', children: 'children' }
const treeRef = ref<InstanceType<typeof ElTree>>()
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

// el-tree 节点对象仅取展开切换所需的最小结构（完整 Node 类型未随组件导出）
interface TreeNodeToggle {
  expanded: boolean
  expand(): void
  collapse(): void
}

async function handleNodeClick(data: TestCaseModule, node: TreeNodeToggle) {
  // 目录整行点击即切换展开/收起（expand-on-click-node 关闭是为了文档点击不误触展开）
  if (data.type === 'directory') {
    if (node.expanded) node.collapse()
    else node.expand()
    // el-tree 点击已把高亮抢到目录上，回退到已打开的文档
    if (currentDocId.value) treeRef.value?.setCurrentKey(currentDocId.value)
    return
  }
  if (data.type !== 'document' || data.id === currentDocId.value) return
  // 已打开文档时切换需二次确认，防止误触打断编辑
  if (currentDocId.value) {
    try {
      await ElMessageBox.confirm('确定离开当前文档，切换到其他文档吗？', '切换文档', { type: 'warning' })
    } catch {
      // el-tree 点击瞬间已抢先高亮新节点，取消后须显式回退
      treeRef.value?.setCurrentKey(currentDocId.value)
      return
    }
  }
  currentDocId.value = data.id
  emit('selectDocument', data.id, data.name)
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
  <div v-loading="loading" class="module-tree">
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
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      node-key="id"
      :indent="12"
      default-expand-all
      :expand-on-click-node="false"
      highlight-current
      :current-node-key="currentDocId"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <div class="module-tree__node">
          <span class="module-tree__label">
            <el-icon v-if="data.type === 'directory'" class="module-tree__icon module-tree__icon--folder"><Folder /></el-icon>
            <el-icon v-else class="module-tree__icon module-tree__icon--doc"><Document /></el-icon>
            <span class="module-tree__name">{{ data.name }}</span>
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

  :deep(.el-tree) {
    --el-tree-node-content-height: 32px;
    flex: 1;
    padding: 2px;
    overflow: auto;
    background: transparent;
  }

  :deep(.el-tree-node__content) {
    border-radius: var(--radius-md);
    margin-bottom: 2px;
    transition: background-color var(--transition-fast);

    &:hover {
      background-color: var(--color-neutral-100);
    }
  }

  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: var(--color-primary-50);
    color: var(--color-primary-600);
    font-weight: 500;

    .module-tree__icon--doc {
      color: var(--color-primary-500);
    }
  }
}

.module-tree__toolbar {
  display: flex;
  align-items: center;
  padding: 2px;
  border-bottom: 1px solid var(--color-neutral-100);
  background: var(--color-neutral-50);
}

.module-tree__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  padding-right: var(--space-xs);
}

.module-tree__label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
}

.module-tree__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-tree__icon {
  flex-shrink: 0;
  font-size: 14px;
  transition: color var(--transition-fast);

  // 目录用警示黄贴近文件夹隐喻，文档用中性灰避免喧宾夺主
  &--folder {
    color: var(--color-warning);
  }

  &--doc {
    color: var(--color-neutral-400);
  }
}

.module-tree__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--transition-fast);

  .el-button + .el-button,
  .el-dropdown + .el-button {
    margin-left: 2px;
  }
}

.module-tree__node:hover .module-tree__actions {
  opacity: 1;
}
</style>
