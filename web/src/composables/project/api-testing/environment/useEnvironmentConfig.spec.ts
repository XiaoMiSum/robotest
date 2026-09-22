import { ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiHeaderItem, ApiHttpConfigPayload } from '@/types'

const mocks = vi.hoisted(() => ({
  testHttpConfig: vi.fn<() => Promise<{ success: boolean; statusCode?: number; durationMs?: number; message?: string }>>(),
  testDataSourceConfig: vi.fn<() => Promise<{ success: boolean; databaseVersion?: string; message?: string }>>(),
  createEmptyHttpConfig: vi.fn<(index: number) => ApiHttpConfigPayload & { id?: string; headers: ApiHeaderItem[] }>(),
  resolveEnvironmentError: vi.fn<(err: unknown) => string>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/project/api-testing/environment', () => ({
  testHttpConfig: mocks.testHttpConfig,
  testDataSourceConfig: mocks.testDataSourceConfig,
}))

vi.mock('@/pages/project/api-testing/environment/environmentsModel', () => ({
  createEmptyHttpConfig: mocks.createEmptyHttpConfig,
  DRIVER_OPTIONS: [
    { driver: 'com.mysql.cj.jdbc.Driver', label: 'MySQL', urlExample: 'jdbc:mysql://localhost:3306/db' },
    { driver: 'org.postgresql.Driver', label: 'PostgreSQL', urlExample: 'jdbc:postgresql://localhost:5432/db' },
  ],
  resolveEnvironmentError: mocks.resolveEnvironmentError,
}))

import { useEnvironmentHttpConfig, useEnvironmentDatasource, type HttpConfigForm, type DsForm } from './useEnvironmentConfig'

function makeHttpConfig(id: string, overrides?: Partial<HttpConfigForm>): HttpConfigForm {
  return {
    id,
    name: `配置 ${id}`,
    refName: `http_${id}`,
    baseUrl: `http://example${id}.com`,
    isDefault: false,
    headers: [{ key: 'Content-Type', value: 'application/json', enabled: true }],
    ...overrides,
  }
}

function makeDsForm(id: string, overrides?: Partial<DsForm>): DsForm {
  return {
    id,
    name: `数据源 ${id}`,
    refName: `db_${id}`,
    driver: 'com.mysql.cj.jdbc.Driver',
    url: 'jdbc:mysql://localhost:3306/test',
    isDefault: false,
    ...overrides,
  }
}

