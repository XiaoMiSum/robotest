import { describe, expect, it } from 'vitest'
import pageSource from './PlanDetailPage.vue?raw'
import composableSource from '../../../composables/project/functional-testing/plan/usePlanDetail.ts?raw'

// 详情页操作按钮按状态显隐，状态判定集中在组合式函数以便单测覆盖
describe('PlanDetailPage 操作按钮状态显隐', () => {
  it('同步与完成共用活跃态，阻塞共用活跃态，恢复按已阻塞状态', () => {
    expect(pageSource.match(/v-if="canOperateActive"/g)).toHaveLength(3)
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
    expect(pageSource).toContain('重试')
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

  it('页头提供下一个待执行用例入口并传递序号映射', () => {
    expect(pageSource).toContain('下一个待执行用例')
    expect(pageSource).toContain('@click="focusNextCase"')
    expect(pageSource).toContain(':disabled="!nextPendingCase"')
    expect(pageSource).toContain(':execution-order="executionOrder"')
    expect(pageSource).toContain('@marked="handleMarked"')
  })
})

// 详情页头收拢为单条无边框横条（交互 06 §1.2）：面包屑保持纯净，右侧同行按序承载需求/状态/进度/操作
describe('PlanDetailPage 页头单条横条', () => {
  it('删除独立标题卡片，当前页名称由面包屑末段承担', () => {
    expect(pageSource).toContain('class="plan-detail__header"')
    expect(pageSource).toContain('class="plan-detail__breadcrumb"')
    expect(pageSource).toContain('to="/workspace/projects/functional-testing?tab=plans"')
    expect(pageSource).not.toContain('plan-detail__head"')
    expect(pageSource).not.toContain('plan-detail__title-row')
    expect(pageSource).not.toContain('class="plan-detail__title"')
  })

  it('右侧区按需求、状态、进度统计、操作按钮顺序排布且统计与按钮竖线分隔', () => {
    const requirement = pageSource.indexOf('plan-detail__requirements')
    const status = pageSource.indexOf('planStatusMeta(detail.status).tagType')
    const progress = pageSource.indexOf('plan-detail__progress-row')
    const actions = pageSource.indexOf('class="plan-detail__actions"')
    expect(requirement).toBeGreaterThanOrEqual(0)
    expect(requirement).toBeLessThan(status)
    expect(status).toBeLessThan(progress)
    expect(progress).toBeLessThan(actions)
    expect(pageSource).toContain('border-left: 1px solid var(--color-neutral-200)')
  })

  it('超宽时页头单行横向滚动不换行，保留滚动能力但隐藏视觉滚动条', () => {
    const headerStyle = pageSource.slice(pageSource.indexOf('.plan-detail__header {'))
    expect(headerStyle).toContain('overflow-x: auto')
    expect(headerStyle).toContain('scrollbar-width: none')
    expect(headerStyle).not.toContain('flex-wrap: wrap')
  })

  it('快照文档树卡片补齐固定 header，仅树体纵向滚动', () => {
    expect(pageSource).toContain('class="plan-detail__tree-header"')
    expect(pageSource).toContain('快照文档')
    expect(pageSource).toContain('只读')
    expect(pageSource).toContain('flex: 1;\n    min-height: 0;')
  })
})
