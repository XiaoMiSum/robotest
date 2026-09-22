import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiBuiltinFunctionGroup, ApiCustomFunctionDetail, ApiCustomFunctionListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchBuiltinCatalog: vi.fn<() => Promise<ApiBuiltinFunctionGroup[]>>(),
  fetchCustomFunctions: vi.fn<() => Promise<ApiCustomFunctionListItem[]>>(),
  fetchCustomFunctionDetail: vi.fn<() => Promise<ApiCustomFunctionDetail>>(),
  createCustomFunction: vi.fn<() => Promise<{ id: string }>>(),
  updateCustomFunction: vi.fn<() => Promise<boolean>>(),
  toggleCustomFunction: vi.fn<() => Promise<boolean>>(),
  deleteCustomFunction: vi.fn<() => Promise<boolean>>(),
  filterFunctions: vi.fn<() => { builtin: ApiBuiltinFunctionGroup[]; custom: ApiCustomFunctionListItem[] }>(),
  resolveFunctionError: vi.fn<() => string>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  useRoute: vi.fn(() => ({ query: {} })),
  useRouter: vi.fn(() => ({ replace: vi.fn() })),
  useAuthStore: vi.fn(() => ({ hasPermission: vi.fn(() => false) })),
}))

vi.mock('vue-router', () => ({
  useRoute: mocks.useRoute,
  useRouter: mocks.useRouter,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/services/apiFunction', () => ({
  fetchBuiltinCatalog: mocks.fetchBuiltinCatalog,
  fetchCustomFunctions: mocks.fetchCustomFunctions,
  fetchCustomFunctionDetail: mocks.fetchCustomFunctionDetail,
  createCustomFunction: mocks.createCustomFunction,
  updateCustomFunction: mocks.updateCustomFunction,
  toggleCustomFunction: mocks.toggleCustomFunction,
  deleteCustomFunction: mocks.deleteCustomFunction,
}))

vi.mock('@/pages/project/functionModel', () => ({
  filterFunctions: mocks.filterFunctions,
  resolveFunctionError: mocks.resolveFunctionError,
  SCOPE_OPTIONS: [
    { value: 'project', label: '项目' },
    { value: 'workspace', label: '空间' },
    { value: 'global', label: '公共' },
  ],
  FUNCTION_TAB_OPTIONS: [
    { value: 'all', label: '全部' },
    { value: 'builtin', label: '内置函数' },
    { value: 'custom', label: '自定义函数' },
  ],
}))

import { useFunctionalTesting } from './useFunctionalTesting'

function builtinGroup(name: string, fns: { name: string; description: string }[]): ApiBuiltinFunctionGroup {
  return {
    name,
    functions: fns.map((f) => ({
      name: f.name,
      signature: `\${${f.name}(...)}`,
      description: f.description,
      params: [],
      example: `\${${f.name}()}`,
      builtin: true,
    })),
  }
}

function makeItem(id: string, name: string, overrides?: Partial<ApiCustomFunctionListItem>): ApiCustomFunctionListItem {
  return {
    id,
    type: 'custom',
    scope: 'project',
    name,
    description: '',
    paramsDesc: null,
    enabled: true,
    updatedAt: '',
    ...overrides,
  }
}

function makeDetail(id: string, name: string, overrides?: Partial<ApiCustomFunctionDetail>): ApiCustomFunctionDetail {
  return {
    ...makeItem(id, name),
    script: 'return 1',
    ...overrides,
  }
}

