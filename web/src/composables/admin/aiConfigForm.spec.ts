import { describe, expect, it } from 'vitest'
import type { AiConfigEmbeddingGroup, AiProviderPreset, AiSettingSchemaGroup, AiSettingSchemaItem } from '@/types'
import {
  buildConfigPayload,
  buildDefaultUniqueParams,
  collectSettingErrors,
  extractUniqueValuesForScope,
  filterSettingGroups,
  getByPath,
  isSettingModified,
  mergeExtraParams,
  resolveDefaultBaseUrl,
  resolveModelHints,
  resolveUniqueParams,
  setByPath,
  settingsStats,
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

describe('collectSettingErrors 全量校验', () => {
  const groups: AiSettingSchemaGroup[] = [
    { group: 'g1', groupLabel: '组一', items: [intItem] },
    { group: 'g2', groupLabel: '组二', items: [weightsItem] },
  ]

  it('全部通过返回 null', () => {
    expect(collectSettingErrors(groups, { 'dedup.topK': 5, 'planOrder.weights': { w1: 0.5, w2: 0.3, w3: 0.2 } })).toBeNull()
  })

  it('返回首个错误文案', () => {
    expect(collectSettingErrors(groups, { 'dedup.topK': 0, 'planOrder.weights': { w1: 0.5, w2: 0.3, w3: 0.2 } })).toContain('不能小于')
    expect(collectSettingErrors(groups, { 'dedup.topK': 5, 'planOrder.weights': { w1: 1, w2: 1, w3: 1 } })).toContain('之和须为 1')
  })
})

describe('buildConfigPayload 载荷组装', () => {
  const savedEmbedding: AiConfigEmbeddingGroup = {
    provider: 'zhipu',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    model: 'embedding-3',
    dimension: 1024,
    apiKey: { configured: true, keySuffix: 'ab' },
    extraParams: { custom: 1 },
  }

  it('saved 源：取已保存 Embedding，apiKey 留空保持原值', () => {
    const payload = buildConfigPayload({
      enabled: true,
      embedding: { kind: 'saved', group: savedEmbedding },
      settings: { 'dedup.topK': 8 },
    })
    expect(payload).toEqual({
      enabled: true,
      embedding: {
        provider: 'zhipu',
        baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
        model: 'embedding-3',
        dimension: 1024,
        apiKey: null,
        extraParams: { custom: 1 },
      },
      settings: { 'dedup.topK': 8 },
    })
  })

  it('saved 源：无 Embedding 时提交 null', () => {
    const payload = buildConfigPayload({
      enabled: false,
      embedding: { kind: 'saved', group: null },
      settings: {},
    })
    expect(payload.embedding).toBeNull()
  })

  it('form 源：解析高级参数并与独有配置项合并', () => {
    const payload = buildConfigPayload({
      enabled: true,
      embedding: {
        kind: 'form',
        group: {
          provider: 'zhipu',
          baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
          model: 'embedding-3',
          dimension: 1024,
          apiKey: 'sk-test',
          uniqueValues: { 'thinking.type': 'disabled' },
          customParams: '{ "top_k": 3 }',
        },
      },
      settings: {},
    })
    expect(payload.embedding).toEqual({
      provider: 'zhipu',
      baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
      model: 'embedding-3',
      dimension: 1024,
      apiKey: 'sk-test',
      extraParams: { top_k: 3, thinking: { type: 'disabled' } },
    })
  })

  it('form 源：核心项全空视为未配置提交 null', () => {
    const payload = buildConfigPayload({
      enabled: false,
      embedding: { kind: 'form', group: { provider: '', baseUrl: '', model: '', dimension: null, apiKey: '', uniqueValues: {}, customParams: '{}' } },
      settings: {},
    })
    expect(payload.embedding).toBeNull()
  })

  it('form 源：非法 JSON 抛错', () => {
    expect(() =>
      buildConfigPayload({
        enabled: true,
        embedding: {
          kind: 'form',
          group: {
            provider: 'zhipu',
            baseUrl: 'u',
            model: 'm',
            dimension: 128,
            apiKey: '',
            uniqueValues: {},
            customParams: '{ not json }',
          },
        },
        settings: {},
      }),
    ).toThrow('必须为 JSON 对象')
  })
})

describe('extractUniqueValuesForScope 抽取独有配置项', () => {
  it('点号路径键从嵌套 extraParams 读取', () => {
    const values = extractUniqueValuesForScope(
      { thinking: { type: 'enabled' } },
      'chat',
      zhipuPreset,
    )
    expect(values).toEqual({ 'thinking.type': 'enabled' })
  })

  it('缺失项回填默认值', () => {
    const values = extractUniqueValuesForScope({}, 'chat', zhipuPreset)
    expect(values).toEqual({ 'thinking.type': 'disabled' })
  })

  it('preset 缺省时返回空对象（自定义供应商）', () => {
    const values = extractUniqueValuesForScope({ a: 1 }, 'chat', undefined)
    expect(values).toEqual({})
  })
})

describe('settingsStats 配置项计数', () => {
  const groups: AiSettingSchemaGroup[] = [
    { group: 'limit', groupLabel: '限流阈值', items: [intItem] },
    { group: 'order', groupLabel: '执行顺序推荐', items: [weightsItem] },
  ]

  it('统计总数与已修改数', () => {
    const stats = settingsStats(groups, {
      'dedup.topK': 8,
      'planOrder.weights': weightsItem.defaultValue,
    })
    expect(stats).toEqual({ total: 2, modified: 1 })
  })

  it('空 schema 返回零', () => {
    expect(settingsStats([], {})).toEqual({ total: 0, modified: 0 })
  })
})

describe('filterSettingGroups 系统配置检索', () => {
  const thresholdItem: AiSettingSchemaItem = {
    key: 'dedup.threshold',
    type: 'int',
    label: '查重相似度阈值',
    description: '判定疑似重复的余弦相似度阈值（hourly TopK）',
    defaultValue: 0.75,
    min: null,
    max: null,
    step: null,
  }
  const limitItem: AiSettingSchemaItem = {
    key: 'limit.hourly',
    type: 'int',
    label: '生成类调用上限',
    description: '生成类每用户每小时调用上限',
    defaultValue: 20,
    min: 1,
    max: 999,
    step: null,
  }
  const groups: AiSettingSchemaGroup[] = [
    { group: 'limit', groupLabel: '限流阈值', items: [limitItem] },
    { group: 'dedup', groupLabel: '语义查重', items: [intItem, thresholdItem] },
  ]
  const form: Record<string, unknown> = {
    'limit.hourly': 20,
    'dedup.topK': 5,
    'dedup.threshold': 0.75,
  }

  it('无条件返回全部组与项', () => {
    const result = filterSettingGroups(groups, form, { query: '', modifiedOnly: false })
    expect(result.map((g) => g.group)).toEqual(['limit', 'dedup'])
    expect(result.flatMap((g) => g.items.map((i) => i.key))).toEqual([
      'limit.hourly',
      'dedup.topK',
      'dedup.threshold',
    ])
  })

  it('组名命中整组保留', () => {
    const result = filterSettingGroups(groups, form, { query: '语义', modifiedOnly: false })
    expect(result.map((g) => g.group)).toEqual(['dedup'])
    expect(result.flatMap((g) => g.items.map((i) => i.key))).toEqual([
      'dedup.topK',
      'dedup.threshold',
    ])
  })

  it('字段文本命中仅保留命中项，组内无命中整组剔除', () => {
    const result = filterSettingGroups(groups, form, { query: '相似度阈值', modifiedOnly: false })
    expect(result.map((g) => g.group)).toEqual(['dedup'])
    expect(result.flatMap((g) => g.items.map((i) => i.key))).toEqual(['dedup.threshold'])
  })

  it('检索不区分大小写并命中说明文本与对象默认值', () => {
    const byDesc = filterSettingGroups(groups, form, { query: 'topk', modifiedOnly: false })
    expect(byDesc.flatMap((g) => g.items.map((i) => i.key))).toEqual(['dedup.threshold'])

    const weightGroups: AiSettingSchemaGroup[] = [
      { group: 'order', groupLabel: '执行顺序推荐', items: [weightsItem] },
    ]
    const byDefault = filterSettingGroups(
      weightGroups,
      { 'planOrder.weights': { w1: 0.5, w2: 0.3, w3: 0.2 } },
      { query: 'w1', modifiedOnly: false },
    )
    expect(byDefault.flatMap((g) => g.items.map((i) => i.key))).toEqual(['planOrder.weights'])
  })

  it('仅看已修改保留已修改项并剔除未修改组', () => {
    const modifiedForm: Record<string, unknown> = {
      'limit.hourly': 50,
      'dedup.topK': 5,
      'dedup.threshold': 0.75,
    }
    const result = filterSettingGroups(groups, modifiedForm, { query: '', modifiedOnly: true })
    expect(result.map((g) => g.group)).toEqual(['limit'])
    expect(result.flatMap((g) => g.items.map((i) => i.key))).toEqual(['limit.hourly'])
  })

  it('检索与仅看已修改叠加为与条件', () => {
    const modifiedForm: Record<string, unknown> = {
      'limit.hourly': 50,
      'dedup.topK': 5,
      'dedup.threshold': 0.75,
    }
    const hit = filterSettingGroups(groups, modifiedForm, { query: '限流', modifiedOnly: true })
    expect(hit.map((g) => g.group)).toEqual(['limit'])

    const miss = filterSettingGroups(groups, modifiedForm, { query: '语义', modifiedOnly: true })
    expect(miss).toEqual([])
  })

  it('去空白后为空视为无条件', () => {
    const result = filterSettingGroups(groups, form, { query: '  ', modifiedOnly: false })
    expect(result.map((g) => g.group)).toEqual(['limit', 'dedup'])
  })
})
