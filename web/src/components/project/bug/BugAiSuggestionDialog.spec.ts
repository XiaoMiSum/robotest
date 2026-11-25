import { describe, expect, it } from 'vitest'
import dialogSource from './BugAiSuggestionDialog.vue?raw'

describe('BugAiSuggestionDialog（加载态与重复展示收敛）', () => {
  it('弹窗加载态不再展示独立图标', () => {
    expect(dialogSource).toContain('AI 正在生成建议，请稍候...')
    expect(dialogSource).not.toContain('is-loading')
    expect(dialogSource).not.toContain('MagicStick')
  })

  it('结果弹窗不再展示疑似重复列表', () => {
    expect(dialogSource).not.toContain('BugDuplicateSuggestList')
    expect(dialogSource).not.toContain('duplicates')
    expect(dialogSource).not.toContain('疑似重复缺陷')
  })
})
