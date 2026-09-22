import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { debugStep } from '@/services/project/scene'
import type { ApiSceneStepItem, ApiSceneStepDebugResp } from '@/types'

/**
 * 场景单步调试（从 SceneEditorPage 提取）。
 * 仅依赖 props.sceneId，零外部耦合。
 */
export function useSceneDebug(sceneId: () => string | undefined) {
  const debugResult = ref<ApiSceneStepDebugResp | null>(null)
  const showDebugResult = ref(false)
  const debugStepId = ref<string | null>(null)

  async function handleDebugStep(step: ApiSceneStepItem) {
    const id = sceneId()
    if (!id) return
    debugStepId.value = step.id
    try {
      const resp = await debugStep(id, step.id)
      debugResult.value = resp
      showDebugResult.value = true
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '调试失败')
    } finally {
      debugStepId.value = null
    }
  }

  function handleDraftDebugDisabled(_step: ApiSceneStepItem) {
    ElMessage.info('创建成功后可在编辑页单步调试')
  }

  return {
    debugResult,
    showDebugResult,
    debugStepId,
    handleDebugStep,
    handleDraftDebugDisabled,
  }
}
