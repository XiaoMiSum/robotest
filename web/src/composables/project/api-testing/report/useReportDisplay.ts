import type { ApiReportStepResult } from '@/types'

export function useReportDisplay() {
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
    if (status === 'success' || status === 'passed') return 'rr-badge--ok'
    if (status === 'failed') return 'rr-badge--fail'
    return 'rr-badge--skip'
  }

  function methodChipClass(method: string | null | undefined): string {
    const m = method?.toUpperCase() ?? ''
    if (m === 'GET') return 'rr-chip--get'
    if (m === 'POST') return 'rr-chip--post'
    if (m === 'PUT') return 'rr-chip--put'
    if (m === 'DELETE') return 'rr-chip--delete'
    return ''
  }

  function paneTone(list: ApiReportStepResult[]): 'ok' | 'fail' | 'skip' | 'empty' {
    if (!list.length) return 'empty'
    if (list.some((s) => s.status === 'failed' || s.status === 'error')) return 'fail'
    if (list.every((s) => s.status === 'success' || s.status === 'passed')) return 'ok'
    return 'skip'
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

  function procUrl(step: ApiReportStepResult): string {
    if (!step.request || step.request.url == null) return ''
    return String(step.request.url)
  }

  function headersEntries(headers: Record<string, unknown> | null | undefined): Array<[string, unknown]> {
    return headers ? Object.entries(headers) : []
  }

  return {
    statusLabel,
    assertionLabel,
    badgeClass,
    methodChipClass,
    paneTone,
    pretty,
    displayValue,
    procUrl,
    headersEntries,
  }
}
