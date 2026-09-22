/**
 * 编辑器状态机（移植自 kityminder-editor 的 fsm runtime）。
 * 编辑内核仅使用 normal ⇄ input 两态；条件表达式约定：
 * 'a -> b' 表示跳转完成后触发，'a - b' 表示跳转前触发（返回真值可拦截跳转）。
 */
export type FsmHandler = (exit: string, enter: string, reason: string, ...args: unknown[]) => unknown

interface FsmCondition {
  when: 'before' | 'after'
  exit: string
  enter: string
}

const BEFORE_ARROW = ' - '
const AFTER_ARROW = ' -> '

function conditionMatch(condition: FsmCondition, when: 'before' | 'after', exit: string, enter: string): boolean {
  if (condition.when !== when) return false
  if (condition.enter !== '*' && condition.enter !== enter) return false
  if (condition.exit !== '*' && condition.exit !== exit) return false
  return true
}

export class Fsm {
  private currentState: string
  private readonly handlers: { condition: FsmCondition; handler: FsmHandler }[] = []

  constructor(defaultState: string) {
    this.currentState = defaultState
  }

  state(): string {
    return this.currentState
  }

  jump(newState: string, reason: string, ...args: unknown[]): string | undefined {
    if (!reason) throw new Error('状态跳转必须提供原因')
    const oldState = this.currentState
    for (const { condition, handler } of this.handlers) {
      if (conditionMatch(condition, 'before', oldState, newState)) {
        if (handler(oldState, newState, reason, ...args)) return
      }
    }
    this.currentState = newState
    for (const { condition, handler } of this.handlers) {
      if (conditionMatch(condition, 'after', oldState, newState)) {
        handler(oldState, newState, reason, ...args)
      }
    }
    return this.currentState
  }

  when(condition: string, handler: FsmHandler): void {
    let when: 'before' | 'after' | undefined
    let resolved = condition.split(BEFORE_ARROW)
    if (resolved.length === 2) {
      when = 'before'
    } else {
      resolved = condition.split(AFTER_ARROW)
      if (resolved.length === 2) when = 'after'
    }
    if (!when) throw new Error(`非法的状态机条件: ${condition}`)
    this.handlers.push({ condition: { when, exit: resolved[0], enter: resolved[1] }, handler })
  }
}
