// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { ASSISTANT_EXAMPLES, citationRoute, isClarifyMessage, parseClarifyContent } from './assistant'
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
})
