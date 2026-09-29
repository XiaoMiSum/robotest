import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  useAuthStore: vi.fn(),
  fetchComponents: vi.fn(),
  fetchEnvironments: vi.fn(),
  fetchEnvironmentDetail: vi.fn(),
  copyComponent: vi.fn(),
  createComponent: vi.fn(),
  deleteComponent: vi.fn(),
  toggleComponent: vi.fn(),
  updateComponent: vi.fn(),
  resolveComponentError: vi.fn(),
  createProcessorComponentConfig: vi.fn(),
  defaultComponentConfig: vi.fn(),
  extractorsFromComponents: vi.fn(),
  parseComponentConfig: vi.fn(),
  componentScopeLabel: vi.fn(),
  componentTypeLabel: vi.fn(),
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

vi.mock('@/services/project/api-testing/component', () => ({
  copyComponent: mocks.copyComponent,
  createComponent: mocks.createComponent,
  deleteComponent: mocks.deleteComponent,
  fetchComponents: mocks.fetchComponents,
  toggleComponent: mocks.toggleComponent,
  updateComponent: mocks.updateComponent,
}))

vi.mock('@/services/project/api-testing/environment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
  fetchEnvironmentDetail: mocks.fetchEnvironmentDetail,
}))

vi.mock('@/composables/project/api-testing/component/componentModel', () => ({
  COMPONENT_SCOPE_OPTIONS: [
    { value: 'global', label: '公共' },
    { value: 'workspace', label: '空间' },
    { value: 'project', label: '项目' },
  ],
  COMPONENT_TAB_OPTIONS: [
    { value: 'all', label: '全部' },
    { value: 'preprocessor', label: '前置' },
    { value: 'postprocessor', label: '后置' },
    { value: 'validator', label: '验证器' },
    { value: 'extractor', label: '提取器' },
  ],
  COMPONENT_TYPE_OPTIONS: [
    { value: 'preprocessor', label: '前置处理器' },
    { value: 'postprocessor', label: '后置处理器' },
    { value: 'validator', label: '验证器' },
    { value: 'extractor', label: '提取器' },
  ],
  SCOPE_TAG_TYPE: { global: undefined, workspace: 'success', project: 'info' },
  componentScopeLabel: mocks.componentScopeLabel,
  componentTypeLabel: mocks.componentTypeLabel,
  resolveComponentError: mocks.resolveComponentError,
}))

vi.mock('@/composables/project/api-testing/processorFormModel', () => ({
  createProcessorComponentConfig: mocks.createProcessorComponentConfig,
  defaultComponentConfig: mocks.defaultComponentConfig,
  extractorsFromComponents: mocks.extractorsFromComponents,
  parseComponentConfig: mocks.parseComponentConfig,
}))

import type { ApiComponentListItem } from '@/types'
import { useComponentPage } from './useComponentPage'

function mockHasPermission(permissions: string[]) {
  mocks.useAuthStore.mockReturnValue({
    hasPermission: (code: string) => permissions.includes(code),
  })
}

function item(id: string, over: Record<string, unknown> = {}): ApiComponentListItem {
  return {
    id,
    name: `组件-${id}`,
    type: 'preprocessor',
    scope: 'project',
    enabled: true,
    sortOrder: 0,
    description: '',
    config: '{"testclass":"http"}',
    updatedAt: '2026-09-01T00:00:00Z',
    ...over,
  } as unknown as ApiComponentListItem
}

const okList = (list: ApiComponentListItem[]) => ({ list, total: list.length })

/** 构造整页数据：条目 id 从 start 起连续，便于校验追加与窗口丢页 */
function pageOf(start: number, count: number, total: number) {
  return {
    list: Array.from({ length: count }, (_, index) => item(`c${start + index}`)),
    total,
  }
}

