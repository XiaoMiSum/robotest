<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPermissionTable, fetchRoleDetail, updateRolePermissions } from '@/services/admin'
import type { PermissionModule } from '@/types'

const props = defineProps<{
  roleId: string
  isSystem: boolean
}>()

const loading = ref(false)
const saving = ref(false)
const modules = ref<PermissionModule[]>([])
const checkedCodes = ref<string[]>([])
// 上次保存时的权限快照：用于撤销修改，以及系统角色已有权限的禁用判定
const savedCodes = ref<string[]>([])

const dirty = computed(() => {
  if (checkedCodes.value.length !== savedCodes.value.length) return true
  const saved = new Set(savedCodes.value)
  return checkedCodes.value.some((code) => !saved.has(code))
})

// 权限点全集只需加载一次
async function ensureModules() {
  if (modules.value.length) return
  modules.value = await fetchPermissionTable()
}

async function load() {
  if (!props.roleId) return
  loading.value = true
  try {
    const [, detail] = await Promise.all([ensureModules(), fetchRoleDetail(props.roleId)])
    checkedCodes.value = [...detail.permissions]
    savedCodes.value = [...detail.permissions]
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载权限配置失败')
  } finally {
    loading.value = false
  }
}

// 系统预置角色的已有权限不可取消
function isLocked(code: string): boolean {
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

watch(() => props.roleId, load, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="perm-table">
    <el-checkbox-group v-model="checkedCodes">
      <el-table :data="modules" border>
        <el-table-column label="操作对象" prop="module" width="160" />
        <el-table-column label="权限点">
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

    <div class="perm-table__actions">
      <el-button :disabled="!dirty" @click="handleRevert">撤销修改</el-button>
      <el-button type="primary" :loading="saving" :disabled="!dirty" @click="handleSave">
        保存权限
      </el-button>
    </div>
  </div>
</template>

<style scoped>
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
