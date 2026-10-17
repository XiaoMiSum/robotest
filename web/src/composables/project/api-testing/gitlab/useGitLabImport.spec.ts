import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  ApiGitImportPreview,
  ApiGitImportStatus,
  ApiGitRepoConfig,
} from '@/types'

const mocks = vi.hoisted(() => ({
  previewGitImport: vi.fn<(repoId: string) => Promise<ApiGitImportPreview>>(),
  startGitImport: vi.fn<(repoId: string) => Promise<ApiGitImportStatus>>(),
  fetchGitImportStatus: vi.fn<(repoId: string) => Promise<ApiGitImportStatus>>(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
  unmountHooks: [] as Array<() => void>,
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    // 组合式在 setup 中被调用：onMounted 立即执行，onBeforeUnmount 收集以便断言轮询清理
    onMounted: (cb: () => void) => {
      cb()
    },
    onBeforeUnmount: (cb: () => void) => {
      mocks.unmountHooks.push(cb)
    },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/services/project/api-testing/gitRepo', () => ({
  previewGitImport: mocks.previewGitImport,
  startGitImport: mocks.startGitImport,
  fetchGitImportStatus: mocks.fetchGitImportStatus,
}))

import {
  buildDetailsCsv,
  buildImportConfirmText,
  detailTypeLabel,
  useGitLabImport,
} from './useGitLabImport'

function makeRepo(overrides?: Partial<ApiGitRepoConfig>): ApiGitRepoConfig {
  return {
    id: 'repo-1',
    projectId: 'proj-1',
    name: '订单接口自动化',
    gitlabBaseUrl: 'https://gitlab.example.com',
    repoPath: 'group/repo',
    defaultBranch: 'master',
    importMode: 'platform',
    enabled: true,
    testSourceDir: 'src/test/java',
    resourceDir: 'src/test/resources',
    envFilePath: 'common/env/env.yaml',
    reimportOverwrite: false,
    lastImportAt: '2026-10-11T10:20:30+08:00',
    lastImportCommit: 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0',
    lastImportSummary: { created: 193, updated: 12, parseFailed: 0, errorFailed: 0, skipped: 0 },
    headCommit: 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0',
    headCheckedAt: null,
    headProbeStatus: 'ok',
    hasUpdates: false,
    ...overrides,
  }
}

function makePreview(overrides?: Partial<ApiGitImportPreview>): ApiGitImportPreview {
  return {
    repoId: 'repo-1',
    branch: 'master',
    headCommit: 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0',
    gate: { blocked: false, reason: null },
    classes: 20,
    effectiveCases: 193,
    modules: {
      containers: { create: 2, update: 0 },
      classes: { create: 20, update: 0, backfill: 0 },
    },
    scenes: { create: 193, update: 12, parseFailed: 1, errorFailed: 1 },
    environment: { action: 'merge', target: '默认环境', httpConfigs: 4, dataSources: 2 },
    details: [
      {
        type: 'parseFailed',
        sceneKey: 'com.example.OrderQueryTest#listByStatus',
        reason: '步骤模板 src/test/resources/order/list/query_status.yaml 未找到',
      },
      {
        type: 'errorFailed',
        sceneKey: null,
        reason: '文件读取失败：未找到 src/test/java/com/example/MissTest.java',
      },
    ],
    parsedScenes: {
      total: 205,
      truncated: true,
      items: [
        { sceneKey: 'com.example.OrderQueryTest#listByStatus', name: '按状态查询订单列表', action: 'update' },
        { sceneKey: 'com.example.OrderQueryTest#createOrder', name: '创建订单', action: 'create' },
      ],
    },
    ...overrides,
  }
}

function makeStatus(overrides?: Partial<ApiGitImportStatus>): ApiGitImportStatus {
  return { stage: 'idle', processed: 0, total: 0, error: null, summary: null, ...overrides }
}

async function flush(times = 10): Promise<void> {
  for (let i = 0; i < times; i += 1) await Promise.resolve()
}

interface InitOptions {
  repo?: ApiGitRepoConfig
  onImported?: () => void
  /** 权限判定：缺省全部放行 */
  can?: (code: string) => boolean
}

function init(options?: InitOptions) {
  const repo = options?.repo ?? makeRepo()
  const can = options?.can ?? (() => true)
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn((code: string) => can(code)) })
  const onImported = options?.onImported ?? vi.fn()
  const s = useGitLabImport(() => repo, onImported)
  return { s, onImported, repo }
}

