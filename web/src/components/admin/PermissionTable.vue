<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPermissionTable, fetchRoleDetail, updateRolePermissions } from '@/services/admin'
import type { PermissionModule } from '@/types'

const props = defineProps<{
  roleId: string
  isSystem: boolean
  roleType: string
  fullAccess: boolean
}>()

const loading = ref(false)
const saving = ref(false)
const modules = ref<PermissionModule[]>([])
const checkedCodes = ref<string[]>([])
const savedCodes = ref<string[]>([])

const dirty = computed(() => {
  if (checkedCodes.value.length !== savedCodes.value.length) return true
  const saved = new Set(savedCodes.value)
  return checkedCodes.value.some((code) => !saved.has(code))
})

async function load() {
  if (!props.roleId) return
  loading.value = true
  try {
    const [perms, detail] = await Promise.all([fetchPermissionTable(props.roleType), fetchRoleDetail(props.roleId)])
    modules.value = perms
    savedCodes.value = [...detail.permissions]
    if (props.fullAccess) {
      checkedCodes.value = perms.flatMap((m) => m.permissions.map((p) => p.code))
    } else {
      checkedCodes.value = [...detail.permissions]
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载权限配置失败')
  } finally {
    loading.value = false
  }
}

function isLocked(code: string): boolean {
  if (props.fullAccess) return true
  return props.isSystem && savedCodes.value.includes(code)
}

function handleRevert() {
  checkedCodes.value = [...savedCodes.value]
}

async function handleSave() {
  saving.value = true
  try {
    const detail = await updateRolePermissions(props.roleId, checkedCodes.value)
    savedCodes.value = [...detail.permissions]
    checkedCodes.value = [...detail.permissions]
    ElMessage.success('权限已保存')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存权限失败')
  } finally {
    saving.value = false
  }
}

const allVisibleCodes = computed(() =>
  modules.value.flatMap((m) => m.permissions.filter((p) => !isLocked(p.code)).map((p) => p.code)),
)
const allChecked = computed(() => allVisibleCodes.value.length > 0 && allVisibleCodes.value.every((c) => checkedCodes.value.includes(c)))
const indeterminate = computed(() => {
  const checked = allVisibleCodes.value.filter((c) => checkedCodes.value.includes(c))
  return checked.length > 0 && checked.length < allVisibleCodes.value.length
})

function toggleAll() {
  if (allChecked.value) {
    const remove = new Set(allVisibleCodes.value)
    checkedCodes.value = checkedCodes.value.filter((c) => !remove.has(c))
  } else {
    const add = allVisibleCodes.value.filter((c) => !checkedCodes.value.includes(c))
    checkedCodes.value = [...checkedCodes.value, ...add]
  }
}

watch(() => props.roleId, load, { immediate: true })
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

    <div v-if="!fullAccess" class="perm-table__actions">
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
