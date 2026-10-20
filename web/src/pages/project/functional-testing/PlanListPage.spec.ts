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
