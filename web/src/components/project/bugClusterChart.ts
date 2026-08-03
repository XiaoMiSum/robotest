import type { AiBugClusterModule, BugSeverity } from '@/types'

/** 模块分布水平条形渲染模型（交互设计 4.2，CSS 自绘） */
export interface ModuleBar {
  moduleName: string
  count: number
  widthPercent: number
}

/** 严重等级分段色条渲染模型 */
export interface SeveritySegment {
  severity: BugSeverity
  count: number
  widthPercent: number
}

/** 分段色条固定等级顺序（与后端 severityDist 键一致，保证展示稳定） */
export const SEVERITY_ORDER: BugSeverity[] = ['fatal', 'serious', 'general', 'minor']

/** 模块分布 → 条形渲染：宽度以最大计数归一化到 100% */
export function buildModuleBars(moduleDist: AiBugClusterModule[]): ModuleBar[] {
  const max = Math.max(1, ...moduleDist.map((m) => m.count))
  return moduleDist.map((m) => ({
    moduleName: m.moduleName,
    count: m.count,
    widthPercent: Math.round((m.count / max) * 100),
  }))
}

/** 等级分布 → 分段色条渲染：按固定顺序输出、宽度按占比归一化（0 计数的段宽度为 0） */
export function buildSeveritySegments(severityDist: Record<BugSeverity, number>): SeveritySegment[] {
  const total = Math.max(1, SEVERITY_ORDER.reduce((sum, s) => sum + (severityDist[s] ?? 0), 0))
  return SEVERITY_ORDER.map((severity) => ({
    severity,
    count: severityDist[severity] ?? 0,
    widthPercent: Math.round(((severityDist[severity] ?? 0) / total) * 100),
  }))
}
