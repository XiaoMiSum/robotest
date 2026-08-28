import { describe, expect, it } from 'vitest'
import type { ApiBuiltinFunctionGroup, ApiCustomFunctionListItem } from '@/types'
import {
  buildEvaluateExpression,
  filterFunctions,
  formatScopeLabel,
  resolveFunctionError,
  unifyFunctionList,
} from './functionModel'

function builtinGroup(name: string, fns: { name: string; description: string }[]): ApiBuiltinFunctionGroup {
  return {
    name,
    functions: fns.map((f) => ({
      name: f.name,
      signature: `\${${f.name}(...)}`,
      description: f.description,
      params: [],
      example: `\${${f.name}()}`,
      builtin: true,
    })),
  }
}

function customItem(name: string, description = '', paramsDesc?: string): ApiCustomFunctionListItem {
  return {
    id: crypto.randomUUID(),
    type: 'custom',
    scope: 'project',
    name,
    description,
    paramsDesc: paramsDesc ?? null,
    enabled: true,
    updatedAt: '',
  }
}

describe('resolveFunctionError', () => {
  it('已知业务错误码返回中文文案', () => {
    const err = Object.assign(new Error('not found'), { code: 1000017021 })
    expect(resolveFunctionError(err)).toBe('函数不存在或不属于当前可见范围')
  })

  it('未知错误码返回原始 message', () => {
    expect(resolveFunctionError(new Error('网络超时'))).toBe('网络超时')
  })

  it('非 Error 对象返回通用文案', () => {
    expect(resolveFunctionError(undefined)).toBe('操作失败，请重试')
    expect(resolveFunctionError(null)).toBe('操作失败，请重试')
  })
})

describe('formatScopeLabel', () => {
  it.each([
    ['project', '项目'],
    ['workspace', '空间'],
    ['global', '公共'],
  ])('scope=%s → %s', (scope, expected) => {
    expect(formatScopeLabel(scope as 'project' | 'workspace' | 'global')).toBe(expected)
  })
})

describe('buildEvaluateExpression', () => {
  it('无参数生成空括号', () => {
    expect(buildEvaluateExpression('uuid', {})).toBe('${uuid()}')
  })

  it('单参数', () => {
    expect(buildEvaluateExpression('random', { min: '1', max: '100' })).toBe('${random(1, 100)}')
  })

  it('跳过空值参数', () => {
    expect(buildEvaluateExpression('faker', { path: 'name.fullName', locale: '' })).toBe(
      '${faker(name.fullName)}',
    )
  })
})

describe('filterFunctions', () => {
  const groups: ApiBuiltinFunctionGroup[] = [
    builtinGroup('数据生成', [
      { name: 'random', description: '生成随机整数' },
      { name: 'uuid', description: '生成 UUID' },
    ]),
  ]
  const custom: ApiCustomFunctionListItem[] = [
    customItem('myFunc', '自定义函数'),
    customItem('anotherFunc', '另一个函数'),
  ]

  it('空关键词返回全部', () => {
    const result = filterFunctions(groups, custom, '')
    expect(result.builtin).toHaveLength(1)
    expect(result.builtin[0].functions).toHaveLength(2)
    expect(result.custom).toHaveLength(2)
  })

  it('按名称过滤内置函数', () => {
    const result = filterFunctions(groups, custom, 'random')
    expect(result.builtin).toHaveLength(1)
    expect(result.builtin[0].functions).toHaveLength(1)
    expect(result.builtin[0].functions[0].name).toBe('random')
    expect(result.custom).toHaveLength(0)
  })

  it('按描述过滤自定义函数', () => {
    const result = filterFunctions(groups, custom, '自定义')
    expect(result.builtin).toHaveLength(0)
    expect(result.custom).toHaveLength(1)
    expect(result.custom[0].name).toBe('myFunc')
  })

  it('大小写不敏感', () => {
    const result = filterFunctions(groups, custom, 'RANDOM')
    expect(result.builtin[0].functions).toHaveLength(1)
  })
})

describe('unifyFunctionList', () => {
  it('合并内置和自定义函数为统一列表', () => {
    const groups: ApiBuiltinFunctionGroup[] = [
      builtinGroup('数据生成', [{ name: 'random', description: '随机数' }]),
    ]
    const custom: ApiCustomFunctionListItem[] = [customItem('myFunc', '自定义')]
    const result = unifyFunctionList(groups, custom)
    expect(result).toHaveLength(2)
    expect(result[0].type).toBe('builtin')
    expect(result[1].type).toBe('custom')
  })

  it('空列表返回空数组', () => {
    expect(unifyFunctionList([], [])).toEqual([])
  })
})
