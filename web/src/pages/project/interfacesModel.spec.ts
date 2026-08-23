import { describe, expect, it } from 'vitest'
import type { ApiInterfaceStepPayload, ProjectModule } from '@/types'
import {
  buildInterfaceListQuery,
  buildVariablesPayload,
  createEditorForm,
  flattenModuleNames,
  methodTagType,
  moveStep,
  reindexSteps,
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

describe('step ordering', () => {
  const steps: ApiInterfaceStepPayload[] = [
    { id: 'a', name: 'A', stepType: 'script', sortOrder: 0, enabled: true, requestConfig: {} },
    { id: 'b', name: 'B', stepType: 'sql', sortOrder: 1, enabled: true, requestConfig: {} },
  ]

  it('reindexSteps 重排序号', () => {
    const reordered = reindexSteps([steps[1], steps[0]])
    expect(reordered.map((step) => step.sortOrder)).toEqual([0, 1])
  })

  it('moveStep 交换相邻项并归一化序号', () => {
    const moved = moveStep(steps, 'b', -1)
    expect(moved.map((step) => step.id)).toEqual(['b', 'a'])
    expect(moved.map((step) => step.sortOrder)).toEqual([0, 1])
  })

  it('moveStep 越界时返回原数组引用', () => {
    expect(moveStep(steps, 'a', -1)).toBe(steps)
    expect(moveStep(steps, 'b', 1)).toBe(steps)
  })
})

describe('editor form conversion', () => {
  it('新建默认表单：GET /、none 请求体、空键值行', () => {
    const form = createEditorForm()
    expect(form).toMatchObject({ method: 'GET', path: '/', bodyType: 'none', status: 'enabled' })
    expect(form.headers).toHaveLength(1)
  })

  it('详情回显还原请求体与键值行，剔除空键行', () => {
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
      steps: [],
      createdAt: '',
      updatedAt: '',
    })
    expect(form.jsonText).toBe(JSON.stringify({ u: 1 }, null, 2))
    expect(form.params).toHaveLength(1)
    const { req, error } = toUpdatePayload(form, 3)
    expect(error).toBeUndefined()
    expect(req.changeVersion).toBe(3)
    expect(req.body).toEqual({ type: 'json', content: { u: 1 } })
    expect(req.headers).toEqual([{ key: 'X-Tag', value: 'v', enabled: true }])
  })

  it('非法 JSON 请求体返回错误而不抛出', () => {
    const form = createEditorForm()
    form.bodyType = 'json'
    form.jsonText = '{broken'
    const { error } = toCreatePayload(form)
    expect(error).toContain('JSON')
  })

  it('form 请求体序列化为键值数组并剔除禁用与空键行', () => {
    const form = createEditorForm()
    form.bodyType = 'form'
    form.formRows = [
      { key: 'u', value: '1', enabled: true },
      { key: 'skip', value: 'x', enabled: false },
      { key: '', value: 'y', enabled: true },
    ]
    const { req } = toCreatePayload(form)
    expect(req.body).toEqual({ type: 'form', content: [{ key: 'u', value: '1', enabled: true }] })
  })
})

describe('buildVariablesPayload', () => {
  it('剔除空名行并按顺序重排 sortOrder', () => {
    const payload = buildVariablesPayload([
      { id: 'v1', name: ' token ', defaultValue: 'a', required: false, sortOrder: 5 },
      { name: '', required: false, sortOrder: 9 },
      { name: 'page', defaultValue: undefined, required: true, sortOrder: 0 },
    ])
    expect(payload.map((row) => row.name)).toEqual(['token', 'page'])
    expect(payload.map((row) => row.sortOrder)).toEqual([0, 1])
  })
})

describe('summarizeImportResult', () => {
  it('拼接三类计数，缺省补零', () => {
    expect(summarizeImportResult({ importHistoryId: 'r1', summary: { created: 2, updated: 1 }, errors: [] })).toBe(
      '新建 2 · 更新 1 · 失败 0',
    )
  })
})
