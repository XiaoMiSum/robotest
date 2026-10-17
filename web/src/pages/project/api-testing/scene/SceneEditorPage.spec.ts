// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElTooltip } from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import type {
  ApiEnvironmentListItem,
  ApiExecutionStartResp,
  ApiGitRepoConfig,
  ApiSceneDetail,
  ProjectModule,
} from '@/types'
import pageSource from './SceneEditorPage.vue?raw'
import headerSource from './SceneEditorHeader.vue?raw'
import popoverSource from './SceneHistoryPopover.vue?raw'
import historySource from '../../../../composables/project/api-testing/scene/useSceneHistory.ts?raw'
import assetSource from '../../../../composables/project/api-testing/scene/useAssetPicker.ts?raw'

const mocks = vi.hoisted(() => ({
  fetchSceneDetail: vi.fn<(sceneId: string) => Promise<ApiSceneDetail>>(),
  updateScene: vi.fn<() => Promise<boolean>>(),
  executeScene: vi.fn<(sceneId: string, req?: unknown) => Promise<ApiExecutionStartResp>>(),
  fetchEnvironments: vi.fn<() => Promise<ApiEnvironmentListItem[]>>(),
  fetchEnvironmentDetail: vi.fn<() => Promise<unknown>>(),
  fetchProjectModuleTree: vi.fn<(assetType: string) => Promise<ProjectModule[]>>(),
  fetchGitRepo: vi.fn<() => Promise<ApiGitRepoConfig | null>>(),
  routerPush: vi.fn(),
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

vi.mock('@/services/project/api-testing/scene', async () => {
  const actual = await vi.importActual<typeof import('@/services/project/api-testing/scene')>(
    '@/services/project/api-testing/scene',
  )
  return { ...actual, fetchSceneDetail: mocks.fetchSceneDetail, updateScene: mocks.updateScene, executeScene: mocks.executeScene }
})

vi.mock('@/services/project/api-testing/environment', async () => {
  const actual = await vi.importActual<typeof import('@/services/project/api-testing/environment')>(
    '@/services/project/api-testing/environment',
  )
  return {
    ...actual,
    fetchEnvironments: mocks.fetchEnvironments,
    fetchEnvironmentDetail: mocks.fetchEnvironmentDetail,
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

import SceneEditorPage from './SceneEditorPage.vue'

// jsdom 未实现 ResizeObserver，el-table 的尺寸监听依赖它
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

const MODULE_TREE: ProjectModule[] = [
  { id: 'mod-1', parentId: null, type: 'directory', name: '订单查询', sortOrder: 1, children: [] },
]

function makeRepo(overrides?: Partial<ApiGitRepoConfig>): ApiGitRepoConfig {
  return {
    id: 'repo-1',
    projectId: 'proj-1',
    name: '订单接口自动化',
    gitlabBaseUrl: 'https://gitlab.example.com',
    repoPath: 'group/repo',
    defaultBranch: 'master',
    importMode: 'gitlab_ci',
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

function makeCiDetail(overrides?: Partial<ApiSceneDetail>): ApiSceneDetail {
  return {
    id: 'scene-1',
    name: '按状态查询订单',
    moduleId: 'mod-1',
    description: null,
    environmentId: null,
    priority: 'P1',
    status: 'draft',
    followed: false,
    variables: [],
    processors: [],
    changeVersion: 7,
    steps: [],
    engine: 'gitlab_ci',
    sourceType: 'gitlab',
    repoId: 'repo-1',
    sourceId: 'com.example.OrderQueryTest#queryOrderStatus',
    sourceRef: 'src/test/java/com/example/OrderQueryTest.java#queryOrderStatus',
    sourceStatus: 'in_sync',
    caseInfo: {
      methodName: 'queryOrderStatus',
      caseName: '按状态查询订单',
      sourceFile: 'src/test/java/com/example/OrderQueryTest.java',
    },
    ...overrides,
  }
}

async function mountEditor(detail: ApiSceneDetail): Promise<VueWrapper> {
  mocks.fetchSceneDetail.mockResolvedValue(detail)
  mocks.fetchProjectModuleTree.mockResolvedValue(MODULE_TREE)
  mocks.fetchEnvironments.mockResolvedValue([])
  mocks.fetchGitRepo.mockResolvedValue(makeRepo())
  const wrapper = mount(SceneEditorPage, {
    props: { sceneId: detail.id },
    global: {
      plugins: [ElementPlus, createPinia()],
      components: { ...ElementPlusIcons },
    },
  })
  await flushPromises()
  await nextTick()
  return wrapper
}

describe('SceneEditorPage 历史与资产错误边界', () => {
  it('执行/变更历史和公共组件列表均透传后端消息并提供重试', () => {
    expect(historySource).toContain('Promise.allSettled')
    expect(historySource).toContain('requestSequence')
    expect(historySource).toContain('ElMessage.error(message)')
    expect(historySource).toContain('retryHistory')
    expect(assetSource).toContain('assetPickerError')
    expect(assetSource).toContain('ElMessage.error(message)')
    expect(pageSource).toContain(':history-error="historyError"')
    expect(pageSource).toContain('@load-history="retryHistory"')
    expect(pageSource).toContain(':error="assetPickerError"')
    expect(headerSource).toContain('<SceneHistoryPopover')
    expect(popoverSource).toContain('v-if="error"')
    expect(popoverSource).toContain("@click=\"emit('load')\"")
    expect(headerSource).toContain('@load="emit(\'load-history\')"')
  })
})

describe('SceneEditorPage gitlab_ci 只读形态（交互 10 §1.9）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('步骤编辑区整体替换为只读信息区与单个用例信息表，编辑入口全部缺席', async () => {
    const wrapper = await mountEditor(makeCiDetail())

    expect(wrapper.find('[data-test="scene-readonly"]').exists()).toBe(true)
    expect(wrapper.find('.scene-editor__tabs-wrap').exists()).toBe(false)
    expect(wrapper.findAll('button').map((btn) => btn.text())).not.toContain('从接口添加')
    expect(wrapper.findAll('button').map((btn) => btn.text())).not.toContain('+ 添加步骤')

    // 只读信息网格：模块 / 来源类型 / 仓库 / 默认分支 / 源引用（包目录折叠为 ...）
    const info = wrapper.find('.ci-info')
    expect(info.text()).toContain('订单查询')
    expect(info.text()).toContain('GitLab 流水线')
    expect(info.text()).toContain('master')
    const links = wrapper.findAll('.ci-info__v a')
    expect(links[0].attributes('href')).toBe('https://gitlab.example.com/group/repo')
    expect(links[0].text()).toContain('订单接口自动化')
    expect(links[1].text()).toBe('src/test/java/.../OrderQueryTest.java#queryOrderStatus ↗')
    expect(links[1].attributes('href')).toContain('/src/test/java/com/example/OrderQueryTest.java')

    // 用例信息恒单条：方法名与源文件（源文件同样折叠包目录）
    const caseKv = wrapper.find('.ci-info--case')
    expect(caseKv.text()).toContain('queryOrderStatus')
    expect(caseKv.text()).toContain('按状态查询订单')
    expect(caseKv.text()).toContain('src/test/java/.../OrderQueryTest.java')
    expect(wrapper.find('.ci-hint').exists()).toBe(true)
  })

  it('缺少用例信息时降级为「用例信息不可用」', async () => {
    const wrapper = await mountEditor(makeCiDetail({ caseInfo: null }))
    expect(wrapper.find('.ci-info--case').exists()).toBe(false)
    expect(wrapper.find('.ci-cases__empty').text()).toBe('用例信息不可用')
  })

  it('顶部执行入口文案为「运行」，点击即触发流水线执行', async () => {
    mocks.executeScene.mockResolvedValue({ executionId: 'exec-1', status: 'running' })
    const wrapper = await mountEditor(makeCiDetail())

    const run = wrapper.find('.scene-editor__run-wrap button')
    expect(run.text()).toBe('▶ 运行')
    expect(run.attributes('disabled')).toBeUndefined()

    await run.trigger('click')
    await flushPromises()
    expect(mocks.executeScene).toHaveBeenCalledWith('scene-1', { environmentId: null })
    expect(mocks.ElMessage.success.mock.calls.at(-1)?.[0]).toBe('场景已触发执行（exec-1）')
  })

  it('仓库不可达时执行入口置灰，悬停给出不可执行原因', async () => {
    const wrapper = await mountEditor(makeCiDetail({ sourceStatus: 'repo_unreachable' }))

    const run = wrapper.find('.scene-editor__run-wrap button')
    expect(run.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.scene-editor__source-status').text()).toBe('仓库不可达')

    const runTooltip = wrapper
      .findAllComponents(ElTooltip)
      .find((cmp) => cmp.props('content') === '仓库不可达，无法执行')
    expect(runTooltip?.props('disabled')).toBe(false)
    // 悬停展示原因：EP tooltip 的打开有宏任务延迟，等待浮层渲染后再断言
    await wrapper.find('.scene-editor__run-wrap').trigger('mouseenter')
    await new Promise((resolve) => setTimeout(resolve, 10))
    await nextTick()
    expect(document.body.textContent).toContain('仓库不可达，无法执行')
  })

  it('GitLab 来源徽标点击跳集成页导入页签', async () => {
    const wrapper = await mountEditor(makeCiDetail())

    await wrapper.find('.scene-editor__git-badge').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/workspace/projects/api-testing',
      query: { tab: 'gitlab', gl: 'import' },
    })
  })
})
