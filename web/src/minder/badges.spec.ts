// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import {
  typeBadge,
  priorityBadge,
  reviewMarkBadge,
  executionResultBadge,
  executionOrderBadge,
  badgeWidth,
  aiGeneratedBadge,
  registerBadgesModule,
} from './badges'
import type { KityMinderGlobal } from './types'

describe('typeBadge', () => {
  it('四种业务类型映射到文案与颜色', () => {
    expect(typeBadge('case')).toEqual({ label: '用例', color: '#A464FF' })
    expect(typeBadge('precondition')).toEqual({ label: '前置', color: '#409EFF' })
    expect(typeBadge('step')).toEqual({ label: '步骤', color: '#67C23A' })
    expect(typeBadge('expected')).toEqual({ label: '预期', color: '#E6A23C' })
  })

  it('normal 与未知类型不显示徽标', () => {
    expect(typeBadge('normal')).toBeNull()
    expect(typeBadge('unknown')).toBeNull()
    expect(typeBadge(undefined)).toBeNull()
    expect(typeBadge(3)).toBeNull()
  })
})

describe('priorityBadge', () => {
  it('P0-P3 映射到文案与颜色（P0 红 / P1 橙 / P2 蓝 / P3 灰）', () => {
    expect(priorityBadge('P0')).toEqual({ label: 'P0', color: '#F56C6C' })
    expect(priorityBadge('P1')).toEqual({ label: 'P1', color: '#E6A23C' })
    expect(priorityBadge('P2')).toEqual({ label: 'P2', color: '#409EFF' })
    expect(priorityBadge('P3')).toEqual({ label: 'P3', color: '#909399' })
  })

  it('空值与非法优先级不显示徽标', () => {
    expect(priorityBadge(null)).toBeNull()
    expect(priorityBadge('P4')).toBeNull()
    expect(priorityBadge('p0')).toBeNull()
  })
})

describe('aiGeneratedBadge', () => {
  it('仅 AI 生成的用例节点显示 AI 徽标', () => {
    expect(aiGeneratedBadge('case', true)).toEqual({ label: 'AI', color: '#00A6A6' })
  })

  it('人工用例、非用例节点与非布尔真值不显示', () => {
    expect(aiGeneratedBadge('case', false)).toBeNull()
    expect(aiGeneratedBadge('case', undefined)).toBeNull()
    expect(aiGeneratedBadge('case', 'true')).toBeNull()
    expect(aiGeneratedBadge('step', true)).toBeNull()
    expect(aiGeneratedBadge('normal', true)).toBeNull()
    expect(aiGeneratedBadge(undefined, true)).toBeNull()
  })
})

describe('registerBadgesModule', () => {
  const originalKity = window.kity
  const originalKityminder = window.kityminder

  afterEach(() => {
    window.kity = originalKity
    window.kityminder = originalKityminder
  })

  it('序号徽标渲染器先注册以紧贴标题，AI 徽标位于用例徽标右侧', () => {
    const renderers: unknown[] = []
    const modules: Record<string, () => Record<string, unknown>> = {}
    window.kity = {
      createClass: (_name: string, _defines: Record<string, unknown>): unknown => {
        const renderer = { order: renderers.length }
        renderers.push(renderer)
        return renderer
      },
    }
    window.kityminder = {
      Render: {},
      Module: {
        register: (name: string, factory: () => Record<string, unknown>): void => {
          modules[name] = factory
        },
      },
    } as unknown as KityMinderGlobal

    expect(registerBadgesModule()).toBe(true)
    const left = modules['TestBadgesModule']?.()['renderers'] as { left: unknown[]; right: unknown[] } | undefined
    expect(left?.left).toHaveLength(4)
    expect(left?.left[0]).toBe(renderers[5])
    expect(left?.left[1]).toBe(renderers[0])
    expect(left?.left[2]).toBe(renderers[1])
    expect(left?.left[3]).toBe(renderers[2])
  })
})

describe('reviewMarkBadge', () => {
  it('通过/不通过映射到文案与颜色（通过绿 / 不通过红）', () => {
    expect(reviewMarkBadge('pass')).toEqual({ label: '✓ 通过', color: '#67C23A' })
    expect(reviewMarkBadge('fail')).toEqual({ label: '✕ 不通过', color: '#F56C6C' })
  })

  it('AI 采纳的评审标记追加 AI 标识', () => {
    expect(reviewMarkBadge('pass', true)).toEqual({ label: '✓ 通过 AI', color: '#67C23A' })
    expect(reviewMarkBadge('fail', true)).toEqual({ label: '✕ 不通过 AI', color: '#F56C6C' })
  })

  it('待评审（空值）与非法标记不显示徽标', () => {
    expect(reviewMarkBadge(null)).toBeNull()
    expect(reviewMarkBadge(undefined)).toBeNull()
    expect(reviewMarkBadge('block')).toBeNull()
    expect(reviewMarkBadge(null, true)).toBeNull()
  })
})

describe('executionResultBadge', () => {
  it('通过/失败/阻塞映射到文案与颜色', () => {
    expect(executionResultBadge('pass')).toEqual({ label: '✓ 通过', color: '#67C23A' })
    expect(executionResultBadge('fail')).toEqual({ label: '✕ 失败', color: '#F56C6C' })
    expect(executionResultBadge('block')).toEqual({ label: '⚠ 阻塞', color: '#C45656' })
  })

  it('未执行是默认态不显示徽标', () => {
    expect(executionResultBadge('untested')).toBeNull()
    expect(executionResultBadge(null)).toBeNull()
  })
})

describe('executionOrderBadge', () => {
  it('正整数序号映射为亮橙 #N 徽标', () => {
    expect(executionOrderBadge(1)).toEqual({ label: '#1', color: '#FF6F00' })
    expect(executionOrderBadge(12)).toEqual({ label: '#12', color: '#FF6F00' })
  })

  it('非关联节点与非法序号不显示徽标', () => {
    expect(executionOrderBadge(null)).toBeNull()
    expect(executionOrderBadge(0)).toBeNull()
    expect(executionOrderBadge(-1)).toBeNull()
    expect(executionOrderBadge(1.5)).toBeNull()
    expect(executionOrderBadge('1')).toBeNull()
  })
})

describe('badgeWidth', () => {
  it('中文比同字符数拉丁文更宽', () => {
    expect(badgeWidth('用例')).toBeGreaterThan(badgeWidth('P0'))
  })

  it('宽度随字符数递增', () => {
    expect(badgeWidth('前置条件')).toBeGreaterThan(badgeWidth('前置'))
  })
})
