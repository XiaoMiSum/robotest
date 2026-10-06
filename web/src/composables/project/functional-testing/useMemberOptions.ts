import { ref } from 'vue'
import { fetchMembers } from '@/services/workspace'
import type { WorkspaceMember } from '@/types'

/**
 * 创建弹窗共用的成员候选（发起评审 / 新建计划 / 圈选确认）：
 * 打开时加载首页 100 条，失败降级空列表不阻塞表单（维持既有降级行为）。
 */
export function useMemberOptions() {
  const memberOptions = ref<WorkspaceMember[]>([])

  async function loadMemberOptions(): Promise<void> {
    try {
      const page = await fetchMembers({ pageNo: 1, pageSize: 100 })
      memberOptions.value = page.list
    } catch {
      // 成员候选加载失败不阻塞创建：参与人 / 负责人可创建后在详情页补充
    }
  }

  return { memberOptions, loadMemberOptions }
}
