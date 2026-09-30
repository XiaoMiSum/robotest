import { describe, expect, it } from 'vitest'
import type { AiPreviewNode } from '@/minder/ai/aiMount'
import {
  buildSummaryCards,
  formatReqTagLabel,
  reqPoolLabel,
  sliceReqTags,
} from './aiPanelPresentation'

function node(partial: Partial<AiPreviewNode>): AiPreviewNode {
  return {
    key: 'k',
    title: '',
    type: 'normal',
    priority: null,
    aiGenerated: true,
    aiSelected: true,
    aiSelectable: false,
    children: [],
    ...partial,
  }
}

describe('formatReqTagLabel 标题截断', () => {
  it('超过 5 字保留前 5 字并追加省略号', () => {
    expect(formatReqTagLabel('登录改版需求')).toBe('登录改版需...')
  })

  it('不超过阈值原样返回', () => {
    expect(formatReqTagLabel('支付流程')).toBe('支付流程')
    expect(formatReqTagLabel('12345')).toBe('12345')
  })
})

describe('sliceReqTags 平铺与溢出', () => {
  it('超过 4 条仅平铺前 4 条并给出溢出数', () => {
    const items = ['a', 'b', 'c', 'd', 'e', 'f']
    expect(sliceReqTags(items)).toEqual({ visible: ['a', 'b', 'c', 'd'], overflow: 2 })
  })

  it('未超限全部平铺且无溢出', () => {
    expect(sliceReqTags(['a', 'b'])).toEqual({ visible: ['a', 'b'], overflow: 0 })
  })
})

describe('reqPoolLabel 需求池 bar 计数', () => {
  it('无条目仅显示需求池', () => {
    expect(reqPoolLabel(0)).toBe('需求池')
  })

  it('有条目带已选计数', () => {
    expect(reqPoolLabel(3)).toBe('需求池 · 已选 3 条')
  })
})

describe('buildSummaryCards 完成态结果卡片', () => {
  it('取用例节点并拼接步骤摘要', () => {
    const cards = buildSummaryCards([
      node({
        title: '优惠券叠加自动拆单',
        type: 'case',
        priority: 'P0',
        children: [
          node({ title: '已登录且持有多张可用券', type: 'precondition' }),
          node({ title: '提交含 3 张券的订单', type: 'step' }),
          node({ title: '按最优顺序叠加', type: 'expected' }),
        ],
      }),
      node({ title: '券与红包互斥校验', type: 'case', priority: 'P1' }),
    ])
    expect(cards).toEqual([
      {
        title: '优惠券叠加自动拆单',
        priority: 'P0',
        steps: '提交含 3 张券的订单',
      },
      { title: '券与红包互斥校验', priority: 'P1', steps: '' },
    ])
  })

  it('结果不含用例节点时回退展示全部顶层节点', () => {
    const cards = buildSummaryCards([node({ title: '分组节点', type: 'normal' })])
    expect(cards).toHaveLength(1)
    expect(cards[0].title).toBe('分组节点')
  })
})
