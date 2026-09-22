import { describe, it, expect } from 'vitest'
import { Fsm } from './fsm'

describe('Fsm 状态机', () => {
  it('初始状态与跳转', () => {
    const fsm = new Fsm('normal')
    expect(fsm.state()).toBe('normal')
    expect(fsm.jump('input', 'user-input')).toBe('input')
    expect(fsm.state()).toBe('input')
  })

  it('跳转必须提供原因', () => {
    const fsm = new Fsm('normal')
    expect(() => fsm.jump('input', '')).toThrow()
  })

  it('after 处理器（"a -> b"）在跳转完成后触发并收到新状态', () => {
    const fsm = new Fsm('normal')
    const calls: string[] = []
    fsm.when('normal -> input', (exit, enter, reason) => {
      calls.push(`${exit}:${enter}:${reason}:${fsm.state()}`)
    })
    fsm.jump('input', 'test')
    expect(calls).toEqual(['normal:input:test:input'])
  })

  it('before 处理器（"a - b"）返回真值可拦截跳转', () => {
    const fsm = new Fsm('normal')
    fsm.when('normal - input', () => true)
    expect(fsm.jump('input', 'test')).toBeUndefined()
    expect(fsm.state()).toBe('normal')
  })

  it('通配符 * 匹配任意状态', () => {
    const fsm = new Fsm('normal')
    let count = 0
    fsm.when('* -> *', () => {
      count++
    })
    fsm.jump('input', 'a')
    fsm.jump('normal', 'b')
    expect(count).toBe(2)
  })

  it('"a -> b" 不会被误解析为 before 条件', () => {
    const fsm = new Fsm('normal')
    let beforeFired = false
    let afterFired = false
    fsm.when('normal - input', () => {
      beforeFired = true
      // 不拦截
      return undefined
    })
    fsm.when('normal -> input', () => {
      afterFired = true
    })
    fsm.jump('input', 'test')
    expect(beforeFired).toBe(true)
    expect(afterFired).toBe(true)
  })

  it('非法条件表达式抛错', () => {
    const fsm = new Fsm('normal')
    expect(() => fsm.when('normal=>input', () => undefined)).toThrow()
  })
})
