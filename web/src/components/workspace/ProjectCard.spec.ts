import { describe, expect, it } from 'vitest'
import cardSource from './ProjectCard.vue?raw'

describe('ProjectCard', () => {
  it('提供项目卡片字段和默认项目操作', () => {
    expect(cardSource).toContain('project-card')
    expect(cardSource).toContain('设为默认')
    expect(cardSource).toContain('进入')
    expect(cardSource).toContain('归档')
    expect(cardSource).toContain('已归档')
  })

  it('归档卡片保持只读并提供启封、删除事件', () => {
    expect(cardSource).toContain("emit('unarchive')")
    expect(cardSource).toContain("emit('delete')")
    expect(cardSource).toContain('project-card--archived')
  })
})