/** 按 pageNo 返回连续整页（每页 20 条） */
function pagedMock(total: number): void {
  mocks.fetchComponents.mockImplementation((params?: { pageNo?: number }) => {
    const pageNo = params?.pageNo ?? 1
    return Promise.resolve(pageOf((pageNo - 1) * 20 + 1, 20, total))
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mockHasPermission([])
  mocks.resolveComponentError.mockReturnValue('操作失败')
  mocks.createProcessorComponentConfig.mockReturnValue({ enabled: true, testclass: 'http', config: {}, extractors: [] })
  mocks.defaultComponentConfig.mockReturnValue({ enabled: true })
  mocks.extractorsFromComponents.mockReturnValue([])
  mocks.parseComponentConfig.mockReturnValue({ testclass: 'http', config: {} })
  mocks.fetchComponents.mockResolvedValue(okList([]))
  mocks.fetchEnvironments.mockResolvedValue([])
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useComponentPage', () => {
  describe('canEdit', () => {
    it('无权限 → false', () => {
      mockHasPermission([])
      expect(useComponentPage().canEdit.value).toBe(false)
    })

    it('具备任一组件编辑权限 → true', () => {
      expect(useComponentPage().canEdit.value).toBe(false)
      mockHasPermission(['api-component:edit'])
      expect(useComponentPage().canEdit.value).toBe(true)
      mockHasPermission(['api-component:edit-space'])
      expect(useComponentPage().canEdit.value).toBe(true)
      mockHasPermission(['api-component:edit-global'])
      expect(useComponentPage().canEdit.value).toBe(true)
    })
  })

  describe('初始状态', () => {
    it('列表与右栏默认为空查看态', () => {
      const s = useComponentPage()
      expect(s.listLoading.value).toBe(false)
      expect(s.loadError.value).toBe(false)
      expect(s.list.value).toEqual([])
      expect(s.selectedId.value).toBeNull()
      expect(s.selectedItem.value).toBeNull()
      expect(s.panelMode.value).toBe('view')
      expect(s.keyword.value).toBe('')
      expect(s.filterType.value).toBe('all')
      expect(s.hasFilter.value).toBe(false)
    })

    it('表单默认为前置处理器新建态', () => {
      const s = useComponentPage()
      expect(s.editingId.value).toBeNull()
      expect(s.saving.value).toBe(false)
      expect(s.form.type).toBe('preprocessor')
      expect(s.form.name).toBe('')
      expect(s.form.scope).toBe('project')
      expect(s.form.config).toEqual({})
      expect(s.form.sortOrder).toBe(0)
    })

    it('关键词或类型页签命中时 hasFilter 为 true', () => {
      const s = useComponentPage()
      s.keyword.value = 'token'
      expect(s.hasFilter.value).toBe(true)
      s.keyword.value = ''
      s.filterType.value = 'validator'
      expect(s.hasFilter.value).toBe(true)
    })
  })

  describe('loadList', () => {
    it('按 pageSize=20 拉取第 1 页并默认选中首项', async () => {
      mocks.fetchComponents.mockResolvedValue(okList([item('a'), item('b')]))
      const s = useComponentPage()

      await s.loadList()

      expect(mocks.fetchComponents).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20, type: undefined, keyword: undefined })
      expect(s.list.value).toHaveLength(2)
      expect(s.selectedId.value).toBe('a')
      expect(s.listLoading.value).toBe(false)
    })

    it('类型页签非全部时透传 type，关键词去空格后透传', async () => {
      const s = useComponentPage()
      s.filterType.value = 'validator'
      s.keyword.value = '  code  '

      await s.loadList()

      expect(mocks.fetchComponents).toHaveBeenCalledWith({
        pageNo: 1,
        pageSize: 20,
        type: 'validator',
        keyword: 'code',
      })
    })

    it('失败时置 loadError 并统一提示', async () => {
      mocks.fetchComponents.mockRejectedValue(new Error('boom'))
      const s = useComponentPage()

      await s.loadList()

      expect(s.loadError.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
      expect(s.listLoading.value).toBe(false)
    })

    it('已选项仍在列表时保持选中，否则由缓存维持右栏详情', async () => {
      mocks.fetchComponents.mockResolvedValue(okList([item('a'), item('b')]))
      const s = useComponentPage()
      await s.loadList()
      expect(s.selectedId.value).toBe('a')

      s.selectComponent('b')
      await nextTick()
      mocks.fetchComponents.mockResolvedValue(okList([item('b'), item('c')]))
      await s.loadList()
      expect(s.selectedId.value).toBe('b')

      // 选中项滑出窗口：保留 id，右栏继续展示缓存详情，不回落到首项
      mocks.fetchComponents.mockResolvedValue(okList([item('c')]))
      await s.loadList()
      expect(s.selectedId.value).toBe('b')
      expect(s.selectedItem.value?.id).toBe('b')
      expect(s.selectedItem.value?.name).toBe('组件-b')
    })
  })

  describe('搜索与页签', () => {
    it('关键词输入按 300ms 防抖后刷新', async () => {
      vi.useFakeTimers()
      const s = useComponentPage()
      s.keyword.value = 'a'
      s.handleSearchInput()
      s.keyword.value = 'ab'
      s.handleSearchInput()

      expect(mocks.fetchComponents).not.toHaveBeenCalled()

      vi.advanceTimersByTime(300)
      await vi.runAllTimersAsync()
      await Promise.resolve()

      expect(mocks.fetchComponents).toHaveBeenCalledTimes(1)
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'ab' }))
    })

    it('切换类型页签立即刷新并带上类型', async () => {
      const s = useComponentPage()
      s.handleTabChange('extractor')

      expect(s.filterType.value).toBe('extractor')
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ type: 'extractor' }))
    })

    it('清除筛选同时清空关键词与页签并刷新', async () => {
      const s = useComponentPage()
      s.keyword.value = 'x'
      s.filterType.value = 'validator'

      s.clearFilters()

      expect(s.keyword.value).toBe('')
      expect(s.filterType.value).toBe('all')
      expect(s.hasFilter.value).toBe(false)
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ keyword: undefined, type: undefined }))
    })
  })

  describe('新建 / 编辑', () => {
    it('startCreate 重置表单、进入 create 并预取引用配置', () => {
      const s = useComponentPage()
      s.form.name = '旧名称'

      s.startCreate()

      expect(s.panelMode.value).toBe('create')
      expect(s.editingId.value).toBeNull()
      expect(s.form.name).toBe('')
      expect(s.form.config).toEqual({ enabled: true, testclass: 'http', config: {}, extractors: [] })
      expect(mocks.fetchEnvironments).toHaveBeenCalled()
    })

    it('startEdit 以选中项回填并进入 edit', () => {
      const s = useComponentPage()
      s.selectComponent('e1')
      s.startEdit(item('e1', { name: 'Token 预置', scope: 'workspace', description: '登录前置', sortOrder: 3 }))

      expect(s.panelMode.value).toBe('edit')
      expect(s.editingId.value).toBe('e1')
      expect(s.form.name).toBe('Token 预置')
      expect(s.form.scope).toBe('workspace')
      expect(s.form.description).toBe('登录前置')
      expect(s.form.sortOrder).toBe(3)
      expect(mocks.parseComponentConfig).toHaveBeenCalledWith('{"testclass":"http"}')
      expect(s.form.config).toEqual({ testclass: 'http', config: {} })
    })

    it('startEdit 无目标时不切换面板', () => {
      const s = useComponentPage()
      s.startEdit(null)
      expect(s.panelMode.value).toBe('view')
      expect(s.editingId.value).toBeNull()
    })

    it('cancelEdit 回到查看态', () => {
      const s = useComponentPage()
      s.startCreate()
      s.cancelEdit()
      expect(s.panelMode.value).toBe('view')
    })
  })

  describe('handleSave', () => {
    it('名称为空时提示且不提交', async () => {
      const s = useComponentPage()
      s.startCreate()

      await s.handleSave()

      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写组件名称')
      expect(mocks.createComponent).not.toHaveBeenCalled()
      expect(s.saving.value).toBe(false)
    })

    it('新建：带作用域提交并在成功后选中新建项', async () => {
      mocks.createComponent.mockResolvedValue({ id: 'new1' })
      mocks.fetchComponents.mockResolvedValue(okList([item('new1')]))
      const s = useComponentPage()
      s.startCreate()
      s.form.name = '  Token 预置  '
      s.form.description = '描述'
      s.form.scope = 'global'

      await s.handleSave()

      expect(mocks.createComponent).toHaveBeenCalledWith({
        type: 'preprocessor',
        name: 'Token 预置',
        description: '描述',
        sortOrder: 0,
        config: { enabled: true, testclass: 'http', config: {}, extractors: [] },
        scope: 'global',
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已创建')
      expect(s.selectedId.value).toBe('new1')
      expect(s.panelMode.value).toBe('view')
      expect(s.saving.value).toBe(false)
    })

    it('编辑：不带作用域提交并回查看态', async () => {
      mocks.updateComponent.mockResolvedValue({})
      const s = useComponentPage()
      s.selectComponent('e1')
      s.startEdit(item('e1', { name: '旧名' }))
      s.form.name = '新名'

      await s.handleSave()

      expect(mocks.updateComponent).toHaveBeenCalledWith('e1', expect.objectContaining({ name: '新名' }))
      expect(mocks.updateComponent.mock.calls[0][1]).not.toHaveProperty('scope')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已更新')
      expect(s.selectedId.value).toBe('e1')
      expect(s.panelMode.value).toBe('view')
    })

    it('提交失败时保留面板与错误信息', async () => {
      mocks.updateComponent.mockRejectedValue(new Error('dup'))
      const s = useComponentPage()
      s.startEdit(item('e1'))
      s.form.name = 'x'

      await s.handleSave()

      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
      expect(s.panelMode.value).toBe('edit')
      expect(s.saving.value).toBe(false)
    })
  })

  describe('启停 / 删除 / 复制', () => {
    it('handleEnableToggle 成功后刷新列表', async () => {
      mocks.fetchComponents.mockResolvedValue(okList([item('a')]))
      const s = useComponentPage()
      await s.loadList()

      await s.handleEnableToggle(false)

      expect(mocks.toggleComponent).toHaveBeenCalledWith('a', false)
      expect(mocks.fetchComponents).toHaveBeenCalledTimes(2)
    })

    it('handleEnableToggle 失败只提示不刷新', async () => {
      mocks.fetchComponents.mockResolvedValue(okList([item('a')]))
      mocks.toggleComponent.mockRejectedValue(new Error('x'))
      const s = useComponentPage()
      await s.loadList()

      await s.handleEnableToggle(false)

      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
      expect(mocks.fetchComponents).toHaveBeenCalledTimes(1)
    })

    it('handleEnableToggle 无选中项时不请求', async () => {
      const s = useComponentPage()
      await s.handleEnableToggle(true)
      expect(mocks.toggleComponent).not.toHaveBeenCalled()
    })

    it('删除未确认时不执行', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      mocks.fetchComponents.mockResolvedValue(okList([item('a'), item('b')]))
      const s = useComponentPage()
      await s.loadList()

      await s.handleDelete(s.list.value[0])

      expect(mocks.deleteComponent).not.toHaveBeenCalled()
      expect(s.selectedId.value).toBe('a')
    })

    it('删除后清空选中并按原位置回选下一项', async () => {
      mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
      mocks.fetchComponents.mockResolvedValueOnce(okList([item('a'), item('b'), item('c')]))
      const s = useComponentPage()
      await s.loadList()

      mocks.fetchComponents.mockResolvedValueOnce(okList([item('a'), item('c')]))
      await s.handleDelete(s.list.value[1])

      expect(mocks.deleteComponent).toHaveBeenCalledWith('b')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
      expect(s.selectedId.value).toBe('c')
      expect(s.panelMode.value).toBe('view')
    })

    it('复制成功后选中副本', async () => {
      mocks.copyComponent.mockResolvedValue({ id: 'copy1' })
      mocks.fetchComponents.mockResolvedValue(okList([item('a'), item('copy1')]))
      const s = useComponentPage()
      await s.loadList()

      await s.handleCopy(s.list.value[0])

      expect(mocks.copyComponent).toHaveBeenCalledWith('a')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已复制')
      expect(s.selectedId.value).toBe('copy1')
    })

    it('副本不在当前筛选结果时保持原选中', async () => {
      mocks.copyComponent.mockResolvedValue({ id: 'copy1' })
      mocks.fetchComponents.mockResolvedValue(okList([item('a')]))
      const s = useComponentPage()
      await s.loadList()

      await s.handleCopy(s.list.value[0])

      expect(s.selectedId.value).toBe('a')
    })

    it('复制失败时提示', async () => {
      mocks.fetchComponents.mockResolvedValue(okList([item('a')]))
      mocks.copyComponent.mockRejectedValue(new Error('x'))
      const s = useComponentPage()
      await s.loadList()

      await s.handleCopy(s.list.value[0])

      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })
  })

  describe('提取器引入', () => {
    it('打开选择器时重置关键词并拉取启用中的提取器', async () => {
      const s = useComponentPage()
      s.extractorPickerKeyword.value = '旧值'

      s.openExtractorPicker()
      await Promise.resolve()

      expect(s.extractorPickerVisible.value).toBe(true)
      expect(s.extractorPickerKeyword.value).toBe('')
      expect(mocks.fetchComponents).toHaveBeenCalledWith({
        type: 'extractor',
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: undefined,
      })
    })

    it('选中项合并进 config.extractors 并提示数量', async () => {
      mocks.extractorsFromComponents.mockReturnValue([{ source: 'json_field' }])
      const s = useComponentPage()
      s.startCreate()
      s.form.config = { enabled: true, testclass: 'http', config: {}, extractors: [{ source: 'old' }] }

      s.handleExtractorPicked([item('e1')])

      expect(mocks.extractorsFromComponents).toHaveBeenCalled()
      expect(s.form.config.extractors).toEqual([{ source: 'old' }, { source: 'json_field' }])
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已引入 1 个提取器')
    })

    it('无有效提取器时不动表单', async () => {
      mocks.extractorsFromComponents.mockReturnValue([])
      const s = useComponentPage()
      s.startCreate()
      const before = s.form.config

      s.handleExtractorPicked([])

      expect(s.form.config).toBe(before)
      expect(mocks.ElMessage.success).not.toHaveBeenCalled()
    })
  })

  describe('滚动加载（窗口分页）', () => {
    it('首屏不足整页时 hasMoreDown 为 false，loadMore 不发请求', async () => {
      mocks.fetchComponents.mockResolvedValueOnce({ list: [item('a')], total: 1 })
      const s = useComponentPage()
      await s.loadList()

      await s.loadMore()

      expect(mocks.fetchComponents).toHaveBeenCalledTimes(1)
      expect(s.hasMoreDown.value).toBe(false)
      expect(s.hasMoreUp.value).toBe(false)
    })

    it('loadMore 拉取下一页并按 id 去重追加', async () => {
      mocks.fetchComponents
        .mockResolvedValueOnce(pageOf(1, 20, 40))
        .mockResolvedValueOnce({ list: [item('c1'), ...pageOf(21, 19, 40).list], total: 40 })
      const s = useComponentPage()
      await s.loadList()
      expect(s.hasMoreDown.value).toBe(true)

      await s.loadMore()

      expect(mocks.fetchComponents).toHaveBeenLastCalledWith({
        pageNo: 2,
        pageSize: 20,
        type: undefined,
        keyword: undefined,
      })
      expect(s.list.value).toHaveLength(39)
      expect(s.hasMoreDown.value).toBe(false)
      expect(s.loadingMore.value).toBe(false)
    })

    it('窗口超过 5 页丢弃首部并回放滚动补偿，之后可再加载上一页', async () => {
      const scroll = { beforeShift: vi.fn(), afterShift: vi.fn() }
      pagedMock(200)
      const s = useComponentPage({ scroll })
      await s.loadList()
      for (let index = 0; index < 5; index++) await s.loadMore()

      expect(s.list.value).toHaveLength(100)
      expect(s.list.value[0]?.id).toBe('c21')
      expect(scroll.beforeShift).toHaveBeenCalledTimes(1)
      expect(scroll.afterShift).toHaveBeenCalledTimes(1)
      expect(s.hasMoreUp.value).toBe(true)

      await s.loadPrev()

      expect(mocks.fetchComponents).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 1 }))
      expect(s.hasMoreUp.value).toBe(false)
      expect(s.list.value).toHaveLength(100)
      expect(s.list.value[0]?.id).toBe('c1')
      expect(s.loadingPrev.value).toBe(false)
    })

    it('已在第一页时 loadPrev 不发请求', async () => {
      mocks.fetchComponents.mockResolvedValueOnce({ list: [item('a')], total: 1 })
      const s = useComponentPage()
      await s.loadList()

      await s.loadPrev()

      expect(mocks.fetchComponents).toHaveBeenCalledTimes(1)
      expect(s.hasMoreUp.value).toBe(false)
    })

    it('下一页加载失败保留已加载数据并给出可重试的行内错误', async () => {
      mocks.fetchComponents
        .mockResolvedValueOnce(pageOf(1, 20, 40))
        .mockRejectedValueOnce(new Error('boom'))
      const s = useComponentPage()
      await s.loadList()

      await s.loadMore()

      expect(s.moreError.value).toBe(true)
      expect(s.list.value).toHaveLength(20)
      expect(s.loadingMore.value).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })

    it('上一页加载失败置 prevError 并统一提示', async () => {
      pagedMock(200)
      const s = useComponentPage()
      await s.loadList()
      for (let index = 0; index < 5; index++) await s.loadMore()
      mocks.fetchComponents.mockRejectedValueOnce(new Error('boom'))

      await s.loadPrev()

      expect(s.prevError.value).toBe(true)
      expect(s.loadingPrev.value).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })

    it('筛选重置后丢弃在途滚动加载的过期响应', async () => {
      let resolveMore: ((value: { list: ApiComponentListItem[]; total: number }) => void) | undefined
      mocks.fetchComponents
        .mockResolvedValueOnce(pageOf(1, 20, 40))
        .mockImplementationOnce(() => new Promise((resolve) => {
          resolveMore = resolve
        }))
      const s = useComponentPage()
      await s.loadList()
      const pending = s.loadMore()

      mocks.fetchComponents.mockResolvedValueOnce({ list: [item('x')], total: 1 })
      await s.loadList()
      resolveMore?.({ list: pageOf(21, 20, 40).list, total: 40 })
      await pending
      await nextTick()

      expect(s.list.value).toHaveLength(1)
      expect(s.list.value[0]?.id).toBe('x')
      expect(s.loadingMore.value).toBe(false)
    })

    it('变更操作后按当前窗口范围顺序重取并保持选中', async () => {
      pagedMock(200)
      mocks.toggleComponent.mockResolvedValue(true)
      const s = useComponentPage()
      await s.loadList()
      await s.loadMore()
      expect(s.list.value).toHaveLength(40)
      const callsBefore = mocks.fetchComponents.mock.calls.length

      await s.handleEnableToggle(false)

      expect(mocks.fetchComponents.mock.calls.length - callsBefore).toBe(2)
      expect(s.list.value).toHaveLength(40)
      expect(s.selectedId.value).toBe('c1')
      expect(s.loadingMore.value).toBe(false)
    })
  })
})
