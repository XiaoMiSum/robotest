<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import {
  createProjectModule,
  createTestCase,
  deleteProjectModule,
  deleteTestCase,
  fetchProjectModuleTree,
  updateProjectModule,
  updateTestCase,
} from '@/services/project'
import type { ProjectModule } from '@/types'

const props = defineProps<{
  assetType: 'testcase' | 'interface' | 'scene'
  /** 筛选模式高亮目标（父页传入当前选中的模块 id） */
  currentModuleId?: string
  /**
   * 点击已展开的父目录时是否收起其子节点。
   * 默认 false（点击父目录只展开、永不收起，便于快速连续浏览目录）；设为 true 时点击收起。
   */
  collapseOnParentClick?: boolean
}>()

const emit = defineEmits<{
  selectDocument: [docId: string, docName: string]
  selectModule: [moduleId: string]
}>()

// 文档模式：testcase 资产按"目录 + 用例文档"组织，点文档 emit selectDocument
// 筛选模式：interface/scene 资产只有目录，点目录 emit selectModule 供父页过滤列表
const isDocumentMode = computed(() => props.assetType === 'testcase')

const treeProps = { label: 'name', children: 'children' }
const treeRef = ref<InstanceType<typeof ElTree>>()
const treeData = ref<ProjectModule[]>([])
const loading = ref(false)
const currentDocId = ref('')
const filterKeyword = ref('')

// el-tree 自带过滤：命中节点自动展开其祖先链，清空关键字即还原
watch(filterKeyword, (val) => treeRef.value?.filter(val))

function filterNode(value: string, data: Record<string, unknown>): boolean {
  if (!value) return true
  return String(data.name ?? '')
    .toLowerCase()
    .includes(value.toLowerCase())
}

async function load() {
  loading.value = true
  try {
    treeData.value = await fetchProjectModuleTree(props.assetType)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载模块树失败')
  } finally {
    loading.value = false
  }
}

interface TreeNodeToggle {
  expanded: boolean
  expand(): void
  collapse(): void
}

async function handleNodeClick(data: ProjectModule, node: TreeNodeToggle) {
  // 目录：筛选模式选中即过滤并展开/收起；文档模式保持纯展开/收起
  if (data.type === 'directory') {
    const collapseEnabled = props.collapseOnParentClick ?? false
    // 已展开时再点击：仅当允许收起才收起，否则保持展开；未展开时一律展开
    if (node.expanded) {
      if (collapseEnabled) node.collapse()
    } else {
      node.expand()
    }
    if (isDocumentMode.value) {
      // el-tree 点击已把高亮抢到目录上，回退到已打开的文档
      if (currentDocId.value) treeRef.value?.setCurrentKey(currentDocId.value)
    } else {
      emit('selectModule', data.id)
    }
    return
  }
  // 文档节点仅在文档模式下存在
  if (!isDocumentMode.value || data.id === currentDocId.value) return
  // 已打开文档时切换需二次确认，防止误触打断编辑
  if (currentDocId.value) {
    try {
      await ElMessageBox.confirm('确定离开当前文档，切换到其他文档吗？', '切换文档', {
        type: 'warning',
      })
    } catch {
      treeRef.value?.setCurrentKey(currentDocId.value)
      return
    }
  }
  currentDocId.value = data.id
  emit('selectDocument', data.id, data.name)
}

async function handleCreate(parent: ProjectModule | null, type: 'directory' | 'document') {
  // 筛选模式只有目录资产，文档创建入口不可达
  const createType: 'directory' | 'document' = isDocumentMode.value ? type : 'directory'
  const typeLabel = createType === 'directory' ? '目录' : '文档'
  try {
    const { value } = await ElMessageBox.prompt(`请输入${typeLabel}名称`, `新建${typeLabel}`, {
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    if (createType === 'directory') {
      await createProjectModule({ parentId: parent?.id ?? null, name: value.trim() })
    } else {
      await createTestCase({ moduleId: parent?.id ?? null, name: value.trim() })
    }
    ElMessage.success(`${typeLabel}已创建`)
    load()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  }
}

async function handleRename(node: ProjectModule) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
      inputValue: node.name,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    if (node.type === 'directory' || !isDocumentMode.value) {
      await updateProjectModule(node.id, { name: value.trim() })
    } else {
      await updateTestCase(node.id, { name: value.trim() })
    }
    ElMessage.success('已重命名')
    load()
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err instanceof Error ? err.message : '重命名失败')
  }
}

