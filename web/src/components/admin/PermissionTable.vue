<script setup lang="ts">
import { usePermissionTable } from '@/composables/admin/usePermissionTable'

const props = defineProps<{
  roleId: string
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
  <div v-loading="loading" class="perm-table">
    <el-checkbox-group v-model="checkedCodes">
      <el-table :data="modules" border>
        <el-table-column label="操作对象" prop="module" width="160" />
        <el-table-column label="权限点">
          <template #header>
            <el-checkbox
              :model-value="allChecked"
              :indeterminate="indeterminate"
              :disabled="isSystem"
              @change="toggleAll"
            >
              权限点
            </el-checkbox>
          </template>
          <template #default="{ row }">
            <el-checkbox
              v-for="p in row.permissions"
              :key="p.code"
              :value="p.code"
              :disabled="isLocked(p.code)"
              class="perm-table__item"
            >
              {{ p.name }}
            </el-checkbox>
          </template>
        </el-table-column>
      </el-table>
    </el-checkbox-group>

    <div v-if="!isSystem" class="perm-table__actions">
      <el-button :disabled="!dirty" @click="handleRevert">撤销修改</el-button>
      <el-button type="primary" :loading="saving" :disabled="!dirty" @click="handleSave">
        保存权限
      </el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.perm-table__item {
  margin-right: 20px;
}

.perm-table__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
