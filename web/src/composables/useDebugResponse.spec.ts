import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import type { ApiDebugExecuteResp } from '@/types'

const mocks = vi.hoisted(() => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  clipboardWriteText: vi.fn<() => Promise<void>>(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
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
}))

Object.defineProperty(globalThis, 'navigator', {
  value: { clipboard: { writeText: mocks.clipboardWriteText } },
  writable: true,
  configurable: true,
})

if (typeof globalThis.window === 'undefined') {
  Object.defineProperty(globalThis, 'window', {
    value: {
      addEventListener: mocks.addEventListener,
      removeEventListener: mocks.removeEventListener,
    },
    writable: true,
    configurable: true,
  })
}

class MockKeyboardEvent {
  type: string
  key: string
  ctrlKey: boolean
  metaKey: boolean
  shiftKey: boolean
  preventDefault = vi.fn()
  constructor(type: string, init?: { key?: string; ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean }) {
    this.type = type
    this.key = init?.key ?? ''
    this.ctrlKey = init?.ctrlKey ?? false
    this.metaKey = init?.metaKey ?? false
    this.shiftKey = init?.shiftKey ?? false
  }
}

Object.defineProperty(globalThis, 'KeyboardEvent', {
  value: MockKeyboardEvent,
  writable: true,
  configurable: true,
})

import { useDebugResponse } from './useDebugResponse'

function makeResponse(overrides?: Partial<ApiDebugExecuteResp>): ApiDebugExecuteResp {
  return {
    debugRecordId: 'rec-1',
    status: 'success',
    responseStatus: 200,
    responseHeaders: { 'Content-Type': 'application/json' },
    responseBody: { ok: true },
    durationMs: 120,
    size: 256,
    ...overrides,
  }
}

function makeErrorResponse(overrides?: Partial<ApiDebugExecuteResp>): ApiDebugExecuteResp {
  return {
    debugRecordId: 'rec-2',
    status: 'error',
    errorMessage: 'Connection refused',
    ...overrides,
  }
}

function findHandler() {
  const call = mocks.addEventListener.mock.calls.find(
    (c: unknown[]) => c[0] === 'keydown'
  )
  return call?.[1] as ((e: unknown) => void) | undefined
}

