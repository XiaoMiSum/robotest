import { describe, expect, it } from 'vitest'
import pageSource from './PlanListPage.vue?raw'

// 行内 [完成][恢复] 叠加权限码，与服务端 @PreAuthorize 同源；[删除] 仍由后端按计划负责人校验
describe('PlanListPage 行内操作权限显隐', () => {
  it('完成与恢复入口叠加权限码条件', () => {
    expect(pageSource).toContain("hasPermission('plan:execute')")
    expect(pageSource).toContain("hasPermission('plan:block')")
    expect(pageSource).toContain('isActivePlan(row.plan.status) && canCompletePlans')
    expect(pageSource).toContain('canResumePlan(row.plan.status) && canResumePlans')
  })

  it('删除按钮不挂权限码条件，仍走后端负责人校验', () => {
    expect(pageSource).toContain(
      '<el-button link type="danger" @click="handleDelete(row.plan)">删除</el-button>',
    )
  })
})

// 高度链路：内容白卡 → 页面 → 卡片 → 表体，滚动只发生在表体（UI-SC-01 / UI-SC-02 / UI-SC-05）
describe('PlanListPage 表格高度链', () => {
  it('表格以 100% 高度内滚，表头固定', () => {
    expect(pageSource).toContain('<el-table :data="rows" :row-key="rowKeyId" empty-text="暂无计划" height="100%">')
    expect(pageSource).toContain('class="plan-list__body"')
  })

  it('页面与卡片建立 flex 高度链，工具栏与分页条常驻不随内容滚动', () => {
    expect(pageSource).toContain('height: 100%')
    expect(pageSource).toContain('min-height: 0')
    // 卡片 body 走组件属性转 flex 列，不以 :deep 重写内部结构（UI-DS-09 / EX-DS-001）
    expect(pageSource).toContain(':body-style="cardBodyStyle"')
    expect(pageSource).toContain('flexDirection:')
  })
})