describe('useFunctionalTesting', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.filterFunctions.mockImplementation(((builtin: ApiBuiltinFunctionGroup[], custom: ApiCustomFunctionListItem[]) => ({ builtin, custom })) as never)
    mocks.fetchBuiltinCatalog.mockResolvedValue([])
    mocks.fetchCustomFunctions.mockResolvedValue([])
    mocks.resolveFunctionError.mockReturnValue('操作失败')
    mocks.useRoute.mockReturnValue({ query: {} } as never)
    mocks.useRouter.mockReturnValue({ replace: vi.fn() } as never)
    mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => false) } as never)
  })

  describe('初始状态', () => {
    it('activeMenu 默认为 cases', () => {
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })

    it('activeMenu 根据 route.query.tab 初始化', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'reviews' } } as never)
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('reviews')
    })

    it('route.query.tab 不在 menuItems 中时回退到 cases', () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'invalid' } } as never)
      const { activeMenu } = useFunctionalTesting()
      expect(activeMenu.value).toBe('cases')
    })

    it('listLoading 初始为 false', () => {
      const { listLoading } = useFunctionalTesting()
      expect(listLoading.value).toBe(false)
    })

    it('loadError 初始为 false', () => {
      const { loadError } = useFunctionalTesting()
      expect(loadError.value).toBe(false)
    })

    it('keyword 初始为空字符串', () => {
      const { keyword } = useFunctionalTesting()
      expect(keyword.value).toBe('')
    })

    it('activeTab 初始为 all', () => {
      const { activeTab } = useFunctionalTesting()
      expect(activeTab.value).toBe('all')
    })

    it('panelMode 初始为 view', () => {
      const { panelMode } = useFunctionalTesting()
      expect(panelMode.value).toBe('view')
    })

    it('selectedType 初始为 null', () => {
      const { selectedType } = useFunctionalTesting()
      expect(selectedType.value).toBeNull()
    })

    it('selectedName 初始为空字符串', () => {
      const { selectedName } = useFunctionalTesting()
      expect(selectedName.value).toBe('')
    })

    it('selectedCustomId 初始为空字符串', () => {
      const { selectedCustomId } = useFunctionalTesting()
      expect(selectedCustomId.value).toBe('')
    })

    it('customDetail 初始为 null', () => {
      const { customDetail } = useFunctionalTesting()
      expect(customDetail.value).toBeNull()
    })

    it('detailLoading 初始为 false', () => {
      const { detailLoading } = useFunctionalTesting()
      expect(detailLoading.value).toBe(false)
    })

    it('saving 初始为 false', () => {
      const { saving } = useFunctionalTesting()
      expect(saving.value).toBe(false)
    })

    it('form 初始值正确', () => {
      const { form } = useFunctionalTesting()
      expect(form.id).toBe('')
      expect(form.name).toBe('')
      expect(form.description).toBe('')
      expect(form.paramsDesc).toBe('')
      expect(form.script).toBe('')
      expect(form.scope).toBe('project')
    })

    it('menuItems 包含预期项', () => {
      const { menuItems } = useFunctionalTesting()
      expect(menuItems).toHaveLength(4)
      expect(menuItems.map((m) => m.key)).toEqual(['cases', 'reviews', 'plans', 'requirements'])
    })
  })

  describe('canEdit', () => {
    it('无权限时返回 false', () => {
      const { canEdit } = useFunctionalTesting()
      expect(canEdit.value).toBe(false)
    })

    it('有 api-func:edit 权限时返回 true', () => {
      const hasPermission = vi.fn((code: string) => code === 'api-func:edit')
      mocks.useAuthStore.mockReturnValue({ hasPermission } as never)
      const { canEdit } = useFunctionalTesting()
      expect(canEdit.value).toBe(true)
    })

    it('有 api-func:edit-space 权限时返回 true', () => {
      const hasPermission = vi.fn((code: string) => code === 'api-func:edit-space')
      mocks.useAuthStore.mockReturnValue({ hasPermission } as never)
      const { canEdit } = useFunctionalTesting()
      expect(canEdit.value).toBe(true)
    })

    it('有 api-func:edit-global 权限时返回 true', () => {
      const hasPermission = vi.fn((code: string) => code === 'api-func:edit-global')
      mocks.useAuthStore.mockReturnValue({ hasPermission } as never)
      const { canEdit } = useFunctionalTesting()
      expect(canEdit.value).toBe(true)
    })
  })

  describe('handleMenuSelect', () => {
    it('选择当前菜单时不做任何操作', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('cases')
      expect(activeMenu.value).toBe('cases')
      expect(replace).not.toHaveBeenCalled()
    })

    it('切换菜单并更新路由', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('reviews')
      expect(activeMenu.value).toBe('reviews')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'reviews' } })
    })

    it('从 cases 切换时如果 testCaseRef 确认离开失败则取消切换', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => false) } as never
      await handleMenuSelect('reviews')
      expect(activeMenu.value).toBe('cases')
      expect(replace).not.toHaveBeenCalled()
    })

    it('从 cases 切换时确认离开成功则继续切换', async () => {
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, testCaseRef, handleMenuSelect } = useFunctionalTesting()
      testCaseRef.value = { confirmLeave: vi.fn(async () => true) } as never
      await handleMenuSelect('reviews')
      expect(activeMenu.value).toBe('reviews')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'reviews' } })
    })

    it('非 cases 页面切换时不需要确认', async () => {
      mocks.useRoute.mockReturnValue({ query: { tab: 'reviews' } } as never)
      const replace = vi.fn()
      mocks.useRouter.mockReturnValue({ replace } as never)
      const { activeMenu, handleMenuSelect } = useFunctionalTesting()
      await handleMenuSelect('plans')
      expect(activeMenu.value).toBe('plans')
      expect(replace).toHaveBeenCalledWith({ query: { tab: 'plans' } })
    })
  })

  describe('loadBuiltin', () => {
    it('加载成功时更新 builtinGroups', async () => {
      const groups = [builtinGroup('字符串', [{ name: 'concat', description: '拼接' }])]
      mocks.fetchBuiltinCatalog.mockResolvedValue(groups)
      const { loadBuiltin, builtinGroups } = useFunctionalTesting()
      await loadBuiltin()
      expect(builtinGroups.value).toEqual(groups)
    })

    it('加载失败时设置 loadError 并显示错误消息', async () => {
      mocks.fetchBuiltinCatalog.mockRejectedValue(new Error('网络异常'))
      mocks.resolveFunctionError.mockReturnValue('网络异常')
      const { loadBuiltin, loadError } = useFunctionalTesting()
      await loadBuiltin()
      expect(loadError.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
    })
  })

  describe('loadCustomList', () => {
    it('加载成功时更新 customList', async () => {
      const items = [makeItem('1', 'myFn')]
      mocks.fetchCustomFunctions.mockResolvedValue(items)
      const { loadCustomList, customList } = useFunctionalTesting()
      await loadCustomList()
      expect(customList.value).toEqual(items)
    })

    it('keyword 为空时不传 keyword 参数', async () => {
      const { loadCustomList } = useFunctionalTesting()
      await loadCustomList()
      expect(mocks.fetchCustomFunctions).toHaveBeenCalledWith(undefined)
    })

    it('keyword 有值时传递 trim 后的 keyword', async () => {
      const { keyword, loadCustomList } = useFunctionalTesting()
      keyword.value = '  test  '
      await loadCustomList()
      expect(mocks.fetchCustomFunctions).toHaveBeenCalledWith({ keyword: 'test' })
    })

    it('加载失败时设置 loadError 并显示错误消息', async () => {
      mocks.fetchCustomFunctions.mockRejectedValue(new Error('请求失败'))
      mocks.resolveFunctionError.mockReturnValue('请求失败')
      const { loadCustomList, loadError } = useFunctionalTesting()
      await loadCustomList()
      expect(loadError.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('请求失败')
    })
  })

  describe('loadAll', () => {
    it('调用时设置 listLoading 为 true', async () => {
      let resolveFn: (value: ApiBuiltinFunctionGroup[]) => void
      mocks.fetchBuiltinCatalog.mockReturnValue(new Promise((r) => { resolveFn = r }))
      const { loadAll, listLoading } = useFunctionalTesting()
      const promise = loadAll()
      expect(listLoading.value).toBe(true)
      resolveFn!([])
      await promise
      expect(listLoading.value).toBe(false)
    })

    it('加载完成后 listLoading 重置为 false', async () => {
      const { loadAll, listLoading } = useFunctionalTesting()
      await loadAll()
      expect(listLoading.value).toBe(false)
    })

    it('加载失败后 listLoading 也被重置', async () => {
      mocks.fetchBuiltinCatalog.mockRejectedValue(new Error('fail'))
      const { loadAll, listLoading } = useFunctionalTesting()
      await loadAll()
      expect(listLoading.value).toBe(false)
    })

    it('加载失败时设置 loadError', async () => {
      mocks.fetchBuiltinCatalog.mockRejectedValue(new Error('fail'))
      mocks.resolveFunctionError.mockReturnValue('fail')
      const { loadAll, loadError } = useFunctionalTesting()
      await loadAll()
      expect(loadError.value).toBe(true)
    })
  })

  describe('filtered', () => {
    it('调用 filterFunctions 传入当前状态', async () => {
      const groups = [builtinGroup('G', [{ name: 'f', description: 'd' }])]
      const items = [makeItem('1', 'c')]
      mocks.fetchBuiltinCatalog.mockResolvedValue(groups)
      mocks.fetchCustomFunctions.mockResolvedValue(items)
      const { loadAll, filtered } = useFunctionalTesting()
      await loadAll()
      expect(filtered.value).toEqual({ builtin: groups, custom: items })
    })
  })

  describe('displayItems', () => {
    it('activeTab 为 all 时展示所有项', async () => {
      const groups = [builtinGroup('G', [{ name: 'builtin1', description: 'desc1' }])]
      const items = [makeItem('1', 'custom1', { description: 'desc2' })]
      mocks.fetchBuiltinCatalog.mockResolvedValue(groups)
      mocks.fetchCustomFunctions.mockResolvedValue(items)
      const { loadAll, displayItems } = useFunctionalTesting()
      await loadAll()
      expect(displayItems.value).toHaveLength(2)
      expect(displayItems.value[0]).toEqual({ type: 'builtin', name: 'builtin1', description: 'desc1' })
      expect(displayItems.value[1]).toEqual({
        type: 'custom', name: 'custom1', description: 'desc2', scope: 'project', id: '1', enabled: true,
      })
    })

    it('activeTab 为 builtin 时只展示内置函数', async () => {
      const groups = [builtinGroup('G', [{ name: 'f1', description: 'd1' }])]
      mocks.fetchBuiltinCatalog.mockResolvedValue(groups)
      mocks.fetchCustomFunctions.mockResolvedValue([makeItem('1', 'c1')])
      const { loadAll, activeTab, displayItems } = useFunctionalTesting()
      await loadAll()
      activeTab.value = 'builtin'
      await nextTick()
      expect(displayItems.value).toHaveLength(1)
      expect(displayItems.value[0].type).toBe('builtin')
    })

    it('activeTab 为 custom 时只展示自定义函数', async () => {
      const items = [makeItem('1', 'c1', { description: 'desc' })]
      mocks.fetchCustomFunctions.mockResolvedValue(items)
      const { loadAll, activeTab, displayItems } = useFunctionalTesting()
      await loadAll()
      activeTab.value = 'custom'
      await nextTick()
      expect(displayItems.value).toHaveLength(1)
      expect(displayItems.value[0].type).toBe('custom')
    })

    it('自定义函数 description 为 null 时使用空字符串', async () => {
      mocks.fetchCustomFunctions.mockResolvedValue([makeItem('1', 'c1', { description: null })])
      const { loadAll, activeTab, displayItems } = useFunctionalTesting()
      await loadAll()
      activeTab.value = 'custom'
      await nextTick()
      expect(displayItems.value[0].description).toBe('')
    })
  })

  describe('handleSearchInput', () => {
    it('300ms 防抖后调用 loadCustomList', async () => {
      vi.useFakeTimers()
      const { keyword, handleSearchInput } = useFunctionalTesting()
      keyword.value = 'test'
      handleSearchInput()
      expect(mocks.fetchCustomFunctions).not.toHaveBeenCalled()
      vi.advanceTimersByTime(300)
      await nextTick()
      expect(mocks.fetchCustomFunctions).toHaveBeenCalled()
      vi.useRealTimers()
    })

    it('连续调用只触发一次请求', async () => {
      vi.useFakeTimers()
      const { handleSearchInput } = useFunctionalTesting()
      handleSearchInput()
      handleSearchInput()
      handleSearchInput()
      vi.advanceTimersByTime(300)
      await nextTick()
      expect(mocks.fetchCustomFunctions).toHaveBeenCalledTimes(1)
      vi.useRealTimers()
    })
  })

  describe('selectItem', () => {
    it('选中 builtin 项', () => {
      const { selectItem, selectedType, selectedName, selectedCustomId, panelMode } = useFunctionalTesting()
      selectItem('builtin', 'concat')
      expect(selectedType.value).toBe('builtin')
      expect(selectedName.value).toBe('concat')
      expect(selectedCustomId.value).toBe('')
      expect(panelMode.value).toBe('view')
    })

    it('选中 custom 项', () => {
      const { selectItem, selectedType, selectedName, selectedCustomId } = useFunctionalTesting()
      selectItem('custom', 'myFn', 'id-1')
      expect(selectedType.value).toBe('custom')
      expect(selectedName.value).toBe('myFn')
      expect(selectedCustomId.value).toBe('id-1')
    })

    it('id 未传时 selectedCustomId 为空字符串', () => {
      const { selectItem, selectedCustomId } = useFunctionalTesting()
      selectItem('custom', 'fn')
      expect(selectedCustomId.value).toBe('')
    })
  })

  describe('selectedBuiltinFn', () => {
    it('selectedType 为 builtin 时在 builtinGroups 中查找', async () => {
      const groups = [builtinGroup('G', [{ name: 'concat', description: '拼接' }])]
      mocks.fetchBuiltinCatalog.mockResolvedValue(groups)
      const { loadAll, selectItem, selectedBuiltinFn } = useFunctionalTesting()
      await loadAll()
      selectItem('builtin', 'concat')
      expect(selectedBuiltinFn.value).toBeDefined()
      expect(selectedBuiltinFn.value!.name).toBe('concat')
    })

    it('找不到时返回 null', () => {
      const { selectItem, selectedBuiltinFn } = useFunctionalTesting()
      selectItem('builtin', 'nonexistent')
      expect(selectedBuiltinFn.value).toBeNull()
    })

    it('selectedType 不是 builtin 时返回 null', () => {
      const { selectedBuiltinFn } = useFunctionalTesting()
      expect(selectedBuiltinFn.value).toBeNull()
    })
  })

  describe('customParams', () => {
    it('无 paramsDesc 时返回空数组', () => {
      const { customParams } = useFunctionalTesting()
      expect(customParams.value).toEqual([])
    })

    it('解析 paramsDesc 为参数列表', async () => {
      const detail = makeDetail('1', 'fn1', { paramsDesc: 'a:描述1,b:描述2' })
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, customParams } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      expect(customParams.value).toHaveLength(2)
      expect(customParams.value[0].name).toBe('a')
      expect(customParams.value[0].required).toBe(true)
      expect(customParams.value[1].name).toBe('b')
    })

    it('单个参数正确解析', async () => {
      const detail = makeDetail('1', 'fn1', { paramsDesc: 'x:描述' })
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, customParams } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      expect(customParams.value).toHaveLength(1)
      expect(customParams.value[0].name).toBe('x')
    })
  })

  describe('customSignature', () => {
    it('无参数时生成无参签名', async () => {
      const detail = makeDetail('1', 'fn1', { paramsDesc: null })
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, customSignature } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      expect(customSignature.value).toBe('${fn1()}')
    })

    it('有参数时生成带参签名', async () => {
      const detail = makeDetail('1', 'fn1', { paramsDesc: 'a:描述,b:描述' })
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, customSignature } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      expect(customSignature.value).toBe('${fn1(a, b)}')
    })
  })

  describe('resetForm', () => {
    it('重置所有表单字段', () => {
      const { form, resetForm } = useFunctionalTesting()
      form.id = 'old-id'
      form.name = 'old-name'
      form.description = 'old-desc'
      form.paramsDesc = 'old-params'
      form.script = 'old-script'
      form.scope = 'workspace'
      resetForm()
      expect(form.id).toBe('')
      expect(form.name).toBe('')
      expect(form.description).toBe('')
      expect(form.paramsDesc).toBe('')
      expect(form.script).toBe('')
      expect(form.scope).toBe('project')
    })
  })

  describe('startCreate', () => {
    it('重置选择并设置 panelMode 为 create', () => {
      const { startCreate, selectedType, selectedName, selectedCustomId, customDetail, panelMode, form } = useFunctionalTesting()
      startCreate()
      expect(selectedType.value).toBe('custom')
      expect(selectedName.value).toBe('')
      expect(selectedCustomId.value).toBe('')
      expect(customDetail.value).toBeNull()
      expect(panelMode.value).toBe('create')
      expect(form.name).toBe('')
    })
  })

  describe('startEdit', () => {
    it('无 customDetail 时不操作', () => {
      const { startEdit, panelMode } = useFunctionalTesting()
      startEdit()
      expect(panelMode.value).toBe('view')
    })

    it('将 customDetail 数据填充到 form 并切换到 edit 模式', async () => {
      const detail = makeDetail('1', 'fn1', {
        description: 'desc',
        paramsDesc: 'p:参数',
        script: 'return 1',
        scope: 'workspace',
      })
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, startEdit, form, panelMode } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      startEdit()
      expect(form.id).toBe('1')
      expect(form.name).toBe('fn1')
      expect(form.description).toBe('desc')
      expect(form.paramsDesc).toBe('p:参数')
      expect(form.script).toBe('return 1')
      expect(form.scope).toBe('workspace')
      expect(panelMode.value).toBe('edit')
    })
  })

  describe('cancelEdit', () => {
    it('切换回 view 模式', () => {
      const { startCreate, cancelEdit, panelMode } = useFunctionalTesting()
      startCreate()
      cancelEdit()
      expect(panelMode.value).toBe('view')
    })

    it('selectedCustomId 为空时清空选择', () => {
      const { startCreate, cancelEdit, selectedType, selectedName } = useFunctionalTesting()
      startCreate()
      cancelEdit()
      expect(selectedType.value).toBeNull()
      expect(selectedName.value).toBe('')
    })

    it('selectedCustomId 不为空时保留选择', async () => {
      const detail = makeDetail('1', 'fn1')
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, cancelEdit, selectedType, selectedName } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      cancelEdit()
      expect(selectedType.value).toBe('custom')
      expect(selectedName.value).toBe('fn1')
    })
  })

  describe('validateForm', () => {
    it('名称为空时返回 false 并显示警告', () => {
      const { validateForm } = useFunctionalTesting()
      expect(validateForm()).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写函数名称')
    })

    it('名称为空白时返回 false', () => {
      const { form, validateForm } = useFunctionalTesting()
      form.name = '   '
      expect(validateForm()).toBe(false)
    })

    it('脚本为空时返回 false 并显示警告', () => {
      const { form, validateForm } = useFunctionalTesting()
      form.name = 'fn1'
      expect(validateForm()).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写 Groovy 脚本')
    })

    it('脚本为空白时返回 false', () => {
      const { form, validateForm } = useFunctionalTesting()
      form.name = 'fn1'
      form.script = '   '
      expect(validateForm()).toBe(false)
    })

    it('名称和脚本都有效时返回 true', () => {
      const { form, validateForm } = useFunctionalTesting()
      form.name = 'fn1'
      form.script = 'return 1'
      expect(validateForm()).toBe(true)
    })
  })

  describe('submitForm', () => {
    it('验证失败时不调用 API', async () => {
      const { submitForm } = useFunctionalTesting()
      await submitForm()
      expect(mocks.createCustomFunction).not.toHaveBeenCalled()
      expect(mocks.updateCustomFunction).not.toHaveBeenCalled()
    })

    it('create 模式下创建成功后刷新列表并切换到 view', async () => {
      mocks.createCustomFunction.mockResolvedValue({ id: 'new-id' })
      const { startCreate, form, submitForm, panelMode, saving } = useFunctionalTesting()
      startCreate()
      form.name = 'newFn'
      form.script = 'return 1'
      await submitForm()
      expect(mocks.createCustomFunction).toHaveBeenCalledWith({
        name: 'newFn',
        description: undefined,
        paramsDesc: undefined,
        script: 'return 1',
        scope: 'project',
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('函数已创建')
      expect(panelMode.value).toBe('view')
      expect(saving.value).toBe(false)
    })

    it('create 模式下创建成功后选中新函数', async () => {
      mocks.createCustomFunction.mockResolvedValue({ id: 'new-id' })
      const { startCreate, form, submitForm, selectedType, selectedName, selectedCustomId } = useFunctionalTesting()
      startCreate()
      form.name = 'newFn'
      form.script = 'return 1'
      await submitForm()
      expect(selectedType.value).toBe('custom')
      expect(selectedName.value).toBe('newFn')
      expect(selectedCustomId.value).toBe('new-id')
    })

    it('edit 模式下更新成功后刷新列表并切换到 view', async () => {
      const detail = makeDetail('1', 'fn1')
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      mocks.updateCustomFunction.mockResolvedValue(true)
      mocks.fetchCustomFunctions.mockResolvedValue([detail])
      const { selectItem, startEdit, form, submitForm, panelMode } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      startEdit()
      form.script = 'return 2'
      await submitForm()
      expect(mocks.updateCustomFunction).toHaveBeenCalledWith('1', {
        name: 'fn1',
        description: undefined,
        paramsDesc: undefined,
        script: 'return 2',
        scope: 'project',
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
      expect(panelMode.value).toBe('view')
    })

    it('edit 模式下选中项的详情被刷新', async () => {
      const detail = makeDetail('1', 'fn1')
      const updatedDetail = makeDetail('1', 'fn1', { script: 'return 2' })
      mocks.fetchCustomFunctionDetail.mockResolvedValueOnce(detail)
      mocks.updateCustomFunction.mockResolvedValue(true)
      mocks.fetchCustomFunctions.mockResolvedValue([detail])
      mocks.fetchCustomFunctionDetail.mockResolvedValueOnce(updatedDetail)
      const { selectItem, startEdit, form, submitForm, customDetail } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      startEdit()
      form.script = 'return 2'
      await submitForm()
      expect(customDetail.value!.script).toBe('return 2')
    })

    it('创建失败时显示错误消息', async () => {
      mocks.createCustomFunction.mockRejectedValue(new Error('dup'))
      mocks.resolveFunctionError.mockReturnValue('dup')
      const { startCreate, form, submitForm, saving } = useFunctionalTesting()
      startCreate()
      form.name = 'newFn'
      form.script = 'return 1'
      await submitForm()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('dup')
      expect(saving.value).toBe(false)
    })

    it('description 为空时不传该字段', async () => {
      mocks.createCustomFunction.mockResolvedValue({ id: 'new-id' })
      const { startCreate, form, submitForm } = useFunctionalTesting()
      startCreate()
      form.name = 'newFn'
      form.script = 'return 1'
      form.description = ''
      await submitForm()
      expect(mocks.createCustomFunction).toHaveBeenCalledWith(
        expect.objectContaining({ description: undefined }),
      )
    })

    it('paramsDesc 为空时不传该字段', async () => {
      mocks.createCustomFunction.mockResolvedValue({ id: 'new-id' })
      const { startCreate, form, submitForm } = useFunctionalTesting()
      startCreate()
      form.name = 'newFn'
      form.script = 'return 1'
      form.paramsDesc = ''
      await submitForm()
      expect(mocks.createCustomFunction).toHaveBeenCalledWith(
        expect.objectContaining({ paramsDesc: undefined }),
      )
    })

    it('name 和 script 自动 trim', async () => {
      mocks.createCustomFunction.mockResolvedValue({ id: 'new-id' })
      const { startCreate, form, submitForm } = useFunctionalTesting()
      startCreate()
      form.name = '  fn1  '
      form.script = '  return 1  '
      await submitForm()
      expect(mocks.createCustomFunction).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'fn1', script: 'return 1' }),
      )
    })
  })

  describe('handleToggle', () => {
    it('item.id 不存在时不调用 API', async () => {
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: undefined }
      const { handleToggle } = useFunctionalTesting()
      await handleToggle(item)
      expect(mocks.toggleCustomFunction).not.toHaveBeenCalled()
    })

    it('启用时调用 toggleCustomFunction 并显示成功消息', async () => {
      mocks.toggleCustomFunction.mockResolvedValue(true)
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: '1', enabled: false }
      const { handleToggle } = useFunctionalTesting()
      await handleToggle(item)
      expect(mocks.toggleCustomFunction).toHaveBeenCalledWith('1', true)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已启用')
    })

    it('禁用时显示禁用消息', async () => {
      mocks.toggleCustomFunction.mockResolvedValue(true)
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: '1', enabled: true }
      const { handleToggle } = useFunctionalTesting()
      await handleToggle(item)
      expect(mocks.toggleCustomFunction).toHaveBeenCalledWith('1', false)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已禁用')
    })

    it('toggle 成功后刷新 customList', async () => {
      mocks.toggleCustomFunction.mockResolvedValue(true)
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: '1', enabled: false }
      const { handleToggle } = useFunctionalTesting()
      await handleToggle(item)
      expect(mocks.fetchCustomFunctions).toHaveBeenCalled()
    })

    it('toggle 成功后更新选中项的 customDetail.enabled', async () => {
      const detail = makeDetail('1', 'fn1', { enabled: false })
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      mocks.toggleCustomFunction.mockResolvedValue(true)
      const { selectItem, handleToggle, customDetail } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: '1', enabled: false }
      await handleToggle(item)
      expect(customDetail.value!.enabled).toBe(true)
    })

    it('toggle 失败时显示错误消息', async () => {
      mocks.toggleCustomFunction.mockRejectedValue(new Error('fail'))
      mocks.resolveFunctionError.mockReturnValue('fail')
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: '1', enabled: false }
      const { handleToggle } = useFunctionalTesting()
      await handleToggle(item)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
    })

    it('handleToggleItem 调用 handleToggle', async () => {
      mocks.toggleCustomFunction.mockResolvedValue(true)
      const item = { type: 'custom' as const, name: 'fn1', description: '', id: '1', enabled: false }
      const { handleToggleItem } = useFunctionalTesting()
      handleToggleItem(item)
      await nextTick()
      expect(mocks.toggleCustomFunction).toHaveBeenCalled()
    })
  })

  describe('handleDelete', () => {
    it('用户取消时不调用删除 API', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const item = makeItem('1', 'fn1')
      const { handleDelete } = useFunctionalTesting()
      await handleDelete(item)
      expect(mocks.deleteCustomFunction).not.toHaveBeenCalled()
    })

    it('确认删除后调用 deleteCustomFunction', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteCustomFunction.mockResolvedValue(true)
      const item = makeItem('1', 'fn1')
      const { handleDelete } = useFunctionalTesting()
      await handleDelete(item)
      expect(mocks.deleteCustomFunction).toHaveBeenCalledWith('1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
    })

    it('删除选中项后清空选择', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteCustomFunction.mockResolvedValue(true)
      const detail = makeDetail('1', 'fn1')
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, handleDelete, selectedType, selectedName, selectedCustomId, customDetail } = useFunctionalTesting()
      selectItem('custom', 'fn1', '1')
      await nextTick()
      const item = makeItem('1', 'fn1')
      await handleDelete(item)
      expect(selectedType.value).toBeNull()
      expect(selectedName.value).toBe('')
      expect(selectedCustomId.value).toBe('')
      expect(customDetail.value).toBeNull()
    })

    it('删除非选中项时不清空选择', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteCustomFunction.mockResolvedValue(true)
      const detail = makeDetail('2', 'fn2')
      mocks.fetchCustomFunctionDetail.mockResolvedValue(detail)
      const { selectItem, handleDelete, selectedType, selectedName } = useFunctionalTesting()
      selectItem('custom', 'fn2', '2')
      await nextTick()
      const item = makeItem('1', 'fn1')
      await handleDelete(item)
      expect(selectedType.value).toBe('custom')
      expect(selectedName.value).toBe('fn2')
    })

    it('删除成功后刷新 customList', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteCustomFunction.mockResolvedValue(true)
      const item = makeItem('1', 'fn1')
      const { handleDelete } = useFunctionalTesting()
      await handleDelete(item)
      expect(mocks.fetchCustomFunctions).toHaveBeenCalled()
    })

    it('删除失败时显示错误消息', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      mocks.deleteCustomFunction.mockRejectedValue(new Error('fail'))
      mocks.resolveFunctionError.mockReturnValue('fail')
      const item = makeItem('1', 'fn1')
      const { handleDelete } = useFunctionalTesting()
      await handleDelete(item)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('fail')
    })

    it('handleDeleteItem 调用 handleDelete', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const item = makeItem('1', 'fn1')
      const { handleDeleteItem } = useFunctionalTesting()
      handleDeleteItem(item as never)
      await nextTick()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
    })

    it('弹窗标题和选项正确', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const item = makeItem('1', 'fn1')
      const { handleDelete } = useFunctionalTesting()
      await handleDelete(item)
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '删除后函数不可恢复，确认删除「fn1」？',
        '删除函数',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
      )
    })
  })

  describe('SCOPE_OPTIONS 和 FUNCTION_TAB_OPTIONS', () => {
    it('导出 SCOPE_OPTIONS', () => {
      const { SCOPE_OPTIONS } = useFunctionalTesting()
      expect(SCOPE_OPTIONS).toHaveLength(3)
      expect(SCOPE_OPTIONS[0].value).toBe('project')
    })

    it('导出 FUNCTION_TAB_OPTIONS', () => {
      const { FUNCTION_TAB_OPTIONS } = useFunctionalTesting()
      expect(FUNCTION_TAB_OPTIONS).toHaveLength(3)
      expect(FUNCTION_TAB_OPTIONS[0].value).toBe('all')
    })
  })
})
