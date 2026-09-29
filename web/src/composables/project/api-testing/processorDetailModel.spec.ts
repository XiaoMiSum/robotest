import { describe, expect, it } from 'vitest'
import { buildProcessorDetailRows, buildProcessorExtractorRows } from './processorDetailModel'

describe('buildProcessorDetailRows', () => {
  it('http 处理器：方法/路径为文本，query 与请求头为键值对，对象请求体为代码块', () => {
    const rows = buildProcessorDetailRows({
      enabled: true,
      testclass: 'http',
      config: {
        method: 'POST',
        ref: '默认环境',
        path: '/api/login',
        query: { tenant: '1' },
        headers: { 'Content-Type': 'application/json' },
        body: { user: 'a' },
      },
      extractors: [],
    })
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r]))

    expect(rows.map((r) => `${r.label}:${r.kind}`)).toEqual([
      '处理器类型:text',
      '环境 HTTP 配置:text',
      '请求方法:text',
      '路径:text',
      'Query 参数:kv',
      '请求头:kv',
      '请求体类型:text',
      '请求体:code',
    ])
    expect(byLabel['处理器类型'].value).toBe('HTTP')
    expect(byLabel['环境 HTTP 配置'].value).toBe('默认环境')
    expect(byLabel['请求方法'].value).toBe('POST')
    expect(byLabel['Query 参数'].pairs).toEqual([{ key: 'tenant', value: '1' }])
    expect(byLabel['请求头'].pairs).toEqual([{ key: 'Content-Type', value: 'application/json' }])
    expect(byLabel['请求体类型'].value).toBe('raw · json')
    expect(byLabel['请求体'].value).toBe('{\n  "user": "a"\n}')
  })

  it('http 处理器：表单体只展示表单参数，不重复展示请求体', () => {
    const rows = buildProcessorDetailRows({
      testclass: 'http',
      config: { data: { grant_type: 'client_credentials' } },
      extractors: [],
    })
    const labels = rows.map((r) => r.label)

    expect(labels).toContain('表单参数')
    expect(labels).not.toContain('请求体')
    expect(rows.find((r) => r.label === '请求体类型')?.value).toBe('x-www-form-urlencoded')
  })

  it('http 处理器：无 query/请求头/请求体时不产生空行，方法缺省 GET', () => {
    const rows = buildProcessorDetailRows({ testclass: 'http', config: {}, extractors: [] })

    expect(rows.map((r) => r.label)).toEqual(['处理器类型', '环境 HTTP 配置', '请求方法', '路径', '请求体类型'])
    expect(rows.find((r) => r.label === '请求方法')?.value).toBe('GET')
    expect(rows.find((r) => r.label === '请求体类型')?.value).toBe('none')
    expect(rows.find((r) => r.label === '环境 HTTP 配置')?.value).toBe('—')
  })

  it('jdbc 处理器：SQL 走代码块，参数以逗号串联，无参数不产生参数行', () => {
    const rows = buildProcessorDetailRows({
      testclass: 'jdbc',
      config: { datasource: '订单库', sql: 'select 1 from dual', args: ['a', 'b'] },
      extractors: [],
    })
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r]))

    expect(rows.map((r) => r.label)).toEqual(['处理器类型', '数据源', 'SQL 语句', '参数'])
    expect(byLabel['处理器类型'].value).toBe('JDBC')
    expect(byLabel['数据源'].value).toBe('订单库')
    expect(byLabel['SQL 语句'].kind).toBe('code')
    expect(byLabel['参数'].value).toBe('a, b')

    const noArgs = buildProcessorDetailRows({ testclass: 'jdbc', config: { sql: 'select 1' } })
    expect(noArgs.map((r) => r.label)).toEqual(['处理器类型', '数据源', 'SQL 语句'])
  })

  it('config 缺失或非对象时回落 HTTP 默认行', () => {
    expect(buildProcessorDetailRows({}).map((r) => r.label)).toEqual([
      '处理器类型',
      '环境 HTTP 配置',
      '请求方法',
      '路径',
      '请求体类型',
    ])
    expect(buildProcessorDetailRows({ testclass: 'http', config: 'not-record' }).find((r) => r.label === '请求方法')?.value)
      .toBe('GET')
  })
})

describe('buildProcessorExtractorRows', () => {
  it('来源映射为中文，空字段补占位符', () => {
    const rows = buildProcessorExtractorRows({
      testclass: 'http',
      config: {},
      extractors: [
        { source: 'json_field', expression: '$.token', variableName: 'token', description: '登录令牌' },
        { source: 'regex', expression: 'id=(\\d+)', variableName: '', description: '' },
      ],
    })

    expect(rows).toEqual([
      { source: 'JSON 字段', expression: '$.token', variableName: 'token', description: '登录令牌' },
      { source: '正则匹配', expression: 'id=(\\d+)', variableName: '—', description: '—' },
    ])
  })

  it('缺失提取器或来源为空时返回空数组 / 占位来源', () => {
    expect(buildProcessorExtractorRows({ testclass: 'http' })).toEqual([])
    expect(buildProcessorExtractorRows({ extractors: [null] })).toEqual([
      { source: '—', expression: '—', variableName: '—', description: '—' },
    ])
  })
})
