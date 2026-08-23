import { describe, expect, it } from 'vitest'
import {
  buildSavePayload,
  createEmptyHttpConfig,
  detailToForm,
  DRIVER_OPTIONS,
  formatImportResult,
  isValidVariableName,
  parseVariablesJson,
  resolveEnvironmentError,
  sortEnvironments,
  toVariablePayloads,
  validateVariableRow,
} from './environmentsModel'
import type { ApiEnvironmentDetail, ApiEnvironmentListItem, ApiVariable } from '@/types'

function listItem(partial: Partial<ApiEnvironmentListItem>): ApiEnvironmentListItem {
  return {
    id: 'id',
    name: 'env',
    scope: 'project',
    isDefault: false,
    sortOrder: 0,
    variableCount: 0,
    dataSourceCount: 0,
    processorCount: 0,
    ...partial,
  }
}

function variable(partial: Partial<ApiVariable>): ApiVariable {
  return { id: 'v1', name: 'K', value: 'V', hasValue: true, type: 'text', ...partial }
}

describe('environmentsModel', () => {
  describe('resolveEnvironmentError', () => {
    it('maps environment error codes to actionable messages', () => {
      expect(resolveEnvironmentError({ code: 1000017401 })).toBe('环境名称重复，请更换名称')
      expect(resolveEnvironmentError({ code: 1000017402 })).toContain('解除引用')
      expect(resolveEnvironmentError({ code: 1000017404 })).toContain('定时任务')
      expect(resolveEnvironmentError({ code: 1000017405 })).toContain('不存在')
      expect(resolveEnvironmentError({ code: 1000017410 })).toBe('变量已存在')
    })

    it('appends backend detail for datasource connection failures', () => {
      const message = resolveEnvironmentError({ code: 1000017403, message: '数据源连接测试失败：Connection refused' })
      expect(message).toContain('数据源连接失败')
      expect(message).toContain('Connection refused')
    })

    it('falls back to backend message or generic text', () => {
      expect(resolveEnvironmentError({ message: '自定义错误' })).toBe('自定义错误')
      expect(resolveEnvironmentError(undefined)).toBe('操作失败，请稍后重试')
    })
  })

  describe('sortEnvironments', () => {
    it('puts default first then keeps sortOrder order', () => {
      const sorted = sortEnvironments([
        listItem({ id: 'c', name: '生产', sortOrder: 2 }),
        listItem({ id: 'b', name: '预发', sortOrder: 1 }),
        listItem({ id: 'a', name: '测试', sortOrder: 5, isDefault: true }),
      ])
      expect(sorted.map((item) => item.id)).toEqual(['a', 'b', 'c'])
    })
  })

  describe('validateVariableRow', () => {
    it('rejects invalid names and duplicates', () => {
      expect(validateVariableRow({ name: '', value: '', type: 'text' }, new Set())).toBe('变量名不能为空')
      expect(validateVariableRow({ name: 'bad-name', value: '', type: 'text' }, new Set())).toContain(
        '仅允许字母、数字与下划线',
      )
      expect(validateVariableRow({ name: 'A', value: '', type: 'text' }, new Set(['A']))).toBe('变量名已存在')
    })

    it('enforces number parsing but allows blank sensitive values', () => {
      expect(validateVariableRow({ name: 'N', value: 'abc', type: 'number' }, new Set())).toBe('数字类型取值非法')
      expect(validateVariableRow({ name: 'S', value: '', type: 'sensitive' }, new Set())).toBeNull()
      expect(isValidVariableName('BASE_URL_1')).toBe(true)
    })
  })

  describe('toVariablePayloads / parseVariablesJson', () => {
    it('keeps sensitive blanks so backend preserves previous cipher', () => {
      const payloads = toVariablePayloads([
        variable({ name: 'PWD', type: 'sensitive', value: '' }),
        variable({ name: 'URL', type: 'text', value: 'https://x' }),
      ])
      expect(payloads[0]).toEqual({ name: 'PWD', type: 'sensitive', description: undefined, value: undefined })
      expect(payloads[1].value).toBe('https://x')
    })

    it('parses variable json arrays with defaults for missing fields', () => {
      const parsed = parseVariablesJson('[{"name":"userId","value":"1","type":"number"}]')
      expect(parsed).toEqual({
        ok: true,
        rows: [{ name: 'userId', type: 'number', value: '1', description: undefined }],
      })
      expect(parseVariablesJson('not json').ok).toBe(false)
      expect(parseVariablesJson('{"name":"K"}').ok).toBe(false)
      expect(parseVariablesJson('[{"name":"bad name"}]').ok).toBe(false)
    })
  })

  describe('buildSavePayload', () => {
    it('round-trips full sub-resources so unedited sections are not wiped', () => {
      const detail = {
        id: 'e1',
        name: '测试环境',
        description: 'desc',
        scope: 'project',
        isDefault: false,
        sortOrder: 0,
        httpConfigs: [
          {
            id: 'h1',
            name: '内部 API',
            refName: 'http_1',
            baseUrl: 'https://staging.example.com',
            defaultMethod: 'GET',
            headers: [{ key: 'Authorization', value: 'Bearer x', enabled: false }],
            timeoutMs: 30000,
            connectTimeoutMs: 10000,
            followRedirects: true,
            verifySsl: true,
            isDefault: true,
          },
        ],
        variables: [variable({})],
        dataSources: [],
        processors: [],
      } as unknown as ApiEnvironmentDetail
      const form = detailToForm(detail)
      const payload = buildSavePayload(form, detail, detail.httpConfigs)

      expect(payload.name).toBe('测试环境')
      expect(payload.httpConfigs?.[0].headers?.[0]).toEqual({ key: 'Authorization', value: 'Bearer x', enabled: false })
      expect(payload.variables?.length).toBe(1)
      expect(createEmptyHttpConfig(1).isDefault).toBe(false)
    })

    it('allows overriding dataSources with locally edited rows', () => {
      const detail = {
        id: 'e1',
        name: 'n',
        scope: 'project',
        isDefault: false,
        sortOrder: 0,
        httpConfigs: [],
        variables: [],
        dataSources: [{ id: 'd1', name: 'old', refName: 'db', driver: 'x', url: 'jdbc:x' }],
        processors: [],
      } as unknown as ApiEnvironmentDetail
      const payload = buildSavePayload(detailToForm(detail), detail, [], [
        { name: 'new', refName: 'db2', driver: 'y', url: 'jdbc:y' },
      ])
      expect(payload.dataSources).toHaveLength(1)
      expect(payload.dataSources?.[0].name).toBe('new')
    })
  })

  describe('formatImportResult', () => {
    it('summarizes counts and empty state', () => {
      expect(formatImportResult({ createdCount: 1, overwrittenCount: 0, skippedCount: 2 })).toBe(
        '导入完成：新增 1 个、跳过 2 个',
      )
      expect(formatImportResult({ createdCount: 0, overwrittenCount: 0, skippedCount: 0 })).toBe('未发生任何变更')
    })
  })

  describe('DRIVER_OPTIONS', () => {
    it('covers the five required drivers with redis pending', () => {
      const labels = DRIVER_OPTIONS.map((option) => option.label)
      expect(labels.filter((label) => !label.includes('Redis'))).toHaveLength(4)
      expect(DRIVER_OPTIONS.find((option) => option.label.includes('Redis'))?.disabled).toBe(true)
      expect(DRIVER_OPTIONS.find((option) => option.label === 'PostgreSQL')?.urlExample).toContain('jdbc:postgresql')
    })
  })
})
