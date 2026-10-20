/** 单帧解析结果：事件名 + JSON 载荷（畸形帧返回 null，事件名过滤交给调用方） */
export interface SseFrame {
  name: string
  data: unknown
}

/**
 * 解析单个 SSE 帧（event/data 行，冒号开头为心跳注释行）。
 * 畸形 JSON 或缺 data 的帧返回 null——单帧失败不中断整条流。
 */
export function parseSseFrame(frame: string): SseFrame | null {
  let name = ''
  const dataLines: string[] = []
  for (const line of frame.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    const sep = line.indexOf(':')
    const field = sep === -1 ? line : line.slice(0, sep)
    let value = sep === -1 ? '' : line.slice(sep + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') name = value
    else if (field === 'data') dataLines.push(value)
  }
  if (!name || dataLines.length === 0) return null
  try {
    return { name, data: JSON.parse(dataLines.join('\n')) as unknown }
  } catch {
    // 畸形数据按未知帧忽略，避免单帧失败中断整条流
    return null
  }
}

/** 增量帧解析器：跨 chunk 缓冲，按空行分帧（SSE 规范，兼容 \r\n） */
export function createSseFrameParser(onFrame: (frame: SseFrame) => void): {
  push: (chunk: string) => void
  flush: () => void
} {
  let buffer = ''
  const emitBlocks = () => {
    for (;;) {
      const match = /\r?\n\r?\n/.exec(buffer)
      if (!match) return
      const frame = buffer.slice(0, match.index)
      buffer = buffer.slice(match.index + match[0].length)
      const parsed = parseSseFrame(frame)
      if (parsed) onFrame(parsed)
    }
  }
  return {
    push: (chunk: string) => {
      buffer += chunk
      emitBlocks()
    },
    flush: () => {
      if (!buffer.trim()) return
      const parsed = parseSseFrame(buffer)
      buffer = ''
      if (parsed) onFrame(parsed)
    },
  }
}
