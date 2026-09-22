import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import type {
  ApiEnvironmentListItem,
  DebugTab,
} from '@/types'

const mocks = vi.hoisted(() => ({
  fetchEnvironments: vi.fn<() => Promise<ApiEnvironmentListItem[]>>(),
  setBodyContentTypeHeader: vi.fn(),
  HTTP_METHODS: [
    'GET',
    'POST',
    'PUT',
    'PATCH',
    'DELETE',
    'OPTIONS',
    'HEAD',
    'CONNECT',
  ] as const,
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => {
      cb()
    },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/project/environment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
}))

vi.mock('@/pages/project/api-testing/debug/debugModel', () => ({
  HTTP_METHODS: mocks.HTTP_METHODS,
  setBodyContentTypeHeader: mocks.setBodyContentTypeHeader,
}))

import { useDebugRequestPanel } from './useDebugRequestPanel'

function makeTab(overrides?: Partial<DebugTab>): DebugTab {
  return {
    id: 'tab-1',
    name: 'Test',
    method: 'GET',
    url: '/api/test',
    headers: [],
    params: [],
    bodies: {
      urlencoded: [],
      raw: null,
    },
    bodyType: 'none',
    auth: { type: 'none' },
    responseTimeoutMs: 30000,
    response: null,
    ...overrides,
  }
}

function makeEnv(overrides?: Partial<ApiEnvironmentListItem>): ApiEnvironmentListItem {
  return {
    id: 'env-1',
    name: 'Dev',
    isDefault: false,
    sortOrder: 0,
    httpConfigCount: 0,
    variableCount: 0,
    dataSourceCount: 0,
    processorCount: 0,
    ...overrides,
  }
}

