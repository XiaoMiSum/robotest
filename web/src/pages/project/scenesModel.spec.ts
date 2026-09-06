import { describe, expect, it } from 'vitest'
import {
  methodTagType,
  stepMethod,
  stepSqlType,
  sortedSteps,
  emptyStepDraft,
  createValidator,
  serializeValidators,
  createExtractor,
  serializeExtractors,
  stepValidatorsFromComponents,
  stepExtractorsFromComponents,
  createStepVariable,
  parseRequestConfig,
  buildEmptyRequestConfig,
  createExecutionConfig,
  STEP_TYPE_OPTIONS,
  VALIDATOR_TARGETS,
  VALIDATOR_CONDITIONS,
  EXTRACTOR_SOURCES,
  SCENE_BODY_TYPES,
  SCENE_RAW_SUBTYPES,
  mapBodyEditKind,
  parseBodyEditState,
  buildBodyFromEditState,
  resolveBodyContentType,
  syncBodyContentTypeHeader,
} from './scenesModel'
import type { ApiComponentListItem, ApiDebugKeyValue, ApiSceneStepItem } from '@/types'

function step(overrides: Partial<ApiSceneStepItem> = {}): ApiSceneStepItem {
  return {
    id: 'step-1',
    name: '步骤 1',
    stepType: 'http',
    sourceType: 'custom',
    sortOrder: 1,
    enabled: true,
    requestConfig: { method: 'GET', url: '/api/test' },
    variables: [],
    processors: [],
    validators: [],
    extractors: [],
    ...overrides,
  }
}

