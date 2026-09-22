<script setup lang="ts">
import { useRoleTree } from '@/composables/admin/useRoleTree'

const emit = defineEmits<{
  select: [node: { id: string; isSystem: boolean; type: string }]
  cleared: []
}>()

const treeProps = { label: 'name', children: 'children' }
const {
  treeData,
  loading,
  currentId,
  handleNodeClick,
  handleAdd,
  handleRename,
  handleDelete,
  load,
} = useRoleTree(
  (node) => emit('select', node),
  () => emit('cleared'),
)

defineExpose({ reload: load })

</script>

<template>
  <div v-loading="loading" class="role-tree">
    <el-tree
      :data="treeData"
      :props="treeProps"
      node-key="id"
      :indent="12"
      :expand-on-click-node="false"
      default-expand-all
      :current-node-key="currentId"
      highlight-current
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <div class="role-tree__node" :class="{ 'role-tree__node--group': data.isGroup }">
          <span class="role-tree__label">
            <el-icon v-if="!data.isGroup" class="role-tree__icon"><User /></el-icon>
            {{ data.name }}
            <el-tag
              v-if="!data.isGroup && data.isSystem"
              size="small"
              type="warning"
              class="role-tree__system-tag"
            >
              预置
            </el-tag>
            <el-tag
              v-if="data.isGroup && data.userCount != null"
              size="small"
              type="info"
              class="role-tree__count"
            >
              {{ data.userCount }}
            </el-tag>
          </span>
          <span class="role-tree__actions">
            <!-- 分组节点：新增该类型角色 -->
            <el-button v-if="data.isGroup" link size="small" @click.stop="handleAdd(data)">
              <el-icon><Plus /></el-icon>
            </el-button>
            <!-- 具体角色：重命名 / 删除（系统预置角色不可删除） -->
            <template v-else>
              <el-button
                v-if="!data.isSystem"
                link
                size="small"
                @click.stop="handleRename(data)"
              >
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button
                v-if="!data.isSystem"
                link
                size="small"
                type="danger"
                @click.stop="handleDelete(data)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </span>
        </div>
      </template>
    </el-tree>
  </div>
</template>

<style scoped lang="scss">
.role-tree {
  height: 100%;
  padding: var(--space-xs) 2px;

  :deep(.el-tree) {
    --el-tree-node-content-height: 34px;
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

    .role-tree__icon {
      color: var(--color-primary-500);
    }
  }
}

.role-tree__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  padding-right: var(--space-xs);
  font-size: 13px;

  // 分组节点作为区块标题，弱化为大写小号灰字
  &--group {
    font-size: 12px;
    font-weight: 600;
    color: var(--color-neutral-500);
    text-transform: uppercase;
    letter-spacing: 0.03em;
  }
}

.role-tree__label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tree__icon {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--color-neutral-400);
  transition: color var(--transition-fast);
}

.role-tree__count {
  transform: scale(0.85);
}

.role-tree__system-tag {
  transform: scale(0.85);
}

.role-tree__actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity var(--transition-fast);

  .el-button + .el-button {
    margin-left: 2px;
  }
}

.role-tree__node:hover .role-tree__actions {
  opacity: 1;
}
</style>
