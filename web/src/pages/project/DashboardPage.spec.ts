import { describe, expect, it } from 'vitest'
import pageSource from './DashboardPage.vue?raw'

describe('项目工作台页面', () => {
  it('包含页头、四个快捷入口和最近动态区域', () => {
    expect(pageSource).toContain('项目工作台')
    expect(pageSource).toContain('data?.projectName')
    expect(pageSource).toContain('projectStatusLabel')
    expect(pageSource).toContain('编写测试用例')
    expect(pageSource).toContain('新建测试计划')
    expect(pageSource).toContain('提交缺陷')
    expect(pageSource).toContain('接口调试')
    expect(pageSource).toContain('最近动态')
    expect(pageSource).toContain('activityTarget(activity)')
  })

  it('使用统一时间格式化工具展示项目周期和动态时间', () => {
    expect(pageSource).toContain('formatDate(data.value?.startTime)')
    expect(pageSource).toContain('formatDateTime(activity.occurredAt)')
  })
})
