import { describe, expect, it } from 'vitest'
import type { AiProviderPreset, AiSettingSchemaItem } from '@/types'
import {
  buildDefaultUniqueParams,
  getByPath,
  isSettingModified,
  mergeExtraParams,
  resolveDefaultBaseUrl,
  resolveModelHints,
  resolveUniqueParams,
  setByPath,
  validateSetting,
  weightsSum,
} from './aiConfigForm'

const zhipuPreset: AiProviderPreset = {
  key: 'zhipu',
  name: '智谱 AI',
  scopes: ['chat', 'embedding'],
  defaultBaseUrl: { chat: 'https://open.bigmodel.cn/api/paas/v4' },
  modelHints: { chat: ['glm-4-plus', 'glm-4-flash'] },
  uniqueParams: {
    chat: [
      {
        key: 'thinking.type',
        type: 'enum',
        defaultValue: 'disabled',
        options: ['enabled', 'disabled'],
        label: '思考模式',
        description: '',
      },
    ],
  },
}

describe('setByPath 点号路径写入', () => {
  it('展开为嵌套对象', () => {
    const target: Record<string, unknown> = {}
    setByPath(target, 'thinking.type', 'disabled')
    expect(target).toEqual({ thinking: { type: 'disabled' } })
  })

  it('顶层键直接写入', () => {
    const target: Record<string, unknown> = {}
    setByPath(target, 'enable_thinking', false)
    expect(target).toEqual({ enable_thinking: false })
  })

  it('保留已有兄弟键', () => {
    const target: Record<string, unknown> = { thinking: { keep: 1 } }
    setByPath(target, 'thinking.type', 'enabled')
    expect(target).toEqual({ thinking: { keep: 1, type: 'enabled' } })
  })
})

describe('getByPath 点号路径读取', () => {
  it('读取嵌套值', () => {
    expect(getByPath({ thinking: { type: 'enabled' } }, 'thinking.type')).toBe('enabled')
  })

  it('缺失路径返回 undefined', () => {
    expect(getByPath({}, 'thinking.type')).toBeUndefined()
    expect(getByPath({ a: 1 }, 'a.b')).toBeUndefined()
  })
})

describe('mergeExtraParams 合并独有配置项与自定义参数', () => {
  it('点号路径键展开并合并进自定义参数', () => {
    const result = mergeExtraParams({ 'thinking.type': 'disabled' }, { custom: 1 })
    expect(result).toEqual({ custom: 1, thinking: { type: 'disabled' } })
  })

  it('顶层模板键覆盖式写入', () => {
    const result = mergeExtraParams({ enable_thinking: false }, {})
    expect(result).toEqual({ enable_thinking: false })
  })

  it('跳过空值', () => {
    const result = mergeExtraParams({ a: '', b: null, c: undefined, d: 'v' }, {})
    expect(result).toEqual({ d: 'v' })
  })
})

describe('buildDefaultUniqueParams 默认值集合', () => {
  it('取模板 defaultValue', () => {
    expect(buildDefaultUniqueParams(zhipuPreset.uniqueParams.chat)).toEqual({
      'thinking.type': 'disabled',
    })
  })

  it('空模板返回空对象', () => {
    expect(buildDefaultUniqueParams(undefined)).toEqual({})
  })
})

describe('preset 解析辅助', () => {
  it('resolveDefaultBaseUrl 缺失组返回空串', () => {
    expect(resolveDefaultBaseUrl(zhipuPreset, 'chat')).toBe('https://open.bigmodel.cn/api/paas/v4')
    expect(resolveDefaultBaseUrl(zhipuPreset, 'embedding')).toBe('')
    expect(resolveDefaultBaseUrl(undefined, 'chat')).toBe('')
  })

  it('resolveModelHints 返回提示清单', () => {
    expect(resolveModelHints(zhipuPreset, 'chat')).toEqual(['glm-4-plus', 'glm-4-flash'])
    expect(resolveModelHints(zhipuPreset, 'embedding')).toEqual([])
  })

  it('resolveUniqueParams 返回模板', () => {
    expect(resolveUniqueParams(zhipuPreset, 'chat')).toHaveLength(1)
    expect(resolveUniqueParams(zhipuPreset, 'embedding')).toEqual([])
    expect(resolveUniqueParams(undefined, 'chat')).toEqual([])
  })
})

const intItem: AiSettingSchemaItem = {
  key: 'dedup.topK',
  type: 'int',
  label: '查重返回条数',
  description: '',
  defaultValue: 5,
  min: 1,
  max: 50,
  step: null,
}

const weightsItem: AiSettingSchemaItem = {
  key: 'planOrder.weights',
  type: 'object',
  label: '推荐权重',
  description: '',
  defaultValue: { w1: 0.5, w2: 0.3, w3: 0.2 },
  min: null,
  max: null,
  step: null,
}

describe('weightsSum 权重求和', () => {
  it('求和三项', () => {
    expect(weightsSum({ w1: 0.5, w2: 0.3, w3: 0.2 })).toBeCloseTo(1)
  })

  it('非法值按 0 计', () => {
    expect(weightsSum(null)).toBe(0)
    expect(weightsSum({ w1: 'x', w2: 0.3 })).toBeCloseTo(0.3)
  })
})

describe('isSettingModified 偏离默认判定', () => {
  it('数值按数值比较（20 与 20.0 视为相等）', () => {
    expect(isSettingModified(intItem, 5)).toBe(false)
    expect(isSettingModified(intItem, 5.0)).toBe(false)
    expect(isSettingModified(intItem, 8)).toBe(true)
  })

  it('对象按结构比较', () => {
    expect(isSettingModified(weightsItem, { w1: 0.5, w2: 0.3, w3: 0.2 })).toBe(false)
    expect(isSettingModified(weightsItem, { w1: 0.6, w2: 0.2, w3: 0.2 })).toBe(true)
  })
})

describe('validateSetting 单项校验', () => {
  it('数值越界返回错误文案', () => {
    expect(validateSetting(intItem, 3)).toBeNull()
    expect(validateSetting(intItem, 0)).toContain('不能小于')
    expect(validateSetting(intItem, 999)).toContain('不能大于')
    expect(validateSetting(intItem, 'x')).toContain('必须为数字')
  })

  it('整数类型拒绝小数', () => {
    expect(validateSetting(intItem, 3.5)).toContain('必须为整数')
  })

  it('权重之和须为 1', () => {
    expect(validateSetting(weightsItem, { w1: 0.5, w2: 0.3, w3: 0.2 })).toBeNull()
    expect(validateSetting(weightsItem, { w1: 0.5, w2: 0.5, w3: 0.5 })).toContain('之和须为 1')
  })
})
