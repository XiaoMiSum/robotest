import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiEnvironmentDetail, ApiEnvironmentListItem, ApiImportResult } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchEnvironments: vi.fn<() => Promise<ApiEnvironmentListItem[]>>(),
  fetchEnvironmentDetail: vi.fn<() => Promise<ApiEnvironmentDetail>>(),
  createEnvironment: vi.fn<() => Promise<{ id: string }>>(),
  updateEnvironment: vi.fn<() => Promise<boolean>>(),
  deleteEnvironment: vi.fn<() => Promise<boolean>>(),
  setDefaultEnvironment: vi.fn<() => Promise<{ success: boolean }>>(),
  sortEnvironment: vi.fn<() => Promise<boolean>>(),
  copyEnvironment: vi.fn<() => Promise<{ id: string }>>(),
  downloadEnvironmentJson: vi.fn<() => Promise<void>>(),
  importEnvironment: vi.fn<() => Promise<ApiImportResult>>(),
  buildSavePayload: vi.fn(),
  formatImportResult: vi.fn<() => string>(),
  resolveEnvironmentError: vi.fn<(err: unknown) => string>(),
  sortEnvironments: vi.fn<(list: ApiEnvironmentListItem[]) => ApiEnvironmentListItem[]>(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: {
    confirm: vi.fn<() => Promise<void>>(),
    alert: vi.fn<() => Promise<void>>(),
  },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => { cb() },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/services/project/environment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
  fetchEnvironmentDetail: mocks.fetchEnvironmentDetail,
  createEnvironment: mocks.createEnvironment,
  updateEnvironment: mocks.updateEnvironment,
  deleteEnvironment: mocks.deleteEnvironment,
  setDefaultEnvironment: mocks.setDefaultEnvironment,
  sortEnvironment: mocks.sortEnvironment,
  copyEnvironment: mocks.copyEnvironment,
  downloadEnvironmentJson: mocks.downloadEnvironmentJson,
  importEnvironment: mocks.importEnvironment,
}))

vi.mock('@/pages/project/api-testing/environment/environmentsModel', () => ({
  buildSavePayload: mocks.buildSavePayload,
  formatImportResult: mocks.formatImportResult,
  resolveEnvironmentError: mocks.resolveEnvironmentError,
  sortEnvironments: mocks.sortEnvironments,
}))

import { useEnvironmentPage } from './useEnvironmentPage'

function makeItem(overrides?: Partial<ApiEnvironmentListItem>): ApiEnvironmentListItem {
  return {
    id: 'env-1',
    name: '测试环境',
    description: '',
    isDefault: false,
    sortOrder: 1,
    scope: 'project',
    httpConfigCount: 0,
    variableCount: 0,
    dataSourceCount: 0,
    processorCount: 0,
    ...overrides,
  }
}

function makeDetail(overrides?: Partial<ApiEnvironmentDetail>): ApiEnvironmentDetail {
  return {
    id: 'env-1',
    name: '测试环境',
    description: '',
    scope: 'project',
    isDefault: false,
    sortOrder: 1,
    httpConfigs: [],
    variables: [],
    dataSources: [],
    processors: [],
    ...overrides,
  }
}

function defaultSortImpl(list: ApiEnvironmentListItem[]): ApiEnvironmentListItem[] {
  return [...list].sort((a, b) => {
    if (a.isDefault !== b.isDefault) return a.isDefault ? -1 : 1
    return a.sortOrder - b.sortOrder
  })
}

function setupMocks(options?: {
  items?: ApiEnvironmentListItem[]
  hasPermission?: boolean
}) {
  const items = options?.items ?? [makeItem()]
  mocks.fetchEnvironments.mockResolvedValue(items)
  mocks.sortEnvironments.mockImplementation(defaultSortImpl)
  mocks.resolveEnvironmentError.mockImplementation((err: unknown) => (err as Error)?.message ?? '操作失败')
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn().mockReturnValue(options?.hasPermission ?? true) })
}

async function initAndFlush(options?: Parameters<typeof setupMocks>[0]) {
  setupMocks(options)
  const s = useEnvironmentPage()
  await vi.dynamicImportSettled()
  return s
}

