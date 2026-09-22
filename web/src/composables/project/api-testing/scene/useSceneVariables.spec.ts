import { ref, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiSceneDetail, ApiVariable } from '@/types'

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

import { useSceneVariables } from './useSceneVariables'

function makeDetail(overrides?: Partial<ApiSceneDetail>): ApiSceneDetail {
  return {
    id: 'scene-1',
    name: '场景1',
    moduleId: 'mod-1',
    description: '',
    environmentId: 'env-1',
    priority: 'P2',
    status: 'draft',
    followed: false,
    variables: [],
    processors: [],
    changeVersion: 1,
    steps: [],
    ...overrides,
  }
}

function makeEnvVariable(overrides?: Partial<ApiVariable>): ApiVariable {
  return {
    id: 'var-1',
    name: 'API_KEY',
    value: 'secret',
    description: '',
    hasValue: true,
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useSceneVariables', () => {
  describe('初始状态', () => {
    it('editVariables 初始为空数组', () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      expect(editVariables.value).toEqual([])
    })

    it('showFunctionHelper 初始为 false', () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { showFunctionHelper } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      expect(showFunctionHelper.value).toBe(false)
    })

    it('showVariableHelper 初始为 false', () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { showVariableHelper } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      expect(showVariableHelper.value).toBe(false)
    })

    it('envVariables 初始为空数组', () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { envVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      expect(envVariables.value).toEqual([])
    })

    it('envVariablesName 初始为空字符串', () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { envVariablesName } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      expect(envVariablesName.value).toBe('')
    })
  })

  describe('watch detail', () => {
    it('detail 有值时将 variables 映射到 editVariables', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      detail.value = makeDetail({
        variables: [
          { name: 'k1', value: 'v1', description: 'desc1' },
          { name: 'k2', value: '', description: '' },
        ],
      })
      await nextTick()
      expect(editVariables.value).toEqual([
        { key: 'k1', value: 'v1', description: 'desc1', enabled: true },
        { key: 'k2', value: '', description: '', enabled: true },
      ])
    })

    it('detail 变量 value 为 null 时映射为空字符串', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      detail.value = makeDetail({
        variables: [{ name: 'k1', value: undefined, description: undefined }],
      })
      await nextTick()
      expect(editVariables.value).toEqual([
        { key: 'k1', value: '', description: '', enabled: true },
      ])
    })

    it('detail 有 variables 数组时正确映射', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      detail.value = makeDetail({
        variables: [
          { name: 'a', value: '1' },
          { name: 'b', value: '2', description: 'desc b' },
        ],
      })
      await nextTick()
      expect(editVariables.value).toHaveLength(2)
      expect(editVariables.value[0]).toEqual({ key: 'a', value: '1', description: '', enabled: true })
      expect(editVariables.value[1]).toEqual({ key: 'b', value: '2', description: 'desc b', enabled: true })
    })
  })

  describe('sceneVariablePayload', () => {
    it('过滤空名行并返回 ApiSceneVariableItem[]', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablePayload, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: 'k1', value: 'v1', description: 'd1', enabled: true },
        { key: '   ', value: 'v2', description: '', enabled: true },
        { key: '', value: 'v3', description: '', enabled: true },
      ]
      expect(sceneVariablePayload()).toEqual([
        { name: 'k1', value: 'v1', description: 'd1' },
      ])
    })

    it('空值字段传 undefined', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablePayload, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: 'k1', value: '', description: '', enabled: true },
      ]
      expect(sceneVariablePayload()).toEqual([
        { name: 'k1', value: undefined, description: undefined },
      ])
    })

    it('trim key 名称', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablePayload, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: '  k1  ', value: 'v1', description: 'd1', enabled: true },
      ]
      expect(sceneVariablePayload()).toEqual([
        { name: 'k1', value: 'v1', description: 'd1' },
      ])
    })

    it('全部过滤后返回空数组', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablePayload, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: '', value: 'v', description: '', enabled: true },
        { key: '  ', value: 'v', description: '', enabled: true },
      ]
      expect(sceneVariablePayload()).toEqual([])
    })
  })

  describe('sceneVariablesForHelper', () => {
    it('过滤空名并返回 name/value/description 结构', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablesForHelper, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: 'k1', value: 'v1', description: 'd1', enabled: true },
        { key: '   ', value: 'v2', description: '', enabled: true },
        { key: '', value: 'v3', description: '', enabled: true },
      ]
      expect(sceneVariablesForHelper.value).toEqual([
        { name: 'k1', value: 'v1', description: 'd1' },
      ])
    })

    it('description 为 null 时展示空字符串', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablesForHelper, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: 'k1', value: 'v1', description: null as unknown as string, enabled: true },
      ]
      expect(sceneVariablesForHelper.value).toEqual([
        { name: 'k1', value: 'v1', description: '' },
      ])
    })

    it('所有变量都被过滤时返回空数组', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      const { sceneVariablesForHelper, editVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      editVariables.value = [
        { key: '', value: 'v', description: '', enabled: true },
      ]
      expect(sceneVariablesForHelper.value).toEqual([])
    })
  })

  describe('openVariableHelper', () => {
    it('已有 editEnvironmentId 时加载对应环境变量', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>('env-1')
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([
        { id: 'env-1', name: '生产环境' },
      ])
      mocks.fetchEnvironmentDetail.mockResolvedValue({
        variables: [makeEnvVariable({ id: 'v1', name: 'DB_HOST', value: 'localhost' })],
      })
      const { openVariableHelper, envVariables, envVariablesName, showVariableHelper } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(envVariablesName.value).toBe('生产环境')
      expect(envVariables.value).toEqual([makeEnvVariable({ id: 'v1', name: 'DB_HOST', value: 'localhost' })])
      expect(showVariableHelper.value).toBe(true)
    })

    it('editEnvironmentId 为空且有默认环境时使用默认环境', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([
        { id: 'env-1', name: '开发环境', isDefault: false },
        { id: 'env-2', name: '测试环境', isDefault: true },
      ])
      mocks.fetchEnvironmentDetail.mockResolvedValue({ variables: [makeEnvVariable()] })
      const { openVariableHelper, envVariablesName } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(mocks.fetchEnvironmentDetail).toHaveBeenCalledWith('env-2')
      expect(envVariablesName.value).toBe('测试环境')
    })

    it('editEnvironmentId 为空且无默认环境时 envId 为 null 不发请求', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>(null)
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([
        { id: 'env-1', name: '环境1', isDefault: false },
      ])
      const { openVariableHelper, envVariables, envVariablesName, showVariableHelper } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(mocks.fetchEnvironmentDetail).not.toHaveBeenCalled()
      expect(envVariables.value).toEqual([])
      expect(envVariablesName.value).toBe('')
      expect(showVariableHelper.value).toBe(true)
    })

    it('editEnvironmentId 存在但不在 options 中时 name 为空', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>('env-unknown')
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([])
      mocks.fetchEnvironmentDetail.mockResolvedValue({ variables: [] })
      const { openVariableHelper, envVariablesName } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(envVariablesName.value).toBe('')
    })

    it('加载失败时显示错误消息且 envVariables 保持空', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>('env-1')
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([
        { id: 'env-1', name: '环境1' },
      ])
      mocks.fetchEnvironmentDetail.mockRejectedValue(new Error('网络错误'))
      const { openVariableHelper, envVariables, showVariableHelper } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载环境变量失败')
      expect(envVariables.value).toEqual([])
      expect(showVariableHelper.value).toBe(true)
    })

    it('每次调用都重置 envVariables', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>('env-1')
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([
        { id: 'env-1', name: '环境1' },
      ])
      mocks.fetchEnvironmentDetail.mockResolvedValue({ variables: [makeEnvVariable()] })
      const { openVariableHelper, envVariables } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(envVariables.value).toHaveLength(1)
      await openVariableHelper()
      expect(envVariables.value).toHaveLength(1)
    })

    it('editEnvironmentId 变更后 openVariableHelper 加载新环境', async () => {
      const detail = ref<ApiSceneDetail | null>(null)
      const editEnvironmentId = ref<string | null>('env-1')
      const environmentOptions = ref<{ id: string; name: string; isDefault?: boolean }[]>([
        { id: 'env-1', name: '环境1' },
        { id: 'env-2', name: '环境2' },
      ])
      mocks.fetchEnvironmentDetail.mockResolvedValue({ variables: [makeEnvVariable({ name: 'OLD' })] })
      const { openVariableHelper, envVariablesName } = useSceneVariables(detail, editEnvironmentId, environmentOptions)
      await openVariableHelper()
      expect(envVariablesName.value).toBe('环境1')
      editEnvironmentId.value = 'env-2'
      mocks.fetchEnvironmentDetail.mockResolvedValue({ variables: [makeEnvVariable({ name: 'NEW' })] })
      await openVariableHelper()
      expect(envVariablesName.value).toBe('环境2')
    })
  })
})
