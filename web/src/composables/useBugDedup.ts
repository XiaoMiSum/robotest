import { ref } from 'vue'
import { dedupBugs } from '@/services/ai'
import type { AiBugDedupItem } from '@/types'

/** 触发查重的最小标题长度（交互设计 3.1） */
const MIN_TITLE_LENGTH = 5

/** 查重执行体签名（测试注入替身用，默认走服务接口） */
export type BugDedupRunner = (payload: {
  title: string
  reproSteps?: string
  excludeBugId?: string
}) => Promise<{ semanticDegraded: boolean; items: AiBugDedupItem[] }>

export interface UseBugDedupOptions {
  /** 当前标题（<5 字符不发起查重） */
  title: () => string
  /** 重现步骤（可选） */
  reproSteps?: () => string | undefined
  /** 编辑既有缺陷时排除自身 */
  excludeBugId?: () => string | null | undefined
  /** 查重执行体（测试注入替身，避免依赖网络） */
  run?: BugDedupRunner
}

/**
 * 缺陷语义查重组合式函数（US-AI-009，交互设计 3.1）：
 * 随表单「AI 建议」按钮触发，与建议请求并发发起，无独立自动触发；
 * 并发以最后一次请求为准（过期响应丢弃），标题不足最小长度不发起。
 * 查重不阻断提交：失败静默清空提示区，不抛错。
 */
export function useBugDedup(options: UseBugDedupOptions) {
  const runner: BugDedupRunner = options.run ?? dedupBugs
  const items = ref<AiBugDedupItem[]>([])
  const semanticDegraded = ref(false)
  const loading = ref(false)

  // 并发过期响应丢弃：请求序号递增，仅接受最近一次的结果
  let requestSeq = 0

  /** 手动执行一次查重：结果写入响应式状态，失败静默清空（不抛错） */
  async function run(): Promise<void> {
    const title = options.title()
    if (title.trim().length < MIN_TITLE_LENGTH) {
      // 标题不足最小长度：清空残留结果，避免旧命中与新输入不匹配
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
    } catch {
      if (seq !== requestSeq) return
      // 查重不阻断提交：失败仅清空提示区
      items.value = []
      semanticDegraded.value = false
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  /** 表单初始化/重置时清空会话态（在途响应一并作废） */
  function reset(): void {
    requestSeq += 1
    items.value = []
    semanticDegraded.value = false
    loading.value = false
  }

  return {
    items,
    semanticDegraded,
    loading,
    run,
    reset,
  }
}
