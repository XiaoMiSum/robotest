import { onBeforeUnmount, ref } from 'vue'
import { dedupBugs } from '@/services/ai'
import type { AiBugDedupItem } from '@/types'

/** 限流错误码（后端 6004）：收到后停用自动触发，避免自动查重持续撞限流（交互设计 3.1） */
const RATE_LIMIT_CODE = 6004
/** 自动触发的最小标题长度（交互设计 3.1） */
const MIN_TITLE_LENGTH = 5
/** 自动触发防抖窗口（交互设计 3.1） */
const DEBOUNCE_MS = 500

/** 查重执行体签名（测试注入替身用，默认走服务接口） */
export type BugDedupRunner = (payload: {
  title: string
  reproSteps?: string
  excludeBugId?: string
}) => Promise<{ semanticDegraded: boolean; items: AiBugDedupItem[] }>

export interface UseBugDedupOptions {
  /** 当前标题（≥5 字符才触发自动查重） */
  title: () => string
  reproSteps?: () => string
  /** 编辑既有缺陷时排除自身 */
  excludeBugId?: () => string | null | undefined
  /** 查重执行体（测试注入替身，避免依赖网络） */
  run?: BugDedupRunner
}

/**
 * 缺陷语义查重组合式函数（US-AI-009，交互设计 3.1）：
 * 自动触发防抖、并发以最后一次请求为准（过期响应丢弃）、6004 后停用自动触发仅保留手动。
 * 查重不阻断提交：失败静默清空提示区，不抛错。
 */
export function useBugDedup(options: UseBugDedupOptions) {
  const runner: BugDedupRunner = options.run ?? dedupBugs
  const items = ref<AiBugDedupItem[]>([])
  const semanticDegraded = ref(false)
  const loading = ref(false)
  /** 收到 6004 后停用自动触发，仅保留 [手动查重]（交互设计 3.1） */
  const autoStopped = ref(false)

  // 并发过期响应丢弃：请求序号递增，仅接受最近一次的结果
  let requestSeq = 0
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  function clearDebounce(): void {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
  }

  async function runDedup(source: 'auto' | 'manual'): Promise<void> {
    const title = options.title()
    if (title.trim().length < MIN_TITLE_LENGTH) {
      // 标题不足最小长度：清空残留结果，避免旧命中与新输入不匹配
      if (source === 'manual') return
      items.value = []
      semanticDegraded.value = false
      return
    }
    const seq = ++requestSeq
    loading.value = true
    try {
      const result = await runner({
        title,
        reproSteps: options.reproSteps?.() || undefined,
        excludeBugId: options.excludeBugId?.() || undefined,
      })
      if (seq !== requestSeq) return
      items.value = result.items
      semanticDegraded.value = result.semanticDegraded
    } catch (err) {
      if (seq !== requestSeq) return
      // 查重不阻断提交：失败仅清空提示区
      items.value = []
      semanticDegraded.value = false
      if (source === 'auto' && (err as { code?: number }).code === RATE_LIMIT_CODE) {
        autoStopped.value = true
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  /** 自动触发入口：500ms 防抖后执行（6004 后静默忽略） */
  function scheduleAuto(): void {
    clearDebounce()
    if (autoStopped.value) return
    debounceTimer = setTimeout(() => {
      void runDedup('auto')
    }, DEBOUNCE_MS)
  }

  /** 手动查重：立即执行，不受 6004 停用影响（兜底入口） */
  function manualRun(): void {
    clearDebounce()
    void runDedup('manual')
  }

  /** 表单初始化/重置时清空会话态（在途响应一并作废） */
  function reset(): void {
    clearDebounce()
    requestSeq += 1
    items.value = []
    semanticDegraded.value = false
    autoStopped.value = false
    loading.value = false
  }

  onBeforeUnmount(clearDebounce)

  return {
    items,
    semanticDegraded,
    loading,
    autoStopped,
    scheduleAuto,
    manualRun,
    reset,
  }
}
