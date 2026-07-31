import { describe, expect, it } from 'vitest'
import type { AiProviderPreset } from '@/types'
import {
  buildDefaultUniqueParams,
  getByPath,
  mergeExtraParams,
  resolveDefaultBaseUrl,
  resolveModelHints,
  resolveUniqueParams,
  setByPath,
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