describe('useEnvironmentHttpConfig', () => {
  let configForms: Ref<HttpConfigForm[]>
  let localIdFn: ReturnType<typeof vi.fn<() => string>>
  let idCounter: number

  beforeEach(() => {
    vi.clearAllMocks()
    idCounter = 100
    configForms = ref<HttpConfigForm[]>([])
    localIdFn = vi.fn(() => `local-${idCounter++}`)
    mocks.createEmptyHttpConfig.mockImplementation((index: number) => ({
      name: `配置 ${index}`,
      refName: `http_${index}`,
      baseUrl: '',
      isDefault: false,
      headers: [{ key: '', value: '', enabled: true }],
    }))
    mocks.resolveEnvironmentError.mockReturnValue('操作失败，请稍后重试')
  })

  describe('初始状态', () => {
    it('activeConfigId 初始为空字符串', () => {
      const { activeConfigId } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(activeConfigId.value).toBe('')
    })

    it('activeConfig 初始为 undefined', () => {
      const { activeConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(activeConfig.value).toBeUndefined()
    })

    it('testingHttpId 初始为空字符串', () => {
      const { testingHttpId } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(testingHttpId.value).toBe('')
    })

    it('orderedConfigForms 初始为空数组', () => {
      const { orderedConfigForms } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(orderedConfigForms.value).toEqual([])
    })
  })

  describe('orderedConfigForms', () => {
    it('默认配置排在前面', () => {
      configForms.value = [
        makeHttpConfig('1', { isDefault: false }),
        makeHttpConfig('2', { isDefault: true }),
        makeHttpConfig('3', { isDefault: false }),
      ]
      const { orderedConfigForms } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(orderedConfigForms.value.map((c) => c.id)).toEqual(['2', '1', '3'])
    })

    it('多个默认配置保持原顺序', () => {
      configForms.value = [
        makeHttpConfig('1', { isDefault: true }),
        makeHttpConfig('2', { isDefault: true }),
      ]
      const { orderedConfigForms } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(orderedConfigForms.value.map((c) => c.id)).toEqual(['1', '2'])
    })

    it('空列表返回空数组', () => {
      const { orderedConfigForms } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(orderedConfigForms.value).toEqual([])
    })
  })

  describe('selectConfig', () => {
    it('设置 activeConfigId 为选中配置的 id', () => {
      configForms.value = [makeHttpConfig('1'), makeHttpConfig('2')]
      const { selectConfig, activeConfigId, activeConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      selectConfig(configForms.value[1])
      expect(activeConfigId.value).toBe('2')
      expect(activeConfig.value).toBe(configForms.value[1])
    })

    it('activeConfig 在配置列表中找到匹配项', () => {
      const form = makeHttpConfig('abc')
      configForms.value = [form]
      const { selectConfig, activeConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      selectConfig(form)
      expect(activeConfig.value).toEqual(form)
    })

    it('activeConfig 在配置列表中找不到时返回 undefined', () => {
      configForms.value = [makeHttpConfig('1')]
      const { activeConfigId, activeConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      activeConfigId.value = 'not-exist'
      expect(activeConfig.value).toBeUndefined()
    })
  })

  describe('addHttpConfig', () => {
    it('调用 createEmptyHttpConfig 并推入新配置', () => {
      configForms.value = [makeHttpConfig('1')]
      const { addHttpConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      addHttpConfig()
      expect(mocks.createEmptyHttpConfig).toHaveBeenCalledWith(2)
      expect(configForms.value).toHaveLength(2)
    })

    it('新配置使用 localId 作为 id', () => {
      const { addHttpConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      addHttpConfig()
      expect(configForms.value[0].id).toBe('local-100')
      expect(localIdFn).toHaveBeenCalled()
    })

    it('新配置自动被选中', () => {
      const { addHttpConfig, activeConfigId } = useEnvironmentHttpConfig(configForms, localIdFn)
      addHttpConfig()
      expect(activeConfigId.value).toBe('local-100')
    })

    it('空列表时新增配置排在第一位', () => {
      const { addHttpConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      addHttpConfig()
      expect(configForms.value).toHaveLength(1)
      expect(configForms.value[0].name).toBe('配置 1')
    })

    it('克隆 headers 而非引用原始对象', () => {
      const original = { key: 'X-Test', value: 'val', enabled: true }
      mocks.createEmptyHttpConfig.mockReturnValue({
        name: 'new',
        refName: 'new_ref',
        baseUrl: '',
        isDefault: false,
        headers: [original],
      })
      const { addHttpConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      addHttpConfig()
      const added = configForms.value[0]
      expect(added.headers[0]).not.toBe(original)
      expect(added.headers[0]).toEqual(original)
    })
  })

  describe('removeHttpConfig', () => {
    it('从列表中移除指定配置', () => {
      configForms.value = [makeHttpConfig('1'), makeHttpConfig('2')]
      const { removeHttpConfig } = useEnvironmentHttpConfig(configForms, localIdFn)
      removeHttpConfig(configForms.value[0])
      expect(configForms.value).toHaveLength(1)
      expect(configForms.value[0].id).toBe('2')
    })

    it('移除当前选中配置时自动选中第一个', () => {
      configForms.value = [makeHttpConfig('1'), makeHttpConfig('2')]
      const { selectConfig, removeHttpConfig, activeConfigId } = useEnvironmentHttpConfig(configForms, localIdFn)
      selectConfig(configForms.value[0])
      removeHttpConfig(configForms.value[0])
      expect(activeConfigId.value).toBe('2')
    })

    it('移除所有配置时 activeConfigId 为空', () => {
      configForms.value = [makeHttpConfig('1')]
      const { selectConfig, removeHttpConfig, activeConfigId } = useEnvironmentHttpConfig(configForms, localIdFn)
      selectConfig(configForms.value[0])
      removeHttpConfig(configForms.value[0])
      expect(activeConfigId.value).toBe('')
    })

    it('移除非选中配置时 activeConfigId 不变', () => {
      configForms.value = [makeHttpConfig('1'), makeHttpConfig('2')]
      const { selectConfig, removeHttpConfig, activeConfigId } = useEnvironmentHttpConfig(configForms, localIdFn)
      selectConfig(configForms.value[1])
      removeHttpConfig(configForms.value[0])
      expect(activeConfigId.value).toBe('2')
    })
  })

  describe('runHttpTest', () => {
    it('environmentId 缺失时显示警告', async () => {
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(makeHttpConfig('1'))
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('环境ID缺失')
      expect(mocks.testHttpConfig).not.toHaveBeenCalled()
    })

    it('baseUrl 为空时显示警告', async () => {
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(makeHttpConfig('1', { baseUrl: '' }), 'env-1')
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先填写 Base URL 再测试连接')
      expect(mocks.testHttpConfig).not.toHaveBeenCalled()
    })

    it('baseUrl 为空白时显示警告', async () => {
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(makeHttpConfig('1', { baseUrl: '   ' }), 'env-1')
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先填写 Base URL 再测试连接')
      expect(mocks.testHttpConfig).not.toHaveBeenCalled()
    })

    it('测试成功时显示成功消息', async () => {
      mocks.testHttpConfig.mockResolvedValue({ success: true, statusCode: 200, durationMs: 150 })
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(mocks.testHttpConfig).toHaveBeenCalledWith('env-1', { baseUrl: 'http://example.com', refName: 'http_1' })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('连接成功：状态码 200，耗时 150ms')
    })

    it('测试成功但无 statusCode 和 durationMs 时显示占位符', async () => {
      mocks.testHttpConfig.mockResolvedValue({ success: true })
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('连接成功：状态码 -，耗时 -ms')
    })

    it('测试失败时显示错误消息', async () => {
      mocks.testHttpConfig.mockResolvedValue({ success: false, message: '连接超时' })
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('连接超时')
    })

    it('测试失败且无 message 时显示通用错误', async () => {
      mocks.testHttpConfig.mockResolvedValue({ success: false })
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('连接失败')
    })

    it('抛出异常时显示 resolveEnvironmentError 结果', async () => {
      mocks.testHttpConfig.mockRejectedValue(new Error('network'))
      mocks.resolveEnvironmentError.mockReturnValue('网络异常')
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(mocks.resolveEnvironmentError).toHaveBeenCalled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
    })

    it('测试期间 testingHttpId 设置为 form.id 并在完成后重置', async () => {
      mocks.testHttpConfig.mockResolvedValue({ success: true })
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest, testingHttpId } = useEnvironmentHttpConfig(configForms, localIdFn)
      expect(testingHttpId.value).toBe('')
      const promise = runHttpTest(form, 'env-1')
      expect(testingHttpId.value).toBe('1')
      await promise
      expect(testingHttpId.value).toBe('')
    })

    it('测试异常时 testingHttpId 也被重置', async () => {
      mocks.testHttpConfig.mockRejectedValue(new Error('fail'))
      const form = makeHttpConfig('1', { baseUrl: 'http://example.com' })
      const { runHttpTest, testingHttpId } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(testingHttpId.value).toBe('')
    })

    it('baseUrl 有前后空白时自动 trim', async () => {
      mocks.testHttpConfig.mockResolvedValue({ success: true })
      const form = makeHttpConfig('1', { baseUrl: '  http://example.com  ' })
      const { runHttpTest } = useEnvironmentHttpConfig(configForms, localIdFn)
      await runHttpTest(form, 'env-1')
      expect(mocks.testHttpConfig).toHaveBeenCalledWith('env-1', {
        baseUrl: 'http://example.com',
        refName: 'http_1',
      })
    })
  })
})

describe('useEnvironmentDatasource', () => {
  let dsForms: Ref<DsForm[]>
  let localIdFn: ReturnType<typeof vi.fn<() => string>>
  let idCounter: number

  beforeEach(() => {
    vi.clearAllMocks()
    idCounter = 200
    dsForms = ref<DsForm[]>([])
    localIdFn = vi.fn(() => `local-${idCounter++}`)
    mocks.resolveEnvironmentError.mockReturnValue('操作失败，请稍后重试')
  })

  describe('初始状态', () => {
    it('activeDsId 初始为空字符串', () => {
      const { activeDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(activeDsId.value).toBe('')
    })

    it('activeDs 初始为 undefined', () => {
      const { activeDs } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(activeDs.value).toBeUndefined()
    })

    it('testingDsId 初始为空字符串', () => {
      const { testingDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(testingDsId.value).toBe('')
    })

    it('orderedDsForms 初始为空数组', () => {
      const { orderedDsForms } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(orderedDsForms.value).toEqual([])
    })
  })

  describe('orderedDsForms', () => {
    it('默认数据源排在前面', () => {
      dsForms.value = [
        makeDsForm('1', { isDefault: false }),
        makeDsForm('2', { isDefault: true }),
        makeDsForm('3', { isDefault: false }),
      ]
      const { orderedDsForms } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(orderedDsForms.value.map((d) => d.id)).toEqual(['2', '1', '3'])
    })

    it('多个默认数据源保持原顺序', () => {
      dsForms.value = [
        makeDsForm('1', { isDefault: true }),
        makeDsForm('2', { isDefault: true }),
      ]
      const { orderedDsForms } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(orderedDsForms.value.map((d) => d.id)).toEqual(['1', '2'])
    })
  })

  describe('selectDs', () => {
    it('设置 activeDsId 为选中数据源的 id', () => {
      dsForms.value = [makeDsForm('1'), makeDsForm('2')]
      const { selectDs, activeDsId, activeDs } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[1])
      expect(activeDsId.value).toBe('2')
      expect(activeDs.value).toBe(dsForms.value[1])
    })

    it('activeDs 在列表中找不到时返回 undefined', () => {
      dsForms.value = [makeDsForm('1')]
      const { activeDsId, activeDs } = useEnvironmentDatasource(dsForms, localIdFn)
      activeDsId.value = 'not-exist'
      expect(activeDs.value).toBeUndefined()
    })
  })

  describe('selectedDsDriverOption', () => {
    it('选中数据源时返回对应驱动选项', () => {
      dsForms.value = [makeDsForm('1', { driver: 'com.mysql.cj.jdbc.Driver' })]
      const { selectDs, selectedDsDriverOption } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      expect(selectedDsDriverOption.value).toEqual({
        driver: 'com.mysql.cj.jdbc.Driver',
        label: 'MySQL',
        urlExample: 'jdbc:mysql://localhost:3306/db',
      })
    })

    it('驱动不匹配时返回 undefined', () => {
      dsForms.value = [makeDsForm('1', { driver: 'unknown.Driver' })]
      const { selectDs, selectedDsDriverOption } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      expect(selectedDsDriverOption.value).toBeUndefined()
    })

    it('未选中数据源时返回 undefined', () => {
      const { selectedDsDriverOption } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(selectedDsDriverOption.value).toBeUndefined()
    })
  })

  describe('handleDsDriverChange', () => {
    it('选中数据源 URL 为空时自动填充 urlExample', () => {
      dsForms.value = [makeDsForm('1', { url: '' })]
      const { selectDs, handleDsDriverChange, activeDs } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      handleDsDriverChange('org.postgresql.Driver')
      expect(activeDs.value!.url).toBe('jdbc:postgresql://localhost:5432/db')
    })

    it('选中数据源 URL 不为空时不覆盖', () => {
      dsForms.value = [makeDsForm('1', { url: 'jdbc:mysql://custom:3306/db' })]
      const { selectDs, handleDsDriverChange, activeDs } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      handleDsDriverChange('org.postgresql.Driver')
      expect(activeDs.value!.url).toBe('jdbc:mysql://custom:3306/db')
    })

    it('未选中数据源时不报错', () => {
      const { handleDsDriverChange } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(() => handleDsDriverChange('org.postgresql.Driver')).not.toThrow()
    })

    it('驱动不匹配时不做任何操作', () => {
      dsForms.value = [makeDsForm('1', { url: '' })]
      const { selectDs, handleDsDriverChange, activeDs } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      handleDsDriverChange('unknown.Driver')
      expect(activeDs.value!.url).toBe('')
    })
  })

  describe('addDataSource', () => {
    it('推入新数据源并自动选中', () => {
      const { addDataSource, activeDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      addDataSource()
      expect(dsForms.value).toHaveLength(1)
      expect(activeDsId.value).toBe('local-200')
    })

    it('新数据源使用 localId', () => {
      const { addDataSource } = useEnvironmentDatasource(dsForms, localIdFn)
      addDataSource()
      expect(dsForms.value[0].id).toBe('local-200')
      expect(localIdFn).toHaveBeenCalled()
    })

    it('新数据源 refName 递增', () => {
      dsForms.value = [makeDsForm('1')]
      const { addDataSource } = useEnvironmentDatasource(dsForms, localIdFn)
      addDataSource()
      expect(dsForms.value[1].refName).toBe('db_2')
    })

    it('新数据源 driver 默认为第一个 DRIVER_OPTIONS 的 driver', () => {
      const { addDataSource } = useEnvironmentDatasource(dsForms, localIdFn)
      addDataSource()
      expect(dsForms.value[0].driver).toBe('com.mysql.cj.jdbc.Driver')
    })

    it('新数据源 isDefault 为 false', () => {
      const { addDataSource } = useEnvironmentDatasource(dsForms, localIdFn)
      addDataSource()
      expect(dsForms.value[0].isDefault).toBe(false)
    })
  })

  describe('removeDataSource', () => {
    it('从列表中移除指定数据源', () => {
      dsForms.value = [makeDsForm('1'), makeDsForm('2')]
      const { removeDataSource } = useEnvironmentDatasource(dsForms, localIdFn)
      removeDataSource(dsForms.value[0])
      expect(dsForms.value).toHaveLength(1)
      expect(dsForms.value[0].id).toBe('2')
    })

    it('移除当前选中数据源时自动选中第一个', () => {
      dsForms.value = [makeDsForm('1'), makeDsForm('2')]
      const { selectDs, removeDataSource, activeDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      removeDataSource(dsForms.value[0])
      expect(activeDsId.value).toBe('2')
    })

    it('移除所有数据源时 activeDsId 为空', () => {
      dsForms.value = [makeDsForm('1')]
      const { selectDs, removeDataSource, activeDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[0])
      removeDataSource(dsForms.value[0])
      expect(activeDsId.value).toBe('')
    })

    it('移除非选中数据源时 activeDsId 不变', () => {
      dsForms.value = [makeDsForm('1'), makeDsForm('2')]
      const { selectDs, removeDataSource, activeDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      selectDs(dsForms.value[1])
      removeDataSource(dsForms.value[0])
      expect(activeDsId.value).toBe('2')
    })
  })

  describe('runDsTest', () => {
    it('environmentId 缺失时显示警告', async () => {
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(makeDsForm('1'))
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('环境ID缺失')
      expect(mocks.testDataSourceConfig).not.toHaveBeenCalled()
    })

    it('url 为空时显示警告', async () => {
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(makeDsForm('1', { url: '' }), 'env-1')
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先填写 URL 再测试连接')
      expect(mocks.testDataSourceConfig).not.toHaveBeenCalled()
    })

    it('url 为空白时显示警告', async () => {
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(makeDsForm('1', { url: '   ' }), 'env-1')
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请先填写 URL 再测试连接')
      expect(mocks.testDataSourceConfig).not.toHaveBeenCalled()
    })

    it('测试成功时显示成功消息', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: true, databaseVersion: '8.0.33' })
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.testDataSourceConfig).toHaveBeenCalledWith('env-1', {
        driver: 'com.mysql.cj.jdbc.Driver',
        url: 'jdbc:mysql://localhost:3306/test',
        connectionProperties: undefined,
      })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('连接成功：8.0.33')
    })

    it('测试成功但无 databaseVersion 时显示简化消息', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: true })
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('连接成功')
    })

    it('测试失败时显示错误消息', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: false, message: '认证失败' })
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('认证失败')
    })

    it('测试失败且无 message 时显示通用错误', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: false })
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('连接失败')
    })

    it('抛出异常时显示 resolveEnvironmentError 结果', async () => {
      mocks.testDataSourceConfig.mockRejectedValue(new Error('timeout'))
      mocks.resolveEnvironmentError.mockReturnValue('连接超时')
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.resolveEnvironmentError).toHaveBeenCalled()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('连接超时')
    })

    it('测试期间 testingDsId 设置为 form.id 并在完成后重置', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: true })
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest, testingDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      expect(testingDsId.value).toBe('')
      const promise = runDsTest(form, 'env-1')
      expect(testingDsId.value).toBe('1')
      await promise
      expect(testingDsId.value).toBe('')
    })

    it('测试异常时 testingDsId 也被重置', async () => {
      mocks.testDataSourceConfig.mockRejectedValue(new Error('fail'))
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test' })
      const { runDsTest, testingDsId } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(testingDsId.value).toBe('')
    })

    it('url 有前后空白时自动 trim', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: true })
      const form = makeDsForm('1', { url: '  jdbc:mysql://localhost:3306/test  ' })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.testDataSourceConfig).toHaveBeenCalledWith('env-1', {
        driver: 'com.mysql.cj.jdbc.Driver',
        url: 'jdbc:mysql://localhost:3306/test',
        connectionProperties: undefined,
      })
    })

    it('传递 connectionProperties 给 API', async () => {
      mocks.testDataSourceConfig.mockResolvedValue({ success: true })
      const props = { ssl: true, timeout: 5000 }
      const form = makeDsForm('1', { url: 'jdbc:mysql://localhost:3306/test', connectionProperties: props })
      const { runDsTest } = useEnvironmentDatasource(dsForms, localIdFn)
      await runDsTest(form, 'env-1')
      expect(mocks.testDataSourceConfig).toHaveBeenCalledWith('env-1', {
        driver: 'com.mysql.cj.jdbc.Driver',
        url: 'jdbc:mysql://localhost:3306/test',
        connectionProperties: props,
      })
    })
  })
})
