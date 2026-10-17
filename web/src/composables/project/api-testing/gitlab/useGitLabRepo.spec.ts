import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  ApiGitConnectionTestReq,
  ApiGitConnectionTestResp,
  ApiGitRepoConfig,
  ApiGitRepoCreateReq,
  ApiGitRepoUpdateReq,
} from '@/types'

const mocks = vi.hoisted(() => ({
  fetchGitRepo: vi.fn<() => Promise<ApiGitRepoConfig | null>>(),
  createGitRepo: vi.fn<(data: ApiGitRepoCreateReq) => Promise<ApiGitRepoConfig>>(),
  updateGitRepo: vi.fn<(repoId: string, data: ApiGitRepoUpdateReq) => Promise<boolean>>(),
  deleteGitRepo: vi.fn<(repoId: string) => Promise<boolean>>(),
  testGitRepoConnection: vi.fn<(data: ApiGitConnectionTestReq) => Promise<ApiGitConnectionTestResp>>(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (cb: () => void) => {
      cb()
    },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

// 页签初始化读 ?gl= 落地参数（交互 09 §1.2），单测无路由实例时提供空 query
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/services/project/api-testing/gitRepo', () => ({
  fetchGitRepo: mocks.fetchGitRepo,
  createGitRepo: mocks.createGitRepo,
  updateGitRepo: mocks.updateGitRepo,
  deleteGitRepo: mocks.deleteGitRepo,
  testGitRepoConnection: mocks.testGitRepoConnection,
}))

import { formatGitLabError, toGitLabError, useGitLabRepo } from './useGitLabRepo'
import composableSource from './useGitLabRepo.ts?raw'

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
    lastImportAt: null,
    lastImportCommit: null,
    lastImportSummary: null,
    headCommit: null,
    headCheckedAt: null,
    headProbeStatus: null,
    hasUpdates: false,
    ...overrides,
  }
}

function makeConnectionResp(overrides?: Partial<ApiGitConnectionTestResp>): ApiGitConnectionTestResp {
  return {
    reachable: true,
    tokenValid: true,
    branchExists: true,
    version: '16.5.0',
    resolvedBranch: 'master',
    ...overrides,
  }
}

function setupMocks(options?: { repo?: ApiGitRepoConfig | null; hasPermission?: boolean }) {
  const repo = options?.repo === undefined ? null : options.repo
  mocks.fetchGitRepo.mockResolvedValue(repo)
  mocks.useAuthStore.mockReturnValue({
    hasPermission: vi.fn().mockReturnValue(options?.hasPermission ?? true),
  })
  mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
}

/** mock 均为微任务链：dynamicImportSettled 之外再排空若干微任务，确保链条完全落定 */
async function flush(): Promise<void> {
  await vi.dynamicImportSettled()
  for (let i = 0; i < 10; i += 1) await Promise.resolve()
}

async function initAndFlush(options?: Parameters<typeof setupMocks>[0]) {
  setupMocks(options)
  const s = useGitLabRepo()
  await flush()
  return s
}

