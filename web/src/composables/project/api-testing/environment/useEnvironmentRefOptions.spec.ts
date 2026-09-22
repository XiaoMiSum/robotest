import { ref, nextTick, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiHttpConfig, ApiDataSource } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchEnvironmentDetail: vi.fn(),
  ElMessage: { error: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('@/services/project/api-testing/environment', () => ({
  fetchEnvironmentDetail: mocks.fetchEnvironmentDetail,
}))

import { useEnvironmentRefOptions } from './useEnvironmentRefOptions'

function makeHttpConfig(id: string): ApiHttpConfig {
  return {
    id,
    name: `HTTP ${id}`,
    refName: `http_${id}`,
    baseUrl: `http://example${id}.com`,
    isDefault: false,
    headers: [],
  }
}

function makeDataSource(id: string): ApiDataSource {
  return {
    id,
    name: `DS ${id}`,
    refName: `ds_${id}`,
    driver: 'com.mysql.cj.jdbc.Driver',
    url: `jdbc:mysql://localhost:3306/db${id}`,
    isDefault: false,
  }
}

describe('useEnvironmentRefOptions', () => {
  let editEnvironmentId: Ref<string | null>

  beforeEach(() => {
    vi.clearAllMocks()
    editEnvironmentId = ref<string | null>(null) as Ref<string | null>
  })

  describe('初始状态', () => {
    it('httpRefOptions 初始为空数组', () => {
      const { httpRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      expect(httpRefOptions.value).toEqual([])
    })

    it('dsRefOptions 初始为空数组', () => {
      const { dsRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      expect(dsRefOptions.value).toEqual([])
    })
  })

  describe('watch editEnvironmentId', () => {
    it('id 从 null 变为有效值时触发加载', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [makeHttpConfig('1')],
        dataSources: [makeDataSource('1')],
      })
      useEnvironmentRefOptions(editEnvironmentId)
      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      expect(mocks.fetchEnvironmentDetail).toHaveBeenCalledWith('env-1')
    })

    it('id 从有效值变为 null 时清空选项', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [makeHttpConfig('1')],
        dataSources: [makeDataSource('1')],
      })
      const { httpRefOptions, dsRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toHaveLength(1)

      editEnvironmentId.value = null
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
    })

    it('id 从一个有效值变为另一个时重新加载', async () => {
      mocks.fetchEnvironmentDetail
        .mockResolvedValueOnce({
          httpConfigs: [makeHttpConfig('1')],
          dataSources: [makeDataSource('1')],
        })
        .mockResolvedValueOnce({
          httpConfigs: [makeHttpConfig('2'), makeHttpConfig('3')],
          dataSources: [],
        })
      const { httpRefOptions } = useEnvironmentRefOptions(editEnvironmentId)

      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toHaveLength(1)

      editEnvironmentId.value = 'env-2'
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toHaveLength(2)
      expect(httpRefOptions.value[0].id).toBe('2')
    })

    it('id 不变时不重复加载', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [],
        dataSources: [],
      })
      useEnvironmentRefOptions(editEnvironmentId)
      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      expect(mocks.fetchEnvironmentDetail).toHaveBeenCalledTimes(1)
    })

    it('id 从空字符串变为有效值时触发加载', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [makeHttpConfig('1')],
        dataSources: [],
      })
      const { httpRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      editEnvironmentId.value = ''
      await nextTick()
      expect(httpRefOptions.value).toEqual([])
      expect(mocks.fetchEnvironmentDetail).not.toHaveBeenCalled()

      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      expect(mocks.fetchEnvironmentDetail).toHaveBeenCalledWith('env-1')
      expect(httpRefOptions.value).toHaveLength(1)
    })
  })

  describe('loadSceneRefOptions', () => {
    it('environmentId 为 null 时清空选项', async () => {
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      httpRefOptions.value = [makeHttpConfig('1')]
      dsRefOptions.value = [makeDataSource('1')]
      await loadSceneRefOptions(null)
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
      expect(mocks.fetchEnvironmentDetail).not.toHaveBeenCalled()
    })

    it('environmentId 为 undefined 时清空选项', async () => {
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      httpRefOptions.value = [makeHttpConfig('1')]
      dsRefOptions.value = [makeDataSource('1')]
      await loadSceneRefOptions(undefined)
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
      expect(mocks.fetchEnvironmentDetail).not.toHaveBeenCalled()
    })

    it('environmentId 为空字符串时清空选项', async () => {
      const { httpRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      httpRefOptions.value = [makeHttpConfig('1')]
      await loadSceneRefOptions('')
      expect(httpRefOptions.value).toEqual([])
      expect(mocks.fetchEnvironmentDetail).not.toHaveBeenCalled()
    })

    it('加载成功时填充 httpRefOptions 和 dsRefOptions', async () => {
      const httpConfigs = [makeHttpConfig('1'), makeHttpConfig('2')]
      const dataSources = [makeDataSource('1')]
      mocks.fetchEnvironmentDetail.mockResolvedValue({ httpConfigs, dataSources })
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(httpRefOptions.value).toEqual(httpConfigs)
      expect(dsRefOptions.value).toEqual(dataSources)
    })

    it('加载成功但返回空数组时正确赋值', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({ httpConfigs: [], dataSources: [] })
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
    })

    it('加载成功且返回完整数据时正确赋值', async () => {
      const httpConfigs = [makeHttpConfig('a'), makeHttpConfig('b')]
      const dataSources = [makeDataSource('x'), makeDataSource('y')]
      mocks.fetchEnvironmentDetail.mockResolvedValue({ httpConfigs, dataSources })
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(httpRefOptions.value).toEqual(httpConfigs)
      expect(dsRefOptions.value).toEqual(dataSources)
    })
  })

  describe('错误处理', () => {
    it('fetchEnvironmentDetail 抛出 Error 时清空选项并显示错误消息', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue(new Error('网络异常'))
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      httpRefOptions.value = [makeHttpConfig('1')]
      dsRefOptions.value = [makeDataSource('1')]
      await loadSceneRefOptions('env-1')
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络异常')
    })

    it('fetchEnvironmentDetail 抛出非 Error 时显示通用错误消息', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue('unknown')
      const { loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载环境配置失败')
    })

    it('fetchEnvironmentDetail 抛出 null 时显示通用错误消息', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue(null)
      const { loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载环境配置失败')
    })

    it('fetchEnvironmentDetail 抛出 undefined 时显示通用错误消息', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue(undefined)
      const { loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载环境配置失败')
    })

    it('watch 触发加载失败时清空选项并显示错误', async () => {
      mocks.fetchEnvironmentDetail.mockRejectedValue(new Error('timeout'))
      const { httpRefOptions, dsRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      httpRefOptions.value = [makeHttpConfig('1')]
      editEnvironmentId.value = 'env-1'
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toEqual([])
      expect(dsRefOptions.value).toEqual([])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('timeout')
    })
  })

  describe('边界场景', () => {
    it('多次快速切换 id 只保留最后一次结果', async () => {
      let resolveFirst: ((v: unknown) => void) | undefined
      let resolveSecond: ((v: unknown) => void) | undefined
      mocks.fetchEnvironmentDetail
        .mockImplementationOnce(() => new Promise((r) => { resolveFirst = r }))
        .mockImplementationOnce(() => new Promise((r) => { resolveSecond = r }))

      const { httpRefOptions, dsRefOptions } = useEnvironmentRefOptions(editEnvironmentId)

      editEnvironmentId.value = 'env-1'
      await nextTick()
      editEnvironmentId.value = 'env-2'
      await nextTick()

      resolveFirst?.({ httpConfigs: [makeHttpConfig('old')], dataSources: [] })
      resolveSecond?.({ httpConfigs: [makeHttpConfig('new')], dataSources: [makeDataSource('new')] })
      await nextTick()
      await nextTick()
      await nextTick()

      expect(httpRefOptions.value).toHaveLength(1)
      expect(httpRefOptions.value[0].id).toBe('new')
      expect(dsRefOptions.value).toHaveLength(1)
    })

    it('首次加载前手动调用 loadSceneRefOptions 不影响 watch', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [makeHttpConfig('1')],
        dataSources: [],
      })
      const { httpRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(httpRefOptions.value).toHaveLength(1)

      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [makeHttpConfig('2')],
        dataSources: [makeDataSource('2')],
      })
      editEnvironmentId.value = 'env-2'
      await nextTick()
      await nextTick()
      expect(httpRefOptions.value).toHaveLength(1)
      expect(httpRefOptions.value[0].id).toBe('2')
    })

    it('返回的 ref 是响应式的', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [makeHttpConfig('1')],
        dataSources: [makeDataSource('1')],
      })
      const { httpRefOptions, dsRefOptions, loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      await loadSceneRefOptions('env-1')
      expect(httpRefOptions.value).toHaveLength(1)
      expect(dsRefOptions.value).toHaveLength(1)
    })

    it('loadSceneRefOptions 的 Promise 返回 void', async () => {
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        httpConfigs: [],
        dataSources: [],
      })
      const { loadSceneRefOptions } = useEnvironmentRefOptions(editEnvironmentId)
      const result = await loadSceneRefOptions('env-1')
      expect(result).toBeUndefined()
    })
  })
})
