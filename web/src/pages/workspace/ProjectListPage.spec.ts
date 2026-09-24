import { describe, expect, it } from 'vitest'
import pageSource from './ProjectListPage.vue?raw'
import composableSource from '../../composables/workspace/useProjectListPage.ts?raw'

describe('ProjectListPage demo structure', () => {
  it('保留演示稿的页头、状态分段、搜索和排序文案', () => {
    expect(pageSource).toContain('项目列表')
    expect(pageSource).toContain('新建项目')
    expect(pageSource).toContain('活跃')
    expect(pageSource).toContain('已归档')
    expect(pageSource).toContain('项目名称 / 描述')
    expect(pageSource).toContain('按最近更新排序')
  })

  it('使用表格列和独立数量请求', () => {
    expect(pageSource).toContain('<el-table')
    expect(pageSource).toContain('项目名称')
    expect(pageSource).toContain('开始时间')
    expect(pageSource).toContain('结束时间')
    expect(composableSource).toContain('fetchProjectStatusCounts')
    expect(composableSource).toContain('requestSequence')
  })

  it('保留默认项目操作但不把设置默认与进入项目绑定', () => {
    expect(pageSource).toContain('setDefaultProject')
    expect(pageSource).toContain('设为默认')
    expect(pageSource).toContain('enterProject')
  })
})