describe('useGitLabRepo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('初始加载', () => {
    it('未配置时 repo 为 null（空态判定），不进入创建态', async () => {
      const s = await initAndFlush()
      expect(s.repo.value).toBeNull()
      expect(s.creating.value).toBe(false)
      expect(s.loadFailed.value).toBe(false)
      expect(s.activeTab.value).toBe('basic')
    })

    it('已配置时回填表单，令牌不回显（只写不读）', async () => {
      const s = await initAndFlush({ repo: makeRepo({ importMode: 'gitlab_ci' }) })
      expect(s.repo.value?.id).toBe('repo-1')
      expect(s.form.name).toBe('订单接口自动化')
      expect(s.form.gitlabBaseUrl).toBe('https://gitlab.example.com')
      expect(s.form.repoPath).toBe('group/repo')
      expect(s.form.importMode).toBe('gitlab_ci')
      expect(s.form.token).toBe('')
    })

    it('加载失败进入错误态并按错误码提示', async () => {
      setupMocks()
      mocks.fetchGitRepo.mockRejectedValue({ code: 1000017701, message: '仓库配置不存在' })
      const s = useGitLabRepo()
      await flush()
      expect(s.loadFailed.value).toBe(true)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017701 仓库配置不存在')
    })
  })

  describe('空态与创建态', () => {
    it('只读账号点击立即配置被拒绝，不进入创建态', async () => {
      const s = await initAndFlush({ hasPermission: false })
      s.startCreate()
      expect(s.creating.value).toBe(false)
      expect(mocks.ElMessage.warning).toHaveBeenCalled()
    })

    it('进入创建态时表单重置为缺省值', async () => {
      const s = await initAndFlush({ repo: makeRepo({ name: '旧名称' }) })
      s.startCreate()
      expect(s.creating.value).toBe(true)
      expect(s.form.name).toBe('')
      expect(s.form.importMode).toBe('platform')
      expect(s.form.repoPath).toBe('')
      expect(s.form.testSourceDir).toBe('src/test/java')
      expect(s.activeTab.value).toBe('basic')
    })

    it('只读账号不可切换接入方式', async () => {
      const s = await initAndFlush({ hasPermission: false })
      s.setImportMode('gitlab_ci')
      expect(s.form.importMode).toBe('platform')
    })
  })

  describe('连通性测试', () => {
    it('创建态成功后回显实际分支名', async () => {
      mocks.testGitRepoConnection.mockResolvedValue(makeConnectionResp({ resolvedBranch: 'dev' }))
      const s = await initAndFlush()
      s.startCreate()
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      s.form.repoPath = 'group/repo'
      await s.runConnectionTest()
      expect(mocks.testGitRepoConnection).toHaveBeenCalledWith({
        gitlabBaseUrl: 'https://gitlab.example.com',
        repoPath: 'group/repo',
        token: undefined,
        defaultBranch: undefined,
      })
      expect(s.testOk.value).toBe(true)
      expect(s.testMessage.value).toBe('连接正常 · 实际分支 dev')
      expect(s.form.defaultBranch).toBe('dev')
    })

    it('创建态提交前规范化项目路径并写回表单', async () => {
      mocks.testGitRepoConnection.mockResolvedValue(makeConnectionResp())
      const s = await initAndFlush()
      s.startCreate()
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      s.form.repoPath = ' /group/repo/ '
      await s.runConnectionTest()
      expect(mocks.testGitRepoConnection).toHaveBeenCalledWith({
        gitlabBaseUrl: 'https://gitlab.example.com',
        repoPath: 'group/repo',
        token: undefined,
        defaultBranch: undefined,
      })
      expect(s.form.repoPath).toBe('group/repo')
    })

    it('编辑态令牌留空时省略 token 与锁定字段，服务端回落已存配置', async () => {
      mocks.testGitRepoConnection.mockResolvedValue(makeConnectionResp())
      const s = await initAndFlush({ repo: makeRepo() })
      await s.runConnectionTest()
      const payload = mocks.testGitRepoConnection.mock.calls[0][0]
      expect(payload.token).toBeUndefined()
      expect(payload.repoPath).toBeUndefined()
      expect(payload.defaultBranch).toBeUndefined()
      expect(s.form.defaultBranch).toBe('master')
    })

    it('重新测试时清除上一次结果，新结果返回前不残留旧错误', async () => {
      mocks.testGitRepoConnection.mockRejectedValueOnce({ code: 1000017702, message: '项目路径不存在' })
      const s = await initAndFlush({ repo: makeRepo() })
      await s.runConnectionTest()
      expect(s.testMessage.value).toBe('1000017702 项目路径不存在')

      const deferred: { resolve: (resp: ApiGitConnectionTestResp) => void } = { resolve: () => {} }
      mocks.testGitRepoConnection.mockReturnValueOnce(new Promise((resolve) => { deferred.resolve = resolve }))
      const pending = s.runConnectionTest()
      expect(s.testMessage.value).toBe('')
      expect(s.testOk.value).toBeNull()
      expect(s.connDetailVisible.value).toBe(false)

      deferred.resolve(makeConnectionResp())
      await pending
      expect(s.testOk.value).toBe(true)
      expect(s.testMessage.value).toBe('连接正常 · 实际分支 master')
    })

    it('失败时展示错误码与原因，徽标转为不可达并可展开详情', async () => {
      mocks.testGitRepoConnection.mockRejectedValue({
        code: 1000017702,
        message: 'GitLab 不可达或令牌无效',
      })
      const s = await initAndFlush({ repo: makeRepo() })
      await s.runConnectionTest()
      expect(s.testOk.value).toBe(false)
      expect(s.testMessage.value).toBe('1000017702 GitLab 不可达或令牌无效')
      expect(s.connDown.value).toBe(true)
      expect(s.connDetailVisible.value).toBe(false)
      s.toggleConnDetail()
      expect(s.connDetailVisible.value).toBe(true)
      s.toggleConnDetail()
      expect(s.connDetailVisible.value).toBe(false)
    })

    it('连通成功时徽标保持已连通，点击徽标不展开详情', async () => {
      mocks.testGitRepoConnection.mockResolvedValue(makeConnectionResp())
      const s = await initAndFlush({ repo: makeRepo() })
      await s.runConnectionTest()
      expect(s.connDown.value).toBe(false)
      s.toggleConnDetail()
      expect(s.connDetailVisible.value).toBe(false)
    })
  })

  describe('保存（创建）', () => {
    it('必填缺失时前置校验拦截，不发请求', async () => {
      const s = await initAndFlush()
      s.startCreate()
      s.form.name = '配置一'
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      await s.save()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写项目路径')
      expect(mocks.createGitRepo).not.toHaveBeenCalled()
      expect(s.activeTab.value).toBe('basic')
    })

    it('保存成功携带三锁定字段与令牌，随后令牌清空并落到导入页签', async () => {
      mocks.createGitRepo.mockResolvedValue(makeRepo())
      const s = await initAndFlush()
      s.startCreate()
      s.form.name = '配置一'
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      s.form.repoPath = 'group/sub/repo'
      s.form.defaultBranch = ''
      s.form.importMode = 'gitlab_ci'
      s.form.token = 'glpat-secret'
      await s.save()
      expect(mocks.createGitRepo).toHaveBeenCalledWith({
        name: '配置一',
        gitlabBaseUrl: 'https://gitlab.example.com',
        repoPath: 'group/sub/repo',
        defaultBranch: undefined,
        importMode: 'gitlab_ci',
        token: 'glpat-secret',
        testSourceDir: 'src/test/java',
        resourceDir: 'src/test/resources',
        envFilePath: 'common/env/env.yaml',
        reimportOverwrite: false,
      })
      expect(s.repo.value?.id).toBe('repo-1')
      expect(s.creating.value).toBe(false)
      expect(s.form.token).toBe('')
      expect(s.activeTab.value).toBe('import')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
    })

    it('项目路径带首尾斜杠时规范化后再提交', async () => {
      mocks.createGitRepo.mockResolvedValue(makeRepo())
      const s = await initAndFlush()
      s.startCreate()
      s.form.name = '配置一'
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      s.form.repoPath = '/group/sub/repo/'
      s.form.token = 'glpat-secret'
      await s.save()
      expect(mocks.createGitRepo.mock.calls[0][0].repoPath).toBe('group/sub/repo')
    })

    it('项目路径只含斜杠时前置校验拦截，不发请求', async () => {
      const s = await initAndFlush()
      s.startCreate()
      s.form.name = '配置一'
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      s.form.repoPath = '///'
      await s.save()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写项目路径')
      expect(mocks.createGitRepo).not.toHaveBeenCalled()
    })

    it('保存被服务端拒（连通性实测失败）时保留表单并提示错误码', async () => {
      mocks.createGitRepo.mockRejectedValue({ code: 1000017702, message: '连接测试失败：分支不存在' })
      const s = await initAndFlush()
      s.startCreate()
      s.form.name = '配置一'
      s.form.gitlabBaseUrl = 'https://gitlab.example.com'
      s.form.repoPath = 'group/repo'
      await s.save()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017702 连接测试失败：分支不存在')
      expect(s.creating.value).toBe(true)
      expect(s.form.name).toBe('配置一')
      expect(s.activeTab.value).toBe('basic')
    })

    it('只读账号保存被拒绝，不发请求', async () => {
      const s = await initAndFlush({ repo: makeRepo(), hasPermission: false })
      await s.save()
      expect(mocks.ElMessage.warning).toHaveBeenCalled()
      expect(mocks.updateGitRepo).not.toHaveBeenCalled()
      expect(mocks.createGitRepo).not.toHaveBeenCalled()
    })
  })

  describe('保存（编辑）', () => {
    it('部分更新：不携带锁定字段，清空的可选字段与空令牌按不修改省略', async () => {
      mocks.updateGitRepo.mockResolvedValue(true)
      const s = await initAndFlush({ repo: makeRepo() })
      s.form.name = '新名称'
      s.form.testSourceDir = ''
      // 保存后重新拉取，表单以服务端持久化结果回填
      mocks.fetchGitRepo.mockResolvedValue(makeRepo({ name: '新名称' }))
      await s.save()
      expect(mocks.updateGitRepo).toHaveBeenCalledWith('repo-1', expect.any(Object))
      const payload = mocks.updateGitRepo.mock.calls[0][1]
      // 锁定字段不出现在更新请求类型与载荷中
      expect(payload).not.toHaveProperty('repoPath')
      expect(payload).not.toHaveProperty('defaultBranch')
      expect(payload).not.toHaveProperty('importMode')
      expect(payload.name).toBe('新名称')
      expect(payload.testSourceDir).toBeUndefined()
      expect(payload.token).toBeUndefined()
      expect(s.form.token).toBe('')
      expect(s.activeTab.value).toBe('import')
      expect(mocks.fetchGitRepo).toHaveBeenCalledTimes(2)
      expect(s.form.name).toBe('新名称')
    })

    it('锁定字段被拒（1000017711）时按错误码提示且不切换页签', async () => {
      mocks.updateGitRepo.mockRejectedValue({ code: 1000017711, message: '锁定字段不可变更' })
      const s = await initAndFlush({ repo: makeRepo() })
      s.form.name = '新名称'
      await s.save()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017711 锁定字段不可变更')
      expect(s.activeTab.value).toBe('basic')
      expect(s.loadFailed.value).toBe(false)
      expect(s.form.name).toBe('新名称')
    })
  })

  describe('删除', () => {
    it('取消确认不发删除请求', async () => {
      const s = await initAndFlush({ repo: makeRepo() })
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      await s.handleDelete()
      expect(mocks.deleteGitRepo).not.toHaveBeenCalled()
      expect(s.repo.value).not.toBeNull()
    })

    it('删除成功回到空态', async () => {
      mocks.deleteGitRepo.mockResolvedValue(true)
      const s = await initAndFlush({ repo: makeRepo() })
      await s.handleDelete()
      expect(mocks.deleteGitRepo).toHaveBeenCalledWith('repo-1')
      expect(s.repo.value).toBeNull()
      expect(s.creating.value).toBe(false)
      expect(s.activeTab.value).toBe('basic')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
    })

    it('同步任务占用被拒（1000017712）时按错误码提示且配置保留', async () => {
      mocks.deleteGitRepo.mockRejectedValue({ code: 1000017712, message: '存在启用中的同步任务' })
      const s = await initAndFlush({ repo: makeRepo() })
      await s.handleDelete()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017712 存在启用中的同步任务')
      expect(s.repo.value).not.toBeNull()
    })

    it('只读账号不弹删除确认', async () => {
      const s = await initAndFlush({ repo: makeRepo(), hasPermission: false })
      await s.handleDelete()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })
  })

  describe('静默刷新（导入完成后重拉配置）', () => {
    it('更新仓库配置但不动整页 loading，保留基础页签未保存的编辑', async () => {
      const s = await initAndFlush({ repo: makeRepo({ name: '旧名称' }) })
      s.form.name = '编辑中未保存'
      mocks.fetchGitRepo.mockResolvedValue(
        makeRepo({ name: '新名称', lastImportAt: '2026-10-13T02:00:00' }),
      )
      await s.refreshQuiet()
      expect(s.repo.value?.name).toBe('新名称')
      expect(s.repo.value?.lastImportAt).toBe('2026-10-13T02:00:00')
      expect(s.loading.value).toBe(false)
      expect(s.loadFailed.value).toBe(false)
      expect(s.form.name).toBe('编辑中未保存')
    })

    it('刷新失败按错误码提示，既有配置保留', async () => {
      const s = await initAndFlush({ repo: makeRepo() })
      mocks.fetchGitRepo.mockRejectedValue({ code: 1000017701, message: '仓库配置不存在' })
      await s.refreshQuiet()
      expect(s.repo.value).not.toBeNull()
      expect(s.loadFailed.value).toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017701 仓库配置不存在')
    })
  })

  describe('令牌只写不读', () => {
    it('令牌仅存于内存表单，不落 store 与任何本地存储', () => {
      expect(composableSource).not.toMatch(/localStorage\.(getItem|setItem|removeItem)|sessionStorage\.(getItem|setItem|removeItem)/)
      expect(composableSource).not.toContain('useLocalStorage')
      // 保存成功与回填均显式清空表单令牌
      expect(composableSource.match(/form\.token = ''/g)?.length).toBeGreaterThanOrEqual(2)
    })
  })

  describe('错误格式化', () => {
    it('带 10 位错误码的业务错误前置错误码', () => {
      expect(formatGitLabError(toGitLabError({ code: 1000017703, message: '路径已绑定' }))).toBe(
        '1000017703 路径已绑定',
      )
    })

    it('无错误码时透传消息，缺省时回落兜底文案', () => {
      expect(formatGitLabError(toGitLabError(new Error('网络错误')))).toBe('网络错误')
      expect(formatGitLabError(toGitLabError(undefined))).toBe('操作失败，请稍后重试')
    })
  })
})
