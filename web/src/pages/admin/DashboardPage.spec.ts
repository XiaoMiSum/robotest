import { describe, expect, it } from 'vitest'
import pageSource from './DashboardPage.vue?raw'
import composableSource from '../../composables/admin/useDashboard.ts?raw'

describe('DashboardPage 列表错误边界', () => {
  it('统计与最近空间分别展示后端消息并提供重试', () => {
    expect(composableSource).toContain('errorMessage(statsRes.reason')
    expect(composableSource).toContain("errorMessage(wsRes.reason, '加载最近空间失败')")
    expect(composableSource).toContain('requestSequence')
    expect(composableSource).toContain('dashboardError')
    expect(composableSource).toContain('workspaceError')
    expect(pageSource).toContain('v-if="dashboardError"')
    expect(pageSource).toContain('v-if="workspaceError"')
    expect(pageSource).toContain('@click="retry"')
  })
})
