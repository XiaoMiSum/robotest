import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDashboardStats, fetchWorkspaces } from '@/services/admin'
import type { AdminWorkspace, DailyActiveUsers, DashboardStats, DashboardUsersStats } from '@/types'
import { formatDateTime } from '@/utils/format'

/** 空间表格每页条数（交互设计 3.5：固定 8 条、创建时间倒序由后端排序保证） */
export const DASHBOARD_WORKSPACE_PAGE_SIZE = 8

/** KPI 脚注片段：delta=true 的片段按增量上标渲染（交互设计 3.2：正增量绿色、+0 中性灰） */
export interface KpiFootPart {
  text: string
  delta?: boolean
}

export interface KpiCard {
  key: 'users' | 'workspaces' | 'projects' | 'activity'
  label: string
  icon: string
  value: number
  unit: string
  foot: KpiFootPart[]
}

/** 折线图几何（viewBox 640×210：X 20→605、基线 170、顶 20，与示例 dashboard.html 一致） */
export interface LineChartGeom {
  points: string
  areaPath: string
  firstX: number
  middleX: number
  lastX: number
  lastY: number
  /** 数值标签 Y：贴顶时不再上移，避免超出 viewBox 被裁切 */
  lastLabelY: number
  lastCount: number
  firstLabel: string
  middleLabel: string
  lastLabel: string
}

export interface DonutSegment {
  key: 'enabled' | 'disabled' | 'locked'
  label: string
  color: string
  value: number
  dashArray: string
  dashOffset: number
}

const LINE = { left: 20, right: 605, baseline: 170, top: 20 } as const

/** 环图半径 46、环宽 14（交互设计 3.4），周长用于 stroke-dasharray 分段 */
const DONUT_RADIUS = 46
const DONUT_CIRCUMFERENCE = 2 * Math.PI * DONUT_RADIUS

const EMPTY_USERS: DashboardUsersStats = {
  total: 0,
  weekNew: 0,
  enabled: 0,
  disabled: 0,
  locked: 0,
}

const EMPTY_GEOM: LineChartGeom = {
  points: '',
  areaPath: '',
  firstX: LINE.left,
  middleX: LINE.left,
  lastX: LINE.right,
  lastY: LINE.baseline,
  lastLabelY: LINE.baseline - 12,
  lastCount: 0,
  firstLabel: '',
  middleLabel: '',
  lastLabel: '',
}

