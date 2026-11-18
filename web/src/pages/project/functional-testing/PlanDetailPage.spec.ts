import { describe, expect, it } from 'vitest'
import pageSource from './PlanDetailPage.vue?raw'
import composableSource from '../../../composables/project/functional-testing/plan/usePlanDetail.ts?raw'

// 详情页操作按钮按状态显隐，状态判定集中在组合式函数以便单测覆盖
describe('PlanDetailPage 操作按钮状态显隐', () => {
  it('同步与完成共用活跃态，阻塞共用活跃态，恢复按已阻塞状态', () => {
    expect(pageSource.match(/v-if="canOperateActive"/g)).toHaveLength(2)
    expect(pageSource).toContain('v-if="canBlock"')
    expect(pageSource).toContain('v-if="canResume"')
    expect(pageSource).toContain('v-if="canAdjustCases"')
  })

  it('组合式不再引入权限码，仅按计划状态派生操作', () => {
    expect(composableSource).not.toContain('hasPermission')
    expect(composableSource).not.toContain('useAuthStore')
    expect(composableSource).toContain("detail.value?.status === 'blocked'")
    // 组合式不得依赖组件层（01-frontend.md §3），状态口径内联在组合式内
    expect(composableSource).not.toContain('@/components/')
  })

  it('执行顺序建议入口按活跃态 + AI 可用性显隐，空计划置灰并说明原因', () => {
    expect(pageSource).toContain('执行顺序建议')
    expect(pageSource).toContain('v-if="orderEntryVisible && canOperateActive"')
    expect(pageSource).toContain(':disabled="!!orderEmptyReason"')
    expect(pageSource).toContain('计划关联用例为空')
    expect(pageSource).toContain('usePlanOrderAssist')
    expect(pageSource).toContain('planId')
  })

  it('发起后在对话框内覆盖进行中 / 终止 / 加载失败 / 排序面板四态', () => {
    expect(pageSource).toContain('v-if="orderRunning"')
    expect(pageSource).toContain('AI 正在生成执行顺序建议')
    expect(pageSource).toContain('v-else-if="orderFailed"')
    expect(pageSource).toContain('再次执行')
    expect(pageSource).toContain('v-else-if="orderLoadError"')
    expect(pageSource).toContain('<PlanOrderPanel')
    expect(pageSource).toContain(':can-adopt="canOperateActive"')
    expect(pageSource).toContain('@close="onOrderPanelClose"')
    expect(pageSource).toContain('@closed="closeOrderAssistPanel"')
  })

  it('应用建议后刷新计划详情与脑图', () => {
    expect(pageSource).toContain('mindMapRef.value?.reload()')
    expect(pageSource).toContain('void load()')
  })
})
