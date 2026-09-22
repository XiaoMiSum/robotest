import { ref, watch, computed } from 'vue'
import type { ApiReportStepResult } from '@/types'
import { useReportDisplay } from './useReportDisplay'

export function useReportProcessors(
  level: () => 'task' | 'scene',
  pre: () => ApiReportStepResult[],
  post: () => ApiReportStepResult[],
) {
  const activeTab = ref<'pre' | 'post'>('pre')

  watch(
    () => [pre().length, post().length] as const,
    ([preLen, postLen]) => {
      if (activeTab.value === 'pre' && preLen === 0 && postLen > 0) activeTab.value = 'post'
      if (activeTab.value === 'post' && postLen === 0 && preLen > 0) activeTab.value = 'pre'
    },
    { immediate: true },
  )

  const paneGroups = computed<Array<{ tab: 'pre' | 'post'; list: ApiReportStepResult[] }>>(() => {
    const groups: Array<{ tab: 'pre' | 'post'; list: ApiReportStepResult[] }> = []
    if (pre().length) groups.push({ tab: 'pre', list: pre() })
    if (post().length) groups.push({ tab: 'post', list: post() })
    return groups
  })

  const collapsedKeys = ref<Set<string>>(new Set())

  function procKey(step: ApiReportStepResult, tab: 'pre' | 'post', index: number): string {
    return step.stepId ?? `${level()}-${tab}-${index}`
  }

  function allProcKeys(): Set<string> {
    const keys = new Set<string>()
    paneGroups.value.forEach((group) => {
      group.list.forEach((step, index) => keys.add(procKey(step, group.tab, index)))
    })
    return keys
  }

  watch(
    () => [pre(), post()] as const,
    () => {
      collapsedKeys.value = allProcKeys()
    },
    { immediate: true, deep: true },
  )

  function isCollapsed(key: string): boolean {
    return collapsedKeys.value.has(key)
  }

  function toggleCollapse(key: string) {
    const set = new Set(collapsedKeys.value)
    if (set.has(key)) set.delete(key)
    else set.add(key)
    collapsedKeys.value = set
  }

  const chain = computed(() => {
    if (level() === 'task') {
      return [
        { label: '任务前置', key: 'task-pre' },
        { label: '场景前置', key: 'scene-pre' },
        { label: '步骤', key: 'steps' },
        { label: '场景后置', key: 'scene-post' },
        { label: '任务后置', key: 'task-post' },
      ]
    }
    return [
      { label: '场景前置', key: 'scene-pre' },
      { label: '步骤', key: 'steps' },
      { label: '场景后置', key: 'scene-post' },
    ]
  })

  const chainHighlightKey = computed(() => {
    if (level() === 'task') return activeTab.value === 'pre' ? 'task-pre' : 'task-post'
    return activeTab.value === 'pre' ? 'scene-pre' : 'scene-post'
  })

  const display = useReportDisplay()

  return {
    activeTab,
    paneGroups,
    collapsedKeys,
    procKey,
    isCollapsed,
    toggleCollapse,
    chain,
    chainHighlightKey,
    ...display,
  }
}
