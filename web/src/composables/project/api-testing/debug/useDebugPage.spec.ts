import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiDebugRecordItem, DebugTab } from '@/types'

const mocks = vi.hoisted(() => ({
  createTab: vi.fn<() => DebugTab>(() => ({
    id: 'tab-init-1',
    name: '',
    method: 'GET',
    url: '',
    headers: [],
    params: [],
    bodies: { urlencoded: [], raw: null },
    bodyType: 'none',
    auth: { type: 'none' },
    responseTimeoutMs: 30000,
    response: null,
  })),
  tabTitle: vi.fn((tab: DebugTab) => tab.name || '新建请求'),
  ensureUrlScheme: vi.fn((url: string) => url),
  buildExecutePayload: vi.fn(),
  markExecuted: vi.fn(),
  applyCurlToTab: vi.fn(),
  tabFromRestore: vi.fn(),
  executeDebug: vi.fn<() => Promise<unknown>>(),
  restoreDebugRecord: vi.fn<() => Promise<unknown>>(),
  parseCurl: vi.fn(),
  consumePendingRequest: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  MAX_DEBUG_TABS: 10,
  windowAddEventListener: vi.fn(),
  windowRemoveEventListener: vi.fn(),
  docAddEventListener: vi.fn(),
  docRemoveEventListener: vi.fn(),
  hotkeyCallback: null as ((e: KeyboardEvent) => void) | null,
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => { cb() },
    onBeforeUnmount: (cb: () => void) => { cb() },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/pages/project/api-testing/debug/debugModel', () => ({
  createTab: mocks.createTab,
  tabTitle: mocks.tabTitle,
  ensureUrlScheme: mocks.ensureUrlScheme,
  buildExecutePayload: mocks.buildExecutePayload,
  markExecuted: mocks.markExecuted,
  applyCurlToTab: mocks.applyCurlToTab,
  tabFromRestore: mocks.tabFromRestore,
  MAX_DEBUG_TABS: mocks.MAX_DEBUG_TABS,
}))

vi.mock('@/services/project/debug', () => ({
  executeDebug: mocks.executeDebug,
  restoreDebugRecord: mocks.restoreDebugRecord,
}))

vi.mock('@/pages/project/api-testing/debug/curlParser', () => ({
  parseCurl: mocks.parseCurl,
}))

vi.mock('@/stores/apiTestingUi', () => ({
  useApiTestingUiStore: vi.fn(() => ({
    consumePendingRequest: mocks.consumePendingRequest,
  })),
}))

vi.stubGlobal('window', {
  addEventListener: mocks.windowAddEventListener,
  removeEventListener: mocks.windowRemoveEventListener,
})
vi.stubGlobal('document', {
  addEventListener: mocks.docAddEventListener,
  removeEventListener: mocks.docRemoveEventListener,
  body: { style: { cursor: '', userSelect: '' } },
})

import { useDebugPage } from './useDebugPage'

function makeTab(overrides?: Partial<DebugTab>): DebugTab {
  return {
    id: overrides?.id ?? 'tab-test-1',
    name: overrides?.name ?? '',
    method: overrides?.method ?? 'GET',
    url: overrides?.url ?? '',
    headers: overrides?.headers ?? [],
    params: overrides?.params ?? [],
    bodies: overrides?.bodies ?? { urlencoded: [], raw: null },
    bodyType: overrides?.bodyType ?? 'none',
    auth: overrides?.auth ?? { type: 'none' },
    responseTimeoutMs: overrides?.responseTimeoutMs ?? 30000,
    response: overrides?.response ?? null,
  }
}

