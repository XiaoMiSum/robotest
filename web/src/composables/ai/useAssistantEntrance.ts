import { computed, ref, type ComputedRef } from 'vue'
import { fetchAiStatus } from '@/services/ai'
import { useAuthStore } from '@/stores/auth'

/**
 * 智能助手入口显隐：AI 总开关可用且具备 ai:task 权限（总册 4.5 / 交互 05 §1）。
 * 不在内部挂载生命周期，由入口组件挂载时调用 refresh()，便于测试与调用时机控制。
 */
export function useAssistantEntrance(): {
  visible: ComputedRef<boolean>
  refresh: () => Promise<void>
} {
  const authStore = useAuthStore()
  const aiAvailable = ref(false)

  async function refresh(): Promise<void> {
    try {
      aiAvailable.value = (await fetchAiStatus()).available
    } catch {
      // 状态接口失败按不可用处理：入口隐藏，不阻塞业务页（总册 4.5）
      aiAvailable.value = false
    }
  }

  const visible = computed(() => aiAvailable.value && authStore.hasPermission('ai:task'))

  return { visible, refresh }
}
