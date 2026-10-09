import { ref, watch } from 'vue'
import { checkBugDuplicates } from '@/services/project'
import type { BugDuplicateCheckItem } from '@/types'

/** 防抖间隔：标题与重现步骤填写完成后实时检索（交互 3.1） */
const DEBOUNCE_MS = 800
/** 向量能力未就绪回执（详设 3.8 口径错误，入口据此置灰） */
const VECTOR_UNAVAILABLE_CODE = 1000018258
const DETECT_LIMIT = 5

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * 录入时重复检测（交互 3.1）：标题 + 重现步骤防抖实时检索同项目相似存量缺陷。
 * 检测结果仅作建议，不自动合并、不改任何缺陷状态。
 */
export function useBugDuplicateCheck(
  title: () => string,
  steps: () => string,
  enabled: () => boolean,
) {
  const items = ref<BugDuplicateCheckItem[]>([])
  const loading = ref(false)
  const checked = ref(false)
  /** 向量未就绪：入口置灰 + 提示（交互 2.4） */
  const vectorUnavailable = ref(false)
  const error = ref('')

  let timer: ReturnType<typeof setTimeout> | null = null
  /** 序号：范围（输入）变化后的过期响应丢弃标记 */
  let seq = 0

  async function run(): Promise<void> {
    const current = ++seq
    loading.value = true
    error.value = ''
    try {
      const resp = await checkBugDuplicates({
        title: title().trim(),
        steps: steps().trim() || undefined,
        limit: DETECT_LIMIT,
      })
      if (current !== seq) return
      items.value = resp.list
      // 高相似项前置，不单靠颜色表意（视觉 4 节）
      items.value.sort((left, right) => right.similarity - left.similarity)
      checked.value = true
      vectorUnavailable.value = false
    } catch (err) {
      if (current !== seq) return
      const code = (err as { code?: number }).code
      if (code === VECTOR_UNAVAILABLE_CODE) {
        // 未配置 / 重建中统一按向量能力未就绪降级，不作为错误弹出
        vectorUnavailable.value = true
        items.value = []
        checked.value = false
      } else {
        error.value = errorMessage(err, '重复检测失败')
      }
    } finally {
      if (current === seq) loading.value = false
    }
  }

  function schedule(): void {
    if (timer !== null) clearTimeout(timer)
    // 已判定向量未就绪：入口置灰，不再重复探测（交互 2.4）
    if (vectorUnavailable.value) return
    if (!enabled() || title().trim().length === 0) {
      items.value = []
      checked.value = false
      return
    }
    timer = setTimeout(() => {
      void run()
    }, DEBOUNCE_MS)
  }

  watch([title, steps], schedule, { immediate: true })

  function dispose(): void {
    if (timer !== null) clearTimeout(timer)
  }

  return { items, loading, checked, vectorUnavailable, error, dispose }
}
