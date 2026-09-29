import { describe, expect, it } from 'vitest'
import {
  COMPONENT_TAB_OPTIONS,
  buildComponentConfigRows,
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
  it('处理器分支委托共享明细模型，提取 testclass 与内层 config', () => {
    const config = dump({
      enabled: true,
      testclass: 'jdbc',
      config: { datasource: '订单库', sql: 'select 1 from dual', args: ['a'] },
      extractors: [],
    })

    const rows = buildComponentConfigRows('preprocessor', config)

    expect(rows.map((r) => r.label)).toEqual(['处理器类型', '数据源', 'SQL 语句', '参数'])
    expect(rows[0].value).toBe('JDBC')
    expect(rows[2].kind).toBe('code')
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

  it('config 为空或非法 JSON 时处理器回落 HTTP 默认行，验证器回落默认目标', () => {
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
