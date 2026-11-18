import { onUnmounted, ref } from 'vue'
import type { ReplanStatusResp } from '@/types'

const POLL_INTERVAL_MS = 2000

/** 只比较集合：顺序变化不算关联需求变化，避免误触发重新规划 */
export function isRequirementSetChanged(before: string[], after: string[]): boolean {
  if (before.length !== after.length) return true
  const beforeSet = new Set(before)
  return after.some((id) => !beforeSet.has(id))
}

/**
 * 编辑提交后跟踪后台重规划：进入终态即停止，卸载页面时也必须停止，
 * 防止已卸载页面继续请求或在异步返回后弹出过期提示。
 */
export function useReplanPolling(fetchStatus: (id: string) => Promise<ReplanStatusResp>) {
  const polling = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null
  let generation = 0

  function stop(): void {
    generation += 1
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
    polling.value = false
  }

  function start(
    id: string,
    onFinish: (status: ReplanStatusResp) => void,
    onError: (message: string) => void,
  ): void {
    stop()
    const token = generation
    polling.value = true

    const tick = async (): Promise<void> => {
      try {
        const status = await fetchStatus(id)
        if (token !== generation) return
        if (status.status === 'succeeded' || status.status === 'failed') {
          stop()
          onFinish(status)
        }
      } catch (error) {
        if (token !== generation) return
        stop()
        onError(error instanceof Error ? error.message : '查询重新规划状态失败')
      }
    }

    // 任务可能已瞬间完成，先查一次再进入定频轮询，避免多等一个间隔
    void tick()
    timer = setInterval(() => {
      void tick()
    }, POLL_INTERVAL_MS)
  }

  onUnmounted(stop)

  return { polling, start, stop }
}
