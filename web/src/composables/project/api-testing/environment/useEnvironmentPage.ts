import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadUserFile } from 'element-plus'
import type { ApiEnvironmentListItem, ApiImportResult } from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  copyEnvironment,
  createEnvironment,
  deleteEnvironment,
  downloadEnvironmentJson,
  fetchEnvironmentDetail,
  fetchEnvironments,
  importEnvironment,
  setDefaultEnvironment,
  sortEnvironment,
  updateEnvironment,
} from '@/services/project/environment'
import { buildSavePayload, formatImportResult, resolveEnvironmentError, sortEnvironments } from '@/pages/project/api-testing/environment/environmentsModel'

export function useEnvironmentPage() {
  const authStore = useAuthStore()
  const canEdit = computed(() => authStore.hasPermission('api-env:edit'))

  const listLoading = ref(false)
  const loadError = ref(false)
  const environments = ref<ApiEnvironmentListItem[]>([])
  const selectedId = ref('')
  const keyword = ref('')

  let searchTimer: ReturnType<typeof setTimeout> | undefined
  function handleSearchInput() {
    clearTimeout(searchTimer)
    searchTimer = setTimeout(() => void loadList(), 300)
  }
  onBeforeUnmount(() => clearTimeout(searchTimer))

  const sortedList = computed(() => sortEnvironments(environments.value))

  async function loadList(keepSelection = true): Promise<void> {
    listLoading.value = true
    loadError.value = false
    try {
      environments.value = await fetchEnvironments(keyword.value.trim() || undefined)
      const stillExists = keepSelection && environments.value.some((item) => item.id === selectedId.value)
      if (!stillExists) selectedId.value = sortedList.value[0]?.id ?? ''
    } catch (err) {
      loadError.value = true
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      listLoading.value = false
    }
  }

  function selectEnvironment(id: string) {
    if (id !== selectedId.value) selectedId.value = id
  }

  function canMove(item: ApiEnvironmentListItem, direction: -1 | 1): boolean {
    const ordered = sortedList.value
    const index = ordered.findIndex((entry) => entry.id === item.id)
    const neighbor = ordered[index + direction]
    return index >= 0 && Boolean(neighbor)
  }

  async function handleMoveItem(item: ApiEnvironmentListItem, direction: -1 | 1) {
    const ordered = sortedList.value
    const index = ordered.findIndex((entry) => entry.id === item.id)
    const neighbor = ordered[index + direction]
    if (index < 0 || !neighbor) return
    try {
      await Promise.all([
        sortEnvironment(item.id, neighbor.sortOrder),
        sortEnvironment(neighbor.id, item.sortOrder),
      ])
      await loadList()
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    }
  }

  async function handleExport() {
    const selected = environments.value.find((item) => item.id === selectedId.value)
    if (!selected) return
    try {
      await downloadEnvironmentJson(selected.id, `${selected.name}.json`)
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    }
  }

  // ==================== 新建 ====================

  const createDialogVisible = ref(false)
  const createForm = reactive({ name: '', description: '', isDefault: false })
  const creating = ref(false)

  function openCreateDialog() {
    if (!canEdit.value) {
      ElMessage.warning('无环境编辑权限')
      return
    }
    createForm.name = ''
    createForm.description = ''
    createForm.isDefault = false
    createDialogVisible.value = true
  }

  async function submitCreate() {
    if (!createForm.name.trim()) {
      ElMessage.warning('请填写环境名称')
      return
    }
    creating.value = true
    try {
      const created = await createEnvironment({
        name: createForm.name.trim(),
        description: createForm.description.trim() || undefined,
        isDefault: createForm.isDefault,
      })
      createDialogVisible.value = false
      ElMessage.success('环境已创建')
      await loadList()
      selectEnvironment(created.id)
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      creating.value = false
    }
  }

  // ==================== 复制 ====================

  const copyDialogVisible = ref(false)
  const copySourceName = ref('')
  const copyForm = reactive({ name: '' })

  function openCopyDialog(item: ApiEnvironmentListItem) {
    copySourceName.value = item.id
    copyForm.name = `${item.name}（副本）`
    copyDialogVisible.value = true
  }

  async function submitCopy() {
    if (!copyForm.name.trim()) return
    try {
      const copied = await copyEnvironment(copySourceName.value, copyForm.name.trim())
      copyDialogVisible.value = false
      ElMessage.success('复制成功')
      await loadList()
      selectEnvironment(copied.id)
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    }
  }

  // ==================== 编辑 ====================

  const editDialogVisible = ref(false)
  const editTargetId = ref('')
  const editForm = reactive({ name: '', description: '', isDefault: false })
  const editing = ref(false)

  function openEditDialog(item: ApiEnvironmentListItem) {
    if (!canEdit.value) {
      ElMessage.warning('无环境编辑权限')
      return
    }
    selectEnvironment(item.id)
    editTargetId.value = item.id
    editForm.name = item.name
    editForm.description = item.description || ''
    editForm.isDefault = item.isDefault
    editDialogVisible.value = true
  }

  async function submitEdit() {
    if (!editForm.name.trim()) {
      ElMessage.warning('请填写环境名称')
      return
    }
    editing.value = true
    try {
      const detail = await fetchEnvironmentDetail(editTargetId.value)
      await updateEnvironment(
        editTargetId.value,
        buildSavePayload(
          { name: editForm.name.trim(), description: editForm.description.trim() || '', isDefault: editForm.isDefault },
          detail,
          detail.httpConfigs,
          detail.dataSources,
        ),
      )
      editDialogVisible.value = false
      ElMessage.success('已保存')
      await loadList()
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      editing.value = false
    }
  }

  // ==================== 导入 / 删除 ====================

  const importDialogVisible = ref(false)
  const importFile = ref<File | null>(null)
  const importFileList = ref<UploadUserFile[]>([])
  const importOverwrite = ref(false)
  const importing = ref(false)

  function openImportDialog() {
    importFile.value = null
    importFileList.value = []
    importOverwrite.value = false
    importDialogVisible.value = true
  }

  function handleImportFileChange(uploadFile: unknown) {
    const raw = (uploadFile as { raw?: File }).raw
    if (raw) importFile.value = raw
  }

  function handleImportFileRemove() {
    importFile.value = null
  }

  async function submitImport() {
    if (!importFile.value) {
      ElMessage.warning('请选择环境 JSON 文件')
      return
    }
    importing.value = true
    try {
      const result: ApiImportResult = await importEnvironment(importFile.value, importOverwrite.value)
      importDialogVisible.value = false
      await ElMessageBox.alert(formatImportResult(result), '导入结果', { confirmButtonText: '知道了' })
      await loadList()
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    } finally {
      importing.value = false
    }
  }

  async function handleDelete(item: ApiEnvironmentListItem) {
    try {
      await ElMessageBox.confirm(`删除后环境配置不可恢复，确认删除「${item.name}」？`, '删除环境', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await deleteEnvironment(item.id)
      ElMessage.success('已删除')
      if (selectedId.value === item.id) selectedId.value = ''
      await loadList()
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    }
  }

  async function handleSetDefault(item: ApiEnvironmentListItem) {
    try {
      await ElMessageBox.confirm(
        `确认将「${item.name}」设为默认环境？场景执行未指定环境时将使用默认环境`,
        '设为默认',
        { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
    try {
      await setDefaultEnvironment(item.id)
      ElMessage.success('已设为默认')
      await loadList()
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
    }
  }

  onMounted(() => void loadList(false))

  return {
    canEdit,
    listLoading,
    loadError,
    environments,
    selectedId,
    keyword,
    sortedList,
    loadList,
    selectEnvironment,
    canMove,
    handleMoveItem,
    handleExport,
    handleSearchInput,
    createDialogVisible,
    createForm,
    creating,
    openCreateDialog,
    submitCreate,
    copyDialogVisible,
    copySourceName,
    copyForm,
    openCopyDialog,
    submitCopy,
    editDialogVisible,
    editTargetId,
    editForm,
    editing,
    openEditDialog,
    submitEdit,
    importDialogVisible,
    importFile,
    importFileList,
    importOverwrite,
    importing,
    openImportDialog,
    handleImportFileChange,
    handleImportFileRemove,
    submitImport,
    handleDelete,
    handleSetDefault,
  }
}
