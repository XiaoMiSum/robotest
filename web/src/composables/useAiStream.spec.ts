import { describe, expect, it } from 'vitest'
import { parseSseFrame } from './useAiStream'

describe('parseSseFrame SSE 帧解析', () => {
  it('解析 delta 帧为 JSON 事件', () => {
    const frame = 'event: delta\ndata: {"content":"增量"}'
    expect(parseSseFrame(frame)).toEqual({ event: 'delta', data: { content: '增量' } })
  })

  it('解析 done 帧携带结构化结果', () => {
    const frame = 'event: done\ndata: {"summaryMarkdown":"## 总结"}'
    expect(parseSseFrame(frame)).toEqual({
      event: 'done',
      data: { summaryMarkdown: '## 总结' },
    })
  })

  it('解析 error 帧', () => {
    const frame = 'event: error\ndata: {"code":6002,"message":"AI 调用失败"}'
    expect(parseSseFrame(frame)).toEqual({
      event: 'error',
      data: { code: 6002, message: 'AI 调用失败' },
    })
  })

  it('未识别事件类型原样透传（如评审摘要 statistics 扩展帧）', () => {
    const frame = 'event: statistics\ndata: {"totalCases":200}'
    expect(parseSseFrame(frame)).toEqual({
      event: 'statistics',
      data: { totalCases: 200 },
    })
  })

  it('无 event 行时默认 message 事件', () => {
    expect(parseSseFrame('data: {"x":1}')).toEqual({ event: 'message', data: { x: 1 } })
  })

  it('注释心跳行返回 null', () => {
    expect(parseSseFrame(': ping')).toBeNull()
  })

  it('无 data 帧返回 null', () => {
    expect(parseSseFrame('event: delta')).toBeNull()
  })

  it('非 JSON data 原样返回文本', () => {
    expect(parseSseFrame('data: 纯文本增量')).toEqual({ event: 'message', data: '纯文本增量' })
  })

  it('多行 data 拼接后解析', () => {
    const frame = 'event: delta\ndata: line1\ndata: line2'
    expect(parseSseFrame(frame)).toEqual({ event: 'delta', data: 'line1\nline2' })
  })
})
