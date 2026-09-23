import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPermissionTable, fetchRoleDetail, updateRolePermissions } from '@/services/admin'
import type { PermissionTopModule } from '@/types'

export function usePermissionTable(getRoleId: () => string, getRoleType: () => string, getIsSystem: () => boolean) {
  const loading = ref(false)
  const saving = ref(false)
  const topModules = ref<PermissionTopModule[]>([])
  const checkedCodes = ref<string[]>([])
  const savedCodes = ref<string[]>([])

  const dirty = computed(() => {
    if (checkedCodes.value.length !== savedCodes.value.length) return true
    const saved = new Set(savedCodes.value)
    return checkedCodes.value.some((code) => !saved.has(code))
  })

  async function load() {
    if (!getRoleId()) return
    loading.value = true
    try {
      const [perms, detail] = await Promise.all([fetchPermissionTable(getRoleType()), fetchRoleDetail(getRoleId())])
      topModules.value = perms
      savedCodes.value = [...detail.permissions]
      checkedCodes.value = [...detail.permissions]
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载权限配置失败')
    } finally {
      loading.value = false
    }
  }

  function isLocked(_code: string): boolean {
    if (getIsSystem()) return true
    return false
  }

  function handleRevert() {
    checkedCodes.value = [...savedCodes.value]
  }

  async function handleSave() {
    saving.value = true
    try {
      const detail = await updateRolePermissions(getRoleId(), checkedCodes.value)
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
    topModules.value.flatMap((top) =>
      top.modules.flatMap((m) => m.permissions.filter((p) => !isLocked(p.code)).map((p) => p.code)),
    ),
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

  watch(() => getRoleId(), load, { immediate: true })

  return {
    loading,
    saving,
    topModules,
    checkedCodes,
    savedCodes,
    dirty,
    isLocked,
    handleRevert,
    handleSave,
    allVisibleCodes,
    allChecked,
    indeterminate,
    toggleAll,
    load,
  }
}
