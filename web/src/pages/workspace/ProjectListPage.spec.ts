import { describe, expect, it } from 'vitest'
import pageSource from './ProjectListPage.vue?raw'
import composableSource from '../../composables/workspace/useProjectListPage.ts?raw'

describe('ProjectListPage card structure', () => {
  it('保留演示稿的页头、状态分段、搜索和排序文案', () => {
    expect(pageSource).toContain('项目列表')
    expect(pageSource).toContain('新建项目')
    expect(pageSource).toContain('活跃')
    expect(pageSource).toContain('已归档')
    expect(pageSource).toContain('项目名称 / 描述')
    expect(pageSource).toContain('按最近更新排序')
  })

  it('使用卡片网格、20 条分页和独立数量请求', () => {
    expect(pageSource).toContain('<ProjectCard')
    expect(pageSource).toContain('project-list-page__grid')
    expect(pageSource).toContain('IntersectionObserver')
    expect(pageSource).toContain('loadMoreProjects')
    expect(pageSource).toContain('project-list-page__create-card')
    expect(pageSource).toContain("hasPermission('project:create')")
    expect(pageSource).not.toContain('共 {{ total }}')
    expect(composableSource).toContain('const PAGE_SIZE = 20')
    expect(composableSource).toContain('fetchProjectStatusCounts')
    expect(composableSource).toContain('requestSequence')
  })

  it('保留默认项目操作但不把设为默认与进入项目绑定', () => {
    expect(pageSource).toContain('setDefaultProject')
    expect(pageSource).toContain('设为默认')
    expect(pageSource).toContain('enterProject')
  })

  it('起止时间按无时区墙钟提交并在编辑回填时转本地', () => {
    // 00-spec 01-api.md §4.3：客户端提交墙钟值，服务端持久化适配层只换算一次；
    // 缺 value-format 会让 Date 被 JSON 序列化为 Z 串，形成时区双换算
    expect(pageSource.match(/value-format="YYYY-MM-DDTHH:mm:ss"/g)).toHaveLength(2)
    expect(pageSource).toContain('formatPickerDateTime(project.startTime)')
    expect(pageSource).toContain('formatPickerDateTime(project.endTime)')
  })
})
