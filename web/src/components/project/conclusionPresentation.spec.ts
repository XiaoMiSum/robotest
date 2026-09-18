import { describe, expect, it } from 'vitest'
import { verdictPresentation } from './conclusionPresentation'

describe('verdictPresentation', () => {
  it('PASS 绿 + 通过', () => {
    const p = verdictPresentation('PASS')
    expect(p.label).toBe('通过')
    expect(p.tagType).toBe('success')
    expect(p.verdict).toBe('PASS')
  })

  it('FAIL 红 + 不通过', () => {
    const p = verdictPresentation('FAIL')
    expect(p.label).toBe('不通过')
    expect(p.tagType).toBe('danger')
  })

  it('INCONCLUSIVE 黄 + 无法判定', () => {
    const p = verdictPresentation('INCONCLUSIVE')
    expect(p.label).toBe('无法判定')
    expect(p.tagType).toBe('warning')
  })
})