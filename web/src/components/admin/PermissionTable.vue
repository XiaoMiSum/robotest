<script setup lang="ts">
import { computed } from 'vue'
import { usePermissionTable } from '@/composables/admin/usePermissionTable'
import type { PermissionItem } from '@/types'

const props = defineProps<{
  roleId: string
  roleName: string
  isSystem: boolean
  roleType: string
}>()

const {
  loading,
  saving,
  topModules,
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

interface PermissionTableRow {
  topModule: string
  topSpan: number
  module: string
  permissions: PermissionItem[]
}

const rows = computed<PermissionTableRow[]>(() =>
  topModules.value.flatMap((top) =>
    top.modules.map((mod, index) => ({
      topModule: top.topModule,
      topSpan: index === 0 ? top.modules.length : 0,
      module: mod.module,
      permissions: mod.permissions,
    })),
  ),
)

// 一级列合并：组首行跨整组，其余行归零让 el-table 跳过渲染、由首行 rowspan 覆盖
const spanMethod = ({ row, columnIndex }: { row: PermissionTableRow; columnIndex: number }) => {
  if (columnIndex !== 0) return undefined
  return row.topSpan === 0 ? { rowspan: 0, colspan: 1 } : { rowspan: row.topSpan, colspan: 1 }
}
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
        <el-table :data="rows" :span-method="spanMethod" border class="perm-table">
          <el-table-column prop="topModule" label="一级模块" width="110" />
          <el-table-column prop="module" label="二级模块" width="130" />
          <el-table-column label="权限点">
            <template #default="{ row }">
              <div class="perm-table__points">
                <el-checkbox
                  v-for="p in row.permissions"
                  :key="p.code"
                  :value="p.code"
                  :disabled="isLocked(p.code)"
                >
                  {{ p.name }}
                </el-checkbox>
              </div>
            </template>
          </el-table-column>
        </el-table>
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
  padding: 12px 24px 16px;
}

/* 一级模块列跨多行合并，垂直居中避免合并组内容错位 */
.perm-table {
  font-size: 13px;

  :deep(td.el-table__cell) {
    vertical-align: middle;
  }
}

.perm-table__points {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 22px;
}
</style>
