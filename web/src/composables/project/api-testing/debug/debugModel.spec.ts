import { describe, expect, it } from 'vitest'
import {
  applyCurlToTab,
  autoTabName,
  buildAuthHeader,
  buildExecutePayload,
  buildRequestSnapshot,
  createTab,
  ensureUrlScheme,
  markExecuted,
  MAX_DEBUG_TABS,
  setBodyContentTypeHeader,
  tabFromRestore,
  tabTitle,
} from './debugModel'
import type { ApiDebugRestoreResp } from '@/types'

describe('debugModel', () => {
  it('createTab 返回空白标签且四态 body 独立缓存', () => {
    const tab = createTab()
    expect(tab.method).toBe('GET')
    expect(tab.name).toBe('')
    expect(tab.bodyType).toBe('none')
    expect(Object.keys(tab.bodies)).toEqual(['urlencoded', 'raw'])
  })

  it('autoTabName 提取路径末段并去除查询串', () => {
    expect(autoTabName('post', 'https://api.example.com/auth/login?next=x')).toBe('POST /auth/login')
    expect(autoTabName('GET', '/users?page=1')).toBe('GET /users')
  })

  it('tabTitle 未命名时显示占位名', () => {
    const tab = createTab()
    expect(tabTitle(tab)).toBe('新建请求')
    tab.name = 'GET /a'
    expect(tabTitle(tab)).toBe('GET /a')
  })

  it('buildExecutePayload 过滤禁用项/空键并剥离 description', () => {
    const tab = createTab()
    tab.method = 'POST'
    tab.url = '/login'
    tab.headers = [
      { key: 'X-On', value: '1', enabled: true, description: '保留此行但提交剥离' },
      { key: 'X-Off', value: '2', enabled: false },
      { key: '', value: '3', enabled: true },
    ]
    tab.bodyType = 'urlencoded'
    tab.bodies.urlencoded = [{ key: 'a', value: '1', enabled: true }]
    const payload = buildExecutePayload(tab)
    expect(payload.headers).toEqual([
      { key: 'X-On', value: '1', enabled: true },
      { key: 'Content-Type', value: 'application/x-www-form-urlencoded', enabled: true },
    ])
    expect(payload.body).toEqual({ type: 'form', content: [{ key: 'a', value: '1', enabled: true }] })
  })

  it('x-www-form-urlencoded 提交原始 key-value 列表（禁用行剔除，键值原样保留）', () => {
    const tab = createTab()
    tab.bodyType = 'urlencoded'
    tab.bodies.urlencoded = [
      { key: 'name', value: '张三', enabled: true },
      { key: 'todo', value: 'off', enabled: false },
      { key: 'next', value: 'a=1&b=2', enabled: true },
    ]
    expect(buildExecutePayload(tab).body).toEqual({
      type: 'form',
      content: [
        { key: 'name', value: '张三', enabled: true },
        { key: 'next', value: 'a=1&b=2', enabled: true },
      ],
    })
  })

  it('切换 body 类型时 Content-Type 头随动并置于第 0 位（覆盖已有 Content-Type）', () => {
    const tab = createTab()
    tab.headers = [{ key: 'Accept', value: 'application/json', enabled: true }]
    setBodyContentTypeHeader(tab, 'urlencoded')
    expect(tab.headers[0]).toEqual({ key: 'Content-Type', value: 'application/x-www-form-urlencoded', enabled: true })
    expect(tab.headers.map((h) => h.key)).toEqual(['Content-Type', 'Accept'])

    setBodyContentTypeHeader(tab, 'raw', 'json')
    expect(tab.headers[0]).toEqual({ key: 'Content-Type', value: 'application/json', enabled: true })

    setBodyContentTypeHeader(tab, 'raw', 'text')
    expect(tab.headers.some((h) => h.key === 'Content-Type')).toBe(false)

    setBodyContentTypeHeader(tab, 'none')
    expect(tab.headers.some((h) => h.key === 'Content-Type')).toBe(false)
  })

  it('raw 子类型 json 自动注入 application/json，text 不注入', () => {
    const tab = createTab()
    tab.bodyType = 'raw'
    tab.bodies.raw = { text: '{"k":1}', subtype: 'json' }
    const payload = buildExecutePayload(tab)
    expect(payload.body).toEqual({ type: 'raw', content: '{"k":1}' })
    expect(payload.headers?.some((h) => h.key === 'Content-Type' && h.value === 'application/json')).toBe(true)
  })

  it('手工 Content-Type 优先于自动注入', () => {
    const tab = createTab()
    tab.bodyType = 'raw'
    tab.bodies.raw = { text: '<a/>', subtype: 'xml' }
    tab.headers = [{ key: 'Content-Type', value: 'text/custom', enabled: true }]
    const contentType = buildExecutePayload(tab).headers?.filter((h) => h.key === 'Content-Type')
    expect(contentType).toEqual([{ key: 'Content-Type', value: 'text/custom', enabled: true }])
  })

  it('Basic/Bearer/API Key 认证换算，且不重复 Authorization', () => {
    expect(buildAuthHeader({ type: 'basic', username: 'u', password: 'p' })?.value).toBe(`Basic ${btoa('u:p')}`)
    expect(buildAuthHeader({ type: 'bearer', token: 'tk' })?.value).toBe('Bearer tk')
    expect(buildAuthHeader({ type: 'apiKey', apiKeyValue: 'v' })?.value).toBe('v')
    expect(buildAuthHeader({ type: 'apiKey', apiKeyName: 'X-Token', apiKeyValue: 'v' })?.key).toBe('X-Token')

    const tab = createTab()
    tab.auth = { type: 'basic', username: 'u', password: 'p' }
    tab.headers = [{ key: 'Authorization', value: 'Bearer manual', enabled: true }]
    const payload = buildExecutePayload(tab)
    expect(payload.headers?.some((h) => h.value === 'Bearer manual')).toBe(true)
    expect(payload.headers?.filter((h) => h.key === 'Authorization')).toHaveLength(1)

    const apiTab = createTab()
    apiTab.auth = { type: 'apiKey', apiKeyName: 'X-Token', apiKeyValue: 'secret' }
    expect(buildExecutePayload(apiTab).headers?.some((h) => h.key === 'X-Token' && h.value === 'secret')).toBe(true)
  })

  it('markExecuted 首次执行自动命名', () => {
    const tab = createTab()
    tab.url = '/api/users'
    markExecuted(tab, { debugRecordId: 'r1', status: 'success', responseStatus: 200 })
    expect(tab.name).toBe('GET /api/users')

    // 已命名标签执行后不覆盖用户命名
    tab.method = 'PUT'
    markExecuted(tab, { debugRecordId: 'r2', status: 'success' })
    expect(tab.name).toBe('GET /api/users')
  })

  it('cURL 导入回填请求体并保留各类型内容缓存', () => {
    const tab = createTab()
    tab.bodies.urlencoded = [{ key: 'keep', value: 'me', enabled: true }]
    applyCurlToTab(tab, {
      method: 'POST',
      url: 'https://x.example.com/a/b',
      headers: [{ key: 'A', value: 'b', enabled: true }],
      body: { type: 'json', content: { k: 1 } },
    })
    expect(tab.url).toBe('https://x.example.com/a/b')
    expect(tab.bodyType).toBe('raw')
    expect(tab.bodies.raw?.subtype).toBe('json')
    expect(tab.bodies.raw && JSON.parse(tab.bodies.raw.text)).toEqual({ k: 1 })
    expect(tab.bodies.urlencoded).toEqual([{ key: 'keep', value: 'me', enabled: true }])
  })

  it('历史恢复生成新标签并回填响应', () => {
    const record: ApiDebugRestoreResp = {
      debugRecordId: 'r9',
      request: {
        method: 'POST',
        url: '/auth/login',
        headers: [{ key: 'Authorization', value: 'Basic abc', enabled: true }],
        body: { type: 'json', content: { u: 1 } },
        params: [],
      },
      response: { statusCode: 200, body: { token: 't' }, elapsed: 88, size: 20 },
      createdAt: '2026-08-17T10:30:00Z',
    }
    const tab = tabFromRestore(record)
    expect(tab.method).toBe('POST')
    expect(tab.name).toBe('POST /auth/login')
    expect(tab.response?.responseStatus).toBe(200)
    expect(tab.bodyType).toBe('raw')
    expect(tab.bodies.raw?.subtype).toBe('json')
    expect(tab.bodies.raw && JSON.parse(tab.bodies.raw.text)).toEqual({ u: 1 })
  })

  it('历史恢复回填 form 原始 key-value 列表 body', () => {
    const record: ApiDebugRestoreResp = {
      debugRecordId: 'r10',
      request: {
        method: 'POST',
        url: '/auth/login',
        headers: [],
        body: {
          type: 'form',
          content: [
            { key: 'name', value: '张三', enabled: true },
            { key: 'next', value: 'a=1&b=2', enabled: true },
          ],
        },
        params: [],
      },
      response: { statusCode: 200, body: {}, elapsed: 10, size: 2 },
      createdAt: '2026-08-17T10:30:00Z',
    }
    const tab = tabFromRestore(record)
    expect(tab.bodyType).toBe('urlencoded')
    expect(tab.bodies.urlencoded).toEqual([
      { key: 'name', value: '张三', enabled: true },
      { key: 'next', value: 'a=1&b=2', enabled: true },
    ])
  })

  it('MAX_DEBUG_TABS 约束为 10', () => {
    expect(MAX_DEBUG_TABS).toBe(10)
  })

  it('ensureUrlScheme 完整 URL/相对路径不变，其余补 http://', () => {
    expect(ensureUrlScheme('https://a.com/x')).toBe('https://a.com/x')
    expect(ensureUrlScheme('http://a.com')).toBe('http://a.com')
    expect(ensureUrlScheme('/api/users')).toBe('/api/users')
    expect(ensureUrlScheme('api.example.com/users')).toBe('http://api.example.com/users')
    expect(ensureUrlScheme('localhost:8080/ping')).toBe('http://localhost:8080/ping')
    expect(ensureUrlScheme('  ')).toBe('')
  })

  it('buildRequestSnapshot 构建不含 timeout/environmentId 的请求快照', () => {
    const tab = createTab()
    tab.method = 'POST'
    tab.url = 'https://api.example.com/login'
    tab.headers = [{ key: 'Accept', value: '*/*', enabled: true }]
    tab.params = [{ key: 'src', value: 'curl', enabled: true }]
    tab.auth = { type: 'bearer', token: 'tk123' }
    tab.bodyType = 'urlencoded'
    tab.bodies.urlencoded = [{ key: 'user', value: 'admin', enabled: true }]
    const snapshot = buildRequestSnapshot(tab)
    expect(snapshot).toBeDefined()
    expect(snapshot!.method).toBe('POST')
    expect(snapshot!.url).toBe('https://api.example.com/login')
    // auth header 和 Content-Type 均注入到 headers
    expect(snapshot!.headers!.some((h) => h.key === 'Authorization' && h.value === 'Bearer tk123')).toBe(true)
    expect(snapshot!.headers!.some((h) => h.key === 'Content-Type' && h.value === 'application/x-www-form-urlencoded')).toBe(true)
    // body 为原始 KV 列表
    expect(snapshot!.body).toEqual({ type: 'form', content: [{ key: 'user', value: 'admin', enabled: true }] })
    // 不含 timeout/environmentId
    expect(snapshot).not.toHaveProperty('timeoutMs')
  })
})