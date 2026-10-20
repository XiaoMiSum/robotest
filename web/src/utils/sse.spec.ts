import { describe, expect, it } from 'vitest'
import { createSseFrameParser, parseSseFrame } from './sse'

describe('utils/sse 帧解析', () => {
  it('解析事件名与 JSON 载荷，忽略心跳注释行', () => {
    const frame = ': ping\nevent: progress\ndata: {"stage":"generating"}'
    expect(parseSseFrame(frame)).toEqual({ name: 'progress', data: { stage: 'generating' } })
  })

  it('未知事件名保留原样交由调用方过滤', () => {
    expect(parseSseFrame('event: revalidate\ndata: {}')).toEqual({ name: 'revalidate', data: {} })
  })

  it('缺事件名或缺 data 的帧返回 null', () => {
    expect(parseSseFrame(': ping')).toBeNull()
    expect(parseSseFrame('event: done')).toBeNull()
    expect(parseSseFrame('data: {}')).toBeNull()
  })

  it('畸形 JSON 返回 null 不抛异常', () => {
    expect(parseSseFrame('event: done\ndata: {broken')).toBeNull()
  })

  it('多行 data 按换行拼接后解析', () => {
    const frame = 'event: done\ndata: {"a":\ndata: 1}'
    expect(parseSseFrame(frame)).toEqual({ name: 'done', data: { a: 1 } })
  })

  it('跨 chunk 缓冲：帧被拆分到两个 chunk 仍完整解析', () => {
    const frames: Array<{ name: string }> = []
    const parser = createSseFrameParser((frame) => frames.push({ name: frame.name }))

    parser.push('event: progress\ndata: {"sta')
    parser.push('ge":"context"}\n\nevent: done\ndata: {}\n\n')

    expect(frames).toEqual([{ name: 'progress' }, { name: 'done' }])
  })

  it('flush 处理结尾缺少空行的残留帧', () => {
    const frames: Array<{ name: string }> = []
    const parser = createSseFrameParser((frame) => frames.push({ name: frame.name }))

    parser.push('event: done\ndata: []')
    parser.flush()

    expect(frames).toEqual([{ name: 'done' }])
  })

  it('flush 对空缓冲不回调', () => {
    const frames: Array<{ name: string }> = []
    const parser = createSseFrameParser((frame) => frames.push({ name: frame.name }))

    parser.flush()

    expect(frames).toEqual([])
  })
})
