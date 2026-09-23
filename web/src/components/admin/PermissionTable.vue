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
  <section class="role-card perm-card">
    <header class="role-card__head">
      <h3 class="role-card__title">
        权限点
        <span class="role-card__subtitle">{{ roleName }} · {{ checkedCodes.length }} 项已授予</span>
      </h3>
      <div class="perm-card__actions">
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
    </header>

    <div v-loading="loading" class="role-card__body perm-card__body">
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
  </section>
</template>

<style scoped lang="scss">
.perm-card__actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);

  /* 间距由 gap 统一控制，抵消按钮相邻默认外边距 */
  .el-button + .el-button {
    margin-left: 0;
  }
}

.perm-card__body {
  padding: 4px 20px 16px;
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
