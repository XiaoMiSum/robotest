import { computed, ref, type ComputedRef } from 'vue'
import { useRoute } from 'vue-router'
import { fetchAiStatus } from '@/services/ai'

/**
 * 智能助手入口显隐：AI 总开关可用且处于项目上下文
 * （总册 4.5 / 交互 05 §1）：助手为项目级能力，目标项目取 X-Active-Project，
 * 故仅在 route.meta.mode=project 时提供上下文，空间级页面一律隐藏。
 * 不在内部挂载生命周期，由入口组件挂载时调用 refresh()，便于测试与调用时机控制。
 */
export function useAssistantEntrance(): {
  visible: ComputedRef<boolean>
  refresh: () => Promise<void>
} {
  const route = useRoute()
  const aiAvailable = ref(false)

  async function refresh(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏，不阻塞业务页（总册 4.5）
      aiAvailable.value = false
    }
  }

  const visible = computed(() => aiAvailable.value && route.meta.mode === 'project')

  return { visible, refresh }
}
