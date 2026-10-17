// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElRadioGroup } from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import type { ApiGitImportPreview, ApiGitImportStatus, ApiGitRepoConfig } from '@/types'

const mocks = vi.hoisted(() => ({
  previewGitImport: vi.fn<(repoId: string) => Promise<ApiGitImportPreview>>(),
  startGitImport: vi.fn<(repoId: string) => Promise<ApiGitImportStatus>>(),
  fetchGitImportStatus: vi.fn<(repoId: string) => Promise<ApiGitImportStatus>>(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
}))

vi.mock('@/services/project/api-testing/gitRepo', () => ({
  previewGitImport: mocks.previewGitImport,
  startGitImport: mocks.startGitImport,
  fetchGitImportStatus: mocks.fetchGitImportStatus,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

// 组件走真实 Element Plus 渲染，仅替换消息与确认框入口便于断言
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return { ...actual, ElMessage: mocks.ElMessage, ElMessageBox: mocks.ElMessageBox }
})

import GitLabImportPane from './GitLabImportPane.vue'

// jsdom 未实现 ResizeObserver，el-table 的尺寸监听依赖它
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
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
    lastImportAt: '2026-10-11T02:20:30Z',
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

function mountPane(repo: ApiGitRepoConfig = makeRepo()): VueWrapper {
  return mount(GitLabImportPane, {
    props: { repo },
    global: { plugins: [ElementPlus], components: { ...ElementPlusIcons } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((btn) => btn.text() === text)
}

async function runPreview(wrapper: VueWrapper): Promise<void> {
  const btn = buttonByText(wrapper, '扫描预览')
  expect(btn).toBeTruthy()
  await btn?.trigger('click')
  await flushPromises()
}

describe('GitLabImportPane', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useAuthStore.mockReturnValue({
      hasPermission: vi.fn().mockReturnValue(true),
    })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
    mocks.fetchGitImportStatus.mockResolvedValue(makeStatus())
  })

  it('常驻上次导入信息条：时间、提交号前 7 位与四段计数', async () => {
    const wrapper = mountPane()
    await flushPromises()
    const last = wrapper.find('.gi-last')
    expect(last.exists()).toBe(true)
    expect(last.text()).toContain('上次导入')
    expect(last.text()).toContain('新建 193')
    expect(last.text()).toContain('解析失败 0')
    expect(wrapper.find('.gi-last__commit').text()).toBe('a1b2c3d')
    expect(wrapper.find('.gi-last__commit').attributes('href')).toBe(
      'https://gitlab.example.com/group/repo/-/commit/a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0',
    )
  })

  it('未预览时给出空态引导，执行导入因无预览而禁用', async () => {
    const wrapper = mountPane()
    await flushPromises()
    expect(wrapper.find('.gi-state__title').text()).toBe('扫描预览后执行导入')
    const execute = buttonByText(wrapper, '执行导入')
    expect(execute).toBeTruthy()
    expect(execute?.attributes('disabled')).toBeDefined()
  })

  it('扫描预览与执行导入为右端主色 link 按钮并带图标', async () => {
    const wrapper = mountPane()
    await flushPromises()
    const right = wrapper.find('.gi-actions__right')
    expect(right.exists()).toBe(true)
    const previewBtn = buttonByText(wrapper, '扫描预览')
    const executeBtn = buttonByText(wrapper, '执行导入')
    expect(previewBtn?.classes()).toEqual(expect.arrayContaining(['is-link', 'el-button--primary']))
    expect(executeBtn?.classes()).toEqual(expect.arrayContaining(['is-link', 'el-button--primary']))
    expect(previewBtn?.find('svg').exists()).toBe(true)
    expect(executeBtn?.find('svg').exists()).toBe(true)
    // 左端不放主操作
    expect(wrapper.find('.gi-actions__left').find('button').exists()).toBe(false)
  })

  it('预览后展示汇总条、环境说明与失败明细表', async () => {
    mocks.previewGitImport.mockResolvedValue(makePreview())
    const wrapper = mountPane()
    await flushPromises()
    await runPreview(wrapper)

    const sum = wrapper.find('.gi-sum')
    expect(sum.text()).toContain('预计导入')
    expect(sum.text()).toContain('新建 193')
    expect(sum.text()).toContain('更新 12')
    expect(sum.text()).toContain('解析失败 1')
    expect(sum.text()).toContain('真实错误 1')
    expect(sum.text()).toContain('测试类 20 · 有效用例 193')
    expect(sum.text()).toContain('环境并入「默认环境」')

    expect(wrapper.findAll('.gi-details__row')).toHaveLength(2)
    expect(wrapper.find('.gi-details').text()).toContain('com.example.OrderQueryTest#listByStatus')
    expect(wrapper.find('.gi-details').text()).toContain('文件级')
    expect(buttonByText(wrapper, '导出 CSV')).toBeTruthy()

    // 预览通过后执行导入才可用
    const execute = buttonByText(wrapper, '执行导入')
    expect(execute?.attributes('disabled')).toBeUndefined()
  })

  it('按类型筛选与汇总条解析失败段联动收窄明细', async () => {
    mocks.previewGitImport.mockResolvedValue(makePreview())
    const wrapper = mountPane()
    await flushPromises()
    await runPreview(wrapper)

    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'parseFailed')
    await flushPromises()
    expect(wrapper.findAll('.gi-details__row')).toHaveLength(1)
    expect(wrapper.find('.gi-details__count').text()).toContain('共 1 条')

    // 点击汇总条解析失败段同样切到该类型
    wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'all')
    await flushPromises()
    const seg = wrapper.find('.gi-seg--link')
    await seg.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.gi-details__row')).toHaveLength(1)
    expect(wrapper.find('.gi-details__row').text()).toContain('解析失败')
  })

  it('解析成功清单默认折叠，展开后展示条目与截断提示', async () => {
    mocks.previewGitImport.mockResolvedValue(makePreview())
    const wrapper = mountPane()
    await flushPromises()
    await runPreview(wrapper)

    const parsed = wrapper.find('.gi-parsed')
    expect(parsed.exists()).toBe(true)
    expect(parsed.text()).toContain('解析成功 205')
    expect(wrapper.find('.gi-parsed__list').exists()).toBe(false)

    await buttonByText(wrapper, '解析成功 205')?.trigger('click')
    await flushPromises()
    expect(wrapper.find('.gi-parsed__list').exists()).toBe(true)
    expect(wrapper.find('.gi-parsed__note').text()).toContain('仅展示前 2 条，共 205 条')
    expect(wrapper.findAll('.gi-parsed__item')).toHaveLength(2)
    expect(wrapper.find('.gi-parsed__item').text()).toContain('更新')
    expect(wrapper.find('.gi-parsed__key').text()).toBe('com.example.OrderQueryTest#listByStatus')
    expect(wrapper.find('.gi-parsed__name').text()).toBe('按状态查询订单列表')
  })

  it('重新扫描先清空上一次结果，loading 期间不展示旧结果也不闪现空态', async () => {
    mocks.previewGitImport.mockResolvedValue(makePreview())
    const wrapper = mountPane()
    await flushPromises()
    await runPreview(wrapper)
    expect(wrapper.find('.gi-sum').exists()).toBe(true)

    const deferred: { resolve: (value: ApiGitImportPreview) => void } = { resolve: () => {} }
    mocks.previewGitImport.mockReturnValueOnce(new Promise((resolve) => { deferred.resolve = resolve }))
    await buttonByText(wrapper, '扫描预览')?.trigger('click')

    expect(wrapper.find('.gi-sum').exists()).toBe(false)
    expect(wrapper.find('.gi-parsed').exists()).toBe(false)
    expect(wrapper.find('.gi-details').exists()).toBe(false)
    expect(wrapper.find('.gi-state').exists()).toBe(false)
    expect(wrapper.find('.gi-progress').exists()).toBe(false)

    deferred.resolve(makePreview())
    await flushPromises()
    expect(wrapper.find('.gi-sum').exists()).toBe(true)
  })

  it('闸门拦截转 info 提示条并可查看上次结果，不渲染明细与空态', async () => {
    mocks.previewGitImport.mockResolvedValue(
      makePreview({
        gate: { blocked: true, reason: '目标分支提交与上次导入一致且无遗留错误，本次导入已跳过' },
        classes: null,
        effectiveCases: null,
        modules: null,
        scenes: null,
        environment: null,
        details: [],
        parsedScenes: null,
      }),
    )
    const wrapper = mountPane()
    await flushPromises()
    await runPreview(wrapper)

    const alert = wrapper.find('.el-alert')
    expect(alert.exists()).toBe(true)
    expect(alert.classes()).toContain('el-alert--info')
    expect(wrapper.text()).toContain('目标分支提交与上次导入一致')
    expect(wrapper.find('.gi-details').exists()).toBe(false)
    expect(wrapper.find('.gi-state').exists()).toBe(false)
    expect(buttonByText(wrapper, '执行导入')?.attributes('disabled')).toBeDefined()

    const openResult = buttonByText(wrapper, '查看上次结果')
    expect(openResult).toBeTruthy()
    await openResult?.trigger('click')
    await flushPromises()
    // el-dialog append-to-body 挂载到 body，从 document 断言其内容
    const dialog = document.body.querySelector('.el-dialog')
    expect(dialog?.textContent).toContain('上次导入结果')
    expect(dialog?.textContent).toContain('a1b2c3d')
  })

  it('缺 api-scene:edit 时隐藏执行导入并提示，仍可预览', async () => {
    mocks.useAuthStore.mockReturnValue({
      hasPermission: vi.fn((code: string) => code === 'api-scene:view'),
    })
    mocks.previewGitImport.mockResolvedValue(makePreview())
    const wrapper = mountPane()
    await flushPromises()
    expect(wrapper.text()).toContain('缺少 api-scene:edit 权限，不可执行导入')
    expect(buttonByText(wrapper, '执行导入')).toBeUndefined()
    await runPreview(wrapper)
    expect(wrapper.find('.gi-sum').exists()).toBe(true)
  })

  it('缺 api-scene:view 时只读提示，不渲染动作行', async () => {
    mocks.useAuthStore.mockReturnValue({
      hasPermission: vi.fn(() => false),
    })
    const wrapper = mountPane()
    await flushPromises()
    expect(wrapper.text()).toContain('当前账号缺少 api-scene:view 权限')
    expect(buttonByText(wrapper, '扫描预览')).toBeUndefined()
    expect(buttonByText(wrapper, '执行导入')).toBeUndefined()
  })

  it('进行中的进度渲染阶段中文与计数', async () => {
    mocks.fetchGitImportStatus.mockResolvedValue(
      makeStatus({ stage: 'parse', processed: 96, total: 193 }),
    )
    const wrapper = mountPane()
    await flushPromises()
    const progress = wrapper.find('.gi-progress')
    expect(progress.exists()).toBe(true)
    expect(progress.text()).toContain('进行中 · 解析用例')
    expect(progress.text()).toContain('96 / 193')
    // 服务端继续轮询由组合式接管，重进恢复：此处仅断言进入即重查
    expect(mocks.fetchGitImportStatus).toHaveBeenCalledTimes(1)
  })

  it('失败进度展示错误原因，完成进度复用导入结果汇总条', async () => {
    mocks.fetchGitImportStatus.mockResolvedValue(
      makeStatus({ stage: 'failed', error: 'GitLab 不可达' }),
    )
    const failed = mountPane()
    await flushPromises()
    expect(failed.find('.gi-progress').text()).toContain('导入失败')
    expect(failed.find('.gi-progress').text()).toContain('GitLab 不可达')
    failed.unmount()

    mocks.fetchGitImportStatus.mockResolvedValue(
      makeStatus({
        stage: 'done',
        processed: 193,
        total: 193,
        summary: { created: 193, updated: 12, parseFailed: 1, errorFailed: 0, skipped: 0 },
      }),
    )
    const done = mountPane()
    await flushPromises()
    expect(done.find('.gi-sum').text()).toContain('导入结果')
    expect(done.find('.gi-sum').text()).toContain('解析失败 1')
    expect(done.find('.gi-progress').text()).toContain('导入完成')
  })
})
