<script setup lang="ts">
import { usePermissionTable } from '@/composables/admin/usePermissionTable'

const props = defineProps<{
  roleId: string
  roleName: string
  isSystem: boolean
  roleType: string
}>()

const {
  loading,
  saving,
  modules,
  checkedCodes,
  dirty,
  isLocked,
  handleRevert,
  handleSave,
  allChecked,
  indeterminate,
  toggleAll,
} = usePermissionTable(
  () => props.roleId,
  () => props.roleType,
  () => props.isSystem,
)
</script>

<template>
  <div class="perm-pane">
    <div class="perm-pane__toolbar">
      <span class="role-card__subtitle">{{ roleName }} · {{ checkedCodes.length }} 项已授予</span>
      <div class="perm-pane__actions">
        <el-checkbox
          :model-value="allChecked"
          :indeterminate="indeterminate"
          :disabled="isSystem"
          @change="toggleAll"
        >
          全选
        </el-checkbox>
        <template v-if="!isSystem">
          <el-button :disabled="!dirty" @click="handleRevert">撤销修改</el-button>
          <el-button type="primary" :loading="saving" :disabled="!dirty" @click="handleSave">
            保存权限
          </el-button>
        </template>
      </div>
    </div>

    <div v-loading="loading" class="perm-pane__body">
      <el-checkbox-group v-model="checkedCodes">
        <div v-for="row in modules" :key="row.module" class="perm-group">
          <div class="perm-group__title">{{ row.module }}</div>
          <div class="perm-list">
            <el-checkbox
              v-for="p in row.permissions"
              :key="p.code"
              :value="p.code"
              :disabled="isLocked(p.code)"
              class="perm-list__item"
            >
              {{ p.name }}
            </el-checkbox>
          </div>
        </div>
      </el-checkbox-group>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* pane 内工具行：Tab 头已承担标题角色，此处只留副标题与操作区 */
.perm-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.perm-pane__toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-neutral-100);
}

.perm-pane__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);

  /* 间距由 gap 统一控制，抵消按钮相邻默认外边距 */
  .el-button + .el-button {
    margin-left: 0;
  }
}

.perm-pane__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* Firefox 对齐全局 6px webkit 细滚动条规范 */
  scrollbar-width: thin;
  padding: 4px 24px 16px;
}

/* 模块分组对齐演示稿 perm-group：虚线分隔、末组免线 */
.perm-group {
  padding: 16px 0;
  border-bottom: 1px dashed var(--color-neutral-200);

  &:last-child {
    border-bottom: none;
  }
}

.perm-group__title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-neutral-700);
}

.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 22px;
}

.perm-list__item {
  margin-right: 0;
  font-size: 13px;
}
</style>
