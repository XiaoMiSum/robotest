import { describe, expect, it } from 'vitest'
import {
  COMPONENT_TAB_OPTIONS,
  buildComponentConfigRows,
  buildComponentExtractorRows,
} from './componentModel'

const dump = (value: unknown): string => JSON.stringify(value)

describe('COMPONENT_TAB_OPTIONS', () => {
  it('首项为全部，其余与四类组件一一对应', () => {
    expect(COMPONENT_TAB_OPTIONS.map((t) => t.value)).toEqual([
      'all',
      'preprocessor',
      'postprocessor',
      'validator',
      'extractor',
    ])
  })
})

describe('buildComponentConfigRows', () => {
  it('http 处理器：方法/路径为文本，query 与请求头为键值对，对象请求体为代码块', () => {
    const config = dump({
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

    const rows = buildComponentConfigRows('preprocessor', config)
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
    const config = dump({
      testclass: 'http',
      config: { data: { grant_type: 'client_credentials' } },
      extractors: [],
    })

    const rows = buildComponentConfigRows('postprocessor', config)
    const labels = rows.map((r) => r.label)

    expect(labels).toContain('表单参数')
    expect(labels).not.toContain('请求体')
    const body = rows.find((r) => r.label === '请求体类型')
    expect(body?.value).toBe('x-www-form-urlencoded')
  })

  it('http 处理器：无 query/请求头/请求体时不产生空行，方法缺省 GET', () => {
    const rows = buildComponentConfigRows('preprocessor', dump({ testclass: 'http', config: {}, extractors: [] }))
    const labels = rows.map((r) => r.label)

    expect(labels).toEqual(['处理器类型', '环境 HTTP 配置', '请求方法', '路径', '请求体类型'])
    expect(rows.find((r) => r.label === '请求方法')?.value).toBe('GET')
    expect(rows.find((r) => r.label === '请求体类型')?.value).toBe('none')
    expect(rows.find((r) => r.label === '环境 HTTP 配置')?.value).toBe('—')
  })

  it('jdbc 处理器：SQL 走代码块，参数以逗号串联', () => {
    const config = dump({
      testclass: 'jdbc',
      config: { datasource: '订单库', sql: 'select 1 from dual', args: ['a', 'b'] },
      extractors: [],
    })

    const rows = buildComponentConfigRows('preprocessor', config)
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r]))

    expect(rows.map((r) => r.label)).toEqual(['处理器类型', '数据源', 'SQL 语句', '参数'])
    expect(byLabel['处理器类型'].value).toBe('JDBC')
    expect(byLabel['数据源'].value).toBe('订单库')
    expect(byLabel['SQL 语句'].kind).toBe('code')
    expect(byLabel['参数'].value).toBe('a, b')
  })

  it('验证器：目标与条件映射为中文，描述缺失时不展示', () => {
    const config = dump({ target: 'json_field', expression: '$.code', condition: 'not_equals', expected: '500' })

    const rows = buildComponentConfigRows('validator', config)
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r]))

    expect(rows.map((r) => r.label)).toEqual(['验证目标', '表达式', '比较条件', '期望值'])
    expect(byLabel['验证目标'].value).toBe('JSON 字段')
    expect(byLabel['比较条件'].value).toBe('不等于')
    expect(byLabel['表达式'].value).toBe('$.code')
    expect(byLabel['期望值'].value).toBe('500')
  })

  it('验证器：缺失字段回落默认值并补出描述行', () => {
    const rows = buildComponentConfigRows('validator', dump({ description: '业务码校验' }))
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r]))

    expect(byLabel['验证目标'].value).toBe('状态码')
    expect(byLabel['比较条件'].value).toBe('等于')
    expect(byLabel['验证器描述'].value).toBe('业务码校验')
  })

  it('提取器：来源映射为中文，缺失字段显示占位符', () => {
    const rows = buildComponentConfigRows('extractor', dump({ source: 'response_header', expression: '' }))
    const byLabel = Object.fromEntries(rows.map((r) => [r.label, r]))

    expect(rows.map((r) => r.label)).toEqual(['提取来源', '表达式', '目标变量名'])
    expect(byLabel['提取来源'].value).toBe('响应头')
    expect(byLabel['表达式'].value).toBe('—')
    expect(byLabel['目标变量名'].value).toBe('—')
  })

  it('config 为空或非法 JSON 时回落 HTTP 默认行', () => {
    expect(buildComponentConfigRows('preprocessor', null).map((r) => r.label)).toEqual([
      '处理器类型',
      '环境 HTTP 配置',
      '请求方法',
      '路径',
      '请求体类型',
    ])
    expect(buildComponentConfigRows('validator', 'not-json').find((r) => r.label === '验证目标')?.value).toBe('状态码')
  })
})

describe('buildComponentExtractorRows', () => {
  it('处理器内嵌提取器：来源映射为中文，空描述回落占位符', () => {
    const config = dump({
      testclass: 'http',
      config: {},
      extractors: [
        { source: 'json_field', expression: '$.token', variableName: 'token', description: '登录令牌' },
        { source: 'regex', expression: 'id=(\\d+)', variableName: '', description: '' },
      ],
    })

    const rows = buildComponentExtractorRows('postprocessor', config)

    expect(rows).toEqual([
      { source: 'JSON 字段', expression: '$.token', variableName: 'token', description: '登录令牌' },
      { source: '正则匹配', expression: 'id=(\\d+)', variableName: '—', description: '—' },
    ])
  })

  it('验证器 / 提取器组件无内嵌提取器', () => {
    expect(buildComponentExtractorRows('validator', dump({ extractors: [{ source: 'json_field' }] }))).toEqual([])
    expect(buildComponentExtractorRows('extractor', null)).toEqual([])
  })
})
