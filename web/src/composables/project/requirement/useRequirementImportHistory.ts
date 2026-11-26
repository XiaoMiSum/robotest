import { computed, onMounted, ref } from 'vue'
import { fetchRequirementSplitRecords } from '@/services/project'
import { fetchMembers } from '@/services/workspace'
import type { RequirementSplitRecordItem, WorkspaceMember } from '@/types'
import {
  type AiTaskStatusMeta,
} from '@/composables/project/ai/taskPresentation'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** 历史页按过程页原型展示任务态文案，与任务中心通用徽标口径不同（交互 06 §2.6） */
const HISTORY_STATUS_META: Record<string, AiTaskStatusMeta> = {
  running: { label: '进行中', tagType: 'info' },
  proposals_ready: { label: '待采纳', tagType: 'warning' },
  closed: { label: '已完成', tagType: 'success' },
  aborted: { label: '已终止', tagType: 'danger' },
}

function historyStatusMeta(status: string): AiTaskStatusMeta {
  return HISTORY_STATUS_META[status] ?? { label: status, tagType: 'info' }
}

/** 导入历史行：联查派生展示字段由服务端下发，前端只做枚举映射与时间格式化 */
export interface RequirementImportHistoryRow extends RequirementSplitRecordItem {
  taskStatusMeta: AiTaskStatusMeta
  submitterName: string
}

export function useRequirementImportHistory() {
  const loading = ref(false)
  const hasLoaded = ref(false)
  const loadError = ref('')
  const rows = ref<RequirementImportHistoryRow[]>([])
  const total = ref(0)
  const pageNo = ref(1)
  const pageSize = ref(20)
  const memberNames = ref(new Map<string, string>())

  let requestId = 0

  const showSkeleton = computed(() => !hasLoaded.value && !loadError.value && loading.value)
  const showError = computed(() => loadError.value.length > 0 && !hasLoaded.value && !loading.value)
  const showRefreshError = computed(() => loadError.value.length > 0 && hasLoaded.value && !loading.value)

  async function loadMembers(): Promise<void> {
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberNames.value = new Map<string, string>(
        page.list.map((member: WorkspaceMember) => [member.userId, member.name]),
      )
    } catch {
      // 成员名属于展示增强，失败时回退发起人 ID 不阻塞历史列表
      memberNames.value = new Map()
    }
  }

  async function load(): Promise<void> {
    const id = ++requestId
    loading.value = true
    loadError.value = ''
    try {
      const page = await fetchRequirementSplitRecords({ pageNo: pageNo.value, pageSize: pageSize.value })
      if (id !== requestId) return
      rows.value = page.list.map((item) => ({
        ...item,
        taskStatusMeta: historyStatusMeta(item.taskStatus ?? ''),
        submitterName: item.submittedBy
          ? memberNames.value.get(item.submittedBy) ?? item.submittedBy
          : '系统',
      }))
      total.value = page.total
      hasLoaded.value = true
    } catch (err) {
      if (id !== requestId) return
      loadError.value = errorMessage(err, '加载导入历史失败')
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  function changePage(next: { pageNo: number; pageSize: number }): void {
    pageNo.value = next.pageNo
    pageSize.value = next.pageSize
    void load()
  }

  onMounted(() => {
    void loadMembers().then(() => load())
  })

  return {
    loading,
    hasLoaded,
    loadError,
    rows,
    total,
    pageNo,
    pageSize,
    showSkeleton,
    showError,
    showRefreshError,
    load,
    changePage,
  }
}
