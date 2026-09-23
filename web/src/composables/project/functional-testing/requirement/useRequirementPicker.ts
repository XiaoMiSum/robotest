import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchRequirements } from '@/services/project'
import type { RequirementPoolItem } from '@/types'

export function useRequirementPicker() {
  const loading = ref(false)
  const items = ref<RequirementPoolItem[]>([])
  const total = ref(0)
  const keyword = ref('')
  const pageNo = ref(1)
  const pageSize = ref(10)
  // 跨页保留选择：id → title，保序
  const selected = ref<Map<string, string>>(new Map())

  async function load() {
    loading.value = true
    try {
      const page = await fetchRequirements({
        keyword: keyword.value || undefined,
        // 选取器仅展示 active 条目：已归档不参与 AI 消费与文档关联（需求规格 3.2.4）
        status: 'active',
        pageNo: pageNo.value,
        pageSize: pageSize.value,
      })
      items.value = page.list
      total.value = page.total
      // 回填已选项标题：打开时 selected 仅占位空串，列表就绪后补全，避免确认后标签只显示关闭按钮
      const next = new Map(selected.value)
      for (const item of page.list) {
        if (next.has(item.id)) next.set(item.id, item.title)
      }
      selected.value = next
    } catch (err) {
      ElMessage.error(err instanceof Error ? err.message : '加载需求失败')
    } finally {
      loading.value = false
    }
  }

  function search(): void {
    pageNo.value = 1
    load()
  }

  function handlePageChange(page: number): void {
    pageNo.value = page
    load()
  }

  return {
    loading,
    items,
    total,
    keyword,
    pageNo,
    pageSize,
    selected,
    load,
    search,
    handlePageChange,
  }
}
