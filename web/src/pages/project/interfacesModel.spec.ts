import { describe, expect, it } from 'vitest'
import type { ProjectModule } from '@/types'
import {
  buildInterfaceListQuery,
  createEditorForm,
  flattenModuleNames,
  methodTagType,
  summarizeImportResult,
  toCreatePayload,
  toSelectableModuleOptions,
  toUpdatePayload,
} from './interfacesModel'

const moduleTree: ProjectModule[] = [
  {
    id: 'dir-1',
    parentId: null,
    type: 'directory',
    name: '电商',
    sortOrder: 0,
    children: [
      { id: 'dir-1-1', parentId: 'dir-1', type: 'directory', name: '交易', sortOrder: 0, children: [] },
      { id: 'doc-1', parentId: 'dir-1', type: 'document', name: '说明文档', sortOrder: 1, children: [] },
    ],
  },
]

describe('buildInterfaceListQuery', () => {
  it('省略空过滤条件且 all 视图不发送', () => {
    expect(buildInterfaceListQuery({ pageNo: 2, pageSize: 20 })).toEqual({ pageNo: 2, pageSize: 20 })
  })

  it('保留非空条件与显式视图', () => {
    const query = buildInterfaceListQuery({
      pageNo: 1,
      pageSize: 10,
      moduleId: 'm1',
      search: '登录',
      status: 'enabled',
      view: 'followed',
    })
    expect(query).toEqual({ pageNo: 1, pageSize: 10, moduleId: 'm1', search: '登录', status: 'enabled', view: 'followed' })
  })
})

describe('methodTagType', () => {
  it('映射语义色并兜底 info', () => {
    expect(methodTagType('GET')).toBe('success')
    expect(methodTagType('delete')).toBe('danger')
    expect(methodTagType('CUSTOM')).toBe('info')
  })
})

describe('toSelectableModuleOptions / flattenModuleNames', () => {
  it('过滤文档节点保留目录层级', () => {
    const options = toSelectableModuleOptions(moduleTree)
    expect(options).toHaveLength(1)
    expect(options[0].children?.map((node) => node.label)).toEqual(['交易'])
  })

  it('展平包含目录与文档', () => {
    const names = flattenModuleNames(moduleTree)
    expect(names.get('dir-1')).toBe('电商')
    expect(names.get('doc-1')).toBe('说明文档')
  })
})