describe('useDebugRequestPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function init(tab?: DebugTab, envId?: { value: string }) {
    const t = reactive(tab ?? makeTab()) as DebugTab
    const env = envId ?? { value: '' }
    return {
      result: useDebugRequestPanel(() => t, env),
      tab: t,
      env,
    }
  }

  describe('导出常量', () => {
    it('BODY_TYPES 包含 none、urlencoded、raw', () => {
      const { result } = init()
      expect(result.BODY_TYPES).toEqual([
        { value: 'none', label: 'none' },
        { value: 'urlencoded', label: 'x-www-form-urlencoded' },
        { value: 'raw', label: 'raw' },
      ])
    })

    it('SUBTYPES 包含五种原始子类型', () => {
      const { result } = init()
      expect(result.SUBTYPES).toEqual(['text', 'json', 'xml', 'html', 'javascript'])
    })

    it('COMMON_HEADERS 包含常见头部', () => {
      const { result } = init()
      expect(result.COMMON_HEADERS).toContain('Authorization')
      expect(result.COMMON_HEADERS).toContain('Content-Type')
      expect(result.COMMON_HEADERS).toContain('Cookie')
    })

    it('HTTP_METHODS 来自 debugModel', () => {
      const { result } = init()
      expect(result.HTTP_METHODS).toBe(mocks.HTTP_METHODS)
    })
  })

  describe('初始状态', () => {
    it('activeParamTab 初始为 params', () => {
      const { result } = init()
      expect(result.activeParamTab.value).toBe('params')
    })

    it('environments 初始为空数组', () => {
      const { result } = init()
      expect(result.environments.value).toEqual([])
    })
  })

  describe('onMounted 环境加载', () => {
    it('成功加载环境列表', async () => {
      const envs = [makeEnv({ id: 'e1', isDefault: true }), makeEnv({ id: 'e2' })]
      mocks.fetchEnvironments.mockResolvedValue(envs)
      const { env } = init()
      await vi.dynamicImportSettled()
      expect(mocks.fetchEnvironments).toHaveBeenCalled()
      expect(env.value).toBe('e1')
    })

    it('无默认环境时选取第一个', async () => {
      mocks.fetchEnvironments.mockResolvedValue([makeEnv({ id: 'e2' })])
      const { env } = init()
      await vi.dynamicImportSettled()
      expect(env.value).toBe('e2')
    })

    it('空环境列表时 envId 为空字符串', async () => {
      mocks.fetchEnvironments.mockResolvedValue([])
      const { env } = init()
      await vi.dynamicImportSettled()
      expect(env.value).toBe('')
    })

    it('加载失败时静默处理', async () => {
      mocks.fetchEnvironments.mockRejectedValue(new Error('网络错误'))
      const { env } = init()
      await vi.dynamicImportSettled()
      expect(env.value).toBe('')
      expect(mocks.ElMessage.warning).not.toHaveBeenCalled()
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })
  })

  describe('pickBodyType', () => {
    it('设置 bodyType 并调用 setBodyContentTypeHeader', () => {
      const { result, tab } = init()
      result.pickBodyType('urlencoded')
      expect(tab.bodyType).toBe('urlencoded')
      expect(mocks.setBodyContentTypeHeader).toHaveBeenCalledWith(
        tab,
        'urlencoded',
        undefined,
      )
    })

    it('切换到 raw 且 bodies.raw 为 null 时创建默认 raw', () => {
      const { result, tab } = init()
      result.pickBodyType('raw')
      expect(tab.bodyType).toBe('raw')
      expect(tab.bodies.raw).toEqual({ text: '', subtype: 'json' })
    })

    it('切换到 raw 且已有 bodies.raw 时保留原有数据', () => {
      const { result, tab } = init(makeTab({ bodies: { urlencoded: [], raw: { text: 'existing', subtype: 'xml' } } }))
      result.pickBodyType('raw')
      expect(tab.bodies.raw).toEqual({ text: 'existing', subtype: 'xml' })
    })

    it('切换到 none', () => {
      const { result, tab } = init()
      result.pickBodyType('none')
      expect(tab.bodyType).toBe('none')
      expect(mocks.setBodyContentTypeHeader).toHaveBeenCalledWith(
        tab,
        'none',
        undefined,
      )
    })

    it('从 raw 切换到其他类型时传递 raw subtype', () => {
      const { result, tab } = init(makeTab({
        bodyType: 'raw',
        bodies: { urlencoded: [], raw: { text: '', subtype: 'xml' } },
      }))
      result.pickBodyType('urlencoded')
      expect(mocks.setBodyContentTypeHeader).toHaveBeenCalledWith(
        tab,
        'urlencoded',
        'xml',
      )
    })
  })

  describe('rawSubtype', () => {
    it('get 返回 tab 中 raw subtype', () => {
      const { result } = init(makeTab({ bodies: { urlencoded: [], raw: { text: '', subtype: 'xml' } } }))
      expect(result.rawSubtype.value).toBe('xml')
    })

    it('get 在 raw 为 null 时返回 json', () => {
      const { result } = init()
      expect(result.rawSubtype.value).toBe('json')
    })

    it('set 更新 raw subtype', () => {
      const { result, tab } = init(makeTab({ bodyType: 'raw', bodies: { urlencoded: [], raw: { text: '', subtype: 'json' } } }))
      result.rawSubtype.value = 'html'
      expect(tab.bodies.raw?.subtype).toBe('html')
      expect(mocks.setBodyContentTypeHeader).toHaveBeenCalledWith(tab, 'raw', 'html')
    })

    it('set 在 raw 为 null 时创建 raw 对象', () => {
      const { result, tab } = init(makeTab({ bodyType: 'raw' }))
      result.rawSubtype.value = 'xml'
      expect(tab.bodies.raw).toEqual({ text: '', subtype: 'xml' })
    })

    it('set 在 bodyType 不是 raw 时不调用 setBodyContentTypeHeader', () => {
      const { result, tab } = init(makeTab({ bodyType: 'none', bodies: { urlencoded: [], raw: { text: '', subtype: 'json' } } }))
      mocks.setBodyContentTypeHeader.mockClear()
      result.rawSubtype.value = 'text'
      expect(tab.bodies.raw?.subtype).toBe('text')
      expect(mocks.setBodyContentTypeHeader).not.toHaveBeenCalled()
    })
  })

  describe('rawText', () => {
    it('get 返回 tab 中 raw text', () => {
      const { result } = init(makeTab({ bodies: { urlencoded: [], raw: { text: 'hello', subtype: 'json' } } }))
      expect(result.rawText.value).toBe('hello')
    })

    it('get 在 raw 为 null 时返回空字符串', () => {
      const { result } = init()
      expect(result.rawText.value).toBe('')
    })

    it('set 更新 raw text', () => {
      const { result, tab } = init(makeTab({ bodies: { urlencoded: [], raw: { text: '', subtype: 'json' } } }))
      result.rawText.value = '{"a":1}'
      expect(tab.bodies.raw?.text).toBe('{"a":1}')
    })

    it('set 在 raw 为 null 时创建 raw 对象', () => {
      const { result, tab } = init()
      result.rawText.value = 'test'
      expect(tab.bodies.raw).toEqual({ text: 'test', subtype: 'json' })
    })
  })

  describe('formatJsonBody', () => {
    it('合法 JSON 时格式化并写回', () => {
      const { result } = init(makeTab({ bodies: { urlencoded: [], raw: { text: '{"a":1}', subtype: 'json' } } }))
      result.formatJsonBody()
      expect(result.rawText.value).toBe(JSON.stringify({ a: 1 }, null, 2))
    })

    it('不合法 JSON 时显示警告', () => {
      const { result } = init(makeTab({ bodies: { urlencoded: [], raw: { text: 'not json', subtype: 'json' } } }))
      result.formatJsonBody()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请求体不是合法 JSON，无法格式化')
      expect(result.rawText.value).toBe('not json')
    })

    it('空字符串时显示警告', () => {
      const { result } = init(makeTab({ bodies: { urlencoded: [], raw: { text: '', subtype: 'json' } } }))
      result.formatJsonBody()
      expect(mocks.ElMessage.warning).toHaveBeenCalled()
    })

    it('格式化复杂嵌套 JSON', () => {
      const obj = { a: [1, 2], b: { c: true } }
      const { result } = init(makeTab({ bodies: { urlencoded: [], raw: { text: JSON.stringify(obj), subtype: 'json' } } }))
      result.formatJsonBody()
      expect(result.rawText.value).toBe(JSON.stringify(obj, null, 2))
    })
  })

  describe('methodColor', () => {
    it('GET 返回 #61affe', () => {
      const { result } = init(makeTab({ method: 'GET' }))
      expect(result.methodColor.value).toBe('#61affe')
    })

    it('POST 返回 #49cc90', () => {
      const { result } = init(makeTab({ method: 'POST' }))
      expect(result.methodColor.value).toBe('#49cc90')
    })

    it('PUT 返回 #fca130', () => {
      const { result } = init(makeTab({ method: 'PUT' }))
      expect(result.methodColor.value).toBe('#fca130')
    })

    it('PATCH 返回 #50e3c2', () => {
      const { result } = init(makeTab({ method: 'PATCH' }))
      expect(result.methodColor.value).toBe('#50e3c2')
    })

    it('DELETE 返回 #f93e3e', () => {
      const { result } = init(makeTab({ method: 'DELETE' }))
      expect(result.methodColor.value).toBe('#f93e3e')
    })

    it('OPTIONS 返回 #0d5aa7', () => {
      const { result } = init(makeTab({ method: 'OPTIONS' }))
      expect(result.methodColor.value).toBe('#0d5aa7')
    })

    it('HEAD 返回 #9012fe', () => {
      const { result } = init(makeTab({ method: 'HEAD' }))
      expect(result.methodColor.value).toBe('#9012fe')
    })

    it('CONNECT 返回 #e8d44d', () => {
      const { result } = init(makeTab({ method: 'CONNECT' }))
      expect(result.methodColor.value).toBe('#e8d44d')
    })

    it('未知方法返回 #999', () => {
      const { result } = init(makeTab({ method: 'CUSTOM' }))
      expect(result.methodColor.value).toBe('#999')
    })

    it('小写 method 正确匹配', () => {
      const { result } = init(makeTab({ method: 'get' }))
      expect(result.methodColor.value).toBe('#61affe')
    })
  })

  describe('activeParamTab 切换', () => {
    it('可切换到 auth', () => {
      const { result } = init()
      result.activeParamTab.value = 'auth'
      expect(result.activeParamTab.value).toBe('auth')
    })

    it('可切换到 headers', () => {
      const { result } = init()
      result.activeParamTab.value = 'headers'
      expect(result.activeParamTab.value).toBe('headers')
    })

    it('可切换到 body', () => {
      const { result } = init()
      result.activeParamTab.value = 'body'
      expect(result.activeParamTab.value).toBe('body')
    })
  })
})