describe('scenesModel', () => {
  describe('methodTagType', () => {
    it('maps HTTP methods to Element Plus tag types', () => {
      expect(methodTagType('GET')).toBe('success')
      expect(methodTagType('POST')).toBe('primary')
      expect(methodTagType('PUT')).toBe('warning')
      expect(methodTagType('PATCH')).toBe('warning')
      expect(methodTagType('DELETE')).toBe('danger')
      expect(methodTagType('OPTIONS')).toBe('info')
    })

    it('is case-insensitive', () => {
      expect(methodTagType('get')).toBe('success')
      expect(methodTagType('post')).toBe('primary')
    })
  })

  describe('stepMethod', () => {
    it('extracts method from requestConfig and uppercases', () => {
      const s = step({ requestConfig: { method: 'post', url: '/x' } })
      expect(stepMethod(s)).toBe('POST')
    })

    it('returns null when requestConfig is missing method', () => {
      expect(stepMethod(step({ requestConfig: { url: '/x' } }))).toBeNull()
    })

    it('returns null when requestConfig is null', () => {
      expect(stepMethod(step({ requestConfig: null as unknown as Record<string, unknown> }))).toBeNull()
    })
  })

  describe('stepSqlType', () => {
    it('extracts SQL keyword from requestConfig.sql and uppercases', () => {
      const s = step({ stepType: 'jdbc', requestConfig: { sql: '  select * from user ' } })
      expect(stepSqlType(s)).toBe('SELECT')
    })

    it('matches insert / update / delete keywords', () => {
      expect(stepSqlType(step({ requestConfig: { sql: 'INSERT INTO t' } }))).toBe('INSERT')
      expect(stepSqlType(step({ requestConfig: { sql: 'update t set a=1' } }))).toBe('UPDATE')
      expect(stepSqlType(step({ requestConfig: { sql: 'DELETE FROM t' } }))).toBe('DELETE')
    })

    it('returns null when sql is missing or not a string', () => {
      expect(stepSqlType(step({ requestConfig: {} }))).toBeNull()
      expect(stepSqlType(step({ requestConfig: { sql: 123 } }))).toBeNull()
      expect(stepSqlType(step({ requestConfig: null as unknown as Record<string, unknown> }))).toBeNull()
    })
  })

  describe('sortedSteps', () => {
    it('returns a new array sorted by sortOrder ascending', () => {
      const a = step({ id: 'a', sortOrder: 3 })
      const b = step({ id: 'b', sortOrder: 1 })
      const c = step({ id: 'c', sortOrder: 2 })
      const sorted = sortedSteps([a, b, c])
      expect(sorted.map((s) => s.id)).toEqual(['b', 'c', 'a'])
    })

    it('does not mutate the input', () => {
      const arr = [step({ sortOrder: 2 }), step({ sortOrder: 1 })]
      sortedSteps(arr)
      expect(arr[0].sortOrder).toBe(2)
    })
  })

  describe('emptyStepDraft', () => {
    it('returns http step with empty name', () => {
      const draft = emptyStepDraft()
      expect(draft.stepType).toBe('http')
      expect(draft.name).toBe('')
      expect(draft.requestConfig.method).toBe('GET')
    })
  })

  describe('createValidator', () => {
    it('returns a validator with unique id and default target', () => {
      const v1 = createValidator()
      const v2 = createValidator()
      expect(v1.id).not.toBe(v2.id)
      expect(v1.enabled).toBe(true)
      expect(v1.target).toBe('status_code')
      expect(v1.condition).toBe('equals')
    })
  })

  describe('serializeValidators', () => {
    it('filters out validators without a target and auto-fills name', () => {
      const items = [
        { ...createValidator(), target: '' },
        { ...createValidator(), target: 'status_code' },
        { ...createValidator(), target: 'status_code', name: '状态码校验' },
      ]
      const result = serializeValidators(items)
      expect(result).toHaveLength(2)
      expect(result[0].name).toBe('断言 status_code')
      expect(result[1].name).toBe('状态码校验')
    })

    it('returns a copy of each item', () => {
      const items = [{ ...createValidator(), name: 'test' }]
      const result = serializeValidators(items)
      expect(result[0]).toEqual(items[0])
      expect(result[0]).not.toBe(items[0])
    })
  })

  describe('createExtractor', () => {
    it('returns an extractor with unique id and default source', () => {
      const e = createExtractor()
      expect(e.source).toBe('json_field')
      expect(e.enabled).toBe(true)
    })
  })

  describe('serializeExtractors', () => {
    it('filters out extractors without variableName and auto-fills name', () => {
      const items = [
        createExtractor(),
        { ...createExtractor(), source: 'json_field', variableName: 'token_value' },
        { ...createExtractor(), source: 'response_header', variableName: '' },
        { ...createExtractor(), source: '', variableName: 'orphan' },
      ]
      const result = serializeExtractors(items)
      expect(result).toHaveLength(1)
      expect(result[0].variableName).toBe('token_value')
      expect(result[0].name).toBe('提取器 json_field')
    })
  })

  describe('stepValidatorsFromComponents', () => {
    it('maps validator assets to step validator rows with fallbacks', () => {
      const rows = stepValidatorsFromComponents([
        { name: 'code 校验', config: JSON.stringify({ target: 'json_field', condition: 'contains', expression: '$.code', expected: '200' }) } as unknown as ApiComponentListItem,
        { name: '空配置', config: null } as unknown as ApiComponentListItem,
      ])
      expect(rows[0]).toMatchObject({ name: 'code 校验', enabled: true, target: 'json_field', condition: 'contains', expression: '$.code', expected: '200' })
      expect(rows[1]).toMatchObject({ name: '空配置', enabled: true, target: 'status_code', condition: 'equals', expression: '', expected: '' })
      expect(rows[0].id).toBeTruthy()
      expect(rows[1].id).not.toBe(rows[0].id)
    })
  })

  describe('stepExtractorsFromComponents', () => {
    it('maps extractor assets to step extractor rows with fallbacks', () => {
      const rows = stepExtractorsFromComponents([
        { name: '取 token', config: JSON.stringify({ source: 'regex', expression: 'token=(.+?)', variableName: 'token' }) } as unknown as ApiComponentListItem,
        { name: '空配置', config: '' } as unknown as ApiComponentListItem,
      ])
      expect(rows[0]).toMatchObject({ name: '取 token', enabled: true, source: 'regex', expression: 'token=(.+?)', variableName: 'token' })
      expect(rows[1]).toMatchObject({ name: '空配置', enabled: true, source: 'json_field', expression: '', variableName: '' })
    })
  })

  describe('createStepVariable', () => {
    it('returns a step variable with default source', () => {
      const v = createStepVariable()
      expect(v.source).toBe('custom')
      expect(v.name).toBe('')
    })
  })

  describe('parseRequestConfig', () => {
    it('returns empty object for null/undefined', () => {
      expect(parseRequestConfig(null)).toEqual({})
      expect(parseRequestConfig(undefined)).toEqual({})
    })

    it('passes through valid config', () => {
      const config = { method: 'POST', url: '/api' }
      expect(parseRequestConfig(config)).toBe(config)
    })

    it('returns empty for non-object', () => {
      expect(parseRequestConfig('string' as unknown as Record<string, unknown>)).toEqual({})
    })
  })

  describe('buildEmptyRequestConfig', () => {
    it('returns a complete empty config', () => {
      const config = buildEmptyRequestConfig()
      expect(config.method).toBe('GET')
      expect(config.headers).toEqual([])
      expect(config.params).toEqual([])
      expect(config.body).toEqual({ type: 'none', content: null })
      expect(config.timeout).toBe(30000)
    })
  })

  describe('createExecutionConfig', () => {
    it('returns defaults', () => {
      const c = createExecutionConfig()
      expect(c.timeout).toBe(30000)
      expect(c.retryCount).toBe(0)
      expect(c.conditionExpression).toBe('')
    })
  })

  describe('constants', () => {
    it('STEP_TYPE_OPTIONS has http and jdbc', () => {
      expect(STEP_TYPE_OPTIONS.map((o) => o.value)).toEqual(['http', 'jdbc'])
    })

    it('VALIDATOR_TARGETS covers expected targets', () => {
      const values = VALIDATOR_TARGETS.map((t) => t.value)
      expect(values).toContain('status_code')
      expect(values).toContain('json_field')
      expect(values).toContain('groovy')
    })

    it('VALIDATOR_CONDITIONS covers standard comparison operators', () => {
      const values = VALIDATOR_CONDITIONS.map((c) => c.value)
      expect(values).toContain('equals')
      expect(values).toContain('contains')
      expect(values).toContain('matches_regex')
    })

    it('EXTRACTOR_SOURCES covers standard sources', () => {
      const values = EXTRACTOR_SOURCES.map((s) => s.value)
      expect(values).toContain('json_field')
      expect(values).toContain('regex')
      expect(values).toContain('full_body')
    })
  })

  describe('请求体（对齐快速调试三态）', () => {
    it('SCENE_BODY_TYPES 仅包含 none / urlencoded / raw', () => {
      expect(SCENE_BODY_TYPES.map((t) => t.value)).toEqual(['none', 'urlencoded', 'raw'])
    })

    it('SCENE_RAW_SUBTYPES 覆盖调试域全部子类型', () => {
      expect(SCENE_RAW_SUBTYPES).toEqual(['text', 'json', 'xml', 'html', 'javascript'])
    })

    it('mapBodyEditKind 映射落库 type → 编辑态三态', () => {
      expect(mapBodyEditKind('form')).toBe('urlencoded')
      expect(mapBodyEditKind('json')).toBe('raw')
      expect(mapBodyEditKind('raw')).toBe('raw')
      expect(mapBodyEditKind('none')).toBe('none')
      expect(mapBodyEditKind(undefined)).toBe('none')
    })

    describe('parseBodyEditState', () => {
      it('form → urlencoded 行', () => {
        const state = parseBodyEditState({
          type: 'form',
          content: [
            { key: 'a', value: '1', enabled: true },
            { key: 'b', value: '2', enabled: false },
          ],
        })
        expect(state.kind).toBe('urlencoded')
        expect(state.urlencodedRows).toEqual([
          { key: 'a', value: '1', enabled: true },
          { key: 'b', value: '2', enabled: false },
        ])
      })

      it('json → raw(json) 并格式化对象为文本', () => {
        const state = parseBodyEditState({ type: 'json', content: { code: 200 } })
        expect(state.kind).toBe('raw')
        expect(state.rawSubtype).toBe('json')
        expect(state.rawText).toContain('"code": 200')
      })

      it('raw 字符串按 JSON 前缀推断 json 子类型，否则 text', () => {
        expect(parseBodyEditState({ type: 'raw', content: '{"a":1}' }).rawSubtype).toBe('json')
        expect(parseBodyEditState({ type: 'raw', content: 'hello' }).rawSubtype).toBe('text')
      })

      it('none/null → none', () => {
        expect(parseBodyEditState(undefined).kind).toBe('none')
        expect(parseBodyEditState({ type: 'none', content: null }).kind).toBe('none')
      })
    })

    describe('buildBodyFromEditState', () => {
      it('urlencoded → form 三元组（去掉禁用行）', () => {
        const { body } = buildBodyFromEditState({
          kind: 'urlencoded',
          rawSubtype: 'text',
          rawText: '',
          urlencodedRows: [
            { key: 'a', value: '1', enabled: true },
            { key: 'b', value: '2', enabled: false },
          ],
        })
        expect(body).toEqual({ type: 'form', content: [{ key: 'a', value: '1', enabled: true }] })
      })

      it('raw+json → json 解析对象；空文本 → 空对象', () => {
        const parsed = buildBodyFromEditState({ kind: 'raw', rawSubtype: 'json', rawText: '{"a":1}', urlencodedRows: [] })
        expect(parsed.body).toEqual({ type: 'json', content: { a: 1 } })
        const emptyObj = buildBodyFromEditState({ kind: 'raw', rawSubtype: 'json', rawText: '  ', urlencodedRows: [] })
        expect(emptyObj.body).toEqual({ type: 'json', content: {} })
      })

      it('raw+json 解析失败返回错误而非 body', () => {
        const result = buildBodyFromEditState({ kind: 'raw', rawSubtype: 'json', rawText: '{bad', urlencodedRows: [] })
        expect(result.body).toBeUndefined()
        expect(result.error).toMatch(/JSON 请求体格式非法/)
      })

      it('raw+text → raw 原始文本', () => {
        const { body } = buildBodyFromEditState({ kind: 'raw', rawSubtype: 'text', rawText: 'plain', urlencodedRows: [] })
        expect(body).toEqual({ type: 'raw', content: 'plain' })
      })

      it('none → none', () => {
        const { body } = buildBodyFromEditState({ kind: 'none', rawSubtype: 'json', rawText: '', urlencodedRows: [] })
        expect(body).toEqual({ type: 'none', content: null })
      })
    })

    describe('Content-Type 联动（对齐快速调试）', () => {
      it('resolveBodyContentType 返回对应 Content-Type；none/text 不注入', () => {
        expect(resolveBodyContentType({ kind: 'urlencoded', rawSubtype: 'text', rawText: '', urlencodedRows: [] }))
          .toBe('application/x-www-form-urlencoded')
        expect(resolveBodyContentType({ kind: 'raw', rawSubtype: 'json', rawText: '', urlencodedRows: [] }))
          .toBe('application/json')
        expect(resolveBodyContentType({ kind: 'raw', rawSubtype: 'xml', rawText: '', urlencodedRows: [] }))
          .toBe('application/xml')
        expect(resolveBodyContentType({ kind: 'raw', rawSubtype: 'text', rawText: '', urlencodedRows: [] }))
          .toBeUndefined()
        expect(resolveBodyContentType({ kind: 'none', rawSubtype: 'text', rawText: '', urlencodedRows: [] }))
          .toBeUndefined()
      })

      function header(k: string, v = k): ApiDebugKeyValue {
        return { key: k, value: v, enabled: true }
      }

      it('syncBodyContentTypeHeader 注入 Content-Type 头', () => {
        const result = syncBodyContentTypeHeader([], { kind: 'urlencoded', rawSubtype: 'text', rawText: '', urlencodedRows: [] })
        expect(result).toEqual([{ key: 'Content-Type', value: 'application/x-www-form-urlencoded', enabled: true }])
      })

      it('syncBodyContentTypeHeader 替换已有 Content-Type（大小写不敏感）并保持其他头', () => {
        const headers = [header('content-type', 'text/plain'), header('Accept', 'application/json')]
        const result = syncBodyContentTypeHeader(headers, { kind: 'raw', rawSubtype: 'json', rawText: '', urlencodedRows: [] })
        expect(result).toEqual([
          { key: 'Content-Type', value: 'application/json', enabled: true },
          header('Accept', 'application/json'),
        ])
      })

      it('syncBodyContentTypeHeader 移除 Content-Type（none/text）', () => {
        const headers = [header('Content-Type'), header('Accept')]
        const result = syncBodyContentTypeHeader(headers, { kind: 'none', rawSubtype: 'text', rawText: '', urlencodedRows: [] })
        expect(result).toEqual([header('Accept')])
      })
    })
  })
})
