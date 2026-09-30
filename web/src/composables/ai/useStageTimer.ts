import { onBeforeUnmount, ref } from 'vue'

const CONNECTING_MS = 1000
const TICK_MS = 400

/**
 * 调用阶段文案 + 已耗时秒表（交互设计 45 §2.9 操作行）：
 * 首秒显示「正在连接模型…」，其后切换为「<动作>… Ns」；仅由调用方 start/stop 驱动。
 */
export function useStageTimer() {
  const active = ref(false)
  const connecting = ref(false)
  const seconds = ref(0)

  let timer: ReturnType<typeof setInterval> | null = null

  function stop(): void {
    if (timer) clearInterval(timer)
    timer = null
    active.value = false
    connecting.value = false
    seconds.value = 0
  }

  function start(): void {
    stop()
    active.value = true
    connecting.value = true
    const startedAt = Date.now()
    timer = setInterval(() => {
      const elapsed = Math.floor((Date.now() - startedAt) / 1000)
      seconds.value = elapsed
      if (elapsed * 1000 >= CONNECTING_MS) connecting.value = false
    }, TICK_MS)
  }

  /** @param action 进行动作文案：「生成中 / 补全中 / 分析中」 */
  function label(action: string): string {
    if (connecting.value) return '正在连接模型…'
    return `${action}… ${seconds.value}s`
  }

  onBeforeUnmount(stop)

  return { active, start, stop, label }
}
