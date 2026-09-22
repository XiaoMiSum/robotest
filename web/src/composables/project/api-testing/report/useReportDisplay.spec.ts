import { describe, expect, it } from 'vitest'
import type { ApiReportStepResult } from '@/types'
import { useReportDisplay } from './useReportDisplay'

function makeStep(overrides?: Partial<ApiReportStepResult>): ApiReportStepResult {
  return {
    stepId: 'step-1',
    name: '步骤1',
    status: 'success',
    ...overrides,
  }
}

describe('useReportDisplay', () => {
  describe('statusLabel', () => {
    it('maps success to 成功', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('success')).toBe('成功')
    })

    it('maps passed to 成功', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('passed')).toBe('成功')
    })

    it('maps failed to 失败', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('failed')).toBe('失败')
    })

    it('maps skipped to 跳过', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('skipped')).toBe('跳过')
    })

    it('maps error to 错误', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('error')).toBe('错误')
    })

    it('maps not_executed to 未执行', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('not_executed')).toBe('未执行')
    })

    it('returns raw status for unknown status', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel('unknown')).toBe('unknown')
    })

    it('returns - for null', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { statusLabel } = useReportDisplay()
      expect(statusLabel(undefined)).toBe('-')
    })
  })

  describe('assertionLabel', () => {
    it('maps passed to 通过', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel('passed')).toBe('通过')
    })

    it('maps success to 通过', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel('success')).toBe('通过')
    })

    it('maps failed to 失败', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel('failed')).toBe('失败')
    })

    it('maps skipped to 跳过', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel('skipped')).toBe('跳过')
    })

    it('returns raw status for unknown value', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel('unknown')).toBe('unknown')
    })

    it('returns - for null', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { assertionLabel } = useReportDisplay()
      expect(assertionLabel(undefined)).toBe('-')
    })
  })

  describe('badgeClass', () => {
    it('returns rr-badge--ok for success', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass('success')).toBe('rr-badge--ok')
    })

    it('returns rr-badge--ok for passed', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass('passed')).toBe('rr-badge--ok')
    })

    it('returns rr-badge--fail for failed', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass('failed')).toBe('rr-badge--fail')
    })

    it('returns rr-badge--skip for error', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass('error')).toBe('rr-badge--skip')
    })

    it('returns rr-badge--skip for null', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass(null)).toBe('rr-badge--skip')
    })

    it('returns rr-badge--skip for undefined', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass(undefined)).toBe('rr-badge--skip')
    })

    it('returns rr-badge--skip for unknown status', () => {
      const { badgeClass } = useReportDisplay()
      expect(badgeClass('skipped')).toBe('rr-badge--skip')
    })
  })

  describe('methodChipClass', () => {
    it('returns rr-chip--get for GET', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('GET')).toBe('rr-chip--get')
    })

    it('normalizes lowercase get', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('get')).toBe('rr-chip--get')
    })

    it('returns rr-chip--post for POST', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('POST')).toBe('rr-chip--post')
    })

    it('normalizes mixed case post', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('PoSt')).toBe('rr-chip--post')
    })

    it('returns rr-chip--put for PUT', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('PUT')).toBe('rr-chip--put')
    })

    it('returns rr-chip--delete for DELETE', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('DELETE')).toBe('rr-chip--delete')
    })

    it('returns empty string for unknown method', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass('PATCH')).toBe('')
    })

    it('returns empty string for null', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass(null)).toBe('')
    })

    it('returns empty string for undefined', () => {
      const { methodChipClass } = useReportDisplay()
      expect(methodChipClass(undefined)).toBe('')
    })
  })

  describe('paneTone', () => {
    it('returns empty for empty list', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([])).toBe('empty')
    })

    it('returns fail when any step is failed', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'success' }),
        makeStep({ status: 'failed' }),
      ])).toBe('fail')
    })

    it('returns fail when any step is error', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'success' }),
        makeStep({ status: 'error' }),
      ])).toBe('fail')
    })

    it('returns ok when all steps are success', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'success' }),
        makeStep({ status: 'success' }),
      ])).toBe('ok')
    })

    it('returns ok when all steps are passed', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'passed' }),
        makeStep({ status: 'passed' }),
      ])).toBe('ok')
    })

    it('returns ok when all steps are success or passed', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'success' }),
        makeStep({ status: 'passed' }),
      ])).toBe('ok')
    })

    it('returns skip when steps have skipped status', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'skipped' }),
        makeStep({ status: 'success' }),
      ])).toBe('skip')
    })

    it('returns skip when all steps are skipped', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'skipped' }),
      ])).toBe('skip')
    })

    it('returns fail even with success steps if failed exists', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([
        makeStep({ status: 'success' }),
        makeStep({ status: 'success' }),
        makeStep({ status: 'failed' }),
      ])).toBe('fail')
    })

    it('returns skip for single skipped step', () => {
      const { paneTone } = useReportDisplay()
      expect(paneTone([makeStep({ status: 'skipped' })])).toBe('skip')
    })
  })

  describe('pretty', () => {
    it('returns - for null', () => {
      const { pretty } = useReportDisplay()
      expect(pretty(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { pretty } = useReportDisplay()
      expect(pretty(undefined)).toBe('-')
    })

    it('returns raw string when not JSON', () => {
      const { pretty } = useReportDisplay()
      expect(pretty('hello')).toBe('hello')
    })

    it('formats valid JSON string', () => {
      const { pretty } = useReportDisplay()
      expect(pretty('{"a":1}')).toBe(JSON.stringify({ a: 1 }, null, 2))
    })

    it('returns raw string when JSON.parse fails', () => {
      const { pretty } = useReportDisplay()
      expect(pretty('{invalid')).toBe('{invalid')
    })

    it('formats object as JSON', () => {
      const { pretty } = useReportDisplay()
      expect(pretty({ x: 1 })).toBe(JSON.stringify({ x: 1 }, null, 2))
    })

    it('formats number as JSON', () => {
      const { pretty } = useReportDisplay()
      expect(pretty(42)).toBe('42')
    })

    it('formats boolean as JSON', () => {
      const { pretty } = useReportDisplay()
      expect(pretty(true)).toBe('true')
    })

    it('formats array as JSON', () => {
      const { pretty } = useReportDisplay()
      expect(pretty([1, 2])).toBe('[\n  1,\n  2\n]')
    })

    it('formats nested JSON string', () => {
      const { pretty } = useReportDisplay()
      const input = JSON.stringify({ a: { b: [1, 2] } })
      expect(pretty(input)).toBe(JSON.stringify({ a: { b: [1, 2] } }, null, 2))
    })

    it('formats empty object', () => {
      const { pretty } = useReportDisplay()
      expect(pretty({})).toBe('{}')
    })

    it('formats empty array', () => {
      const { pretty } = useReportDisplay()
      expect(pretty([])).toBe('[]')
    })
  })

  describe('displayValue', () => {
    it('returns - for null', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue(null)).toBe('-')
    })

    it('returns - for undefined', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue(undefined)).toBe('-')
    })

    it('returns string as-is', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue('text')).toBe('text')
    })

    it('returns stringified object', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue({ a: 1 })).toBe('{"a":1}')
    })

    it('returns stringified array', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue([1, 2])).toBe('[1,2]')
    })

    it('returns stringified number', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue(42)).toBe('42')
    })

    it('returns stringified boolean', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue(false)).toBe('false')
    })

    it('returns empty string for empty object', () => {
      const { displayValue } = useReportDisplay()
      expect(displayValue({})).toBe('{}')
    })
  })

  describe('procUrl', () => {
    it('returns url string when request.url exists', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: { method: 'GET', url: 'https://example.com/api' } })
      expect(procUrl(step)).toBe('https://example.com/api')
    })

    it('returns empty string when request is null', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: null })
      expect(procUrl(step)).toBe('')
    })

    it('returns empty string when request is undefined', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: undefined })
      expect(procUrl(step)).toBe('')
    })

    it('returns empty string when request.url is null', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: { method: 'GET', url: null } })
      expect(procUrl(step)).toBe('')
    })

    it('returns empty string when request.url is undefined', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: { method: 'GET', url: undefined } })
      expect(procUrl(step)).toBe('')
    })

    it('stringifies numeric url', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: { method: 'GET', url: 123 } })
      expect(procUrl(step)).toBe('123')
    })

    it('stringifies object url via String()', () => {
      const { procUrl } = useReportDisplay()
      const step = makeStep({ request: { method: 'GET', url: { host: 'a.com', port: 8080 } } })
      expect(procUrl(step)).toBe('[object Object]')
    })
  })

  describe('headersEntries', () => {
    it('returns entries for non-null headers', () => {
      const { headersEntries } = useReportDisplay()
      const result = headersEntries({ 'content-type': 'application/json', 'x-token': 'abc' })
      expect(result).toEqual([['content-type', 'application/json'], ['x-token', 'abc']])
    })

    it('returns empty array for null', () => {
      const { headersEntries } = useReportDisplay()
      expect(headersEntries(null)).toEqual([])
    })

    it('returns empty array for undefined', () => {
      const { headersEntries } = useReportDisplay()
      expect(headersEntries(undefined)).toEqual([])
    })

    it('returns empty array for empty object', () => {
      const { headersEntries } = useReportDisplay()
      expect(headersEntries({})).toEqual([])
    })
  })
})