describe('useDebugPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    let seq = 0
    mocks.createTab.mockImplementation(() => {
      seq++
      return makeTab({ id: `tab-${seq}` })
    })
    mocks.consumePendingRequest.mockReturnValue(null)
    mocks.hotkeyCallback = null
    mocks.windowAddEventListener.mockImplementation((event: string, cb: (e: KeyboardEvent) => void) => {
      if (event === 'keydown') mocks.hotkeyCallback = cb
    })
  })

  function init(emitFn?: (e: 'view-interface', interfaceId: string) => void) {
    const emit = emitFn ?? vi.fn()
    return useDebugPage(emit)
  }

  function fireHotkey(e: Partial<KeyboardEvent>) {
    const event = {
      ctrlKey: false,
      metaKey: false,
      shiftKey: false,
      key: '',
      preventDefault: vi.fn(),
      ...e,
    } as unknown as KeyboardEvent
    mocks.hotkeyCallback?.(event)
    return event
  }

  describe('初始状态', () => {
    it('tabs 包含一个初始标签', () => {
      const s = init()
      expect(s.tabs.value).toHaveLength(1)
    })

    it('activeTabId 等于第一个标签 id', () => {
      const s = init()
      expect(s.activeTabId.value).toBe(s.tabs.value[0].id)
    })

    it('showHistory 初始为 false', () => {
      const s = init()
      expect(s.showHistory.value).toBe(false)
    })

    it('debugEnvironmentId 初始为空字符串', () => {
      const s = init()
      expect(s.debugEnvironmentId.value).toBe('')
    })

    it('activeTab 返回当前激活标签', () => {
      const s = init()
      expect(s.activeTab.value.id).toBe(s.activeTabId.value)
    })

    it('canAddTab 初始为 true（1 < 10）', () => {
      const s = init()
      expect(s.canAddTab.value).toBe(true)
    })

    it('saveVisible 初始为 false', () => {
      const s = init()
      expect(s.saveVisible.value).toBe(false)
    })

    it('saveRecordId 初始为空字符串', () => {
      const s = init()
      expect(s.saveRecordId.value).toBe('')
    })

    it('canSave 初始为 false', () => {
      const s = init()
      expect(s.canSave.value).toBe(false)
    })

    it('executing 初始为 false', () => {
      const s = init()
      expect(s.executing.value).toBe(false)
    })

    it('curlVisible 初始为 false', () => {
      const s = init()
      expect(s.curlVisible.value).toBe(false)
    })

    it('curlText 初始为空字符串', () => {
      const s = init()
      expect(s.curlText.value).toBe('')
    })

    it('requestHeight 初始为 50', () => {
      const s = init()
      expect(s.requestHeight.value).toBe(50)
    })

    it('isDragging 初始为 false', () => {
      const s = init()
      expect(s.isDragging.value).toBe(false)
    })

    it('renamingId 初始为空字符串', () => {
      const s = init()
      expect(s.renamingId.value).toBe('')
    })

    it('renamingValue 初始为空字符串', () => {
      const s = init()
      expect(s.renamingValue.value).toBe('')
    })

    it('HISTORY_TAB_ID 为 __history__', () => {
      expect(useDebugPage(vi.fn()).HISTORY_TAB_ID).toBe('__history__')
    })

    it('onMounted 注册了 keydown 事件监听', () => {
      init()
      expect(mocks.windowAddEventListener).toHaveBeenCalledWith('keydown', expect.any(Function))
    })

    it('onBeforeUnmount 移除 keydown 事件监听', () => {
      init()
      expect(mocks.windowRemoveEventListener).toHaveBeenCalledWith('keydown', expect.any(Function))
    })
  })

  describe('computed', () => {
    it('activeTabId 对应的 tab 不在 tabs 中时回退到 tabs[0]', () => {
      const s = init()
      s.activeTabId.value = 'non-existent'
      expect(s.activeTab.value.id).toBe(s.tabs.value[0].id)
    })

    it('canAddTab 达到 MAX_DEBUG_TABS 时为 false', () => {
      const s = init()
      for (let i = 1; i < 10; i++) {
        s.tabs.value.push(makeTab({ id: `tab-${i + 1}` }))
      }
      expect(s.tabs.value).toHaveLength(10)
      expect(s.canAddTab.value).toBe(false)
    })

    it('saveRecordId 从 activeTab.response.debugRecordId 获取', () => {
      const s = init()
      s.tabs.value[0].response = { debugRecordId: 'rec-1' } as never
      expect(s.saveRecordId.value).toBe('rec-1')
    })

    it('saveRecordId 在 response 为 null 时为空字符串', () => {
      const s = init()
      expect(s.saveRecordId.value).toBe('')
    })

    it('canSave 在 saveRecordId 有值时为 true', () => {
      const s = init()
      s.tabs.value[0].response = { debugRecordId: 'rec-1' } as never
      expect(s.canSave.value).toBe(true)
    })
  })

  describe('handleSave', () => {
    it('canSave 为 true 时设置 saveVisible', () => {
      const s = init()
      s.tabs.value[0].response = { debugRecordId: 'rec-1' } as never
      s.handleSave()
      expect(s.saveVisible.value).toBe(true)
    })

    it('canSave 为 false 时不做任何事', () => {
      const s = init()
      s.handleSave()
      expect(s.saveVisible.value).toBe(false)
    })
  })

  describe('handleSaved', () => {
    it('用户确认后调用 emit view-interface', async () => {
      const emit = vi.fn()
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      const s = init(emit)
      await s.handleSaved('iface-1')
      expect(emit).toHaveBeenCalledWith('view-interface', 'iface-1')
    })

    it('用户取消时 catch 空函数', async () => {
      const emit = vi.fn()
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const s = init(emit)
      await s.handleSaved('iface-1')
      expect(emit).not.toHaveBeenCalled()
    })
  })

  describe('switchTab', () => {
    it('切换到指定标签', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      s.switchTab('tab-2')
      expect(s.activeTabId.value).toBe('tab-2')
      expect(s.showHistory.value).toBe(false)
    })

    it('切换到 HISTORY_TAB_ID 时 showHistory 为 true', () => {
      const s = init()
      s.switchTab('__history__')
      expect(s.activeTabId.value).toBe('__history__')
      expect(s.showHistory.value).toBe(true)
    })
  })

  describe('addTab', () => {
    it('正常添加新标签并激活', () => {
      const s = init()
      s.addTab()
      expect(s.tabs.value).toHaveLength(2)
      expect(s.activeTabId.value).toBe(s.tabs.value[1].id)
    })

    it('达到上限时不添加', () => {
      const s = init()
      for (let i = 1; i < 10; i++) {
        s.tabs.value.push(makeTab({ id: `tab-${i + 1}` }))
      }
      s.addTab()
      expect(s.tabs.value).toHaveLength(10)
    })
  })

  describe('closeTab', () => {
    it('关闭非激活标签', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      s.closeTab(tab2)
      expect(s.tabs.value).toHaveLength(1)
    })

    it('关闭激活标签时切换到前一个', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      s.switchTab('tab-2')
      s.closeTab(tab2)
      expect(s.tabs.value).toHaveLength(1)
      expect(s.activeTabId.value).toBe(s.tabs.value[0].id)
    })

    it('关闭第一个标签（index 0）时切换到第一个', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      s.closeTab(s.tabs.value[0])
      expect(s.activeTabId.value).toBe(s.tabs.value[0].id)
    })

    it('关闭所有标签后自动创建新标签', () => {
      const s = init()
      s.closeTab(s.tabs.value[0])
      expect(s.tabs.value).toHaveLength(1)
    })
  })

  describe('startRename / commitRename', () => {
    it('startRename 设置 renamingId 和 renamingValue', () => {
      const s = init()
      const tab = makeTab({ id: 'tab-r1', name: '原始名' })
      s.startRename(tab)
      expect(s.renamingId.value).toBe('tab-r1')
      expect(s.renamingValue.value).toBe('原始名')
    })

    it('commitRename 在值非空且 id 匹配时更新 tab.name', () => {
      const s = init()
      const tab = makeTab({ id: 'tab-r1', name: '旧名' })
      s.startRename(tab)
      s.renamingValue.value = '新名'
      s.commitRename(tab)
      expect(tab.name).toBe('新名')
      expect(s.renamingId.value).toBe('')
    })

    it('commitRename 在值为空时不更新 tab.name', () => {
      const s = init()
      const tab = makeTab({ id: 'tab-r1', name: '旧名' })
      s.startRename(tab)
      s.renamingValue.value = '   '
      s.commitRename(tab)
      expect(tab.name).toBe('旧名')
      expect(s.renamingId.value).toBe('')
    })

    it('commitRename 在 id 不匹配时不更新 tab.name 但清除 renamingId', () => {
      const s = init()
      const tab = makeTab({ id: 'tab-r1', name: '旧名' })
      s.renamingId.value = 'other-id'
      s.renamingValue.value = '新名'
      s.commitRename(tab)
      expect(tab.name).toBe('旧名')
      expect(s.renamingId.value).toBe('')
    })
  })

  describe('handleExecute', () => {
    it('url 为空时显示警告并返回', async () => {
      const s = init()
      await s.handleExecute()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请输入请求 URL')
      expect(s.executing.value).toBe(false)
    })

    it('url 为空白时显示警告', async () => {
      const s = init()
      s.tabs.value[0].url = '   '
      await s.handleExecute()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请输入请求 URL')
    })

    it('executing 为 true 时不重复执行', async () => {
      const s = init()
      s.tabs.value[0].url = 'http://example.com'
      s.executing.value = true
      await s.handleExecute()
      expect(mocks.executeDebug).not.toHaveBeenCalled()
    })

    it('成功执行后 markExecuted 被调用', async () => {
      const resp = { status: 'success' }
      mocks.executeDebug.mockResolvedValue(resp)
      mocks.ensureUrlScheme.mockReturnValue('http://example.com')
      const s = init()
      s.tabs.value[0].url = 'http://example.com'
      await s.handleExecute()
      expect(mocks.ensureUrlScheme).toHaveBeenCalledWith('http://example.com')
      expect(mocks.buildExecutePayload).toHaveBeenCalled()
      expect(mocks.markExecuted).toHaveBeenCalledWith(s.tabs.value[0], resp)
      expect(s.executing.value).toBe(false)
    })

    it('执行失败时 executing 仍恢复为 false', async () => {
      mocks.executeDebug.mockRejectedValue(new Error('network'))
      const s = init()
      s.tabs.value[0].url = 'http://example.com'
      await s.handleExecute()
      expect(s.executing.value).toBe(false)
    })

    it('传入 environmentId 时传递给 buildExecutePayload', async () => {
      mocks.executeDebug.mockResolvedValue({})
      const s = init()
      s.tabs.value[0].url = 'http://example.com'
      await s.handleExecute('env-1')
      expect(mocks.buildExecutePayload).toHaveBeenCalledWith(s.tabs.value[0], 'env-1')
    })

    it('未传 environmentId 时传递 undefined', async () => {
      mocks.executeDebug.mockResolvedValue({})
      const s = init()
      s.tabs.value[0].url = 'http://example.com'
      await s.handleExecute()
      expect(mocks.buildExecutePayload).toHaveBeenCalledWith(s.tabs.value[0], undefined)
    })
  })

  describe('handleImportCurl', () => {
    it('curlText 为空时不执行', () => {
      const s = init()
      s.handleImportCurl()
      expect(mocks.parseCurl).not.toHaveBeenCalled()
    })

    it('curlText 仅空白时不执行', () => {
      const s = init()
      s.curlText.value = '   '
      s.handleImportCurl()
      expect(mocks.parseCurl).not.toHaveBeenCalled()
    })

    it('解析成功时回填标签并关闭弹窗', () => {
      const parsed = {
        method: 'POST',
        url: 'http://test.com',
        headers: [{ key: 'Content-Type', value: 'application/json', enabled: true }],
        bodyType: 'json' as const,
        bodyContent: { key: 'val' },
      }
      mocks.parseCurl.mockReturnValue(parsed)
      const s = init()
      s.curlText.value = 'curl -X POST http://test.com'
      s.handleImportCurl()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(s.tabs.value[0], {
        method: 'POST',
        url: 'http://test.com',
        headers: parsed.headers,
        body: { type: 'json', content: { key: 'val' } },
      })
      expect(s.curlVisible.value).toBe(false)
      expect(s.curlText.value).toBe('')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('cURL 解析成功，已回填当前标签')
    })

    it('parseCurl 返回的 bodyType 为 null 时映射为 none', () => {
      mocks.parseCurl.mockReturnValue({
        method: 'GET',
        url: 'http://test.com',
        headers: [],
        bodyType: null,
        bodyContent: null,
      })
      const s = init()
      s.curlText.value = 'curl http://test.com'
      s.handleImportCurl()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(s.tabs.value[0], {
        method: 'GET',
        url: 'http://test.com',
        headers: [],
        body: { type: 'none', content: null },
      })
    })

    it('解析失败时显示错误消息（Error 类型）', () => {
      mocks.parseCurl.mockImplementation(() => { throw new Error('解析出错') })
      const s = init()
      s.curlText.value = 'invalid curl'
      s.handleImportCurl()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('解析出错')
    })

    it('解析失败时显示通用错误消息（非 Error 类型）', () => {
      mocks.parseCurl.mockImplementation(() => { throw 'unknown' })
      const s = init()
      s.curlText.value = 'invalid curl'
      s.handleImportCurl()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('cURL 解析失败')
    })
  })

  describe('handleRestoreRecord', () => {
    it('成功恢复后新建标签并切换', async () => {
      const restoredTab = makeTab({ id: 'restored-1' })
      mocks.restoreDebugRecord.mockResolvedValue({ request: {}, response: {}, debugRecordId: 'rec-1' })
      mocks.tabFromRestore.mockReturnValue(restoredTab)
      const s = init()
      const record = { id: 'rec-1' } as ApiDebugRecordItem
      await s.handleRestoreRecord(record)
      expect(mocks.restoreDebugRecord).toHaveBeenCalledWith('rec-1')
      expect(mocks.tabFromRestore).toHaveBeenCalled()
      expect(s.tabs.value).toHaveLength(2)
      expect(s.activeTabId.value).toBe('restored-1')
      expect(s.showHistory.value).toBe(false)
    })

    it('恢复失败时不抛出异常', async () => {
      mocks.restoreDebugRecord.mockRejectedValue(new Error('fail'))
      const s = init()
      await expect(s.handleRestoreRecord({ id: 'rec-1' } as ApiDebugRecordItem)).resolves.toBeUndefined()
    })
  })

  describe('divider 拖拽', () => {
    it('onDividerMouseDown 设置 isDragging 并注册事件', () => {
      const s = init()
      const e = { preventDefault: vi.fn() } as unknown as MouseEvent
      s.onDividerMouseDown(e)
      expect(e.preventDefault).toHaveBeenCalled()
      expect(s.isDragging.value).toBe(true)
      expect(mocks.docAddEventListener).toHaveBeenCalledWith('mousemove', expect.any(Function))
      expect(mocks.docAddEventListener).toHaveBeenCalledWith('mouseup', expect.any(Function))
    })

    it('onDividerMouseMove 在非拖拽时不更新高度', () => {
      const s = init()
      s.onDividerMouseMove({ clientY: 100 } as MouseEvent)
      expect(s.requestHeight.value).toBe(50)
    })

    it('onDividerMouseMove 在拖拽时更新高度', () => {
      const s = init()
      const mockRect = { top: 0, height: 200 }
      s.containerRef.value = { getBoundingClientRect: () => mockRect } as never
      s.isDragging.value = true
      s.onDividerMouseMove({ clientY: 60 } as MouseEvent)
      expect(s.requestHeight.value).toBe(30)
    })

    it('onDividerMouseMove 限制最小 20%', () => {
      const s = init()
      const mockRect = { top: 0, height: 200 }
      s.containerRef.value = { getBoundingClientRect: () => mockRect } as never
      s.isDragging.value = true
      s.onDividerMouseMove({ clientY: 10 } as MouseEvent)
      expect(s.requestHeight.value).toBe(20)
    })

    it('onDividerMouseMove 限制最大 80%', () => {
      const s = init()
      const mockRect = { top: 0, height: 200 }
      s.containerRef.value = { getBoundingClientRect: () => mockRect } as never
      s.isDragging.value = true
      s.onDividerMouseMove({ clientY: 190 } as MouseEvent)
      expect(s.requestHeight.value).toBe(80)
    })

    it('onDividerMouseMove 在 containerRef 未设置时不更新', () => {
      const s = init()
      s.isDragging.value = true
      s.onDividerMouseMove({ clientY: 100 } as MouseEvent)
      expect(s.requestHeight.value).toBe(50)
    })

    it('onDividerMouseUp 清理状态并移除事件', () => {
      const s = init()
      s.isDragging.value = true
      s.onDividerMouseUp()
      expect(s.isDragging.value).toBe(false)
      expect(mocks.docRemoveEventListener).toHaveBeenCalledWith('mousemove', expect.any(Function))
      expect(mocks.docRemoveEventListener).toHaveBeenCalledWith('mouseup', expect.any(Function))
    })
  })

  describe('methodColor', () => {
    it('返回已知方法对应的颜色', () => {
      const s = init()
      expect(s.methodColor('GET')).toBe('#61affe')
      expect(s.methodColor('POST')).toBe('#49cc90')
      expect(s.methodColor('PUT')).toBe('#fca130')
      expect(s.methodColor('PATCH')).toBe('#50e3c2')
      expect(s.methodColor('DELETE')).toBe('#f93e3e')
      expect(s.methodColor('OPTIONS')).toBe('#0d5aa7')
      expect(s.methodColor('HEAD')).toBe('#9012fe')
      expect(s.methodColor('CONNECT')).toBe('#e8d44d')
    })

    it('未知方法返回默认颜色', () => {
      const s = init()
      expect(s.methodColor('TRACE')).toBe('#999')
    })

    it('小写方法名也返回正确颜色', () => {
      const s = init()
      expect(s.methodColor('get')).toBe('#61affe')
    })
  })

  describe('handleAuxClick', () => {
    it('中键点击时关闭标签', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      const e = { button: 1, preventDefault: vi.fn() } as unknown as MouseEvent
      s.handleAuxClick(e, tab2)
      expect(e.preventDefault).toHaveBeenCalled()
      expect(s.tabs.value).toHaveLength(1)
    })

    it('左键点击时不关闭标签', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      const e = { button: 0, preventDefault: vi.fn() } as unknown as MouseEvent
      s.handleAuxClick(e, tab2)
      expect(s.tabs.value).toHaveLength(2)
    })
  })

  describe('onHotkey (via captured callback)', () => {
    it('Ctrl+Shift+H 切换到历史标签', () => {
      init()
      const e = fireHotkey({ ctrlKey: true, shiftKey: true, key: 'h' })
      expect(mocks.hotkeyCallback).toBeDefined()
      expect(e.preventDefault).toHaveBeenCalled()
    })

    it('Ctrl+Shift+H 在历史标签时切回第一个标签', () => {
      const s = init()
      s.switchTab('__history__')
      fireHotkey({ ctrlKey: true, shiftKey: true, key: 'h' })
      expect(s.showHistory.value).toBe(false)
      expect(s.activeTabId.value).toBe(s.tabs.value[0].id)
    })

    it('Meta+Shift+H 也能切换', () => {
      init()
      fireHotkey({ metaKey: true, shiftKey: true, key: 'h' })
    })

    it('Ctrl+Enter 执行请求', async () => {
      mocks.executeDebug.mockResolvedValue({})
      const s = init()
      s.tabs.value[0].url = 'http://test.com'
      fireHotkey({ ctrlKey: true, key: 'Enter' })
      expect(mocks.executeDebug).toHaveBeenCalled()
    })

    it('Ctrl+Enter 在历史模式时不执行', () => {
      const s = init()
      s.switchTab('__history__')
      fireHotkey({ ctrlKey: true, key: 'Enter' })
      expect(mocks.executeDebug).not.toHaveBeenCalled()
    })

    it('Ctrl+T 添加新标签', () => {
      const s = init()
      fireHotkey({ ctrlKey: true, key: 't' })
      expect(s.tabs.value).toHaveLength(2)
    })

    it('Ctrl+W 关闭当前标签', () => {
      const s = init()
      const tab2 = makeTab({ id: 'tab-2' })
      s.tabs.value.push(tab2)
      fireHotkey({ ctrlKey: true, key: 'w' })
      expect(s.tabs.value).toHaveLength(1)
    })

    it('Ctrl+W 在历史模式时不关闭', () => {
      const s = init()
      s.switchTab('__history__')
      fireHotkey({ ctrlKey: true, key: 'w' })
      expect(s.tabs.value).toHaveLength(1)
    })

    it('非修饰键组合不触发', () => {
      const e = fireHotkey({ ctrlKey: false, key: 'a' })
      expect(e.preventDefault).not.toHaveBeenCalled()
    })
  })

  describe('consumePendingInterfaceRequest', () => {
    it('有 pending request 时新建标签并填充', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '测试接口',
        method: 'POST',
        path: '/api/test',
        headers: [{ key: 'Auth', value: 'token', enabled: true }],
        params: [{ key: 'v', value: '1', enabled: true }],
        body: { type: 'json', content: { data: 1 } },
      })
      const s = init()
      expect(s.tabs.value).toHaveLength(2)
      const newTab = s.tabs.value[1]
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(newTab, {
        method: 'POST',
        url: '/api/test',
        headers: [{ key: 'Auth', value: 'token', enabled: true }],
        body: { type: 'json', content: { data: 1 } },
        params: [{ key: 'v', value: '1', enabled: true }],
      })
      expect(newTab.name).toBe('测试接口')
    })

    it('无 pending request 时不创建新标签', () => {
      mocks.consumePendingRequest.mockReturnValue(null)
      const s = init()
      expect(s.tabs.value).toHaveLength(1)
    })

    it('pending request 无 name 时自动生成', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'GET',
        path: '/api/list',
        headers: null,
        params: null,
        body: null,
      })
      const s = init()
      const newTab = s.tabs.value[1]
      expect(newTab.name).toBe('GET /api/list')
    })

    it('body type 为 form 时数组转 Record', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: 'form',
        method: 'POST',
        path: '/submit',
        headers: null,
        params: null,
        body: { type: 'form', content: [{ key: 'k', value: 'v' }] },
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'form', content: { k: 'v' } } }),
      )
    })

    it('body type 为 undefined 时映射为 none', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: 'no-body',
        method: 'GET',
        path: '/health',
        headers: null,
        params: null,
        body: null,
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'none', content: undefined } }),
      )
    })
  })

  describe('normalizeBodyType / normalizeBodyContent 内部逻辑', () => {
    it('form 类型空数组转为空 Record', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'POST',
        path: '/api',
        body: { type: 'form', content: [] },
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'form', content: {} } }),
      )
    })

    it('raw 类型直接透传', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'POST',
        path: '/api',
        body: { type: 'raw', content: 'raw text' },
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'raw', content: 'raw text' } }),
      )
    })

    it('binary 类型直接透传', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'POST',
        path: '/api',
        body: { type: 'binary', content: 'bin' },
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'binary', content: 'bin' } }),
      )
    })

    it('form 数组 content 转换为 Record', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'POST',
        path: '/api',
        body: {
          type: 'form',
          content: [
            { key: 'a', value: '1' },
            { key: 'b', value: '2' },
          ],
        },
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'form', content: { a: '1', b: '2' } } }),
      )
    })

    it('form 数组空 key 被过滤', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'POST',
        path: '/api',
        body: {
          type: 'form',
          content: [
            { key: '', value: 'skip' },
            { key: 'valid', value: 'ok' },
          ],
        },
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'form', content: { valid: 'ok' } } }),
      )
    })

    it('null body 映射为 none', () => {
      mocks.consumePendingRequest.mockReturnValue({
        name: '',
        method: 'GET',
        path: '/api',
        body: null,
      })
      init()
      expect(mocks.applyCurlToTab).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ body: { type: 'none', content: undefined } }),
      )
    })
  })
})
