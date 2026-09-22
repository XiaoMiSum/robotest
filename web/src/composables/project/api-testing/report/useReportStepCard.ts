import { ref, watch } from 'vue'
import type { ApiReportAssertion, ApiReportExtractor, ApiReportStepResult } from '@/types'

export function useReportStepCard(steps: () => ApiReportStepResult[]) {
  function stepKey(step: ApiReportStepResult, index: number): string {
    return step.stepId ?? `step-${index}`
  }

  function defaultOpenKeys(): Set<string> {
    return new Set(
      steps()
        .filter((s) => s.status === 'failed' || s.status === 'error')
        .map((step, index) => stepKey(step, index)),
    )
  }

  const openKeys = ref<Set<string>>(new Set(defaultOpenKeys()))

  watch(
    () => steps().length,
    () => {
      openKeys.value = defaultOpenKeys()
    },
  )

  function isOpen(key: string): boolean {
    return openKeys.value.has(key)
  }

  function onToggle(key: string, event: Event) {
    const open = (event.target as HTMLDetailsElement).open
    const set = new Set(openKeys.value)
    if (open) set.add(key)
    else set.delete(key)
    openKeys.value = set
  }

  const allOpen = ref(false)

  function toggleAll() {
    const set = new Set(openKeys.value)
    if (allOpen.value) {
      set.clear()
    } else {
      steps().forEach((step, index) => set.add(stepKey(step, index)))
    }
    openKeys.value = set
    allOpen.value = !allOpen.value
  }

  function statusLabel(status: string | null | undefined): string {
    const map: Record<string, string> = {
      success: '成功',
      passed: '成功',
      failed: '失败',
      skipped: '跳过',
      error: '错误',
      not_executed: '未执行',
    }
    return map[status ?? ''] ?? status ?? '-'
  }

  function assertionLabel(status: string | null | undefined): string {
    if (status === 'passed' || status === 'success') return '通过'
    if (status === 'failed') return '失败'
    if (status === 'skipped') return '跳过'
    return status ?? '-'
  }

  function badgeClass(status: string | null | undefined): string {
    if (status === 'success' || status === 'passed') return 'rsc-badge--ok'
    if (status === 'failed') return 'rsc-badge--fail'
    return 'rsc-badge--skip'
  }

  function methodChipClass(method: string | null | undefined): string {
    const m = method?.toUpperCase() ?? ''
    if (m === 'GET') return 'rsc-chip--get'
    if (m === 'POST') return 'rsc-chip--post'
    if (m === 'PUT') return 'rsc-chip--put'
    if (m === 'DELETE') return 'rsc-chip--delete'
    return ''
  }

  function pretty(value: unknown): string {
    if (value == null) return '-'
    if (typeof value === 'string') {
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch {
        return value
      }
    }
    return JSON.stringify(value, null, 2)
  }

  function displayValue(value: unknown): string {
    if (value == null) return '-'
    return typeof value === 'string' ? value : JSON.stringify(value)
  }

  function formatDuration(ms: number | null | undefined): string {
    if (ms == null) return '-'
    if (ms < 1000) return `${ms} ms`
    return `${(ms / 1000).toFixed(1)} s`
  }

  function headersEntries(headers: Record<string, unknown> | null | undefined): Array<[string, unknown]> {
    return headers ? Object.entries(headers) : []
  }

  function headerValue(headers: Record<string, unknown> | null | undefined, key: string): string | null {
    if (!headers) return null
    const hit = Object.keys(headers).find((k) => k.toLowerCase() === key.toLowerCase())
    if (hit == null) return null
    const v = headers[hit]
    return v == null ? null : typeof v === 'string' ? v : String(v)
  }

  function responseMeta(step: ApiReportStepResult): string {
    const contentType = headerValue(step.response?.headers ?? null, 'content-type') ?? ''
    return ['HTTP/1.1', contentType].filter(Boolean).join(' · ')
  }

  return {
    openKeys,
    allOpen,
    stepKey,
    isOpen,
    onToggle,
    toggleAll,
    statusLabel,
    assertionLabel,
    badgeClass,
    methodChipClass,
    pretty,
    displayValue,
    formatDuration,
    headersEntries,
    headerValue,
    responseMeta,
  }
}

export type { ApiReportAssertion, ApiReportExtractor }
