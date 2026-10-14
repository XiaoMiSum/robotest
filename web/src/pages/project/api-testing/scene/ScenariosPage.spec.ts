// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElSelect } from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import type {
  ApiGitRepoConfig,
  ApiScenePageItem,
  PageResult,
  ProjectModule,
} from '@/types'

const mocks = vi.hoisted(() => ({
  fetchScenePage: vi.fn<(params: Record<string, unknown>) => Promise<PageResult<ApiScenePageItem>>>(),
  deleteScene: vi.fn<() => Promise<boolean>>(),
  executeScene: vi.fn<() => Promise<unknown>>(),
  followScene: vi.fn<() => Promise<boolean>>(),
  unfollowScene: vi.fn<() => Promise<boolean>>(),
  batchDeleteScenes: vi.fn<() => Promise<boolean>>(),
  batchMoveScenes: vi.fn<() => Promise<boolean>>(),
  fetchGitRepo: vi.fn<() => Promise<ApiGitRepoConfig | null>>(),
  fetchProjectModuleTree: vi.fn<(assetType: string) => Promise<ProjectModule[]>>(),
  routerPush: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush }),
  useRoute: () => ({ query: {} }),
}))

// 组件走真实 Element Plus 渲染，仅替换消息与确认框入口便于断言
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return { ...actual, ElMessage: mocks.ElMessage, ElMessageBox: mocks.ElMessageBox }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/services/project/api-testing/scene', async () => {
  const actual = await vi.importActual<typeof import('@/services/project/api-testing/scene')>(
    '@/services/project/api-testing/scene',
  )
  return {
    ...actual,
    fetchScenePage: mocks.fetchScenePage,
    deleteScene: mocks.deleteScene,
    executeScene: mocks.executeScene,
    followScene: mocks.followScene,
    unfollowScene: mocks.unfollowScene,
    batchDeleteScenes: mocks.batchDeleteScenes,
    batchMoveScenes: mocks.batchMoveScenes,
  }
})

vi.mock('@/services/project/api-testing/gitRepo', async () => {
  const actual = await vi.importActual<typeof import('@/services/project/api-testing/gitRepo')>(
    '@/services/project/api-testing/gitRepo',
  )
  return { ...actual, fetchGitRepo: mocks.fetchGitRepo }
})

vi.mock('@/services/project', async () => {
  const actual = await vi.importActual<typeof import('@/services/project')>('@/services/project')
  return { ...actual, fetchProjectModuleTree: mocks.fetchProjectModuleTree }
})

import ScenariosPage from './ScenariosPage.vue'

// jsdom 未实现 ResizeObserver，el-table 的尺寸监听依赖它
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

function makeScene(overrides?: Partial<ApiScenePageItem>): ApiScenePageItem {
  return {
    id: 'scene-1',
    name: '按状态查询订单',
    moduleId: null,
    environmentId: null,
    priority: 'P1',
    status: 'draft',
    stepCount: 3,
    lastExecutedAt: null,
    lastStatus: null,
    updatedAt: '2026-10-11T02:20:30Z',
    followed: false,
    sourceType: 'platform',
    repoId: null,
    sourceRef: null,
    sourceStatus: null,
    ...overrides,
  }
}

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

const INTEGRATION_PAGE = '/workspace/projects/api-testing'

async function mountPage(
  rows: ApiScenePageItem[],
  repo: ApiGitRepoConfig | null = makeRepo(),
): Promise<VueWrapper> {
  mocks.fetchScenePage.mockResolvedValue({ list: rows, total: rows.length })
  mocks.fetchGitRepo.mockResolvedValue(repo)
  mocks.fetchProjectModuleTree.mockResolvedValue([])
  const wrapper = mount(ScenariosPage, {
    global: {
      plugins: [ElementPlus],
      components: { ...ElementPlusIcons },
      // 模块树自带轮询与独立服务调用，与来源徽标断言无关，隔离为桩
      stubs: { ProjectModuleTree: true },
    },
  })
  await flushPromises()
  return wrapper
}

/** el-select 下拉面板挂载在 body，改为按组件契约直接驱动 v-model 更新 */
async function changeSelect(wrapper: VueWrapper, placeholder: string, value: string): Promise<void> {
  const select = wrapper
    .findAllComponents(ElSelect)
    .find((cmp) => cmp.props('placeholder') === placeholder)
  expect(select).toBeTruthy()
  select?.vm.$emit('update:modelValue', value)
  await flushPromises()
}

