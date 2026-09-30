import { describe, expect, it, vi } from 'vitest'
import { useStageTimer } from './useStageTimer'

describe('useStageTimer 阶段文案与秒表', () => {
  it('启动后先显示连接中，超过 1 秒切换为已耗时', () => {
    vi.useFakeTimers()
    try {
      const stage = useStageTimer()
      stage.start()
      expect(stage.active.value).toBe(true)
      expect(stage.label('生成中')).toBe('正在连接模型…')

      vi.advanceTimersByTime(1200)
      expect(stage.label('生成中')).toBe('生成中… 1s')

      vi.advanceTimersByTime(2000)
      expect(stage.label('生成中')).toBe('生成中… 3s')

      stage.stop()
      expect(stage.active.value).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('停止后计时不再推进', () => {
    vi.useFakeTimers()
    try {
      const stage = useStageTimer()
      stage.start()
      vi.advanceTimersByTime(1500)
      stage.stop()
      vi.advanceTimersByTime(5000)
      expect(stage.label('分析中')).toBe('分析中… 0s')
    } finally {
      vi.useRealTimers()
    }
  })

  it('重复启动会重置计时（重新发起不留上一轮耗时）', () => {
    vi.useFakeTimers()
    try {
      const stage = useStageTimer()
      stage.start()
      vi.advanceTimersByTime(4000)
      stage.start()
      expect(stage.label('补全中')).toBe('正在连接模型…')
      stage.stop()
    } finally {
      vi.useRealTimers()
    }
  })
})
