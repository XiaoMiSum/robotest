import { describe, expect, it } from 'vitest'
import pageSource from './WorkspaceInfoPage.vue?raw'

describe('WorkspaceInfoPage demo styling', () => {
  it('保留现有数据并采用演示稿的信息层级', () => {
    expect(pageSource).toContain('空间信息')
    expect(pageSource).toContain('空间基础资料与运行统计')
    expect(pageSource).toContain('ws-info__kpi-grid')
    expect(pageSource).toContain('detail?.memberCount')
    expect(pageSource).toContain('detail?.projectCount')
    expect(pageSource).toContain('基础信息')
    expect(pageSource).toContain('空间 ID')
    expect(pageSource).toContain('创建时间')
    expect(pageSource).toContain('保存修改')
  })

  it('样式优化不引入 Demo 中尚不存在的数据或交互', () => {
    expect(pageSource).not.toContain('编辑模式')
    expect(pageSource).not.toContain('放弃修改')
    expect(pageSource).not.toContain('创建人')
    expect(pageSource).not.toContain('未关闭缺陷')
  })
})