describe('ScenariosPage GitLab 来源与源状态展示', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn().mockReturnValue(true) })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  })

  it('GitLab 导入场景展示来源徽标，平台原生场景不展示；点击徽标跳集成页导入页签', async () => {
    const wrapper = await mountPage([
      makeScene({ id: 'scene-gl', name: '流水线查询订单', sourceType: 'gitlab' }),
      makeScene({ id: 'scene-native', name: '平台原生场景' }),
    ])

    const badges = wrapper.findAll('.scenarios-page__source-badge')
    expect(badges).toHaveLength(1)
    expect(badges[0].text()).toBe('GitLab')

    await badges[0].trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: INTEGRATION_PAGE,
      query: { tab: 'gitlab', gl: 'import' },
    })
  })

  it('[来源] 筛选按 sourceType 下发查询，重置后不再携带该条件', async () => {
    const wrapper = await mountPage([makeScene()])

    await changeSelect(wrapper, '来源', 'gitlab')
    expect(mocks.fetchScenePage).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNo: 1, sourceType: 'gitlab' }),
    )

    await changeSelect(wrapper, '来源', 'platform')
    expect(mocks.fetchScenePage).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNo: 1, sourceType: 'platform' }),
    )

    const reset = wrapper.findAll('button').find((btn) => btn.text() === '重置')
    expect(reset).toBeTruthy()
    await reset?.trigger('click')
    const lastQuery = mocks.fetchScenePage.mock.calls.at(-1)?.[0]
    expect(lastQuery).not.toHaveProperty('sourceType')
  })

  it('源状态徽标按状态渲染对应文案与配色，in_sync 不渲染徽标', async () => {
    const wrapper = await mountPage([
      makeScene({ id: 's1', name: '源码已退化场景', sourceType: 'gitlab', sourceStatus: 'parse_failed' }),
      makeScene({ id: 's2', name: '来源已移除场景', sourceType: 'gitlab', sourceStatus: 'repo_removed' }),
      makeScene({ id: 's3', name: '仓库不可达场景', sourceType: 'gitlab', sourceStatus: 'repo_unreachable' }),
      makeScene({ id: 's4', name: '同步中场景', sourceType: 'gitlab', sourceStatus: 'in_sync' }),
    ])

    const badges = wrapper.findAll('.scenarios-page__source-status')
    expect(badges.map((badge) => badge.text())).toEqual(['源码已退化', '来源已移除', '仓库不可达'])
    expect(badges[0].classes()).toContain('is-parse_failed')
    expect(badges[1].classes()).toContain('is-repo_removed')
    expect(badges[2].classes()).toContain('is-repo_unreachable')
  })

  it('源状态徽标点击跳集成页（不带落地页签参数）', async () => {
    const wrapper = await mountPage([
      makeScene({ sourceType: 'gitlab', sourceStatus: 'repo_removed' }),
    ])

    await wrapper.find('.scenarios-page__source-status').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: INTEGRATION_PAGE,
      query: { tab: 'gitlab' },
    })
  })
})

describe('ScenariosPage 仓库级「源有更新」提示条', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn().mockReturnValue(true) })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  })

  it('hasUpdates 为真时仅渲染一条提示条，前往导入跳集成页导入页签', async () => {
    const rows = [
      makeScene({ id: 'scene-gl', sourceType: 'gitlab' }),
      makeScene({ id: 'scene-gl-2', name: '另一导入场景', sourceType: 'gitlab' }),
    ]
    const wrapper = await mountPage(rows, makeRepo({ hasUpdates: true }))

    const banners = wrapper.findAll('.scenarios-page__source-updates')
    expect(banners).toHaveLength(1)
    expect(banners[0].text()).toContain('所属仓库有更新，导入或同步后生效')

    const goImport = banners[0].find('button')
    expect(goImport?.text()).toBe('前往导入')
    await goImport?.trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: INTEGRATION_PAGE,
      query: { tab: 'gitlab', gl: 'import' },
    })
  })

  it('hasUpdates 为假时不渲染提示条', async () => {
    const wrapper = await mountPage([makeScene({ sourceType: 'gitlab' })], makeRepo({ hasUpdates: false }))
    expect(wrapper.find('.scenarios-page__source-updates').exists()).toBe(false)
    // 列表主流程不受影响
    expect(wrapper.findAll('.el-table__row')).toHaveLength(1)
  })

  it('仓库信息拉取失败时静默降级，不渲染提示条且列表照常展示', async () => {
    mocks.fetchGitRepo.mockRejectedValue(new Error('网络错误'))
    const wrapper = await mountPage([makeScene({ sourceType: 'gitlab' })])
    expect(wrapper.find('.scenarios-page__source-updates').exists()).toBe(false)
    expect(wrapper.findAll('.el-table__row')).toHaveLength(1)
    expect(mocks.ElMessage.error).not.toHaveBeenCalled()
  })
})
