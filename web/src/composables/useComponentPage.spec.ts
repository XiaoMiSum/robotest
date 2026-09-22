import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  useAuthStore: vi.fn(),
  fetchComponents: vi.fn(),
  fetchEnvironments: vi.fn(),
  fetchEnvironmentDetail: vi.fn(),
  batchDeleteComponents: vi.fn(),
  batchToggleComponents: vi.fn(),
  copyComponent: vi.fn(),
  createComponent: vi.fn(),
  deleteComponent: vi.fn(),
  toggleComponent: vi.fn(),
  updateComponent: vi.fn(),
  resolveComponentError: vi.fn(),
  createProcessorComponentConfig: vi.fn(),
  defaultComponentConfig: vi.fn(),
  extractorsFromComponents: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/services/project/component', () => ({
  batchDeleteComponents: mocks.batchDeleteComponents,
  batchToggleComponents: mocks.batchToggleComponents,
  copyComponent: mocks.copyComponent,
  createComponent: mocks.createComponent,
  deleteComponent: mocks.deleteComponent,
  fetchComponents: mocks.fetchComponents,
  toggleComponent: mocks.toggleComponent,
  updateComponent: mocks.updateComponent,
}))

vi.mock('@/services/project/environment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
  fetchEnvironmentDetail: mocks.fetchEnvironmentDetail,
}))

vi.mock('@/pages/project/componentModel', () => ({
  COMPONENT_SCOPE_OPTIONS: [
    { value: 'global', label: '公共' },
    { value: 'workspace', label: '空间' },
    { value: 'project', label: '项目' },
  ],
  COMPONENT_TYPE_OPTIONS: [
    { value: 'preprocessor', label: '前置处理器' },
    { value: 'postprocessor', label: '后置处理器' },
    { value: 'validator', label: '验证器' },
    { value: 'extractor', label: '提取器' },
  ],
  SCOPE_TAG_TYPE: { global: undefined, workspace: 'success', project: 'info' },
  componentScopeLabel: vi.fn(),
  componentTypeLabel: vi.fn(),
  resolveComponentError: mocks.resolveComponentError,
}))

vi.mock('@/components/api-testing/processorFormModel', () => ({
  createProcessorComponentConfig: mocks.createProcessorComponentConfig,
  defaultComponentConfig: mocks.defaultComponentConfig,
  extractorsFromComponents: mocks.extractorsFromComponents,
}))

import { useComponentPage } from './useComponentPage'

function mockHasPermission(permissions: string[]) {
  mocks.useAuthStore.mockReturnValue({
    hasPermission: (code: string) => permissions.includes(code),
  })
}

const okList = { list: [], total: 0 }

beforeEach(() => {
  vi.clearAllMocks()
  mockHasPermission([])
  mocks.resolveComponentError.mockReturnValue('操作失败')
  mocks.createProcessorComponentConfig.mockReturnValue({ enabled: true, testclass: 'http', config: {}, extractors: [] })
  mocks.defaultComponentConfig.mockReturnValue({ enabled: true })
  mocks.extractorsFromComponents.mockReturnValue([])
  mocks.fetchComponents.mockResolvedValue(okList)
  mocks.fetchEnvironments.mockResolvedValue([])
})