describe('useDebugResponse', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  function create(getter?: () => ApiDebugExecuteResp | null) {
    const response = getter ?? (() => makeResponse())
    return useDebugResponse(response)
  }

  describe('初始状态', () => {
    it('activeTab 初始为 body', () => {
      const s = create()
      expect(s.activeTab.value).toBe('body')
    })

    it('bodyMode 初始为 pretty', () => {
      const s = create()
      expect(s.bodyMode.value).toBe('pretty')
    })

    it('langOverride 初始为 null', () => {
      const s = create()
      expect(s.langOverride.value).toBeNull()
    })

    it('searchKeyword 初始为空字符串', () => {
      const s = create()
      expect(s.searchKeyword.value).toBe('')
    })

    it('searchExpanded 初始为 false', () => {
      const s = create()
      expect(s.searchExpanded.value).toBe(false)
    })

    it('copying 初始为 false', () => {
      const s = create()
      expect(s.copying.value).toBe(false)
    })
  })

  describe('statusConfig', () => {
    it('response 为 null 时返回默认样式', () => {
      const s = create(() => null)
      expect(s.statusConfig.value).toEqual({ color: '#999', bg: '#f5f5f5', label: '—' })
    })

    it('status 为 error 时返回错误样式', () => {
      const s = create(() => makeErrorResponse())
      expect(s.statusConfig.value).toEqual({ color: '#f93e3e', bg: '#feeaEA', label: '请求失败' })
    })

    it('2xx 状态码返回成功样式', () => {
      const s = create(() => makeResponse({ responseStatus: 200 }))
      expect(s.statusConfig.value).toEqual({ color: '#49cc90', bg: '#eafaf1', label: '成功' })
    })

    it('3xx 状态码返回重定向样式', () => {
      const s = create(() => makeResponse({ responseStatus: 302 }))
      expect(s.statusConfig.value).toEqual({ color: '#61affe', bg: '#eaf3ff', label: '重定向' })
    })

    it('4xx 状态码返回客户端错误样式', () => {
      const s = create(() => makeResponse({ responseStatus: 404 }))
      expect(s.statusConfig.value).toEqual({ color: '#fca130', bg: '#fef6e8', label: '客户端错误' })
    })

    it('5xx 状态码返回服务端错误样式', () => {
      const s = create(() => makeResponse({ responseStatus: 500 }))
      expect(s.statusConfig.value).toEqual({ color: '#f93e3e', bg: '#feeaEA', label: '服务端错误' })
    })

    it('无 responseStatus 时返回默认样式', () => {
      const s = create(() => makeResponse({ responseStatus: undefined }))
      expect(s.statusConfig.value).toEqual({ color: '#999', bg: '#f5f5f5', label: '—' })
    })
  })

  describe('statusCode', () => {
    it('response 为 null 时返回 —', () => {
      const s = create(() => null)
      expect(s.statusCode.value).toBe('—')
    })

    it('status 为 error 时返回 ERROR', () => {
      const s = create(() => makeErrorResponse())
      expect(s.statusCode.value).toBe('ERROR')
    })

    it('正常返回状态码字符串', () => {
      const s = create(() => makeResponse({ responseStatus: 201 }))
      expect(s.statusCode.value).toBe('201')
    })

    it('无 responseStatus 时返回 —', () => {
      const s = create(() => makeResponse({ responseStatus: undefined }))
      expect(s.statusCode.value).toBe('—')
    })
  })

  describe('statusTooltip', () => {
    it('response 为空时返回空字符串', () => {
      const s = create(() => null)
      expect(s.statusTooltip.value).toBe('')
    })

    it('200 返回完整 tooltip', () => {
      const s = create(() => makeResponse({ responseStatus: 200 }))
      expect(s.statusTooltip.value).toBe('200 OK — 请求成功')
    })

    it('404 返回完整 tooltip', () => {
      const s = create(() => makeResponse({ responseStatus: 404 }))
      expect(s.statusTooltip.value).toBe('404 Not Found — 资源不存在')
    })

    it('未知状态码回退到 statusConfig label', () => {
      const s = create(() => makeResponse({ responseStatus: 999 }))
      expect(s.statusTooltip.value).toContain('999')
    })
  })

  describe('bodyText', () => {
    it('response 为空时返回空字符串', () => {
      const s = create(() => null)
      expect(s.bodyText.value).toBe('')
    })

    it('responseBody 为 null 时返回空字符串', () => {
      const s = create(() => makeResponse({ responseBody: null }))
      expect(s.bodyText.value).toBe('')
    })

    it('responseBody 为 undefined 时返回空字符串', () => {
      const s = create(() => makeResponse({ responseBody: undefined }))
      expect(s.bodyText.value).toBe('')
    })

    it('responseBody 为字符串时直接返回', () => {
      const s = create(() => makeResponse({ responseBody: 'raw text' }))
      expect(s.bodyText.value).toBe('raw text')
    })

    it('responseBody 为对象时 JSON 格式化', () => {
      const s = create(() => makeResponse({ responseBody: { key: 'value' } }))
      expect(s.bodyText.value).toBe('{\n  "key": "value"\n}')
    })

    it('responseBody 为数组时 JSON 格式化', () => {
      const s = create(() => makeResponse({ responseBody: [1, 2] }))
      expect(s.bodyText.value).toBe('[\n  1,\n  2\n]')
    })

    it('JSON.stringify 失败时回退到 String(body)', () => {
      const circular: Record<string, unknown> = {}
      circular.self = circular
      const s = create(() => makeResponse({ responseBody: circular }))
      expect(s.bodyText.value).toBe(String(circular))
    })
  })

  describe('lang', () => {
    it('默认从 content-type 检测 json', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'content-type': 'application/json; charset=utf-8' } })
      )
      expect(s.lang.value).toBe('json')
    })

    it('检测 html', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'Content-Type': 'text/html' } })
      )
      expect(s.lang.value).toBe('html')
    })

    it('检测 xml', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'Content-Type': 'application/xml' } })
      )
      expect(s.lang.value).toBe('xml')
    })

    it('检测 javascript', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'Content-Type': 'text/javascript' } })
      )
      expect(s.lang.value).toBe('javascript')
    })

    it('无 content-type 时默认 text', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: {} })
      )
      expect(s.lang.value).toBe('text')
    })

    it('langOverride 覆盖检测结果', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'content-type': 'application/json' } })
      )
      s.langOverride.value = 'html'
      expect(s.lang.value).toBe('html')
    })
  })

  describe('parsedJson', () => {
    it('lang 非 json 时返回 undefined', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'Content-Type': 'text/plain' }, responseBody: 'hi' })
      )
      expect(s.parsedJson.value).toBeUndefined()
    })

    it('bodyText 为空时返回 undefined', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'content-type': 'application/json' }, responseBody: null })
      )
      expect(s.parsedJson.value).toBeUndefined()
    })

    it('合法 JSON 对象返回解析结果', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'content-type': 'application/json' }, responseBody: { a: 1 } })
      )
      expect(s.parsedJson.value).toEqual({ a: 1 })
    })

    it('JSON 为基本类型时返回 undefined', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'content-type': 'application/json' }, responseBody: 'string' })
      )
      expect(s.parsedJson.value).toBeUndefined()
    })

    it('JSON 解析失败时返回 undefined', () => {
      const s = create(() =>
        makeResponse({
          responseHeaders: { 'content-type': 'application/json' },
          responseBody: '{bad json',
        })
      )
      expect(s.parsedJson.value).toBeUndefined()
    })
  })

  describe('headerEntries', () => {
    it('response 为空时返回空数组', () => {
      const s = create(() => null)
      expect(s.headerEntries.value).toEqual([])
    })

    it('正常返回 headers 键值对', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'X-Custom': 'abc', 'Content-Type': 'text/plain' } })
      )
      expect(s.headerEntries.value).toEqual([
        ['X-Custom', 'abc'],
        ['Content-Type', 'text/plain'],
      ])
    })

    it('responseHeaders 为空对象时返回空数组', () => {
      const s = create(() => makeResponse({ responseHeaders: {} }))
      expect(s.headerEntries.value).toEqual([])
    })
  })

  describe('cookieEntries', () => {
    it('无 set-cookie 头时返回空数组', () => {
      const s = create(() =>
        makeResponse({ responseHeaders: { 'Content-Type': 'text/html' } })
      )
      expect(s.cookieEntries.value).toEqual([])
    })

    it('解析单个 cookie', () => {
      const s = create(() =>
        makeResponse({
          responseHeaders: { 'set-cookie': 'session=abc123; Path=/; HttpOnly' },
        })
      )
      expect(s.cookieEntries.value).toEqual([
        { name: 'session', value: 'abc123', attributes: ['Path=/', 'HttpOnly'] },
      ])
    })

    it('解析多个 set-cookie 头', () => {
      const s = create(() =>
        makeResponse({
          responseHeaders: {
            'set-cookie': 'a=1; Path=/',
            'Set-Cookie': 'b=2; Secure',
          },
        })
      )
      expect(s.cookieEntries.value).toHaveLength(2)
    })

    it('跳过无效 cookie 行（无等号）', () => {
      const s = create(() =>
        makeResponse({
          responseHeaders: { 'set-cookie': 'invalid; Path=/' },
        })
      )
      expect(s.cookieEntries.value).toEqual([])
    })
  })

  describe('formatSize', () => {
    it('undefined 返回 —', () => {
      const s = create()
      expect(s.formatSize(undefined)).toBe('—')
    })

    it('小于 1KB 显示 B', () => {
      const s = create()
      expect(s.formatSize(512)).toBe('512 B')
    })

    it('等于 0 显示 0 B', () => {
      const s = create()
      expect(s.formatSize(0)).toBe('0 B')
    })

    it('1KB 以上显示 KB', () => {
      const s = create()
      expect(s.formatSize(2048)).toBe('2.0 KB')
    })

    it('1MB 以上显示 MB', () => {
      const s = create()
      expect(s.formatSize(2 * 1024 * 1024)).toBe('2.00 MB')
    })

    it('小数精度正确', () => {
      const s = create()
      expect(s.formatSize(1536)).toBe('1.5 KB')
    })
  })

  describe('highlightResult', () => {
    it('keyword 为空时返回 null', () => {
      const s = create()
      s.searchKeyword.value = ''
      expect(s.highlightResult.value).toBeNull()
    })

    it('keyword 纯空格时返回 null', () => {
      const s = create()
      s.searchKeyword.value = '   '
      expect(s.highlightResult.value).toBeNull()
    })

    it('bodyText 为空时返回 null', () => {
      const s = create(() => makeResponse({ responseBody: null }))
      s.searchKeyword.value = 'test'
      expect(s.highlightResult.value).toBeNull()
    })

    it('匹配时返回分段和索引', () => {
      const s = create(() => makeResponse({ responseBody: 'hello world' }))
      s.searchKeyword.value = 'world'
      const result = s.highlightResult.value!
      expect(result.segments.length).toBeGreaterThan(0)
      expect(result.indexes.length).toBe(1)
      expect(result.segments[result.indexes[0]].highlight).toBe(true)
      expect(result.segments[result.indexes[0]].text).toBe('world')
    })

    it('大小写不敏感匹配', () => {
      const s = create(() => makeResponse({ responseBody: 'Hello HELLO hello' }))
      s.searchKeyword.value = 'hello'
      expect(s.highlightResult.value!.indexes.length).toBe(3)
    })

    it('无匹配时返回 null', () => {
      const s = create(() => makeResponse({ responseBody: 'hello' }))
      s.searchKeyword.value = 'xyz'
      expect(s.highlightResult.value).toBeNull()
    })

    it('匹配超过 1000 次时截断', () => {
      const text = 'a'.repeat(1001)
      const s = create(() => makeResponse({ responseBody: text }))
      s.searchKeyword.value = 'a'
      expect(s.highlightResult.value!.indexes.length).toBe(1000)
    })
  })

  describe('currentMatchIndex', () => {
    it('无匹配时返回 -1', () => {
      const s = create()
      s.searchKeyword.value = 'xyz'
      expect(s.currentMatchIndex.value).toBe(-1)
    })

    it('有匹配时返回当前高亮段索引', () => {
      const s = create(() => makeResponse({ responseBody: 'abc abc' }))
      s.searchKeyword.value = 'abc'
      expect(s.currentMatchIndex.value).toBe(0)
    })
  })

  describe('searchCountInfo', () => {
    it('无匹配且有关键词时显示 无匹配', () => {
      const s = create(() => makeResponse({ responseBody: 'hello' }))
      s.searchKeyword.value = 'xyz'
      expect(s.searchCountInfo.value).toBe('无匹配')
    })

    it('有匹配时显示 1/N 格式', () => {
      const s = create(() => makeResponse({ responseBody: 'a a a' }))
      s.searchKeyword.value = 'a'
      expect(s.searchCountInfo.value).toBe('1/3')
    })

    it('关键词为空时返回空字符串', () => {
      const s = create()
      expect(s.searchCountInfo.value).toBe('')
    })
  })

  describe('nextMatch / prevMatch', () => {
    it('nextMatch 递增 currentMatch', () => {
      const s = create(() => makeResponse({ responseBody: 'a a a' }))
      s.searchKeyword.value = 'a'
      s.nextMatch()
      expect(s.searchCountInfo.value).toBe('2/3')
    })

    it('nextMatch 到末尾时循环到开头', () => {
      const s = create(() => makeResponse({ responseBody: 'a a' }))
      s.searchKeyword.value = 'a'
      s.nextMatch()
      s.nextMatch()
      expect(s.searchCountInfo.value).toBe('1/2')
    })

    it('prevMatch 递减 currentMatch', () => {
      const s = create(() => makeResponse({ responseBody: 'a a a' }))
      s.searchKeyword.value = 'a'
      s.nextMatch()
      s.prevMatch()
      expect(s.searchCountInfo.value).toBe('1/3')
    })

    it('prevMatch 从开头循环到末尾', () => {
      const s = create(() => makeResponse({ responseBody: 'a a' }))
      s.searchKeyword.value = 'a'
      s.prevMatch()
      expect(s.searchCountInfo.value).toBe('2/2')
    })

    it('无匹配时不报错', () => {
      const s = create(() => makeResponse({ responseBody: 'hello' }))
      s.searchKeyword.value = 'xyz'
      s.nextMatch()
      s.prevMatch()
      expect(s.currentMatchIndex.value).toBe(-1)
    })
  })

  describe('toggleSearch', () => {
    it('展开搜索面板', () => {
      const s = create()
      s.toggleSearch()
      expect(s.searchExpanded.value).toBe(true)
      expect(s.activeTab.value).toBe('body')
    })

    it('再次调用收起并清空 keyword', () => {
      const s = create()
      s.toggleSearch()
      s.searchKeyword.value = 'test'
      s.toggleSearch()
      expect(s.searchExpanded.value).toBe(false)
      expect(s.searchKeyword.value).toBe('')
    })
  })

  describe('handleCopy', () => {
    it('成功复制时显示成功消息', async () => {
      mocks.clipboardWriteText.mockResolvedValue(undefined)
      const s = create()
      await s.handleCopy()
      expect(mocks.clipboardWriteText).toHaveBeenCalled()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已复制响应体')
      expect(s.copying.value).toBe(true)
    })

    it('复制后 copying 定时恢复为 false', async () => {
      mocks.clipboardWriteText.mockResolvedValue(undefined)
      const s = create()
      await s.handleCopy()
      vi.advanceTimersByTime(1200)
      expect(s.copying.value).toBe(false)
    })

    it('clipboard 写入失败时显示警告', async () => {
      mocks.clipboardWriteText.mockRejectedValue(new Error('no permission'))
      const s = create()
      await s.handleCopy()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('复制失败，请手动选择复制')
    })

    it('bodyText 为空时不调用 clipboard', async () => {
      const s = create(() => makeResponse({ responseBody: null }))
      await s.handleCopy()
      expect(mocks.clipboardWriteText).not.toHaveBeenCalled()
    })
  })

  describe('watch response 重置状态', () => {
    it('response 变化时重置所有搜索和 tab 状态', async () => {
      const resp = ref<ApiDebugExecuteResp | null>(makeResponse())
      const s = create(() => resp.value)
      s.activeTab.value = 'headers'
      s.bodyMode.value = 'raw'
      s.langOverride.value = 'xml'
      s.searchKeyword.value = 'test'
      s.searchExpanded.value = true
      resp.value = makeResponse({ responseStatus: 201 })
      await nextTick()
      expect(s.activeTab.value).toBe('body')
      expect(s.bodyMode.value).toBe('pretty')
      expect(s.langOverride.value).toBeNull()
      expect(s.searchKeyword.value).toBe('')
      expect(s.searchExpanded.value).toBe(false)
    })
  })

  describe('键盘事件', () => {
    it('Ctrl+F 打开搜索面板', () => {
      const s = create()
      const handler = findHandler()!
      expect(handler).toBeDefined()
      handler(new MockKeyboardEvent('keydown', { key: 'f', ctrlKey: true }))
      expect(s.searchExpanded.value).toBe(true)
    })

    it('Cmd+F 打开搜索面板（Mac）', () => {
      const s = create()
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'f', metaKey: true }))
      expect(s.searchExpanded.value).toBe(true)
    })

    it('Ctrl+G 跳到下一个匹配', () => {
      const s = create(() => makeResponse({ responseBody: 'a a a' }))
      s.searchKeyword.value = 'a'
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'g', ctrlKey: true }))
      expect(s.searchCountInfo.value).toBe('2/3')
    })

    it('Ctrl+Shift+G 跳到上一个匹配', () => {
      const s = create(() => makeResponse({ responseBody: 'a a a' }))
      s.searchKeyword.value = 'a'
      s.nextMatch()
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'g', ctrlKey: true, shiftKey: true }))
      expect(s.searchCountInfo.value).toBe('1/3')
    })

    it('Escape 收起搜索面板', () => {
      const s = create()
      s.toggleSearch()
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'Escape' }))
      expect(s.searchExpanded.value).toBe(false)
    })

    it('response 为空时 Ctrl+F 不打开搜索', () => {
      const s = create(() => null)
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'f', ctrlKey: true }))
      expect(s.searchExpanded.value).toBe(false)
    })

    it('无匹配时 Ctrl+G 不报错', () => {
      const s = create(() => makeResponse({ responseBody: 'hello' }))
      s.searchKeyword.value = 'xyz'
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'g', ctrlKey: true }))
      expect(s.currentMatchIndex.value).toBe(-1)
    })

    it('Escape 在搜索未展开时无效果', () => {
      const s = create()
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'Escape' }))
      expect(s.searchExpanded.value).toBe(false)
    })

    it('普通按键不触发搜索', () => {
      const s = create()
      const handler = findHandler()!
      handler(new MockKeyboardEvent('keydown', { key: 'a' }))
      expect(s.searchExpanded.value).toBe(false)
    })
  })

  describe('生命周期', () => {
    it('挂载时注册 keydown 监听', () => {
      create()
      expect(mocks.addEventListener).toHaveBeenCalledWith('keydown', expect.any(Function))
    })

    it('卸载时移除 keydown 监听', () => {
      create()
      expect(mocks.removeEventListener).toHaveBeenCalledWith('keydown', expect.any(Function))
    })
  })
})
