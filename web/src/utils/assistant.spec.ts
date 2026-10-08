// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import {
  ASSISTANT_EXAMPLES,
  assistantActionLabel,
  citationRoute,
  formatIntentChangeValue,
  intentFieldLabel,
  isClarifyMessage,
  parseClarifyContent,
  splitReplaceChange,
} from './assistant'
import type { AiAssistantMessage } from '@/types'

function makeMessage(overrides: Partial<AiAssistantMessage> = {}): AiAssistantMessage {
  return {
    id: 'm1',
    conversationId: 'c1',
    role: 'assistant',
    content: '内容',
    attachments: null,
    intent: null,
    citations: null,
    execution: null,
    status: 'done',
    createdAt: '2026-10-03T00:00:00.000Z',
    ...overrides,
  }
}

describe('utils/assistant 澄清解析', () => {
  it('反问与选项按落盘格式拆分', () => {
    expect(parseClarifyContent('要建哪个模块的评审？\n\n- 登录模块\n- 订单模块')).toEqual({
      question: '要建哪个模块的评审？',
      options: ['登录模块', '订单模块'],
    })
  })

  it('无选项时整段作为反问', () => {
    expect(parseClarifyContent('请补充更多信息')).toEqual({
      question: '请补充更多信息',
      options: [],
    })
  })

  it('反问正文含空行时仍从末尾识别选项块', () => {
    expect(parseClarifyContent('请确认：\n\n背景说明\n\n- A\n- B')).toEqual({
      question: '请确认：\n\n背景说明',
      options: ['A', 'B'],
    })
  })

  it('空内容返回空反问', () => {
    expect(parseClarifyContent(null)).toEqual({ question: '', options: [] })
    expect(parseClarifyContent('   ')).toEqual({ question: '', options: [] })
  })

  it('仅选项行无反问时按原文返回，避免误吞', () => {
    expect(parseClarifyContent('- A\n- B')).toEqual({ question: '- A\n- B', options: [] })
  })

  it('isClarifyMessage 仅命中 done 的无意图无引用助手消息', () => {
    expect(isClarifyMessage(makeMessage())).toBe(true)
    expect(isClarifyMessage(makeMessage({ role: 'user' }))).toBe(false)
    expect(isClarifyMessage(makeMessage({ status: 'streaming' }))).toBe(false)
    expect(isClarifyMessage(makeMessage({ status: 'error' }))).toBe(false)
    expect(isClarifyMessage(makeMessage({ citations: [] }))).toBe(false)
    expect(
      isClarifyMessage(
        makeMessage({ intent: { kind: 'create_review' } as AiAssistantMessage['intent'] }),
      ),
    ).toBe(false)
  })

  it('citationRoute 覆盖可定位引用类型，其余返回 null', () => {
    expect(citationRoute('requirement', 'r1')).toBe('/workspace/projects/requirements/r1')
    expect(citationRoute('test_review', 'v1')).toBe('/workspace/projects/reviews/v1')
    expect(citationRoute('test_plan', 'p1')).toBe('/workspace/projects/plans/p1')
    expect(citationRoute('mindmap_document', 'd1')).toBe(
      '/workspace/projects/functional-testing?documentId=d1',
    )
    expect(citationRoute('module', 'm1')).toBeNull()
    expect(citationRoute('test_case', 't1')).toBeNull()
    expect(citationRoute(undefined, 'x')).toBeNull()
    expect(citationRoute('requirement', undefined)).toBeNull()
  })

  it('空会话引导示例为非空指令列表', () => {
    expect(ASSISTANT_EXAMPLES.length).toBeGreaterThan(0)
    expect(ASSISTANT_EXAMPLES.every((item) => item.trim().length > 0)).toBe(true)
  })

  it('动作与字段标签已登记值翻译，未知值原样展示', () => {
    expect(assistantActionLabel('create_case')).toBe('创建用例')
    expect(assistantActionLabel('view_plan_progress')).toBe('查看计划进度')
    expect(assistantActionLabel('unknown_action')).toBe('unknown_action')
    expect(intentFieldLabel('priority')).toBe('优先级')
    expect(intentFieldLabel('custom_field')).toBe('custom_field')
  })

  it('变更值任意 JSON 序列化为展示文本', () => {
    expect(formatIntentChangeValue('P1')).toBe('P1')
    expect(formatIntentChangeValue(3)).toBe('3')
    expect(formatIntentChangeValue(true)).toBe('true')
    expect(formatIntentChangeValue(null)).toBe('—')
    expect(formatIntentChangeValue(undefined)).toBe('—')
    expect(formatIntentChangeValue(['步骤一', '步骤二'])).toBe('步骤一\n步骤二')
    expect(formatIntentChangeValue({ from: 'P0', to: 'P1' })).toBe('{"from":"P0","to":"P1"}')
  })

  it('splitReplaceChange 拆分原值与新值，无原值时返回 null', () => {
    expect(splitReplaceChange('P0 → P1')).toEqual({ before: 'P0', after: 'P1' })
    expect(splitReplaceChange('仅新值')).toBeNull()
    expect(splitReplaceChange('旧值 → ')).toBeNull()
    expect(splitReplaceChange(null)).toBeNull()
  })
})
