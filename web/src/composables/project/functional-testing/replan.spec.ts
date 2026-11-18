// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { isRequirementSetChanged } from './replan'

describe('isRequirementSetChanged', () => {
  it('只比较集合，顺序变化不算变化', () => {
    expect(isRequirementSetChanged(['a', 'b'], ['b', 'a'])).toBe(false)
  })

  it('数量变化算变化', () => {
    expect(isRequirementSetChanged(['a'], ['a', 'b'])).toBe(true)
    expect(isRequirementSetChanged(['a', 'b'], ['a'])).toBe(true)
  })

  it('集合成员变化算变化', () => {
    expect(isRequirementSetChanged(['a', 'b'], ['a', 'c'])).toBe(true)
  })

  it('空集合与空集合相等', () => {
    expect(isRequirementSetChanged([], [])).toBe(false)
    expect(isRequirementSetChanged([], ['a'])).toBe(true)
  })
})
