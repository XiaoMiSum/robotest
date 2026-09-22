import { ref, watch, computed, type Ref } from 'vue'
import type { ApiSceneDetail, ApiSceneVariableItem, ApiDebugKeyValue, ApiVariable } from '@/types'
import { fetchEnvironmentDetail } from '@/services/project/environment'
import { ElMessage } from 'element-plus'

/**
 * 场景变量管理 + 变量助手（从 SceneEditorPage 提取）。
 * 依赖 detail ref 和 environmentOptions ref，返回变量编辑态与序列化方法。
 */
export function useSceneVariables(
  detail: Ref<ApiSceneDetail | null>,
  editEnvironmentId: Ref<string | null>,
  environmentOptions: Ref<{ id: string; name: string; isDefault?: boolean }[]>,
) {
  const editVariables = ref<(ApiDebugKeyValue & { description: string })[]>([])

  watch(detail, (d) => {
    if (d) {
      editVariables.value = d.variables.map((v: ApiSceneVariableItem) => ({
        key: v.name,
        value: v.value ?? '',
        description: v.description ?? '',
        enabled: true,
      }))
    }
  })

  /** 序列化为提交结构：key 即变量名，过滤空行与空名 */
  function sceneVariablePayload(): ApiSceneVariableItem[] {
    return editVariables.value
      .filter((v) => v.key.trim())
      .map((v) => ({ name: v.key.trim(), value: v.value || undefined, description: v.description || undefined }))
  }

  /** 变量助手按 name/value 展示，类型对齐其 props 声明 */
  const sceneVariablesForHelper = computed(() =>
    editVariables.value
      .filter((v) => v.key.trim())
      .map((v) => ({ name: v.key, value: v.value, description: v.description ?? '' })),
  )

  // ==================== 变量助手弹窗 ====================
  const showFunctionHelper = ref(false)
  const showVariableHelper = ref(false)
  const envVariables = ref<ApiVariable[]>([])
  const envVariablesName = ref('')

  /** 打开变量助手：展示场景关联环境（未选则取默认环境）的变量 + 场景变量（含未保存） */
  async function openVariableHelper(): Promise<void> {
    envVariables.value = []
    let envId = editEnvironmentId.value
    if (!envId) {
      const def = environmentOptions.value.find((env) => env.isDefault)
      envId = def?.id ?? null
      envVariablesName.value = def?.name ?? ''
    } else {
      const env = environmentOptions.value.find((e) => e.id === envId)
      envVariablesName.value = env?.name ?? ''
    }
    if (envId) {
      try {
        const envDetail = await fetchEnvironmentDetail(envId)
        envVariables.value = envDetail.variables
      } catch {
        ElMessage.error('加载环境变量失败')
      }
    }
    showVariableHelper.value = true
  }

  return {
    editVariables,
    sceneVariablePayload,
    sceneVariablesForHelper,
    showFunctionHelper,
    showVariableHelper,
    envVariables,
    envVariablesName,
    openVariableHelper,
  }
}
