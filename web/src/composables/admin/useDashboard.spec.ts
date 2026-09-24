import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminWorkspace, DailyActiveUsers, DashboardStats, DashboardUsersStats } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchDashboardStats: vi.fn(),
  fetchWorkspaces: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => {
      cb()
    },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/admin', () => ({
  fetchDashboardStats: mocks.fetchDashboardStats,
  fetchWorkspaces: mocks.fetchWorkspaces,
}))

import {
  buildDonutSegments,
  buildLineChart,
  DASHBOARD_WORKSPACE_PAGE_SIZE,
  useDashboard,
} from './useDashboard'

function makeDaily(): DailyActiveUsers[] {
  return Array.from({ length: 14 }, (_, i) => ({
    date: `2026-09-${String(i + 1).padStart(2, '0')}`,
    count: i,
  }))
}

function makeStats(overrides?: Partial<DashboardStats>): DashboardStats {
  return {
    generatedAt: '2026-09-23T01:00:00',
    users: { total: 128, weekNew: 6, enabled: 108, disabled: 16, locked: 4 },
    workspaces: { total: 16, active: 14, dissolved: 2 },
    projects: { total: 54, weekNew: 3 },
    activity: { todayLogins: 63 },
    activeUsersDaily: makeDaily(),
    ...overrides,
  }
}

function makeWorkspace(overrides?: Partial<AdminWorkspace>): AdminWorkspace {
  return {
    id: 'ws-1',
    name: '空间一',
    description: '',
    status: 'active',
    memberCount: 3,
    projectCount: 2,
    createdByName: '张明',
    createdAt: '2026-09-20T02:00:00',
    ...overrides,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

describe('useDashboard 状态加载', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchDashboardStats.mockResolvedValue(makeStats())
    mocks.fetchWorkspaces.mockResolvedValue({ list: [makeWorkspace()], total: 16 })
  })

  it('加载成功填充统计、KPI 与空间表格（每页 8 条）', async () => {
    const d = useDashboard()
    await vi.dynamicImportSettled()

    expect(d.loading.value).toBe(false)
    expect(d.stats.value).not.toBeNull()
    expect(d.subtitle.value).toMatch(/^平台整体运行状态 · 数据截至 /)
    expect(mocks.fetchWorkspaces).toHaveBeenCalledWith({ pageNo: 1, pageSize: 8 })
    expect(d.kpiCards.value.map((c) => c.value)).toEqual([128, 16, 54, 63])
    expect(d.workspaceList.value).toHaveLength(1)
    expect(d.workspaceTotal.value).toBe(16)
    expect(d.workspacePages.value).toBe(2)
  })

  it('KPI 脚注文案组装；今日活跃无脚注（接口调用/失败率已裁剪）', async () => {
    const d = useDashboard()
    await vi.dynamicImportSettled()

    expect(d.kpiCards.value[0].foot).toEqual([
      { text: '较上周 ' },
      { text: '+6', delta: true },
      { text: ' · 启用 108' },
    ])
    expect(d.kpiCards.value[1].foot).toEqual([{ text: '活跃 14 · 已解散 2' }])
    expect(d.kpiCards.value[2].foot).toEqual([
      { text: '近 7 日新增 ' },
      { text: '+3', delta: true },
    ])
    expect(d.kpiCards.value[3].foot).toEqual([])
  })

  it('增量为 0 时输出 +0（页面按中性灰渲染）', async () => {
    const stats = makeStats()
    stats.users.weekNew = 0
    stats.projects.weekNew = 0
    mocks.fetchDashboardStats.mockResolvedValue(stats)

    const d = useDashboard()
    await vi.dynamicImportSettled()

    expect(d.kpiCards.value[0].foot[1]).toEqual({ text: '+0', delta: true })
    expect(d.kpiCards.value[2].foot[1]).toEqual({ text: '+0', delta: true })
  })

  it('统计接口失败：提示报错、空间区块保留展示', async () => {
    mocks.fetchDashboardStats.mockRejectedValue(new Error('boom'))

    const d = useDashboard()
    await vi.dynamicImportSettled()

    expect(d.stats.value).toBeNull()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('boom')
    expect(d.workspaceList.value).toHaveLength(1)
    expect(d.loading.value).toBe(false)
  })

  it('空间接口失败：统计区块保留展示', async () => {
    mocks.fetchWorkspaces.mockRejectedValue(new Error('boom'))

    const d = useDashboard()
    await vi.dynamicImportSettled()

    expect(d.stats.value).not.toBeNull()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('boom')
    expect(d.workspaceList.value).toHaveLength(0)
  })

  it('非 Error 异常使用对应场景兜底文案', async () => {
    mocks.fetchDashboardStats.mockRejectedValue('stats network')
    mocks.fetchWorkspaces.mockRejectedValue('workspace network')
    useDashboard()
    await vi.dynamicImportSettled()
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载数据概览失败')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载最近空间失败')
  })

  it('忽略晚到的旧请求结果和错误', async () => {
    const firstStats = deferred<DashboardStats>()
    const firstWorkspaces = deferred<{ list: AdminWorkspace[]; total: number }>()
    const secondStats = deferred<DashboardStats>()
    const secondWorkspaces = deferred<{ list: AdminWorkspace[]; total: number }>()
    mocks.fetchDashboardStats
      .mockReturnValueOnce(firstStats.promise)
      .mockReturnValueOnce(secondStats.promise)
    mocks.fetchWorkspaces
      .mockReturnValueOnce(firstWorkspaces.promise)
      .mockReturnValueOnce(secondWorkspaces.promise)
    const d = useDashboard()
    const secondLoad = d.loadAll()
    secondStats.resolve(makeStats({ users: { total: 2, weekNew: 0, enabled: 2, disabled: 0, locked: 0 } }))
    secondWorkspaces.resolve({ list: [makeWorkspace({ id: 'new' })], total: 1 })
    await secondLoad
    firstStats.reject(new Error('旧统计失败'))
    firstWorkspaces.reject(new Error('旧空间失败'))
    await vi.dynamicImportSettled()
    expect(d.stats.value?.users.total).toBe(2)
    expect(d.workspaceList.value[0]?.id).toBe('new')
    expect(d.loading.value).toBe(false)
    expect(mocks.ElMessage.error).not.toHaveBeenCalled()
  })

  it('切页按目标页拉取；刷新回第 1 页', async () => {
    const d = useDashboard()
    await vi.dynamicImportSettled()

    d.gotoWorkspacePage(2)
    await vi.dynamicImportSettled()
    expect(d.workspacePage.value).toBe(2)
    expect(mocks.fetchWorkspaces).toHaveBeenLastCalledWith({ pageNo: 2, pageSize: 8 })

    d.refresh()
    await vi.dynamicImportSettled()
    expect(d.workspacePage.value).toBe(1)
    expect(mocks.fetchWorkspaces).toHaveBeenLastCalledWith({ pageNo: 1, pageSize: 8 })
  })

  it('页脚总数归一到 8 条/页', () => {
    const d = useDashboard()
    expect(DASHBOARD_WORKSPACE_PAGE_SIZE).toBe(8)
    expect(d.workspacePages.value).toBe(0)
  })
})

