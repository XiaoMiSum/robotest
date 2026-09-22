<script setup lang="ts">
import { useProjectModuleTree } from '@/composables/project/useProjectModuleTree'
import { ref, watch } from 'vue'
import { ElTree } from 'element-plus'

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

const {
  isDocumentMode,
  treeData,
  loading,
  currentDocId,
  handleNodeClick,
  handleCreate,
  handleRename,
  handleDelete,
  allowDrop,
  handleNodeDrop,
  load,
} = useProjectModuleTree({
  getAssetType: () => props.assetType,
  getCollapseOnParentClick: () => props.collapseOnParentClick,
  getTreeRef: () => treeRef.value,
  onSelectDocument: (docId, docName) => emit('selectDocument', docId, docName),
  onSelectModule: (moduleId) => emit('selectModule', moduleId),
})

const treeProps = { label: 'name', children: 'children' }
const treeRef = ref<InstanceType<typeof ElTree>>()
const filterKeyword = ref('')

// el-tree 自带过滤：命中节点自动展开其祖先链，清空关键字即还原
watch(filterKeyword, (val) => treeRef.value?.filter(val))

function filterNode(value: string, data: Record<string, unknown>): boolean {
  if (!value) return true
  return String(data.name ?? '')
    .toLowerCase()
    .includes(value.toLowerCase())
}

defineExpose({ reload: load, getTree: () => treeData.value })

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
