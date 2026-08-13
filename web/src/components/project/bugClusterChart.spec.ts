import { describe, expect, it } from 'vitest'
import { buildModuleBars, buildSeveritySegments, SEVERITY_ORDER } from './bugClusterChart'
import type { AiBugClusterModule } from '@/types'

describe('buildModuleBars 模块分布 → 条形渲染', () => {
  it('宽度以最大计数归一化到 100%', () => {
    const dist: AiBugClusterModule[] = [
      { moduleId: 'm1', moduleName: '登录模块', count: 8 },
      { moduleId: 'm2', moduleName: '支付模块', count: 4 },
      { moduleId: 'm3', moduleName: '消息模块', count: 2 },
    ]
    const bars = buildModuleBars(dist)
    expect(bars).toHaveLength(3)
    expect(bars[0]).toEqual({ moduleName: '登录模块', count: 8, widthPercent: 100 })
    expect(bars[1]).toEqual({ moduleName: '支付模块', count: 4, widthPercent: 50 })
    expect(bars[2]).toEqual({ moduleName: '消息模块', count: 2, widthPercent: 25 })
  })

  it('输入为空时返回空数组（不抛异常）', () => {
    expect(buildModuleBars([])).toEqual([])
  })

  it('计数为 0 时按 0% 归一化（避免除以 0）', () => {
    const dist: AiBugClusterModule[] = [{ moduleId: 'm1', moduleName: '登录模块', count: 0 }]
    expect(buildModuleBars(dist)).toEqual([{ moduleName: '登录模块', count: 0, widthPercent: 0 }])
  })
})

describe('buildSeveritySegments 等级分布 → 分段色条', () => {
  it('按固定等级顺序输出，宽度按占比归一化', () => {
    const segments = buildSeveritySegments({ fatal: 1, serious: 2, general: 1, minor: 0 })
    expect(segments.map((s) => s.severity)).toEqual(SEVERITY_ORDER)
    expect(segments.map((s) => s.widthPercent)).toEqual([25, 50, 25, 0])
    expect(segments.map((s) => s.count)).toEqual([1, 2, 1, 0])
  })

  it('全为 0 时输出 0% 分段（避免除以 0）', () => {
    const segments = buildSeveritySegments({ fatal: 0, serious: 0, general: 0, minor: 0 })
    expect(segments.map((s) => s.widthPercent)).toEqual([0, 0, 0, 0])
  })

  it('缺失键按 0 处理（后端聚合保证四键齐全，前端兜底）', () => {
    const segments = buildSeveritySegments({ fatal: 1 } as Record<string, number>)
    expect(segments.map((s) => s.count)).toEqual([1, 0, 0, 0])
  })
})
