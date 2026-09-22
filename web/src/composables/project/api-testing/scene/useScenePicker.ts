import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ApiScenePageItem, ProjectModule } from '@/types'
import { fetchScenePage } from '@/services/project/api-testing/scene'
import { fetchProjectModuleTree } from '@/services/project'
import { flattenModuleNames } from '@/composables/project/api-testing/interface/interfacesModel'

export function useScenePicker({
  visible,
  getSelectedIds,
  getTableRef,
}: {
  visible: Ref<boolean>
  getSelectedIds: () => string[] | undefined
  getTableRef: () =>
    | { clearSelection(): void; toggleRowSelection(row: ApiScenePageItem, selected?: boolean): void }
    | undefined
}) {
  const loading = ref(false)
  const items = ref<ApiScenePageItem[]>([])
  const total = ref(0)
  const keyword = ref('')
  const moduleId = ref<string | undefined>()
  const pageNo = ref(1)
  const pageSize = 20

  // 模块名映射与筛选选项
  const moduleOptions = ref<{ value: string; label: string }[]>([])
  const moduleNames = ref<Map<string, string>>(new Map())

  async function loadModules() {
    try {
      const tree: ProjectModule[] = await fetchProjectModuleTree('scene')
      moduleNames.value = flattenModuleNames(tree)
      moduleOptions.value = Array.from(moduleNames.value, ([value, label]) => ({ value, label }))
    } catch (error) {
      moduleNames.value = new Map()
      moduleOptions.value = []
      if (visible.value) ElMessage.error(error instanceof Error ? `模块加载失败：${error.message}` : '模块加载失败')
    }
  }

  async function load() {
    loading.value = true
    try {
      const params: { pageNo: number; pageSize: number; moduleId?: string; search?: string } = {
        pageNo: pageNo.value,
        pageSize,
      }
      if (keyword.value.trim()) params.search = keyword.value.trim()
      if (moduleId.value) params.moduleId = moduleId.value
      const page = await fetchScenePage(params)
      items.value = page.list
      total.value = page.total
      // 回勾已选：列表就绪后补勾，配合 reserve-selection 跨页保留
      const preselect = new Set(getSelectedIds() ?? [])
      requestAnimationFrame(() => {
        for (const row of items.value) {
          if (preselect.has(row.id)) getTableRef()?.toggleRowSelection(row, true)
        }
      })
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '场景加载失败')
    } finally {
      loading.value = false
    }
  }

  function search(): void {
    pageNo.value = 1
    void load()
  }

  function handleModuleChange(): void {
    search()
  }

  function handlePageChange(page: number): void {
    pageNo.value = page
    void load()
  }

  return {
    loading,
    items,
    total,
    keyword,
    moduleId,
    pageNo,
    pageSize,
    moduleOptions,
    moduleNames,
    loadModules,
    load,
    search,
    handleModuleChange,
    handlePageChange,
  }
}