async function handleDelete(node: ProjectModule) {
  const typeLabel = node.type === 'directory' ? '目录' : '文档'
  const confirmMsg =
    node.type === 'directory'
      ? `确定删除目录「${node.name}」吗？目录必须为空才能删除。`
      : `确定删除文档「${node.name}」吗？文档下所有用例数据将被级联删除。`
  try {
    await ElMessageBox.confirm(confirmMsg, `删除${typeLabel}`, { type: 'warning' })
  } catch {
    return
  }
  try {
    if (node.type === 'directory' || !isDocumentMode.value) {
      await deleteProjectModule(node.id)
    } else {
      await deleteTestCase(node.id)
    }
    ElMessage.success('已删除')
    if (currentDocId.value === node.id) {
      currentDocId.value = ''
    }
    load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

defineExpose({ reload: load, getTree: () => treeData.value })

// 定时静默刷新：多人协同下模块/文档可能被他人增删改，轮询保证左侧树最终一致；
// 不走 loading 遮罩且失败不弹窗，避免每分钟打断用户编辑（错误在下次轮询自愈，手动操作仍走 load() 提示）
const POLL_INTERVAL = 60_000
let pollTimer: ReturnType<typeof setInterval> | null = null

async function silentRefresh() {
  try {
    treeData.value = await fetchProjectModuleTree(props.assetType)
  } catch {
    // 静默忽略，等待下一轮
  }
}

onMounted(() => {
  void load()
  pollTimer = setInterval(silentRefresh, POLL_INTERVAL)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})

// el-tree 拖拽回调的 Node.data 是宽松的 TreeNodeData，业务上即模块节点
function nodeData(node: Node): ProjectModule {
  return node.data as ProjectModule
}

function allowDrop(dragging: Node, drop: Node, type: 'prev' | 'inner' | 'next'): boolean {
  // 文档是叶子节点，只允许放入目录内部；同级前后排序不限
  if (type === 'inner') return nodeData(drop).type === 'directory'
  // 文档拖到目录行只允许放入内部：el-tree 按行高上下边缘判为 before/after，
  // 放行会被解析成移到目录的父层级（根目录时 parentId 为 null），与"拖入目录"预期不符
  if (nodeData(dragging).type === 'document' && nodeData(drop).type === 'directory') return false
  return true
}

async function handleNodeDrop(dragging: Node, drop: Node, dropType: 'before' | 'after' | 'inner') {
  const parentNode = dropType === 'inner' ? drop : drop.parent
  const parentId = parentNode && parentNode.level > 0 ? nodeData(parentNode).id : null
  const siblings = parentNode?.childNodes ?? []
  const dragId = nodeData(dragging).id
  const targetIndex = Math.max(
    0,
    siblings.findIndex((n) => nodeData(n).id === dragId),
  )
  const dragNode = nodeData(dragging)
  try {
    if (dragNode.type === 'directory' || !isDocumentMode.value) {
      await updateProjectModule(dragId, { parentId, targetIndex })
    } else {
      await updateTestCase(dragId, { moduleId: parentId, targetIndex })
    }
    ElMessage.success('移动成功')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '移动失败')
  }
  await load()
  if (currentDocId.value) {
    await nextTick()
    treeRef.value?.setCurrentKey(currentDocId.value)
  }
}
</script>

<template>
  <div v-loading="loading" class="module-tree">
    <div class="module-tree__toolbar">
      <el-input
        v-model="filterKeyword"
        size="small"
        placeholder="搜索目录 / 文档"
        clearable
        class="module-tree__search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-dropdown
        v-if="isDocumentMode"
        trigger="click"
        @command="(cmd: string) => handleCreate(null, cmd as 'directory' | 'document')"
      >
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
      <el-button v-else size="small" type="primary" @click="handleCreate(null, 'directory')">
        <el-icon><Plus /></el-icon>新建
      </el-button>
    </div>

    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      node-key="id"
      :indent="12"
      default-expand-all
      :expand-on-click-node="false"
      :filter-node-method="filterNode"
      highlight-current
      :current-node-key="isDocumentMode ? (currentDocId || undefined) : (props.currentModuleId || undefined)"
      draggable
      :allow-drop="allowDrop"
      @node-drop="handleNodeDrop"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <div class="module-tree__node">
          <span class="module-tree__label">
            <el-icon
              v-if="data.type === 'directory'"
              class="module-tree__icon module-tree__icon--folder"
              ><Folder
            /></el-icon>
            <el-icon v-else class="module-tree__icon module-tree__icon--doc"><Document /></el-icon>
            <span class="module-tree__name">{{ data.name }}</span>
          </span>
          <span class="module-tree__actions">
            <el-dropdown
              v-if="data.type === 'directory'"
              trigger="click"
              size="small"
              @command="(cmd: string) => handleCreate(data, cmd as 'directory' | 'document')"
            >
              <el-button link size="small" @click.stop
                ><el-icon><Plus /></el-icon
              ></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="directory">新建子目录</el-dropdown-item>
                  <el-dropdown-item v-if="isDocumentMode" command="document">新建文档</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link size="small" @click.stop="handleRename(data)"
              ><el-icon><EditPen /></el-icon
            ></el-button>
            <el-button link size="small" type="danger" @click.stop="handleDelete(data)"
              ><el-icon><Delete /></el-icon
            ></el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-empty
      v-if="!loading && !treeData.length"
      description="暂无模块，点击[新建]创建"
      :image-size="40"
    />
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
  gap: var(--space-xs);
  padding: 2px;
  border-bottom: 1px solid var(--color-neutral-100);
  background: var(--color-neutral-50);
}

.module-tree__search {
  flex: 1;
  min-width: 0;
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