describe('useComponentPage', () => {
  describe('canEdit', () => {
    it('no permissions → false', () => {
      mockHasPermission([])
      const { canEdit } = useComponentPage()
      expect(canEdit.value).toBe(false)
    })

    it('has api-component:edit → true', () => {
      mockHasPermission(['api-component:edit'])
      const { canEdit } = useComponentPage()
      expect(canEdit.value).toBe(true)
    })

    it('has api-component:edit-space → true', () => {
      mockHasPermission(['api-component:edit-space'])
      const { canEdit } = useComponentPage()
      expect(canEdit.value).toBe(true)
    })

    it('has api-component:edit-global → true', () => {
      mockHasPermission(['api-component:edit-global'])
      const { canEdit } = useComponentPage()
      expect(canEdit.value).toBe(true)
    })
  })

  describe('初始状态', () => {
    it('listLoading 初始为 false', () => {
      const { listLoading } = useComponentPage()
      expect(listLoading.value).toBe(false)
    })

    it('loadError 初始为 false', () => {
      const { loadError } = useComponentPage()
      expect(loadError.value).toBe(false)
    })

    it('list 初始为空数组', () => {
      const { list } = useComponentPage()
      expect(list.value).toEqual([])
    })

    it('total 初始为 0', () => {
      const { total } = useComponentPage()
      expect(total.value).toBe(0)
    })

    it('selectedIds 初始为空数组', () => {
      const { selectedIds } = useComponentPage()
      expect(selectedIds.value).toEqual([])
    })

    it('keyword 初始为空字符串', () => {
      const { keyword } = useComponentPage()
      expect(keyword.value).toBe('')
    })

    it('keywordDraft 初始为空字符串', () => {
      const { keywordDraft } = useComponentPage()
      expect(keywordDraft.value).toBe('')
    })

    it('filterType 初始为空字符串', () => {
      const { filterType } = useComponentPage()
      expect(filterType.value).toBe('')
    })

    it('filterScope 初始为空字符串', () => {
      const { filterScope } = useComponentPage()
      expect(filterScope.value).toBe('')
    })

    it('filterEnabled 初始为空字符串', () => {
      const { filterEnabled } = useComponentPage()
      expect(filterEnabled.value).toBe('')
    })

    it('pageNo 初始为 1', () => {
      const { pageNo } = useComponentPage()
      expect(pageNo.value).toBe(1)
    })

    it('pageSize 初始为 20', () => {
      const { pageSize } = useComponentPage()
      expect(pageSize.value).toBe(20)
    })

    it('drawerVisible 初始为 false', () => {
      const { drawerVisible } = useComponentPage()
      expect(drawerVisible.value).toBe(false)
    })

    it('editingId 初始为 null', () => {
      const { editingId } = useComponentPage()
      expect(editingId.value).toBeNull()
    })

    it('saving 初始为 false', () => {
      const { saving } = useComponentPage()
      expect(saving.value).toBe(false)
    })

    it('hasSelection 初始为 false', () => {
      const { hasSelection } = useComponentPage()
      expect(hasSelection.value).toBe(false)
    })
  })

  describe('form', () => {
    it('初始值正确', () => {
      const { form } = useComponentPage()
      expect(form.type).toBe('preprocessor')
      expect(form.name).toBe('')
      expect(form.description).toBe('')
      expect(form.scope).toBe('project')
      expect(form.sortOrder).toBe(0)
      expect(form.config).toEqual({})
    })
  })

  describe('basicConfigEnabled', () => {
    it('config.enabled 未定义时返回 true', () => {
      const { form, basicConfigEnabled } = useComponentPage()
      form.config = {}
      expect(basicConfigEnabled.value).toBe(true)
    })

    it('config.enabled 为 false 时返回 false', () => {
      const { form, basicConfigEnabled } = useComponentPage()
      form.config = { enabled: false }
      expect(basicConfigEnabled.value).toBe(false)
    })

    it('setter 更新 config.enabled', () => {
      const { form, basicConfigEnabled } = useComponentPage()
      form.config = {}
      basicConfigEnabled.value = false
      expect(form.config.enabled).toBe(false)
    })

    it('setter 设置为 true', () => {
      const { form, basicConfigEnabled } = useComponentPage()
      form.config = { enabled: false }
      basicConfigEnabled.value = true
      expect(form.config.enabled).toBe(true)
    })
  })

  describe('loadList', () => {
    it('成功时设置 list 和 total', async () => {
      const items = [{ id: '1' }, { id: '2' }]
      mocks.fetchComponents.mockResolvedValue({ list: items, total: 2 })
      const { loadList, list, total, listLoading } = useComponentPage()
      await loadList()
      expect(list.value).toEqual(items)
      expect(total.value).toBe(2)
      expect(listLoading.value).toBe(false)
    })

    it('成功后清空 selectedIds', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [], total: 0 })
      const { loadList, selectedIds } = useComponentPage()
      selectedIds.value = ['1', '2']
      await loadList()
      expect(selectedIds.value).toEqual([])
    })

    it('失败时设置 loadError 为 true 并显示错误', async () => {
      mocks.fetchComponents.mockRejectedValue(new Error('fail'))
      const { loadList, loadError, listLoading } = useComponentPage()
      await loadList()
      expect(loadError.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
      expect(listLoading.value).toBe(false)
    })

    it('设置 listLoading 为 true 然后恢复', async () => {
      const { loadList, listLoading } = useComponentPage()
      expect(listLoading.value).toBe(false)
      const p = loadList()
      expect(listLoading.value).toBe(true)
      await p
      expect(listLoading.value).toBe(false)
    })

    it('传入 filterType 参数', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList, filterType } = useComponentPage()
      filterType.value = 'extractor'
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'extractor' }),
      )
    })

    it('传入 filterScope 参数', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList, filterScope } = useComponentPage()
      filterScope.value = 'workspace'
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ scope: 'workspace' }),
      )
    })

    it('filterEnabled 为 true 时传 enabled=true', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList, filterEnabled } = useComponentPage()
      filterEnabled.value = true
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ enabled: true }),
      )
    })

    it('filterEnabled 为 false 时传 enabled=false', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList, filterEnabled } = useComponentPage()
      filterEnabled.value = false
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ enabled: false }),
      )
    })

    it('filterEnabled 为空字符串时传 enabled=undefined', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList, filterEnabled } = useComponentPage()
      filterEnabled.value = ''
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ enabled: undefined }),
      )
    })

    it('keyword 有值时传递 trim 后的关键字', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList, keyword } = useComponentPage()
      keyword.value = '  test  '
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: 'test' }),
      )
    })

    it('keyword 为空时传递 keyword=undefined', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { loadList } = useComponentPage()
      await loadList()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: undefined }),
      )
    })
  })

  describe('handlePageChange', () => {
    it('更新 pageNo 并重新加载', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handlePageChange, pageNo } = useComponentPage()
      await handlePageChange(3)
      expect(pageNo.value).toBe(3)
      expect(mocks.fetchComponents).toHaveBeenCalled()
    })
  })

  describe('handleSizeChange', () => {
    it('更新 pageSize 和 pageNo=1 并重新加载', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSizeChange, pageSize, pageNo } = useComponentPage()
      await handleSizeChange(50)
      expect(pageSize.value).toBe(50)
      expect(pageNo.value).toBe(1)
      expect(mocks.fetchComponents).toHaveBeenCalled()
    })
  })

  describe('handleSearch', () => {
    it('复制 keywordDraft 到 keyword 并重置 pageNo', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSearch, keyword, keywordDraft, pageNo } = useComponentPage()
      keywordDraft.value = 'hello'
      pageNo.value = 5
      await handleSearch()
      expect(keyword.value).toBe('hello')
      expect(pageNo.value).toBe(1)
    })
  })

  describe('handleReset', () => {
    it('重置所有筛选条件和分页', async () => {
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleReset, keyword, keywordDraft, filterType, filterScope, filterEnabled, pageNo } = useComponentPage()
      keyword.value = 'abc'
      keywordDraft.value = 'abc'
      filterType.value = 'extractor'
      filterScope.value = 'workspace'
      filterEnabled.value = true
      pageNo.value = 3
      await handleReset()
      expect(keyword.value).toBe('')
      expect(keywordDraft.value).toBe('')
      expect(filterType.value).toBe('')
      expect(filterScope.value).toBe('')
      expect(filterEnabled.value).toBe('')
      expect(pageNo.value).toBe(1)
    })
  })

  describe('handleSelectionChange', () => {
    it('更新 selectedIds', () => {
      const { handleSelectionChange, selectedIds } = useComponentPage()
      handleSelectionChange([{ id: '1' } as never, { id: '2' } as never])
      expect(selectedIds.value).toEqual(['1', '2'])
    })

    it('空数组时 selectedIds 为空', () => {
      const { handleSelectionChange, selectedIds } = useComponentPage()
      selectedIds.value = ['1']
      handleSelectionChange([])
      expect(selectedIds.value).toEqual([])
    })
  })

  describe('hasSelection', () => {
    it('selectedIds 非空时为 true', () => {
      const { selectedIds, hasSelection } = useComponentPage()
      selectedIds.value = ['1']
      expect(hasSelection.value).toBe(true)
    })

    it('selectedIds 为空时为 false', () => {
      const { hasSelection } = useComponentPage()
      expect(hasSelection.value).toBe(false)
    })
  })

  describe('handleToggle', () => {
    it('成功时调用 toggleComponent 并重新加载', async () => {
      mocks.toggleComponent.mockResolvedValue(true)
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleToggle } = useComponentPage()
      await handleToggle({ id: 'c1', enabled: false } as never)
      expect(mocks.toggleComponent).toHaveBeenCalledWith('c1', true)
      expect(mocks.fetchComponents).toHaveBeenCalled()
    })

    it('失败时显示错误', async () => {
      mocks.toggleComponent.mockRejectedValue(new Error('fail'))
      const { handleToggle } = useComponentPage()
      await handleToggle({ id: 'c1', enabled: true } as never)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleBatchToggle', () => {
    it('无选中时不执行', async () => {
      const { handleBatchToggle } = useComponentPage()
      await handleBatchToggle(true)
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('确认后调用 batchToggleComponents', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.batchToggleComponents.mockResolvedValue(true)
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleBatchToggle, selectedIds } = useComponentPage()
      selectedIds.value = ['1', '2']
      await handleBatchToggle(true)
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '确认启用选中的 2 个组件？',
        '批量启用',
        expect.anything(),
      )
      expect(mocks.batchToggleComponents).toHaveBeenCalledWith(['1', '2'], true)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已启用')
    })

    it('停用时显示正确消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.batchToggleComponents.mockResolvedValue(true)
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleBatchToggle, selectedIds } = useComponentPage()
      selectedIds.value = ['1']
      await handleBatchToggle(false)
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '确认停用选中的 1 个组件？',
        '批量停用',
        expect.anything(),
      )
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已停用')
    })

    it('取消时不执行批量操作', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const { handleBatchToggle, selectedIds } = useComponentPage()
      selectedIds.value = ['1']
      await handleBatchToggle(true)
      expect(mocks.batchToggleComponents).not.toHaveBeenCalled()
    })

    it('API 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.batchToggleComponents.mockRejectedValue(new Error('fail'))
      const { handleBatchToggle, selectedIds } = useComponentPage()
      selectedIds.value = ['1']
      await handleBatchToggle(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleBatchDelete', () => {
    it('无选中时不执行', async () => {
      const { handleBatchDelete } = useComponentPage()
      await handleBatchDelete()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('确认后调用 batchDeleteComponents', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.batchDeleteComponents.mockResolvedValue(true)
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleBatchDelete, selectedIds } = useComponentPage()
      selectedIds.value = ['1', '2']
      await handleBatchDelete()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        expect.stringContaining('2'),
        '批量删除',
        expect.anything(),
      )
      expect(mocks.batchDeleteComponents).toHaveBeenCalledWith(['1', '2'])
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
    })

    it('取消时不执行', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const { handleBatchDelete, selectedIds } = useComponentPage()
      selectedIds.value = ['1']
      await handleBatchDelete()
      expect(mocks.batchDeleteComponents).not.toHaveBeenCalled()
    })

    it('API 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.batchDeleteComponents.mockRejectedValue(new Error('fail'))
      const { handleBatchDelete, selectedIds } = useComponentPage()
      selectedIds.value = ['1']
      await handleBatchDelete()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleDelete', () => {
    it('确认后调用 deleteComponent', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteComponent.mockResolvedValue(true)
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleDelete } = useComponentPage()
      await handleDelete({ id: 'c1', name: '测试组件' } as never)
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '删除后不可恢复，确认删除「测试组件」？已引入的副本不受影响',
        '删除组件',
        expect.anything(),
      )
      expect(mocks.deleteComponent).toHaveBeenCalledWith('c1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
    })

    it('取消时不执行', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const { handleDelete } = useComponentPage()
      await handleDelete({ id: 'c1', name: 'test' } as never)
      expect(mocks.deleteComponent).not.toHaveBeenCalled()
    })

    it('API 失败时显示错误', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteComponent.mockRejectedValue(new Error('fail'))
      const { handleDelete } = useComponentPage()
      await handleDelete({ id: 'c1', name: 'test' } as never)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('handleCopy', () => {
    it('成功时显示消息并重新加载', async () => {
      mocks.copyComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleCopy } = useComponentPage()
      await handleCopy({ id: 'c1' } as never)
      expect(mocks.copyComponent).toHaveBeenCalledWith('c1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已复制')
      expect(mocks.fetchComponents).toHaveBeenCalled()
    })

    it('失败时显示错误', async () => {
      mocks.copyComponent.mockRejectedValue(new Error('fail'))
      const { handleCopy } = useComponentPage()
      await handleCopy({ id: 'c1' } as never)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('openCreateDrawer', () => {
    it('重置 form 为默认值并打开抽屉', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openCreateDrawer, editingId, drawerVisible, form } = useComponentPage()
      openCreateDrawer()
      expect(editingId.value).toBeNull()
      expect(drawerVisible.value).toBe(true)
      expect(form.type).toBe('preprocessor')
      expect(form.name).toBe('')
      expect(form.description).toBe('')
      expect(form.scope).toBe('project')
      expect(form.sortOrder).toBe(0)
    })

    it('调用 createProcessorComponentConfig 初始化 config', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openCreateDrawer, form } = useComponentPage()
      openCreateDrawer()
      expect(mocks.createProcessorComponentConfig).toHaveBeenCalled()
      expect(form.config).toEqual({ enabled: true, testclass: 'http', config: {}, extractors: [] })
    })
  })

  describe('openEditDrawer', () => {
    it('设置 editingId 和 form 值', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openEditDrawer, editingId, drawerVisible, form } = useComponentPage()
      openEditDrawer({
        id: 'c1',
        type: 'extractor',
        name: '提取器',
        description: 'desc',
        scope: 'workspace',
        sortOrder: 5,
        config: '{"key":"val"}',
      } as never)
      expect(editingId.value).toBe('c1')
      expect(drawerVisible.value).toBe(true)
      expect(form.type).toBe('extractor')
      expect(form.name).toBe('提取器')
      expect(form.description).toBe('desc')
      expect(form.scope).toBe('workspace')
      expect(form.sortOrder).toBe(5)
      expect(form.config).toEqual({ key: 'val' })
    })

    it('config 为 null 时使用空对象', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openEditDrawer, form } = useComponentPage()
      openEditDrawer({ id: 'c1', type: 'preprocessor', name: 'a', config: null, scope: 'project' } as never)
      expect(form.config).toEqual({})
    })

    it('config 为非法 JSON 时使用空对象', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openEditDrawer, form } = useComponentPage()
      openEditDrawer({ id: 'c1', type: 'preprocessor', name: 'a', config: 'not-json', scope: 'project' } as never)
      expect(form.config).toEqual({})
    })

    it('description 为 null 时使用空字符串', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openEditDrawer, form } = useComponentPage()
      openEditDrawer({ id: 'c1', type: 'preprocessor', name: 'a', description: null, scope: 'project' } as never)
      expect(form.description).toBe('')
    })

    it('sortOrder 非数字时使用 0', () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openEditDrawer, form } = useComponentPage()
      openEditDrawer({ id: 'c1', type: 'preprocessor', name: 'a', sortOrder: undefined, scope: 'project' } as never)
      expect(form.sortOrder).toBe(0)
    })
  })

  describe('watch form.type', () => {
    it('非编辑模式下切换到 preprocessor 时重置 config', async () => {
      const { form } = useComponentPage()
      form.config = { old: true }
      form.type = 'postprocessor'
      await nextTick()
      expect(mocks.createProcessorComponentConfig).toHaveBeenCalled()
    })

    it('编辑模式下不重置 config', async () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openEditDrawer, form } = useComponentPage()
      openEditDrawer({ id: 'c1', type: 'preprocessor', name: 'a', config: '{"keep":true}', scope: 'project' } as never)
      mocks.createProcessorComponentConfig.mockClear()
      form.type = 'postprocessor'
      await nextTick()
      expect(mocks.createProcessorComponentConfig).not.toHaveBeenCalled()
    })

    it('切换到非处理器类型时 config 设为空对象', async () => {
      const { form } = useComponentPage()
      form.type = 'extractor'
      await nextTick()
      expect(form.config).toEqual({})
    })
  })

  describe('handleSave', () => {
    it('名称为空时显示警告', async () => {
      const { handleSave, form } = useComponentPage()
      form.name = '   '
      await handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写组件名称')
      expect(mocks.createComponent).not.toHaveBeenCalled()
    })

    it('新建成功时调用 createComponent 并关闭抽屉', async () => {
      mocks.createComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSave, form, drawerVisible, saving } = useComponentPage()
      form.name = '新组件'
      await handleSave()
      expect(mocks.createComponent).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'preprocessor',
          name: '新组件',
          scope: 'project',
        }),
      )
      expect(drawerVisible.value).toBe(false)
      expect(saving.value).toBe(false)
    })

    it('description 有值时 trim 后传递', async () => {
      mocks.createComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSave, form } = useComponentPage()
      form.name = 'test'
      form.description = '  desc  '
      await handleSave()
      expect(mocks.createComponent).toHaveBeenCalledWith(
        expect.objectContaining({ description: 'desc' }),
      )
    })

    it('description 为空字符串时传 undefined', async () => {
      mocks.createComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSave, form } = useComponentPage()
      form.name = 'test'
      form.description = ''
      await handleSave()
      expect(mocks.createComponent).toHaveBeenCalledWith(
        expect.objectContaining({ description: undefined }),
      )
    })

    it('编辑成功时调用 updateComponent', async () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      mocks.updateComponent.mockResolvedValue(true)
      mocks.fetchComponents.mockResolvedValue(okList)
      const { openEditDrawer, handleSave, form } = useComponentPage()
      openEditDrawer({ id: 'c1', type: 'preprocessor', name: 'old', config: '{}', scope: 'project' } as never)
      form.name = 'updated'
      await handleSave()
      expect(mocks.updateComponent).toHaveBeenCalledWith('c1', expect.objectContaining({ name: 'updated' }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已更新')
    })

    it('API 失败时显示错误', async () => {
      mocks.createComponent.mockRejectedValue(new Error('fail'))
      const { handleSave, form, saving } = useComponentPage()
      form.name = 'test'
      await handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
      expect(saving.value).toBe(false)
    })

    it('合并 defaultComponentConfig', async () => {
      mocks.createComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSave, form } = useComponentPage()
      form.name = 'test'
      form.config = { custom: true }
      await handleSave()
      expect(mocks.defaultComponentConfig).toHaveBeenCalled()
      expect(mocks.createComponent).toHaveBeenCalledWith(
        expect.objectContaining({ config: { enabled: true, custom: true } }),
      )
    })

    it('config 为空对象时仅包含 defaultComponentConfig', async () => {
      mocks.createComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList)
      const { handleSave, form } = useComponentPage()
      form.name = 'test'
      form.config = {}
      await handleSave()
      expect(mocks.createComponent).toHaveBeenCalledWith(
        expect.objectContaining({ config: { enabled: true } }),
      )
    })
  })

  describe('extractorPicker', () => {
    it('openExtractorPicker 打开 picker 并加载数据', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [{ id: 'e1' }], total: 1 })
      const { openExtractorPicker, extractorPickerVisible, extractorPickerKeyword } = useComponentPage()
      openExtractorPicker()
      expect(extractorPickerVisible.value).toBe(true)
      expect(extractorPickerKeyword.value).toBe('')
      await nextTick()
      expect(mocks.fetchComponents).toHaveBeenCalledWith({
        type: 'extractor',
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: undefined,
      })
    })

    it('loadExtractorAssets 成功时设置 items', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [{ id: 'e1' }, { id: 'e2' }], total: 2 })
      const { loadExtractorAssets, extractorPickerItems } = useComponentPage()
      await loadExtractorAssets()
      expect(extractorPickerItems.value).toHaveLength(2)
    })

    it('loadExtractorAssets 失败时显示错误', async () => {
      mocks.fetchComponents.mockRejectedValue(new Error('fail'))
      const { loadExtractorAssets, extractorPickerLoading } = useComponentPage()
      await loadExtractorAssets()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
      expect(extractorPickerLoading.value).toBe(false)
    })

    it('loadExtractorAssets 传递 keyword', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [], total: 0 })
      const { loadExtractorAssets, extractorPickerKeyword } = useComponentPage()
      extractorPickerKeyword.value = 'my extractor'
      await loadExtractorAssets()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: 'my extractor' }),
      )
    })

    it('keyword 为空白时传 undefined', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [], total: 0 })
      const { loadExtractorAssets, extractorPickerKeyword } = useComponentPage()
      extractorPickerKeyword.value = '   '
      await loadExtractorAssets()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: undefined }),
      )
    })
  })

  describe('handleExtractorPicked', () => {
    it('无提取器时不操作', () => {
      mocks.extractorsFromComponents.mockReturnValue([])
      const { handleExtractorPicked } = useComponentPage()
      handleExtractorPicked([])
      expect(mocks.ElMessage.success).not.toHaveBeenCalled()
    })

    it('有提取器时合并到 form.config.extractors', () => {
      const incoming = [{ source: 'body', expression: '$.id', variableName: 'id', description: '', enabled: true }]
      mocks.extractorsFromComponents.mockReturnValue(incoming)
      const { handleExtractorPicked, form } = useComponentPage()
      handleExtractorPicked([{ id: 'e1' } as never])
      expect(form.config.extractors).toEqual(incoming)
    })

    it('已有 extractors 时追加而非覆盖', () => {
      const existing = [{ source: 'header', expression: 'X-Test', variableName: 'h', description: '', enabled: true }]
      const incoming = [{ source: 'body', expression: '$.x', variableName: 'x', description: '', enabled: true }]
      mocks.extractorsFromComponents.mockReturnValue(incoming)
      const { handleExtractorPicked, form } = useComponentPage()
      form.config = { extractors: existing }
      handleExtractorPicked([{ id: 'e1' } as never])
      expect(form.config.extractors).toHaveLength(2)
    })
  })

  describe('loadProcessorRefOptions (via drawer)', () => {
    it('有默认环境时加载 httpConfigs 和 dataSources', async () => {
      mocks.fetchEnvironments.mockResolvedValue([{ id: 'env1', isDefault: true }])
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [{ baseUrl: 'http://a.com' }],
        dataSources: [{ name: 'db1' }],
      })
      const { openCreateDrawer, httpRefOptions, dsRefOptions } = useComponentPage()
      openCreateDrawer()
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toEqual([{ baseUrl: 'http://a.com' }])
      expect(dsRefOptions.value).toEqual([{ name: 'db1' }])
    })

    it('无默认环境时清空选项', async () => {
      mocks.fetchEnvironments.mockResolvedValue([{ id: 'env1', isDefault: false }])
      const { openCreateDrawer, httpRefOptions, dsRefOptions } = useComponentPage()
      openCreateDrawer()
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
    })

    it('环境列表为空时清空选项', async () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { openCreateDrawer, httpRefOptions, dsRefOptions } = useComponentPage()
      openCreateDrawer()
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
    })

    it('API 失败时清空选项并显示错误', async () => {
      mocks.fetchEnvironments.mockRejectedValue(new Error('fail'))
      const { openCreateDrawer, httpRefOptions, dsRefOptions } = useComponentPage()
      openCreateDrawer()
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('exported constants', () => {
    it('导出 COMPONENT_TYPE_OPTIONS', () => {
      const { COMPONENT_TYPE_OPTIONS } = useComponentPage()
      expect(COMPONENT_TYPE_OPTIONS).toBeDefined()
      expect(COMPONENT_TYPE_OPTIONS.length).toBeGreaterThan(0)
    })

    it('导出 COMPONENT_SCOPE_OPTIONS', () => {
      const { COMPONENT_SCOPE_OPTIONS } = useComponentPage()
      expect(COMPONENT_SCOPE_OPTIONS).toBeDefined()
      expect(COMPONENT_SCOPE_OPTIONS.length).toBeGreaterThan(0)
    })

    it('导出 SCOPE_TAG_TYPE', () => {
      const { SCOPE_TAG_TYPE } = useComponentPage()
      expect(SCOPE_TAG_TYPE).toBeDefined()
    })

    it('导出 componentTypeLabel', () => {
      const { componentTypeLabel } = useComponentPage()
      expect(typeof componentTypeLabel).toBe('function')
    })

    it('导出 componentScopeLabel', () => {
      const { componentScopeLabel } = useComponentPage()
      expect(typeof componentScopeLabel).toBe('function')
    })
  })
})
