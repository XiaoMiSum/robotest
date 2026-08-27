import { describe, expect, it } from 'vitest'
import type { ApiMockMatchRuleType } from '@/types'
import {
  BODY_TYPES,
  HTTP_METHODS,
  MATCH_RULE_TYPES,
  createEditorForm,
  createEmptyMatchRule,
  detailToForm,
  formToPayload,
  methodTagType,
} from './mocksModel'

describe('methodTagType', () => {
  it('映射语义色并兜底 info', () => {
    expect(methodTagType('GET')).toBe('success')
    expect(methodTagType('POST')).toBe('primary')
    expect(methodTagType('PUT')).toBe('warning')
    expect(methodTagType('PATCH')).toBe('warning')
    expect(methodTagType('DELETE')).toBe('danger')
    expect(methodTagType('HEAD')).toBe('info')
    expect(methodTagType('OPTIONS')).toBe('info')
    expect(methodTagType('CUSTOM')).toBe('info')
  })

  it('大小写不敏感', () => {
    expect(methodTagType('get')).toBe('success')
    expect(methodTagType('delete')).toBe('danger')
  })
})

describe('MATCH_RULE_TYPES / BODY_TYPES / HTTP_METHODS', () => {
  it('匹配规则包含 header/param/body 三种', () => {
    const values = MATCH_RULE_TYPES.map((t) => t.value)
    expect(values).toEqual(['header', 'param', 'body'])
  })

  it('响应体类型包含 json/text/xml/binary', () => {
    expect(BODY_TYPES.map((t) => t.value)).toEqual(['json', 'text', 'xml', 'binary'])
  })

  it('HTTP_METHODS 包含 7 种方法', () => {
    expect(HTTP_METHODS).toHaveLength(7)
    expect(HTTP_METHODS).toContain('GET')
    expect(HTTP_METHODS).toContain('DELETE')
  })
})

describe('createEmptyMatchRule', () => {
  it('返回空的 header 类型规则', () => {
    const rule = createEmptyMatchRule()
    expect(rule).toEqual({ type: 'header', name: '', value: '' })
  })
})

describe('createEditorForm', () => {
  it('新建默认表单：GET /、启用、不跟随 API', () => {
    const form = createEditorForm()
    expect(form).toMatchObject({
      name: '',
      method: 'GET',
      path: '/',
      enabled: true,
      followApi: false,
      responseStatus: 200,
      responseBodyType: 'json',
      responseBody: '',
      delayMs: 0,
    })
  })

  it('默认包含一行空匹配规则和一行 Content-Type 响应头', () => {
    const form = createEditorForm()
    expect(form.matchRules).toHaveLength(1)
    expect(form.matchRules[0]).toEqual({ type: 'header', name: '', value: '' })
    expect(form.responseHeaders).toHaveLength(1)
    expect(form.responseHeaders[0]).toEqual({ key: 'Content-Type', value: 'application/json' })
  })
})

describe('detailToForm', () => {
  const detail = {
    name: '登录成功',
    description: '返回 token',
    method: 'POST',
    path: '/api/login',
    enabled: false,
    followApi: true,
    matchRules: [
      { type: 'header' as ApiMockMatchRuleType, name: 'Authorization', value: 'Bearer.*' },
      { type: 'body' as ApiMockMatchRuleType, name: '$.username', value: 'admin' },
    ],
    responseStatus: 201,
    responseHeaders: { 'Content-Type': 'application/json', 'X-Custom': 'test' },
    responseBodyType: 'json' as const,
    responseBody: '{"token":"abc"}',
    delayMs: 500,
    groupSize: 3,
  }

  it('还原所有字段到表单', () => {
    const form = detailToForm(detail)
    expect(form.name).toBe('登录成功')
    expect(form.description).toBe('返回 token')
    expect(form.method).toBe('POST')
    expect(form.path).toBe('/api/login')
    expect(form.enabled).toBe(false)
    expect(form.followApi).toBe(true)
    expect(form.responseStatus).toBe(201)
    expect(form.delayMs).toBe(500)
    expect(form.responseBody).toBe('{"token":"abc"}')
  })

  it('响应头从 Record 还原为 key-value 数组', () => {
    const form = detailToForm(detail)
    expect(form.responseHeaders).toEqual([
      { key: 'Content-Type', value: 'application/json' },
      { key: 'X-Custom', value: 'test' },
    ])
  })

  it('匹配规则被深拷贝', () => {
    const form = detailToForm(detail)
    expect(form.matchRules).toHaveLength(2)
    expect(form.matchRules[0]).toEqual(detail.matchRules[0])
    form.matchRules[0].name = 'mutated'
    expect(detail.matchRules[0].name).toBe('Authorization')
  })

  it('null 响应头兜底为默认 Content-Type', () => {
    const form = detailToForm({ ...detail, responseHeaders: null })
    expect(form.responseHeaders).toEqual([{ key: 'Content-Type', value: 'application/json' }])
  })

  it('空匹配规则数组兜底为一行空规则', () => {
    const form = detailToForm({ ...detail, matchRules: [] })
    expect(form.matchRules).toHaveLength(1)
    expect(form.matchRules[0]).toEqual(createEmptyMatchRule())
  })

  it('null description/responseBody 兜底为空字符串', () => {
    const form = detailToForm({ ...detail, description: null, responseBody: null })
    expect(form.description).toBe('')
    expect(form.responseBody).toBe('')
  })
})

describe('formToPayload', () => {
  it('trim 名称和路径', () => {
    const form = createEditorForm()
    form.name = '  mock  '
    form.path = '  /api/test  '
    const payload = formToPayload(form)
    expect(payload.name).toBe('mock')
    expect(payload.path).toBe('/api/test')
  })

  it('空描述转为 null', () => {
    const form = createEditorForm()
    form.description = '   '
    expect(formToPayload(form).description).toBeNull()
  })

  it('过滤空名称的匹配规则', () => {
    const form = createEditorForm()
    form.matchRules = [
      { type: 'header', name: 'Authorization', value: '.*' },
      { type: 'header', name: '', value: '' },
      { type: 'param', name: 'token', value: 'abc' },
    ]
    const payload = formToPayload(form)
    expect(payload.matchRules).toHaveLength(2)
    expect(payload.matchRules![0].name).toBe('Authorization')
    expect(payload.matchRules![1].name).toBe('token')
  })

  it('响应头转为 Record 并过滤空键', () => {
    const form = createEditorForm()
    form.responseHeaders = [
      { key: 'Content-Type', value: 'text/plain' },
      { key: '', value: 'ignored' },
      { key: 'X-Request-Id', value: '123' },
    ]
    const payload = formToPayload(form)
    expect(payload.responseHeaders).toEqual({
      'Content-Type': 'text/plain',
      'X-Request-Id': '123',
    })
  })

  it('空 responseBody 转为 null', () => {
    const form = createEditorForm()
    form.responseBody = ''
    expect(formToPayload(form).responseBody).toBeNull()
  })

  it('保留 enabled/followApi/responseStatus/delayMs', () => {
    const form = createEditorForm()
    form.enabled = false
    form.followApi = true
    form.responseStatus = 404
    form.delayMs = 1000
    const payload = formToPayload(form)
    expect(payload.enabled).toBe(false)
    expect(payload.followApi).toBe(true)
    expect(payload.responseStatus).toBe(404)
    expect(payload.delayMs).toBe(1000)
  })

  it('responseBodyType 保持原始值', () => {
    const form = createEditorForm()
    form.responseBodyType = 'xml'
    expect(formToPayload(form).responseBodyType).toBe('xml')
  })
})
