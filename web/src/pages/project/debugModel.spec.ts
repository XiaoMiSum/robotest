import { describe, expect, it } from 'vitest'
import {
  applyCurlToTab,
  autoTabName,
  buildAuthHeader,
  buildExecutePayload,
  createTab,
  markExecuted,
  MAX_DEBUG_TABS,
  tabFromRestore,
  tabTitle,
} from './debugModel'
import type { ApiDebugRestoreResp } from '@/types'

describe('debugModel', () => {
  it('createTab 返回空白标签且 bodies 四类型独立缓存', () => {
    const tab = createTab()
    expect(tab.method).toBe('GET')
    expect(tab.name).toBe('')
    expect(Object.keys(tab.bodies)).toEqual(['none', 'json', 'form', 'raw'])
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

  it('buildExecutePayload 过滤禁用项与空键', () => {
    const tab = createTab()
    tab.method = 'POST'
    tab.url = '/login'
    tab.headers = [
      { key: 'X-On', value: '1', enabled: true },
      { key: 'X-Off', value: '2', enabled: false },
      { key: '', value: '3', enabled: true },
    ]
    tab.bodies.json = '{"a":1}'
    const payload = buildExecutePayload(tab)
    expect(payload.headers).toEqual([{ key: 'X-On', value: '1', enabled: true }])
    expect(payload.body).toEqual({ type: 'json', content: { a: 1 } })
  })

  it('认证配置换算为 Authorization 头且不覆盖手工头', () => {
    const auth = { type: 'basic' as const, username: 'u', password: 'p' }
    expect(buildAuthHeader(auth)?.value).toBe(`Basic ${btoa('u:p')}`)

    const tab = createTab()
    tab.auth = auth
    tab.headers = [{ key: 'Authorization', value: 'Bearer manual', enabled: true }]
    const payload = buildExecutePayload(tab)
    expect(payload.headers?.some((h) => h.value === 'Bearer manual')).toBe(true)
    expect(payload.headers?.filter((h) => h.key === 'Authorization')).toHaveLength(1)
  })

  it('非法 JSON 文本降级为 raw 提交', () => {
    const tab = createTab()
    tab.bodies.json = 'not-json{'
    const payload = buildExecutePayload(tab)
    expect(payload.body).toEqual({ type: 'json', content: 'not-json{' })
  })

  it('markExecuted 首次执行自动命名并清除脏标记', () => {
    const tab = createTab()
    tab.url = '/api/users'
    tab.dirty = true
    markExecuted(tab, { debugRecordId: 'r1', status: 'success', responseStatus: 200 })
    expect(tab.name).toBe('GET /api/users')
    expect(tab.dirty).toBe(false)

    // 已命名标签执行后不覆盖用户命名
    tab.method = 'PUT'
    markExecuted(tab, { debugRecordId: 'r2', status: 'success' })
    expect(tab.name).toBe('GET /api/users')
  })

  it('cURL 导入回填请求体并保留各类型内容缓存', () => {
    const tab = createTab()
    tab.bodies.raw = 'keep-me'
    applyCurlToTab(tab, {
      method: 'POST',
      url: 'https://x.example.com/a/b',
      headers: [{ key: 'A', value: 'b', enabled: true }],
      body: { type: 'json', content: { k: 1 } },
    })
    expect(tab.url).toBe('https://x.example.com/a/b')
    expect(JSON.parse(tab.bodies.json)).toEqual({ k: 1 })
    expect(tab.bodies.raw).toBe('keep-me')
    expect(tab.dirty).toBe(true)
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
    expect(JSON.parse(tab.bodies.json)).toEqual({ u: 1 })
  })

  it('MAX_DEBUG_TABS 约束为 10', () => {
    expect(MAX_DEBUG_TABS).toBe(10)
  })
})
