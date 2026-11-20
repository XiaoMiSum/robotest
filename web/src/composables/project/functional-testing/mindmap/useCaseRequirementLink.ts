import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Minder, MinderNode } from '@/minder/types'
import { getRequirement } from '@/services/project'
import type { RequirementRef } from '@/types'
import type { RequirementPick } from '@/composables/project/requirement/useRequirementPicker'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

export interface CaseRequirementLinkOptions {
  caseId: () => string
  getMinder: () => Minder | null
  flushPersistence: () => void
}

/**
 * 脑图「关联需求」：作用于当前选中用例节点。关联存于节点 data 的 requirementId，
 * 随脑图持久化帧落库；回显按 id 取需求详情，无独立 REST 读写接口。
 */
export function useCaseRequirementLink(options: CaseRequirementLinkOptions) {
  const visible = ref(false)
  const saving = ref(false)
  const linked = ref<RequirementRef[]>([])

  let requestId = 0
  let targetCaseId = ''

  function findNode(id: string): MinderNode | null {
    const root = options.getMinder()?.getRoot()
    if (!root || !id) return null
    let found: MinderNode | null = null
    root.traverse((node) => {
      if (found === null && node.data['id'] === id) found = node
    })
    return found
  }

  async function open(): Promise<void> {
    const id = options.caseId()
    if (!id) return
    const raw = findNode(id)?.data['requirementId']
    const requirementId = typeof raw === 'string' && raw ? raw : null
    const seq = ++requestId
    targetCaseId = id
    linked.value = []
    if (!requirementId) {
      visible.value = true
      return
    }
    try {
      const detail = await getRequirement(requirementId)
      if (seq !== requestId || id !== options.caseId()) return
      linked.value = [{ id: detail.id, code: detail.code, title: detail.title }]
    } catch (err) {
      if (seq !== requestId) return
      linked.value = []
      ElMessage.error(errorMessage(err, '加载关联需求失败'))
      return
    }
    if (seq !== requestId) return
    visible.value = true
  }

  function close(): void {
    requestId += 1
    visible.value = false
  }

  /** 选取器回填格式：仅需 id / code / title 三元组 */
  function picks(): RequirementPick[] {
    return linked.value.map((item) => ({ id: item.id, code: item.code, title: item.title }))
  }

  function confirm(ids: string[]): void {
    const id = targetCaseId
    if (!id || saving.value) return
    const minder = options.getMinder()
    const node = findNode(id)
    if (!minder || !node) return
    saving.value = true
    try {
      node.setData('requirementId', ids[0] ?? null)
      // setData 不走内核命令，补发 contentchange 才能进入 history / Yjs / 持久化帧
      minder.refresh()
      minder.fire('contentchange')
      options.flushPersistence()
      visible.value = false
    } finally {
      saving.value = false
    }
  }

  return { visible, saving, linked, open, close, picks, confirm }
}
