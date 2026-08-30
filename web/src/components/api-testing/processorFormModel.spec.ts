import { describe, expect, it } from 'vitest'
import {
  defaultComponentConfig,
  defaultProcessorConfig,
  extractorsFromComponents,
  isProcessorComponentType,
  kvRowsToMap,
  mapToKvRows,
  parseComponentConfig,
  parseJdbcProcessorForm,
  parseProcessorElement,
  toHttpConfig,
  toJdbcConfig,
  toProcessorElement,
} from './processorFormModel'
import type { ApiComponentListItem } from '@/types'

function extractorComponent(partial: Partial<ApiComponentListItem>): ApiComponentListItem {
  return {
    id: 'comp-1',
    scope: 'project',
    type: 'extractor',
    name: '提取 Token',
    description: null,
    sortOrder: 0,
    config: null,
    enabled: true,
    updatedAt: '2026-08-17 10:30:00',
    ...partial,
  }
}

describe('processorFormModel', () => {
  describe('isProcessorComponentType', () => {
    it('marks processor types only', () => {
      expect(isProcessorComponentType('preprocessor')).toBe(true)
      expect(isProcessorComponentType('postprocessor')).toBe(true)
      expect(isProcessorComponentType('validator')).toBe(false)
      expect(isProcessorComponentType('extractor')).toBe(false)
    })
  })

  describe('defaultProcessorConfig', () => {
    it('seeds enabled and sortOrder defaults', () => {
      expect(defaultProcessorConfig()).toEqual({ enabled: true, sortOrder: 0 })
    })
  })

  describe('defaultComponentConfig', () => {
    it('seeds only enabled default（排序号走顶层 sortOrder，不进入 config）', () => {
      expect(defaultComponentConfig()).toEqual({ enabled: true })
    })
  })

  describe('parseComponentConfig', () => {
    it('parses a valid JSON object config', () => {
      expect(parseComponentConfig('{"source":"header","variableName":"token"}')).toEqual({
        source: 'header',
        variableName: 'token',
      })
    })

    it('returns empty object for null / invalid / non-object config', () => {
      expect(parseComponentConfig(null)).toEqual({})
      expect(parseComponentConfig('not json')).toEqual({})
      expect(parseComponentConfig('"plain"')).toEqual({})
      expect(parseComponentConfig('[1,2]')).toEqual({})
    })
  })

  describe('extractorsFromComponents', () => {
    it('converts each selected component config into an independent extractor row', () => {
      const items = [
        extractorComponent({
          config: '{"enabled":true,"source":"header","expression":"X-Token","variableName":"token","description":"取 Token 头"}',
        }),
        extractorComponent({
          id: 'comp-2',
          config: '{"source":"body","expression":"$.data.id","variableName":"userId"}',
        }),
      ]
      expect(extractorsFromComponents(items)).toEqual([
        {
          enabled: true,
          source: 'header',
          expression: 'X-Token',
          variableName: 'token',
          description: '取 Token 头',
        },
        {
          enabled: true,
          source: 'body',
          expression: '$.data.id',
          variableName: 'userId',
          description: '',
        },
      ])
    })

    it('falls back to defaults for missing or broken fields', () => {
      const items = [
        extractorComponent({ config: '{"enabled":false,"variableName":"v"}' }),
        extractorComponent({ id: 'comp-2', config: null }),
      ]
      expect(extractorsFromComponents(items)).toEqual([
        { enabled: false, source: '', expression: '', variableName: 'v', description: '' },
        { enabled: true, source: '', expression: '', variableName: '', description: '' },
      ])
    })
  })

  describe('kvRowsToMap / mapToKvRows', () => {
    it('drops rows with empty keys', () => {
      expect(kvRowsToMap([{ key: '', value: 'x' }, { key: 'A', value: '1' }])).toEqual({ A: '1' })
    })

    it('round-trips a map through rows', () => {
      expect(mapToKvRows({ A: '1', B: '2' })).toEqual([{ key: 'A', value: '1' }, { key: 'B', value: '2' }])
      expect(mapToKvRows(undefined)).toEqual([])
    })
  })

  describe('toHttpConfig', () => {
    it('omits default method, empty fields and empty maps', () => {
      expect(toHttpConfig({
        method: 'GET',
        baseUrl: '',
        path: '',
        http2: false,
        headerRows: [],
        queryRows: [{ key: '', value: 'v' }],
        bodyKind: 'none',
        bodyText: '',
        formRows: [],
      })).toEqual({})
    })

    it('maps form editor rows to Ryze keys in order', () => {
      expect(toHttpConfig({
        method: 'POST',
        baseUrl: 'https://api.example.com',
        path: '/token',
        http2: true,
        headerRows: [{ key: 'Authorization', value: 'Bearer ${token}' }],
        queryRows: [{ key: 'rid', value: '1' }],
        bodyKind: 'json',
        bodyText: '{\n  "a": 1\n}',
        formRows: [],
      })).toEqual({
        method: 'POST',
        base_url: 'https://api.example.com',
        path: '/token',
        'http/2': true,
        headers: { Authorization: 'Bearer ${token}' },
        query: { rid: '1' },
        body: { a: 1 },
      })
    })

    it('compiles form body into data and raw body into a string', () => {
      expect(toHttpConfig({
        method: 'POST',
        baseUrl: '',
        path: '',
        http2: false,
        headerRows: [],
        queryRows: [],
        bodyKind: 'form',
        bodyText: '',
        formRows: [{ key: 'k', value: 'v' }],
      })).toEqual({ method: 'POST', data: { k: 'v' } })
      expect(toHttpConfig({
        method: 'POST',
        baseUrl: '',
        path: '',
        http2: false,
        headerRows: [],
        queryRows: [],
        bodyKind: 'raw',
        bodyText: 'plain text',
        formRows: [],
      })).toEqual({ method: 'POST', body: 'plain text' })
    })

    it('keeps unparseable json text as a string instead of dropping it', () => {
      expect(toHttpConfig({
        method: 'POST',
        baseUrl: '',
        path: '',
        http2: false,
        headerRows: [],
        queryRows: [],
        bodyKind: 'json',
        bodyText: '{ broken',
        formRows: [],
      })).toEqual({ method: 'POST', body: '{ broken' })
    })
  })

  describe('parseJdbcProcessorForm / toJdbcConfig', () => {
    it('reads datasource/sql/args and strips non-string args', () => {
      const element = {
        testclass: 'jdbc',
        config: { datasource: 'mysql_main', sql: 'SELECT 1', args: ['a', 1] },
      }
      expect(parseJdbcProcessorForm(element)).toEqual({ datasource: 'mysql_main', sql: 'SELECT 1', args: ['a'] })
      expect(toJdbcConfig({ datasource: ' mysql_main ', sql: ' SELECT 1 ', args: ['a', 'b'] })).toEqual({
        datasource: 'mysql_main',
        sql: 'SELECT 1',
        args: ['a', 'b'],
      })
    })

    it('omits empty jdbc fields', () => {
      expect(toJdbcConfig({ datasource: '', sql: '', args: [] })).toEqual({})
    })
  })

  describe('parseProcessorElement / toProcessorElement', () => {
    it('round-trips an http element into Ryze config and keeps platform overlays', () => {
      const element = {
        testclass: 'http',
        config: {
          method: 'POST',
          base_url: 'https://api.example.com',
          path: '/token',
          headers: { Authorization: 'B' },
          body: { a: 1 },
        },
        extractors: [{ enabled: true, source: 'body', expression: '$.token', variableName: 'token', description: '' }],
        enabled: false,
        sortOrder: 2,
      }
      expect(toProcessorElement(element, parseProcessorElement(element))).toEqual(element)
    })

    it('drops legacy flat keys and leaves overlay untouched', () => {
      const legacy = {
        handlerType: 'http',
        method: 'POST',
        url: 'https://api.example.com/token',
        contentType: 'application/json',
        enabled: true,
      }
      const compiled = toProcessorElement(legacy, { ...parseProcessorElement(legacy), testclass: 'http' })
      expect(compiled.testclass).toBe('http')
      expect(compiled.handlerType).toBeUndefined()
      expect(compiled.method).toBeUndefined()
      expect(compiled.url).toBeUndefined()
      expect(compiled.contentType).toBeUndefined()
      expect(compiled.enabled).toBe(true)
      expect(compiled).not.toHaveProperty('config.body')
    })

    it('defaults bodyKind from stored body type (object vs string prefix)', () => {
      const objElement = parseProcessorElement({
        testclass: 'http',
        config: { body: { a: 1 } },
      })
      expect(objElement.http.bodyKind).toBe('json')
      expect(objElement.http.bodyText).toContain('"a": 1')
      const rawElement = parseProcessorElement({
        testclass: 'http',
        config: { body: 'plain' },
      })
      expect(rawElement.http.bodyKind).toBe('raw')
      const jsonStringElement = parseProcessorElement({
        testclass: 'http',
        config: { body: '{"a":1}' },
      })
      expect(jsonStringElement.http.bodyKind).toBe('json')
    })

    it('prefers data over body when both present (body 优先级语义仅作用于编译方向)', () => {
      const element = parseProcessorElement({
        testclass: 'http',
        config: { data: { k: 'v' }, body: { a: 1 } },
      })
      expect(element.http.bodyKind).toBe('form')
      expect(element.http.formRows).toEqual([{ key: 'k', value: 'v' }])
    })
  })
})