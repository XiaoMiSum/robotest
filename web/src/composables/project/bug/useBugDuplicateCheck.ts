import { ref } from 'vue'
import { checkBugDuplicates } from '@/services/project'
import type { BugDuplicateCheckItem } from '@/types'

const DETECT_LIMIT = 5

/**
 * 录入时重复检测（交互 3.1）：标题失焦触发，检索同项目相似存量缺陷。
 * 无命中或功能不可用（AI 未启用 / 向量未就绪 / 请求失败）时保持静默（items 为空），
 * 命中后由调用方弹窗展示；结果仅作建议，不自动合并、不改任何缺陷状态。
 */
export function useBugDuplicateCheck(
  title: () => string,
  steps: () => string,
  enabled: () => boolean,
) {
  const items = ref<BugDuplicateCheckItem[]>([])
  const loading = ref(false)
  /** 序号：连续触发时丢弃过期响应，以最后一次为准 */
  let seq = 0

  /** 触发一次检测：标题为空或能力未启用直接静默返回 */
  async function check(): Promise<void> {
    if (!enabled() || title().trim().length === 0) return
    const current = ++seq
    loading.value = true
    try {
      const resp = await checkBugDuplicates({
        title: title().trim(),
        steps: steps().trim() || undefined,
        limit: DETECT_LIMIT,
      })
      if (current !== seq) return
      // 高相似项前置，不单靠颜色表意（视觉 4 节）
      items.value = [...resp.list].sort((left, right) => right.similarity - left.similarity)
    } catch {
      // 向量未就绪（1000018258）与请求失败统一按功能不可用静默降级，不打扰录入（交互 2.4）
      items.value = []
    } finally {
      if (current === seq) loading.value = false
    }
  }

  /** 丢弃在途响应，避免卸载后写状态 */
  function dispose(): void {
    seq += 1
  }

  return { items, loading, check, dispose }
}
