<script setup lang="ts">
import { computed } from 'vue'
import { useRoleTree } from '@/composables/admin/useRoleTree'

const emit = defineEmits<{
  select: [node: { id: string; isSystem: boolean; type: string; name: string }]
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

// 卡头 subtitle：具体角色总数（分组节点不计）
const roleCount = computed(() =>
  treeData.value.reduce((sum, group) => sum + (group.children?.length ?? 0), 0),
)

defineExpose({ reload: load })
</script>

<template>
  <section class="role-card role-tree-panel">
    <header class="role-card__head">
      <h3 class="role-card__title">
        角色列表
        <span class="role-card__subtitle">{{ roleCount }}</span>
      </h3>
    </header>

    <div v-loading="loading" class="role-card__body role-tree">
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
              {{ data.name }}
              <el-tag
                v-if="!data.isGroup && data.isSystem"
                size="small"
                type="warning"
                class="role-tree__system-tag"
              >
                预置
              </el-tag>
            </span>
            <span class="role-tree__side">
              <span v-if="!data.isGroup && data.userCount != null" class="role-tree__count">
                {{ data.userCount }} 人
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
            </span>
          </div>
        </template>
      </el-tree>
    </div>
  </section>
</template>

<style scoped lang="scss">
.role-tree {
  padding: var(--space-xs);

  :deep(.el-tree) {
    --el-tree-node-content-height: 44px;
    background: transparent;
  }

  /* 节点底色/描边对齐演示稿 role-item：hover 浅底、选中浅色底+浅边（border-box 保证不撑高） */
  :deep(.el-tree-node__content) {
    border: 1px solid transparent;
    border-radius: var(--radius-md);
    margin-bottom: 4px;
    transition:
      background-color var(--transition-fast),
      border-color var(--transition-fast);

    &:hover {
      background-color: var(--color-neutral-25);
    }
  }

  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: var(--color-primary-50);
    border-color: var(--color-primary-100);
  }
}

.role-tree__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  padding-right: var(--space-xs);
}

.role-tree__label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  /* 角色名对齐演示稿 role-item__name */
  font-size: 14px;
  font-weight: 600;
  color: var(--color-neutral-900);
}

/* 分组根弱化为区块标题 */
.role-tree__node--group .role-tree__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-500);
  letter-spacing: 0.03em;
}

.role-tree__system-tag {
  flex-shrink: 0;
  transform: scale(0.85);
}

.role-tree__side {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

/* 人数徽标对齐演示稿 tag--neutral */
.role-tree__count {
  padding: 1px 8px;
  border: 1px solid var(--color-neutral-200);
  border-radius: var(--radius-full);
  background: var(--color-neutral-50);
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-neutral-500);
}

.role-tree__actions {
  display: flex;
  align-items: center;
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
