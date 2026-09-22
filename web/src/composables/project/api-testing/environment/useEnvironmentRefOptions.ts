import { ref, watch, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchEnvironmentDetail } from '@/services/project/api-testing/environment'
import type { ApiHttpConfig, ApiDataSource } from '@/types'

/**
 * 环境 HTTP 配置 / 数据源下拉选项（从 SceneEditorPage 提取）。
 * 跟随 editEnvironmentId 变化自动加载，供处理器 ref 下拉使用。
 */
export function useEnvironmentRefOptions(editEnvironmentId: Ref<string | null>) {
  const httpRefOptions = ref<ApiHttpConfig[]>([])
  const dsRefOptions = ref<ApiDataSource[]>([])

  async function loadSceneRefOptions(environmentId: string | null | undefined): Promise<void> {
    if (!environmentId) {
      httpRefOptions.value = []
      dsRefOptions.value = []
      return
    }
    try {
      const envDetail = await fetchEnvironmentDetail(environmentId)
      httpRefOptions.value = envDetail.httpConfigs
      dsRefOptions.value = envDetail.dataSources
    } catch (err) {
      httpRefOptions.value = []
      dsRefOptions.value = []
      ElMessage.error(err instanceof Error ? err.message : '加载环境配置失败')
    }
  }

  watch(editEnvironmentId, (id) => { void loadSceneRefOptions(id) })

  return { httpRefOptions, dsRefOptions, loadSceneRefOptions }
}