describe('editor form conversion', () => {
  it('新建默认表单：GET /、none 请求体、空键值行', () => {
    const form = createEditorForm()
    expect(form).toMatchObject({ method: 'GET', path: '/', bodyType: 'none', status: 'enabled' })
    expect(form.headers).toHaveLength(1)
  })

  it('详情回显还原请求体与键值行（json→raw tab），剔除空键行', () => {
    const form = createEditorForm({
      id: 'i1',
      name: '登录',
      protocol: 'http',
      method: 'POST',
      path: '/login',
      description: null,
      moduleId: null,
      headers: [{ key: 'X-Tag', value: 'v', enabled: true }],
      body: { type: 'json', content: { u: 1 } },
      params: [{ key: '', value: '', enabled: true }],
      restParams: [],
      auth: null,
      status: 'enabled',
      changeVersion: 3,
      responseExample: { status: 200 },
      referenceCount: 0,
      followed: false,
      createdAt: '',
      updatedAt: '',
    })
    expect(form.bodyType).toBe('raw')
    expect(form.rawSubtype).toBe('json')
    expect(form.rawText).toBe(JSON.stringify({ u: 1 }, null, 2))
    expect(form.params).toHaveLength(1)
    const { req, error } = toUpdatePayload(form, 3)
    expect(error).toBeUndefined()
    expect(req.changeVersion).toBe(3)
    expect(req.body).toEqual({ type: 'json', content: { u: 1 } })
    expect(req.headers).toEqual([{ key: 'X-Tag', value: 'v', enabled: true }])
  })

  it('非法 JSON 请求体返回错误而不抛出', () => {
    const form = createEditorForm()
    form.bodyType = 'raw'
    form.rawSubtype = 'json'
    form.rawText = '{broken'
    const { error } = toCreatePayload(form)
    expect(error).toContain('JSON')
  })

  it('urlencoded 请求体序列化为键值数组并剔除禁用与空键行', () => {
    const form = createEditorForm()
    form.bodyType = 'urlencoded'
    form.urlencodedRows = [
      { key: 'u', value: '1', enabled: true },
      { key: 'skip', value: 'x', enabled: false },
      { key: '', value: 'y', enabled: true },
    ]
    const { req } = toCreatePayload(form)
    expect(req.body).toEqual({ type: 'form', content: [{ key: 'u', value: '1', enabled: true }] })
  })

  it('raw 非 json 子类型保留原始文本直传', () => {
    const form = createEditorForm()
    form.bodyType = 'raw'
    form.rawSubtype = 'text'
    form.rawText = 'hello <xml/>'
    const { req } = toCreatePayload(form)
    expect(req.body).toEqual({ type: 'raw', content: 'hello <xml/>' })
  })

  it('回显仅定义存储的验证器/提取器', () => {    const form = createEditorForm({
      id: 'i2',
      name: '含配置',
      protocol: 'http',
      method: 'GET',
      path: '/c',
      description: null,
      moduleId: null,
      headers: [],
      body: null,
      params: [],
      restParams: [],
      auth: null,
      status: 'enabled',
      changeVersion: 1,
      responseExample: null,
      referenceCount: 0,
      followed: false,
      createdAt: '',
      updatedAt: '',
      validators: [{ target: 'status_code', condition: 'equals' }],
      extractors: [{ source: 'json_field', variableName: 'token' }],
    })
    expect(form.validators).toEqual([{ target: 'status_code', condition: 'equals' }])
    const { req } = toCreatePayload(form)
    expect(req.validators).toEqual([{ target: 'status_code', condition: 'equals' }])
    expect(req.extractors).toEqual([{ source: 'json_field', variableName: 'token' }])
  })


  it('回显认证配置（basic/bearer/apiKey）并还原', () => {
    const form = createEditorForm({
      id: 'i3',
      name: '带认证',
      protocol: 'http',
      method: 'GET',
      path: '/secure',
      description: null,
      moduleId: null,
      headers: [],
      body: null,
      params: [],
      restParams: [],
      auth: { type: 'bearer', token: 'abc' },
      status: 'enabled',
      changeVersion: 1,
      responseExample: null,
      referenceCount: 0,
      followed: false,
      createdAt: '',
      updatedAt: '',
    })
    expect(form.auth).toEqual({ type: 'bearer', token: 'abc' })
  })

  it('No Auth 不发送 auth，已配置认证序列化为 auth 列', () => {
    const empty = createEditorForm()
    expect(toCreatePayload(empty).req.auth).toBeUndefined()

    const form = createEditorForm()
    form.auth = { type: 'apiKey', apiKeyName: 'X-Token', apiKeyValue: 'secret' }
    const { req } = toCreatePayload(form)
    expect(req.auth).toEqual({ type: 'apiKey', apiKeyName: 'X-Token', apiKeyValue: 'secret' })
  })

  it('响应示例 body/headers 分别序列化并保留 status', () => {
    const form = createEditorForm({
      id: 'i4', name: 'resp', protocol: 'http', method: 'GET', path: '/r',
      description: null, moduleId: null, headers: [], body: null, params: [],
      restParams: [], auth: null, status: 'enabled', changeVersion: 1,
      responseExample: { status: 201, headers: { 'X-Req': '1' }, body: { ok: true } },
      referenceCount: 0, followed: false, createdAt: '', updatedAt: '',
    })
    expect(form.responseBodyText).toBe(JSON.stringify({ ok: true }, null, 2))
    expect(form.responseHeadersText).toBe(JSON.stringify({ 'X-Req': '1' }, null, 2))
    const { req } = toCreatePayload(form)
    expect(req.responseExample).toEqual({ body: { ok: true }, status: 201, headers: { 'X-Req': '1' } })
  })

  it('响应示例 JSON 非法返回错误', () => {
    const form = createEditorForm()
    form.responseBodyText = '{bad'
    const { error } = toCreatePayload(form)
    expect(error).toContain('响应体')
  })

  it('响应示例 headers JSON 非法返回错误', () => {
    const form = createEditorForm()
    form.responseHeadersText = '[1,2'
    const { error } = toCreatePayload(form)
    expect(error).toContain('响应头')
  })
})

describe('summarizeImportResult', () => {
  it('拼接三类计数，缺省补零', () => {
    expect(summarizeImportResult({ importHistoryId: 'r1', summary: { created: 2, updated: 1 }, errors: [] })).toBe(
      '新建 2 · 更新 1 · 失败 0',
    )
  })
})