describe('useEnvironmentPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  describe('初始状态', () => {
    it('environments 和 selectedId 在 mount loadList 完成后正确设置', async () => {
      const item = makeItem({ id: 'env-1' })
      const s = await initAndFlush({ items: [item] })
      expect(s.environments.value).toEqual([item])
      expect(s.selectedId.value).toBe('env-1')
    })

    it('listLoading 在 loadList 完成后为 false', async () => {
      const s = await initAndFlush()
      expect(s.listLoading.value).toBe(false)
    })

    it('loadError 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.loadError.value).toBe(false)
    })

    it('keyword 初始为空字符串', async () => {
      const s = await initAndFlush()
      expect(s.keyword.value).toBe('')
    })

    it('createDialogVisible 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.createDialogVisible.value).toBe(false)
    })

    it('copyDialogVisible 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.copyDialogVisible.value).toBe(false)
    })

    it('editDialogVisible 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.editDialogVisible.value).toBe(false)
    })

    it('importDialogVisible 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.importDialogVisible.value).toBe(false)
    })

    it('importFile 初始为 null', async () => {
      const s = await initAndFlush()
      expect(s.importFile.value).toBeNull()
    })

    it('importFileList 初始为空数组', async () => {
      const s = await initAndFlush()
      expect(s.importFileList.value).toEqual([])
    })

    it('importOverwrite 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.importOverwrite.value).toBe(false)
    })

    it('creating 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.creating.value).toBe(false)
    })

    it('editing 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.editing.value).toBe(false)
    })

    it('importing 初始为 false', async () => {
      const s = await initAndFlush()
      expect(s.importing.value).toBe(false)
    })

    it('createForm 初始值正确', async () => {
      const s = await initAndFlush()
      expect(s.createForm).toEqual({ name: '', description: '', isDefault: false })
    })

    it('copySourceName 初始为空字符串', async () => {
      const s = await initAndFlush()
      expect(s.copySourceName.value).toBe('')
    })

    it('copyForm 初始值正确', async () => {
      const s = await initAndFlush()
      expect(s.copyForm).toEqual({ name: '' })
    })

    it('editTargetId 初始为空字符串', async () => {
      const s = await initAndFlush()
      expect(s.editTargetId.value).toBe('')
    })

    it('editForm 初始值正确', async () => {
      const s = await initAndFlush()
      expect(s.editForm).toEqual({ name: '', description: '', isDefault: false })
    })
  })

  describe('computed canEdit', () => {
    it('有编辑权限时返回 true', async () => {
      const s = await initAndFlush({ hasPermission: true })
      expect(s.canEdit.value).toBe(true)
    })

    it('无编辑权限时返回 false', async () => {
      const s = await initAndFlush({ hasPermission: false })
      expect(s.canEdit.value).toBe(false)
    })
  })

  describe('computed sortedList', () => {
    it('调用 sortEnvironments 并返回排序结果', async () => {
      const item1 = makeItem({ id: 'a', sortOrder: 2 })
      const item2 = makeItem({ id: 'b', sortOrder: 1 })
      mocks.sortEnvironments.mockReturnValue([item2, item1])
      const s = await initAndFlush({ items: [item1, item2] })
      expect(s.sortedList.value).toEqual([item2, item1])
    })

    it('空列表时返回空数组', async () => {
      const s = await initAndFlush({ items: [] })
      expect(s.sortedList.value).toEqual([])
    })
  })

  describe('loadList', () => {
    it('成功加载时填充 environments 并设置 selectedId', async () => {
      const item = makeItem({ id: 'env-1' })
      const s = await initAndFlush({ items: [item] })
      expect(s.environments.value).toEqual([item])
      expect(s.selectedId.value).toBe('env-1')
      expect(s.listLoading.value).toBe(false)
      expect(s.loadError.value).toBe(false)
    })

    it('keepSelection=true 时保留已选 id（存在时）', async () => {
      const items = [makeItem({ id: 'env-1' }), makeItem({ id: 'env-2' })]
      const s = await initAndFlush({ items })
      s.selectedId.value = 'env-2'
      await s.loadList(true)
      expect(s.selectedId.value).toBe('env-2')
    })

    it('keepSelection=true 时 selectedId 不存在则回退到首项', async () => {
      const items = [makeItem({ id: 'env-1' })]
      const s = await initAndFlush({ items })
      s.selectedId.value = 'env-missing'
      await s.loadList(true)
      expect(s.selectedId.value).toBe('env-1')
    })

    it('keepSelection=false 时不保留选中', async () => {
      const item = makeItem({ id: 'env-1' })
      const s = await initAndFlush({ items: [item] })
      s.selectedId.value = 'env-1'
      await s.loadList(false)
      expect(s.selectedId.value).toBe('env-1')
    })

    it('keyword 非空时传递 trimmed 值', async () => {
      const s = await initAndFlush({ items: [] })
      mocks.fetchEnvironments.mockResolvedValue([])
      s.keyword.value = '  test  '
      await s.loadList()
      expect(mocks.fetchEnvironments).toHaveBeenCalledWith('test')
    })

    it('keyword 为空时不传参', async () => {
      const s = await initAndFlush({ items: [] })
      mocks.fetchEnvironments.mockResolvedValue([])
      s.keyword.value = ''
      await s.loadList()
      expect(mocks.fetchEnvironments).toHaveBeenCalledWith(undefined)
    })

    it('加载失败时设置 loadError 并显示错误消息', async () => {
      const s = await initAndFlush()
      mocks.fetchEnvironments.mockRejectedValue(new Error('网络错误'))
      await s.loadList()
      expect(s.loadError.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.listLoading.value).toBe(false)
    })

    it('列表为空时 selectedId 为空字符串', async () => {
      const s = await initAndFlush({ items: [] })
      expect(s.selectedId.value).toBe('')
    })
  })

  describe('selectEnvironment', () => {
    it('切换 selectedId', async () => {
      const s = await initAndFlush()
      s.selectEnvironment('env-2')
      expect(s.selectedId.value).toBe('env-2')
    })

    it('重复 id 不触发更新', async () => {
      const s = await initAndFlush()
      s.selectedId.value = 'env-1'
      s.selectEnvironment('env-1')
      expect(s.selectedId.value).toBe('env-1')
    })
  })

  describe('canMove', () => {
    it('有邻居时返回 true', async () => {
      const a = makeItem({ id: 'a', sortOrder: 1 })
      const b = makeItem({ id: 'b', sortOrder: 2 })
      mocks.sortEnvironments.mockReturnValue([a, b])
      const s = await initAndFlush({ items: [a, b] })
      expect(s.canMove(a, 1)).toBe(true)
    })

    it('无邻居时返回 false（末尾向右）', async () => {
      const a = makeItem({ id: 'a', sortOrder: 1 })
      const b = makeItem({ id: 'b', sortOrder: 2 })
      mocks.sortEnvironments.mockReturnValue([a, b])
      const s = await initAndFlush({ items: [a, b] })
      expect(s.canMove(b, 1)).toBe(false)
    })

    it('无邻居时返回 false（首位向左）', async () => {
      const a = makeItem({ id: 'a', sortOrder: 1 })
      mocks.sortEnvironments.mockReturnValue([a])
      const s = await initAndFlush({ items: [a] })
      expect(s.canMove(a, -1)).toBe(false)
    })

    it('item 不在列表中返回 false', async () => {
      const a = makeItem({ id: 'a', sortOrder: 1 })
      mocks.sortEnvironments.mockReturnValue([a])
      const s = await initAndFlush({ items: [a] })
      expect(s.canMove(makeItem({ id: 'unknown' }), 1)).toBe(false)
    })
  })

  describe('handleMoveItem', () => {
    it('交换顺序后调用 loadList', async () => {
      const a = makeItem({ id: 'a', sortOrder: 1 })
      const b = makeItem({ id: 'b', sortOrder: 2 })
      mocks.sortEnvironments.mockReturnValue([a, b])
      const s = await initAndFlush({ items: [a, b] })
      mocks.sortEnvironment.mockResolvedValue(true)
      mocks.fetchEnvironments.mockResolvedValue([a, b])
      await s.handleMoveItem(a, 1)
      expect(mocks.sortEnvironment).toHaveBeenCalledWith('a', 2)
      expect(mocks.sortEnvironment).toHaveBeenCalledWith('b', 1)
    })

    it('item 不在列表中直接返回', async () => {
      const s = await initAndFlush({ items: [] })
      await s.handleMoveItem(makeItem({ id: 'unknown' }), 1)
      expect(mocks.sortEnvironment).not.toHaveBeenCalled()
    })

    it('失败时显示错误消息', async () => {
      const a = makeItem({ id: 'a', sortOrder: 1 })
      const b = makeItem({ id: 'b', sortOrder: 2 })
      mocks.sortEnvironments.mockReturnValue([a, b])
      const s = await initAndFlush({ items: [a, b] })
      mocks.sortEnvironment.mockRejectedValue(new Error('排序失败'))
      await s.handleMoveItem(a, 1)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('排序失败')
    })
  })

  describe('handleExport', () => {
    it('选中项存在时调用 downloadEnvironmentJson', async () => {
      const item = makeItem({ id: 'env-1', name: '生产环境' })
      mocks.downloadEnvironmentJson.mockResolvedValue()
      const s = await initAndFlush({ items: [item] })
      await s.handleExport()
      expect(mocks.downloadEnvironmentJson).toHaveBeenCalledWith('env-1', '生产环境.json')
    })

    it('无选中项时不调用 download', async () => {
      const s = await initAndFlush({ items: [] })
      await s.handleExport()
      expect(mocks.downloadEnvironmentJson).not.toHaveBeenCalled()
    })

    it('下载失败时显示错误', async () => {
      const item = makeItem({ id: 'env-1', name: '测试' })
      mocks.downloadEnvironmentJson.mockRejectedValue(new Error('下载失败'))
      const s = await initAndFlush({ items: [item] })
      await s.handleExport()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('下载失败')
    })
  })

  describe('openCreateDialog', () => {
    it('有权限时打开对话框并重置表单', async () => {
      const s = await initAndFlush({ hasPermission: true })
      s.openCreateDialog()
      expect(s.createDialogVisible.value).toBe(true)
      expect(s.createForm.name).toBe('')
      expect(s.createForm.description).toBe('')
      expect(s.createForm.isDefault).toBe(false)
    })

    it('无权限时显示警告并拒绝打开', async () => {
      const s = await initAndFlush({ hasPermission: false })
      s.openCreateDialog()
      expect(s.createDialogVisible.value).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('无环境编辑权限')
    })
  })

  describe('submitCreate', () => {
    it('名称为空时显示警告', async () => {
      const s = await initAndFlush({ hasPermission: true })
      s.createForm.name = '  '
      await s.submitCreate()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写环境名称')
      expect(mocks.createEnvironment).not.toHaveBeenCalled()
    })

    it('成功创建后关闭对话框、显示成功消息并刷新列表', async () => {
      const s = await initAndFlush({ hasPermission: true })
      mocks.createEnvironment.mockResolvedValue({ id: 'new-id' })
      mocks.fetchEnvironments.mockResolvedValue([makeItem({ id: 'new-id' })])
      s.createForm.name = '新环境'
      s.createForm.description = '描述'
      s.createForm.isDefault = true
      await s.submitCreate()
      expect(mocks.createEnvironment).toHaveBeenCalledWith({
        name: '新环境',
        description: '描述',
        isDefault: true,
      })
      expect(s.createDialogVisible.value).toBe(false)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('环境已创建')
      expect(s.selectedId.value).toBe('new-id')
    })

    it('description 为空字符串时不传 description', async () => {
      const s = await initAndFlush({ hasPermission: true })
      mocks.createEnvironment.mockResolvedValue({ id: 'new-id' })
      s.createForm.name = '新环境'
      s.createForm.description = ''
      await s.submitCreate()
      expect(mocks.createEnvironment).toHaveBeenCalledWith({
        name: '新环境',
        description: undefined,
        isDefault: false,
      })
    })

    it('创建失败时显示错误', async () => {
      const s = await initAndFlush({ hasPermission: true })
      mocks.createEnvironment.mockRejectedValue(new Error('创建失败'))
      s.createForm.name = '新环境'
      await s.submitCreate()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('创建失败')
      expect(s.creating.value).toBe(false)
    })

    it('creating 在 finally 中恢复为 false', async () => {
      const s = await initAndFlush({ hasPermission: true })
      mocks.createEnvironment.mockRejectedValue(new Error('fail'))
      s.createForm.name = '环境'
      await s.submitCreate()
      expect(s.creating.value).toBe(false)
    })
  })

  describe('openCopyDialog', () => {
    it('设置 sourceName 和默认副本名并打开对话框', async () => {
      const s = await initAndFlush()
      s.openCopyDialog(makeItem({ id: 'env-1', name: '生产' }))
      expect(s.copySourceName.value).toBe('env-1')
      expect(s.copyForm.name).toBe('生产（副本）')
      expect(s.copyDialogVisible.value).toBe(true)
    })
  })

  describe('submitCopy', () => {
    it('名称为空时不调用 API', async () => {
      const s = await initAndFlush()
      s.copyForm.name = '  '
      await s.submitCopy()
      expect(mocks.copyEnvironment).not.toHaveBeenCalled()
    })

    it('成功复制后关闭对话框、显示成功并刷新', async () => {
      const s = await initAndFlush()
      mocks.copyEnvironment.mockResolvedValue({ id: 'copied-id' })
      mocks.fetchEnvironments.mockResolvedValue([makeItem({ id: 'copied-id' })])
      s.copySourceName.value = 'env-1'
      s.copyForm.name = '副本环境'
      await s.submitCopy()
      expect(mocks.copyEnvironment).toHaveBeenCalledWith('env-1', '副本环境')
      expect(s.copyDialogVisible.value).toBe(false)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('复制成功')
      expect(s.selectedId.value).toBe('copied-id')
    })

    it('复制失败时显示错误', async () => {
      const s = await initAndFlush()
      mocks.copyEnvironment.mockRejectedValue(new Error('复制失败'))
      s.copySourceName.value = 'env-1'
      s.copyForm.name = '副本'
      await s.submitCopy()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('复制失败')
    })
  })

  describe('openEditDialog', () => {
    it('有权限时填充表单并打开', async () => {
      const s = await initAndFlush({ hasPermission: true })
      const item = makeItem({ id: 'env-2', name: '编辑环境', description: 'desc', isDefault: true })
      s.openEditDialog(item)
      expect(s.editTargetId.value).toBe('env-2')
      expect(s.editForm.name).toBe('编辑环境')
      expect(s.editForm.description).toBe('desc')
      expect(s.editForm.isDefault).toBe(true)
      expect(s.editDialogVisible.value).toBe(true)
      expect(s.selectedId.value).toBe('env-2')
    })

    it('无权限时显示警告', async () => {
      const s = await initAndFlush({ hasPermission: false })
      s.openEditDialog(makeItem())
      expect(s.editDialogVisible.value).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('无环境编辑权限')
    })

    it('description 为空时填充空字符串', async () => {
      const s = await initAndFlush({ hasPermission: true })
      s.openEditDialog(makeItem({ description: undefined }))
      expect(s.editForm.description).toBe('')
    })
  })

  describe('submitEdit', () => {
    it('名称为空时显示警告', async () => {
      const s = await initAndFlush({ hasPermission: true })
      s.editForm.name = '  '
      await s.submitEdit()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写环境名称')
      expect(mocks.fetchEnvironmentDetail).not.toHaveBeenCalled()
    })

    it('成功保存后关闭对话框、显示成功并刷新', async () => {
      const s = await initAndFlush({ hasPermission: true })
      const detail = makeDetail()
      mocks.fetchEnvironmentDetail.mockResolvedValue(detail)
      mocks.updateEnvironment.mockResolvedValue(true)
      mocks.buildSavePayload.mockReturnValue({ name: '编辑后', httpConfigs: [], variables: [], dataSources: [], processors: [] })
      s.editTargetId.value = 'env-1'
      s.editForm.name = '编辑后'
      s.editForm.description = '新描述'
      s.editForm.isDefault = true
      await s.submitEdit()
      expect(mocks.fetchEnvironmentDetail).toHaveBeenCalledWith('env-1')
      expect(mocks.updateEnvironment).toHaveBeenCalled()
      expect(s.editDialogVisible.value).toBe(false)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
    })

    it('编辑失败时显示错误', async () => {
      const s = await initAndFlush({ hasPermission: true })
      mocks.fetchEnvironmentDetail.mockResolvedValue(makeDetail())
      mocks.updateEnvironment.mockRejectedValue(new Error('编辑失败'))
      mocks.buildSavePayload.mockReturnValue({ name: 'test' })
      s.editTargetId.value = 'env-1'
      s.editForm.name = '编辑'
      await s.submitEdit()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('编辑失败')
      expect(s.editing.value).toBe(false)
    })

    it('editing 在 finally 中恢复为 false', async () => {
      const s = await initAndFlush({ hasPermission: true })
      mocks.fetchEnvironmentDetail.mockRejectedValue(new Error('fail'))
      s.editTargetId.value = 'env-1'
      s.editForm.name = '编辑'
      await s.submitEdit()
      expect(s.editing.value).toBe(false)
    })
  })

  describe('openImportDialog', () => {
    it('重置状态并打开对话框', async () => {
      const s = await initAndFlush()
      s.openImportDialog()
      expect(s.importFile.value).toBeNull()
      expect(s.importFileList.value).toEqual([])
      expect(s.importOverwrite.value).toBe(false)
      expect(s.importDialogVisible.value).toBe(true)
    })
  })

  describe('handleImportFileChange', () => {
    it('从 uploadFile 中提取 raw 赋值 importFile', async () => {
      const s = await initAndFlush()
      const rawFile = new File(['test'], 'test.json')
      s.handleImportFileChange({ raw: rawFile })
      expect(s.importFile.value).toBe(rawFile)
    })

    it('uploadFile 无 raw 时 importFile 保持 null', async () => {
      const s = await initAndFlush()
      s.handleImportFileChange({})
      expect(s.importFile.value).toBeNull()
    })
  })

  describe('handleImportFileRemove', () => {
    it('清空 importFile', async () => {
      const s = await initAndFlush()
      s.importFile.value = new File(['test'], 'test.json')
      s.handleImportFileRemove()
      expect(s.importFile.value).toBeNull()
    })
  })

  describe('submitImport', () => {
    it('未选择文件时显示警告', async () => {
      const s = await initAndFlush()
      await s.submitImport()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择环境 JSON 文件')
      expect(mocks.importEnvironment).not.toHaveBeenCalled()
    })

    it('导入成功后关闭对话框、显示结果并刷新', async () => {
      const s = await initAndFlush()
      const result: ApiImportResult = { createdCount: 2, overwrittenCount: 1, skippedCount: 0 }
      mocks.importEnvironment.mockResolvedValue(result)
      mocks.formatImportResult.mockReturnValue('导入完成：新增 2 个、覆盖 1 个')
      mocks.ElMessageBox.alert.mockResolvedValue()
      s.importFile.value = new File(['data'], 'env.json')
      s.importOverwrite.value = true
      await s.submitImport()
      expect(mocks.importEnvironment).toHaveBeenCalledWith(s.importFile.value, true)
      expect(s.importDialogVisible.value).toBe(false)
      expect(mocks.ElMessageBox.alert).toHaveBeenCalledWith('导入完成：新增 2 个、覆盖 1 个', '导入结果', { confirmButtonText: '知道了' })
    })

    it('导入失败时显示错误', async () => {
      const s = await initAndFlush()
      mocks.importEnvironment.mockRejectedValue(new Error('导入失败'))
      s.importFile.value = new File(['data'], 'env.json')
      await s.submitImport()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('导入失败')
      expect(s.importing.value).toBe(false)
    })

    it('importing 在 finally 中恢复为 false', async () => {
      const s = await initAndFlush()
      mocks.importEnvironment.mockRejectedValue(new Error('fail'))
      s.importFile.value = new File(['data'], 'env.json')
      await s.submitImport()
      expect(s.importing.value).toBe(false)
    })
  })

  describe('handleDelete', () => {
    it('用户确认后删除并刷新', async () => {
      const s = await initAndFlush()
      mocks.ElMessageBox.confirm.mockResolvedValue()
      mocks.deleteEnvironment.mockResolvedValue(true)
      mocks.fetchEnvironments.mockResolvedValue([])
      await s.handleDelete(makeItem({ id: 'env-1', name: '测试环境' }))
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '删除后环境配置不可恢复，确认删除「测试环境」？',
        '删除环境',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
      expect(mocks.deleteEnvironment).toHaveBeenCalledWith('env-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
    })

    it('用户取消时不调用 delete', async () => {
      const s = await initAndFlush()
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      await s.handleDelete(makeItem())
      expect(mocks.deleteEnvironment).not.toHaveBeenCalled()
    })

    it('删除的是当前选中项时清空 selectedId', async () => {
      const s = await initAndFlush()
      s.selectedId.value = 'env-1'
      mocks.ElMessageBox.confirm.mockResolvedValue()
      mocks.deleteEnvironment.mockResolvedValue(true)
      mocks.fetchEnvironments.mockResolvedValue([])
      await s.handleDelete(makeItem({ id: 'env-1', name: '环境' }))
      expect(s.selectedId.value).toBe('')
    })

    it('删除的不是当前选中项时保留 selectedId', async () => {
      const item1 = makeItem({ id: 'env-1', name: '环境1' })
      const item2 = makeItem({ id: 'env-2', name: '环境2' })
      mocks.fetchEnvironments.mockResolvedValue([item1, item2])
      const s = await initAndFlush({ items: [item1, item2] })
      s.selectedId.value = 'env-2'
      mocks.ElMessageBox.confirm.mockResolvedValue()
      mocks.deleteEnvironment.mockResolvedValue(true)
      mocks.fetchEnvironments.mockResolvedValue([item2])
      await s.handleDelete(item1)
      expect(s.selectedId.value).toBe('env-2')
    })

    it('删除失败时显示错误', async () => {
      const s = await initAndFlush()
      mocks.ElMessageBox.confirm.mockResolvedValue()
      mocks.deleteEnvironment.mockRejectedValue(new Error('删除失败'))
      await s.handleDelete(makeItem({ id: 'env-1', name: '环境' }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
    })
  })

  describe('handleSetDefault', () => {
    it('用户确认后调用 setDefaultEnvironment 并刷新', async () => {
      const s = await initAndFlush()
      mocks.ElMessageBox.confirm.mockResolvedValue()
      mocks.setDefaultEnvironment.mockResolvedValue({ success: true })
      await s.handleSetDefault(makeItem({ id: 'env-1', name: '生产环境' }))
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '确认将「生产环境」设为默认环境？场景执行未指定环境时将使用默认环境',
        '设为默认',
        { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
      )
      expect(mocks.setDefaultEnvironment).toHaveBeenCalledWith('env-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已设为默认')
    })

    it('用户取消时不调用 setDefault', async () => {
      const s = await initAndFlush()
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      await s.handleSetDefault(makeItem())
      expect(mocks.setDefaultEnvironment).not.toHaveBeenCalled()
    })

    it('设置失败时显示错误', async () => {
      const s = await initAndFlush()
      mocks.ElMessageBox.confirm.mockResolvedValue()
      mocks.setDefaultEnvironment.mockRejectedValue(new Error('设置失败'))
      await s.handleSetDefault(makeItem({ id: 'env-1', name: '环境' }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('设置失败')
    })
  })

  describe('handleSearchInput', () => {
    it('300ms 内多次触发只执行一次 loadList', async () => {
      const s = await initAndFlush({ items: [] })
      vi.clearAllMocks()
      mocks.fetchEnvironments.mockResolvedValue([])
      s.handleSearchInput()
      s.handleSearchInput()
      s.handleSearchInput()
      expect(mocks.fetchEnvironments).not.toHaveBeenCalled()
      vi.advanceTimersByTime(300)
      await vi.dynamicImportSettled()
      expect(mocks.fetchEnvironments).toHaveBeenCalledTimes(1)
    })

    it('重置 timer 后前一次调用被取消', async () => {
      const s = await initAndFlush({ items: [] })
      vi.clearAllMocks()
      mocks.fetchEnvironments.mockResolvedValue([])
      s.handleSearchInput()
      vi.advanceTimersByTime(200)
      s.handleSearchInput()
      vi.advanceTimersByTime(300)
      await vi.dynamicImportSettled()
      expect(mocks.fetchEnvironments).toHaveBeenCalledTimes(1)
    })
  })

  describe('onMounted', () => {
    it('挂载时自动调用 loadList(false)', async () => {
      await initAndFlush({ items: [] })
      expect(mocks.fetchEnvironments).toHaveBeenCalledWith(undefined)
    })

    it('挂载时调用 loadList 传入 undefined keyword', async () => {
      await initAndFlush({ items: [] })
      expect(mocks.fetchEnvironments).toHaveBeenCalledWith(undefined)
    })
  })
})
