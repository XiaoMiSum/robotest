import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadUserFile } from 'element-plus'
import type { ApiEnvironmentDetail, ApiEnvironmentListItem, ApiImportResult } from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
  deleteEnvironment,
  downloadEnvironmentJson,
  fetchEnvironmentDetail,
  fetchEnvironments,
  importEnvironment,
  setDefaultEnvironment,
  sortEnvironment,
  updateEnvironment,
} from '@/services/project/api-testing/environment'
import {
  buildSavePayload,
  emptyEnvironmentDetail,
  formatImportResult,
  resolveEnvironmentError,
  seedFromDetail,
  sortEnvironments,
} from '@/composables/project/api-testing/environment/environmentsModel'

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

  // ==================== 新建 / 复制（面板新建态，均走创建接口） ====================

  const createMode = ref(false)
  const createSeed = ref<ApiEnvironmentDetail | null>(null)
  const createDirty = ref(false)

  /** 新建与副本统一排到列表末尾（交互设计 34 §1.4） */
  function nextSortOrder(): number {
    return environments.value.reduce((max, item) => Math.max(max, item.sortOrder), 0) + 1
  }

  function exitCreate() {
    createMode.value = false
    createSeed.value = null
    createDirty.value = false
  }

  /** 离开新建态前的放弃确认：有改动先问，确认后退出并放行后续动作 */
  async function confirmLeaveCreate(): Promise<boolean> {
    if (!createMode.value) return true
    if (createDirty.value) {
      try {
        await ElMessageBox.confirm('新建内容尚未创建，确认放弃？', '放弃新建', {
          type: 'warning',
          confirmButtonText: '放弃',
          cancelButtonText: '继续编辑',
        })
      } catch {
        return false
      }
    }
    exitCreate()
    return true
  }

  function enterCreate(seed: ApiEnvironmentDetail) {
    createSeed.value = seed
    createDirty.value = false
    createMode.value = true
  }

  async function startCreate() {
    if (!canEdit.value) {
      ElMessage.warning('无环境编辑权限')
      return
    }
    if (!(await confirmLeaveCreate())) return
    enterCreate(emptyEnvironmentDetail(nextSortOrder()))
  }

  /** 复制：取源环境详情预填新建态面板，确认后由面板提交创建 */
  async function startCopy(item: ApiEnvironmentListItem) {
    if (!canEdit.value) {
      ElMessage.warning('无环境编辑权限')
      return
    }
    let source: ApiEnvironmentDetail
    try {
      source = await fetchEnvironmentDetail(item.id)
    } catch (err) {
      ElMessage.error(resolveEnvironmentError(err))
      return
    }
    if (!(await confirmLeaveCreate())) return
    enterCreate(seedFromDetail(source, `${item.name}（副本）`, nextSortOrder()))
  }

  async function handleCreated(id: string) {
    exitCreate()
    await loadList()
    selectEnvironment(id)
  }

  async function handleSelect(id: string) {
    if (!(await confirmLeaveCreate())) return
    selectEnvironment(id)
  }

  // ==================== 编辑 ====================

  const editDialogVisible = ref(false)
  const editTargetId = ref('')
  const editForm = reactive({ name: '', description: '', isDefault: false })
  const editing = ref(false)

  async function openEditDialog(item: ApiEnvironmentListItem) {
    if (!canEdit.value) {
      ElMessage.warning('无环境编辑权限')
      return
    }
    if (!(await confirmLeaveCreate())) return
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
    if (!(await confirmLeaveCreate())) return
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
    createMode,
    createSeed,
    createDirty,
    startCreate,
    startCopy,
    exitCreate,
    handleCreated,
    handleSelect,
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