describe('buildLineChart 折线图几何', () => {
  it('14 点等分 X 轴，首/中/末标签为 MM-dd', () => {
    const geom = buildLineChart(makeDaily())

    expect(geom.points.split(' ')).toHaveLength(14)
    expect(geom.firstX).toBe(20)
    expect(geom.lastX).toBe(605)
    expect(geom.firstLabel).toBe('09-01')
    expect(geom.middleLabel).toBe('09-07')
    expect(geom.lastLabel).toBe('09-14')
    expect(geom.areaPath.endsWith('Z')).toBe(true)
    expect(geom.lastCount).toBe(13)
    // 末点为最大值时贴顶 20，标签上移需留在 viewBox 内
    expect(geom.lastY).toBe(20)
    expect(geom.lastLabelY).toBeGreaterThanOrEqual(12)
  })

  it('全 0 时折线贴基线、末点标注 0', () => {
    const daily = makeDaily().map((d) => ({ ...d, count: 0 }))
    const geom = buildLineChart(daily)

    geom.points.split(' ').forEach((pair) => {
      expect(pair.endsWith(',170')).toBe(true)
    })
    expect(geom.lastCount).toBe(0)
    expect(geom.lastY).toBe(170)
  })

  it('空数据返回空几何（页面维持空态不抛异常）', () => {
    const geom = buildLineChart([])
    expect(geom.points).toBe('')
    expect(geom.areaPath).toBe('')
    expect(geom.lastCount).toBe(0)
  })
})

describe('buildDonutSegments 环图分段', () => {
  const users: DashboardUsersStats = { total: 128, weekNew: 6, enabled: 108, disabled: 16, locked: 4 }

  it('三段弧长之和等于周长，偏移依次累加', () => {
    const segments = buildDonutSegments(users)
    const circumference = 2 * Math.PI * 46
    const arcs = segments.map((s) => Number.parseFloat(s.dashArray.split(' ')[0]))

    expect(segments.map((s) => s.label)).toEqual(['启用', '停用', '锁定'])
    expect(arcs.reduce((sum, a) => sum + a, 0)).toBeCloseTo(circumference, 1)
    expect(segments[0].dashOffset).toBe(0)
    // offset 按未舍入弧长累计，与解析出的已舍入弧长允许 ≤0.01 的舍入差
    expect(segments[1].dashOffset).toBeCloseTo(-arcs[0], 1)
    expect(segments[2].dashOffset).toBeCloseTo(-(arcs[0] + arcs[1]), 1)
  })

  it('total=0 时各段为 0，只留灰底环', () => {
    const segments = buildDonutSegments({ total: 0, weekNew: 0, enabled: 0, disabled: 0, locked: 0 })

    segments.forEach((s) => {
      expect(Number.parseFloat(s.dashArray.split(' ')[0])).toBe(0)
    })
  })
})