describe('useGitLabImport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.unmountHooks.length = 0
    vi.useFakeTimers()
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
    mocks.fetchGitImportStatus.mockResolvedValue(makeStatus())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('挂载重查与轮询生命周期', () => {
    it('未导入（idle）只查一次，不启动轮询', async () => {
      const { s } = init()
      await flush()
      expect(s.progress.value?.stage).toBe('idle')
      expect(s.importing.value).toBe(false)
      await vi.advanceTimersByTimeAsync(5000)
      expect(mocks.fetchGitImportStatus).toHaveBeenCalledTimes(1)
    })

    it('重进时发现进行中的导入则续轮询，到 done 收口并刷新仓库配置', async () => {
      mocks.fetchGitImportStatus
        .mockResolvedValueOnce(makeStatus({ stage: 'parse', processed: 96, total: 193 }))
        .mockResolvedValue(
          makeStatus({
            stage: 'done',
            processed: 193,
            total: 193,
            summary: { created: 193, updated: 12, parseFailed: 1, errorFailed: 0, skipped: 0 },
          }),
        )
      const { s, onImported } = init()
      await flush()
      expect(s.importing.value).toBe(true)
      await vi.advanceTimersByTimeAsync(1000)
      await flush()
      expect(s.progress.value?.stage).toBe('done')
      expect(s.importing.value).toBe(false)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('导入完成')
      expect(onImported).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(5000)
      expect(mocks.fetchGitImportStatus).toHaveBeenCalledTimes(2)
    })

    it('挂载查询失败静默，不弹错误也不阻塞动作行', async () => {
      mocks.fetchGitImportStatus.mockRejectedValue({ code: 1000017701, message: '仓库配置不存在' })
      const { s } = init()
      await flush()
      expect(s.progress.value).toBeNull()
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })

    it('页面离开（卸载）即停止轮询', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      const { s } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(s.importing.value).toBe(true)
      mocks.unmountHooks.forEach((cb) => cb())
      await vi.advanceTimersByTimeAsync(5000)
      expect(mocks.fetchGitImportStatus).toHaveBeenCalledTimes(1)
    })

    it('轮询网络抖动不终止，下一轮继续判定', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      // 挂载重查空闲 → 第一轮轮询网络失败 → 后续轮询返回终态
      mocks.fetchGitImportStatus.mockReset()
      mocks.fetchGitImportStatus
        .mockResolvedValueOnce(makeStatus())
        .mockRejectedValueOnce(new Error('network'))
        .mockResolvedValue(
          makeStatus({
            stage: 'done',
            processed: 193,
            total: 193,
            summary: { created: 193, updated: 12, parseFailed: 0, errorFailed: 0, skipped: 0 },
          }),
        )
      const { s, onImported } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      await vi.advanceTimersByTimeAsync(1000)
      await flush()
      expect(onImported).not.toHaveBeenCalled()
      expect(s.importing.value).toBe(true)
      await vi.advanceTimersByTimeAsync(1000)
      await flush()
      expect(onImported).toHaveBeenCalledTimes(1)
    })

    it('failed 终态弹错误并停止轮询', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      // 挂载重查空闲，其后轮询一律返回失败终态
      mocks.fetchGitImportStatus.mockReset()
      mocks.fetchGitImportStatus
        .mockResolvedValueOnce(makeStatus())
        .mockResolvedValue(makeStatus({ stage: 'failed', error: 'GitLab 不可达' }))
      const { s, onImported } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      await vi.advanceTimersByTimeAsync(1000)
      await flush()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('GitLab 不可达')
      expect(onImported).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(5000)
      expect(mocks.fetchGitImportStatus).toHaveBeenCalledTimes(2)
    })
  })

  describe('扫描预览与幂等闸门', () => {
    it('预览放行时保存结果并清闸门态', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      const { s } = init()
      await flush()
      await s.runPreview()
      expect(s.preview.value?.scenes?.create).toBe(193)
      expect(s.gateBlocked.value).toBe(false)
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })

    it('闸门拦截（gate.blocked）转 info 态，不弹错误、不保留预览', async () => {
      mocks.previewGitImport.mockResolvedValue(
        makePreview({
          gate: { blocked: true, reason: '目标分支提交与上次导入一致且无遗留错误，本次导入已跳过' },
          classes: null,
          effectiveCases: null,
          modules: null,
          scenes: null,
          environment: null,
          details: [],
        }),
      )
      const { s } = init()
      await flush()
      await s.runPreview()
      expect(s.preview.value).toBeNull()
      expect(s.gateBlocked.value).toBe(true)
      expect(s.gateReason.value).toContain('一致')
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })

    it('预览失败按错误码提示', async () => {
      mocks.previewGitImport.mockRejectedValue({ code: 1000017702, message: 'GitLab 不可达' })
      const { s } = init()
      await flush()
      await s.runPreview()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017702 GitLab 不可达')
      expect(s.previewing.value).toBe(false)
    })

    it('缺 api-scene:view 时预览与执行均不可发起', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      const { s } = init({ can: () => false })
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(mocks.previewGitImport).not.toHaveBeenCalled()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
      expect(mocks.startGitImport).not.toHaveBeenCalled()
    })
  })

  describe('执行导入', () => {
    it('取消二次确认不发导入请求', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const { s } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(mocks.startGitImport).not.toHaveBeenCalled()
      expect(s.submitting.value).toBe(false)
    })

    it('确认文案随「重复导入覆盖」切换', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      const { s } = init({ repo: makeRepo({ reimportOverwrite: false }) })
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '导入将按归属标识合并，已修改的平台侧内容不被覆盖，是否继续？',
        '执行导入',
        expect.anything(),
      )
    })

    it('覆盖开启时确认文案切换为覆盖口径', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      const { s } = init({ repo: makeRepo({ reimportOverwrite: true }) })
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalledWith(
        '导入将按归属标识合并，并以仓库内容覆盖已修改的平台侧场景内容，是否继续？',
        '执行导入',
        expect.anything(),
      )
    })

    it('未预览时不发导入请求（预览通过才可执行）', async () => {
      const { s } = init()
      await flush()
      await s.runImport()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('导入被闸门拦截（1000017709）收口为 info 态而非错误 Toast', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockRejectedValue({
        code: 1000017709,
        message: '目标分支提交与上次导入一致且无遗留错误，本次导入已跳过',
      })
      const { s } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(s.gateBlocked.value).toBe(true)
      expect(s.preview.value).toBeNull()
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
      expect(s.importing.value).toBe(false)
    })

    it('并发导入（1000017708）按错误码提示', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockRejectedValue({ code: 1000017708, message: '同仓库存在进行中的导入' })
      const { s } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017708 同仓库存在进行中的导入')
      expect(s.gateBlocked.value).toBe(false)
    })

    it('导入完成后清空过期预览并刷新仓库配置', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      mocks.startGitImport.mockResolvedValue(makeStatus({ stage: 'fetch' }))
      mocks.fetchGitImportStatus.mockResolvedValue(
        makeStatus({
          stage: 'done',
          processed: 193,
          total: 193,
          summary: { created: 193, updated: 12, parseFailed: 1, errorFailed: 0, skipped: 0 },
        }),
      )
      const { s, onImported } = init()
      await flush()
      await s.runPreview()
      await s.runImport()
      expect(s.preview.value).not.toBeNull()
      await vi.advanceTimersByTimeAsync(1000)
      await flush()
      expect(s.preview.value).toBeNull()
      expect(s.detailFilter.value).toBe('all')
      expect(onImported).toHaveBeenCalledTimes(1)
    })
  })

  describe('明细筛选与导出', () => {
    it('按类型筛选，导出仅含当前筛选行', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      const { s } = init()
      await flush()
      await s.runPreview()
      expect(s.filteredDetails.value).toHaveLength(2)
      s.detailFilter.value = 'parseFailed'
      expect(s.filteredDetails.value).toHaveLength(1)
      expect(s.filteredDetails.value[0].type).toBe('parseFailed')
      s.detailFilter.value = 'errorFailed'
      expect(s.filteredDetails.value).toHaveLength(1)
      expect(s.filteredDetails.value[0].sceneKey).toBeNull()
      s.detailFilter.value = 'all'
      expect(s.filteredDetails.value).toHaveLength(2)
    })

    it('预览放行后重置筛选', async () => {
      mocks.previewGitImport.mockResolvedValue(makePreview())
      const { s } = init()
      await flush()
      await s.runPreview()
      s.detailFilter.value = 'parseFailed'
      await s.runPreview()
      expect(s.detailFilter.value).toBe('all')
    })
  })

  describe('纯函数', () => {
    it('CSV 转义引号、逗号与换行，并带表头', () => {
      const csv = buildDetailsCsv([
        { type: 'errorFailed', sceneKey: null, reason: '文件读取失败：未找到 "a,b".java' },
      ])
      expect(csv).toBe('类型,类或方法,原因\r\n真实错误,,"文件读取失败：未找到 ""a,b"".java"')
    })

    it('类型标签未知取值原样透出', () => {
      expect(detailTypeLabel('parseFailed')).toBe('解析失败')
      expect(detailTypeLabel('errorFailed')).toBe('真实错误')
      expect(detailTypeLabel('other')).toBe('other')
    })

    it('确认文案随重复导入覆盖开关切换', () => {
      expect(buildImportConfirmText(makeRepo({ reimportOverwrite: false }))).toContain('不被覆盖')
      expect(buildImportConfirmText(makeRepo({ reimportOverwrite: true }))).toContain('覆盖已修改')
    })
  })
})