function round1(value: number): number {
  return Math.round(value * 10) / 10
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

/** 后端 date 为 YYYY-MM-DD 纯日期串，直接截取 MM-dd（不走 Date 解析，避免时区偏移出错） */
function axisLabel(date: string): string {
  return date.length === 10 ? date.slice(5) : date
}

/** 14 日序列 → 折线/面积路径几何；空数据贴基线、末点标注 0（交互设计 3.3） */
export function buildLineChart(daily: DailyActiveUsers[]): LineChartGeom {
  if (daily.length === 0) {
    return EMPTY_GEOM
  }
  // 全 0 时以 1 归一化，保证 y 落在基线而不是 NaN
  const max = Math.max(1, ...daily.map((d) => d.count))
  const span = (LINE.right - LINE.left) / Math.max(daily.length - 1, 1)
  const xs = daily.map((_, i) => round1(LINE.left + i * span))
  const ys = daily.map((d) => round1(LINE.baseline - (d.count / max) * (LINE.baseline - LINE.top)))
  const lastY = ys[ys.length - 1]
  const middleIndex = Math.floor((daily.length - 1) / 2)
  const pairs = xs.map((x, i) => `${x},${ys[i]}`)
  return {
    points: pairs.join(' '),
    areaPath: `M ${pairs.join(' L ')} L ${xs[xs.length - 1]},${LINE.baseline} L ${xs[0]},${LINE.baseline} Z`,
    firstX: xs[0],
    middleX: xs[middleIndex],
    lastX: xs[xs.length - 1],
    lastY,
    lastLabelY: Math.max(lastY - 12, 12),
    lastCount: daily[daily.length - 1].count,
    firstLabel: axisLabel(daily[0].date),
    middleLabel: axisLabel(daily[middleIndex].date),
    lastLabel: axisLabel(daily[daily.length - 1].date),
  }
}

/** 用户状态 → 环图三段（启用绿/停用灰/锁定红）；total=0 时各段为 0，只留灰底环 */
export function buildDonutSegments(users: DashboardUsersStats): DonutSegment[] {
  const defs: { key: DonutSegment['key']; label: string; color: string; value: number }[] = [
    { key: 'enabled', label: '启用', color: 'var(--color-success)', value: users.enabled },
    { key: 'disabled', label: '停用', color: 'var(--color-info)', value: users.disabled },
    { key: 'locked', label: '锁定', color: 'var(--color-danger)', value: users.locked },
  ]
  let consumed = 0
  return defs.map((d) => {
    const seg = users.total > 0 ? (d.value / users.total) * DONUT_CIRCUMFERENCE : 0
    const offset = round2(-consumed)
    const segment: DonutSegment = {
      ...d,
      dashArray: `${round2(seg)} ${round2(DONUT_CIRCUMFERENCE - seg)}`,
      // -0 与 0 在 Object.is 断言下不等，首段归一为 +0
      dashOffset: offset === 0 ? 0 : offset,
    }
    consumed += seg
    return segment
  })
}

export function useDashboard() {
  const loading = ref(false)
  const stats = ref<DashboardStats | null>(null)
  const workspaceList = ref<AdminWorkspace[]>([])
  const workspaceTotal = ref(0)
  const workspacePage = ref(1)

  const workspacePages = computed(() =>
    Math.ceil(workspaceTotal.value / DASHBOARD_WORKSPACE_PAGE_SIZE),
  )

  const subtitle = computed(() => {
    const s = stats.value
    return s ? `平台整体运行状态 · 数据截至 ${formatDateTime(s.generatedAt)}` : '平台整体运行状态'
  })

  const kpiCards = computed<KpiCard[]>(() => {
    const s = stats.value
    const users = s?.users
    const ws = s?.workspaces
    const projects = s?.projects
    return [
      {
        key: 'users',
        label: '用户总数',
        icon: 'User',
        value: users?.total ?? 0,
        unit: '人',
        foot: [
          { text: '较上周 ' },
          { text: `+${users?.weekNew ?? 0}`, delta: true },
          { text: ` · 启用 ${users?.enabled ?? 0}` },
        ],
      },
      {
        key: 'workspaces',
        label: '工作空间',
        icon: 'OfficeBuilding',
        value: ws?.total ?? 0,
        unit: '个',
        foot: [{ text: `活跃 ${ws?.active ?? 0} · 已解散 ${ws?.dissolved ?? 0}` }],
      },
      {
        key: 'projects',
        label: '项目总数',
        icon: 'Folder',
        value: projects?.total ?? 0,
        unit: '个',
        foot: [
          { text: '近 7 日新增 ' },
          { text: `+${projects?.weekNew ?? 0}`, delta: true },
        ],
      },
      // 今日活跃脚注「接口调用 · 失败率」按需求裁剪，只展示主值与单位
      {
        key: 'activity',
        label: '今日活跃',
        icon: 'Lightning',
        value: s?.activity.todayLogins ?? 0,
        unit: '人次',
        foot: [],
      },
    ]
  })

  const lineChart = computed(() => buildLineChart(stats.value?.activeUsersDaily ?? []))
  const donutSegments = computed(() => buildDonutSegments(stats.value?.users ?? EMPTY_USERS))
  const donutTotal = computed(() => stats.value?.users.total ?? 0)

  async function loadAll() {
    loading.value = true
    try {
      // 两块数据独立容错：一块失败只 Toast 对应区块，已成功的区块保留展示（交互设计 3.7）
      const [statsRes, wsRes] = await Promise.allSettled([
        fetchDashboardStats(),
        fetchWorkspaces({
          pageNo: workspacePage.value,
          pageSize: DASHBOARD_WORKSPACE_PAGE_SIZE,
        }),
      ])
      if (statsRes.status === 'fulfilled') {
        stats.value = statsRes.value
      } else {
        ElMessage.error('加载数据概览失败')
      }
      if (wsRes.status === 'fulfilled') {
        workspaceList.value = wsRes.value.list
        workspaceTotal.value = wsRes.value.total
      } else {
        ElMessage.error('加载最近空间失败')
      }
    } finally {
      loading.value = false
    }
  }

  /** 刷新：统计与空间表格一并重拉，表格回到第 1 页（交互设计 3.6） */
  function refresh() {
    workspacePage.value = 1
    loadAll()
  }

  function gotoWorkspacePage(page: number) {
    workspacePage.value = page
    loadAll()
  }

  onMounted(loadAll)

  return {
    loading,
    stats,
    subtitle,
    kpiCards,
    lineChart,
    donutSegments,
    donutTotal,
    workspaceList,
    workspaceTotal,
    workspacePage,
    workspacePages,
    loadAll,
    refresh,
    gotoWorkspacePage,
  }
}
