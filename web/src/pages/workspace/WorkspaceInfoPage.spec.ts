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
    expect(pageSource).toContain('创建人')
    expect(pageSource).toContain('创建时间')
    expect(pageSource).toContain('保存修改')
  })

  it('统计卡片可进入对应页面且不扩展无关指标', () => {
    expect(pageSource).toContain('to="/workspace/members"')
    expect(pageSource).toContain('to="/workspace/projects"')
    expect(pageSource).not.toContain('编辑模式')
    expect(pageSource).not.toContain('放弃修改')
    expect(pageSource).not.toContain('未关闭缺陷')
  })
})
