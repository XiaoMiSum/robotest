// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import type {
  ApiGitConnectionTestReq,
  ApiGitConnectionTestResp,
  ApiGitImportPreview,
  ApiGitImportStatus,
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
  previewGitImport: vi.fn<(repoId: string) => Promise<ApiGitImportPreview>>(),
  startGitImport: vi.fn<(repoId: string) => Promise<ApiGitImportStatus>>(),
  fetchGitImportStatus: vi.fn<(repoId: string) => Promise<ApiGitImportStatus>>(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn<() => Promise<void>>() },
}))

// 页签初始化读 ?gl= 落地参数（交互 09 §1.2），单测固定空 query
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

// 组件走真实 Element Plus 渲染，仅替换消息与确认框入口便于断言
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return { ...actual, ElMessage: mocks.ElMessage, ElMessageBox: mocks.ElMessageBox }
})

vi.mock('@/services/project/api-testing/gitRepo', () => ({
  fetchGitRepo: mocks.fetchGitRepo,
  createGitRepo: mocks.createGitRepo,
  updateGitRepo: mocks.updateGitRepo,
  deleteGitRepo: mocks.deleteGitRepo,
  testGitRepoConnection: mocks.testGitRepoConnection,
  previewGitImport: mocks.previewGitImport,
  startGitImport: mocks.startGitImport,
  fetchGitImportStatus: mocks.fetchGitImportStatus,
}))

import GitLabSettingsPage from './GitLabSettingsPage.vue'

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

function makeStatus(overrides?: Partial<ApiGitImportStatus>): ApiGitImportStatus {
  return { stage: 'idle', processed: 0, total: 0, error: null, summary: null, ...overrides }
}

let active: VueWrapper | null = null

async function mountPage(): Promise<VueWrapper> {
  const wrapper = mount(GitLabSettingsPage, {
    global: { plugins: [ElementPlus], components: { ...ElementPlusIcons } },
  })
  active = wrapper
  await flushPromises()
  return wrapper
}

function buttonByText(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((btn) => btn.text() === text)
}

function activeTabText(wrapper: VueWrapper): string {
  return wrapper.find('.el-tabs__item.is-active').text()
}

/** DOMWrapper.element 推导为 Element，取输入值需断言为 HTMLInputElement */
function inputValue(node: { element: Element }): string {
  return (node.element as HTMLInputElement).value
}

/** [⋯] 更多菜单经 teleport 挂到 body，需触发后从 document 取菜单项 */
async function openMoreMenu(wrapper: VueWrapper): Promise<HTMLElement> {
  await wrapper.find('.gl-more').trigger('click')
  await nextTick()
  await nextTick()
  const item = Array.from(document.body.querySelectorAll('.el-dropdown-menu__item')).find((el) =>
    el.textContent?.includes('删除配置'),
  )
  expect(item).toBeTruthy()
  return item as HTMLElement
}

describe('GitLabSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn().mockReturnValue(true) })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
    mocks.fetchGitImportStatus.mockResolvedValue(makeStatus())
  })

  afterEach(() => {
    active?.unmount()
    active = null
  })

  it('空态给出接入前置清单，立即配置后渲染创建态表单与接入方式卡', async () => {
    mocks.fetchGitRepo.mockResolvedValue(null)
    const wrapper = await mountPage()

    expect(wrapper.find('.gl-state__title').text()).toBe('尚未配置 GitLab 仓库')
    const reqs = wrapper.find('.gl-reqs')
    expect(reqs.text()).toContain('@Test')
    expect(reqs.text()).toContain('@RyzeTest')

    await buttonByText(wrapper, '立即配置')?.trigger('click')
    await flushPromises()

    // 创建态：三锁定字段可填、接入方式两卡并列，默认平台执行
    expect(wrapper.find('input[placeholder="group/subgroup/repo"]').exists()).toBe(true)
    const modes = wrapper.findAll('.gl-mode')
    expect(modes.map((mode) => mode.text())).toEqual([
      expect.stringContaining('平台执行'),
      expect.stringContaining('流水线执行'),
    ])
    expect(modes[0].classes()).toContain('is-active')
    // 首次配置未落库，不展示连通性徽标与删除入口
    expect(wrapper.find('.gl-badge').exists()).toBe(false)
    expect(wrapper.find('.gl-more').exists()).toBe(false)
  })

  it('已配置时回填可编辑字段，锁定字段以只读文本呈现且令牌只写为空', async () => {
    mocks.fetchGitRepo.mockResolvedValue(makeRepo({ importMode: 'gitlab_ci' }))
    const wrapper = await mountPage()

    expect(inputValue(wrapper.find('input[placeholder="如：订单接口自动化"]'))).toBe('订单接口自动化')
    expect(inputValue(wrapper.find('input[placeholder="https://gitlab.example.com"]'))).toBe(
      'https://gitlab.example.com',
    )

    const locked = wrapper.find('.gl-locked')
    expect(locked.text()).toContain('创建后锁定')
    expect(locked.text()).toContain('group/repo')
    expect(locked.text()).toContain('master')
    expect(locked.text()).toContain('流水线执行')
    expect(locked.text()).toContain('gitlab_ci')
    // 锁定字段不渲染为（禁用）输入框
    expect(wrapper.find('input[placeholder="group/subgroup/repo"]').exists()).toBe(false)
    expect(wrapper.find('input[placeholder="master"]').exists()).toBe(false)

    // 令牌只写不读：空值 + 说明文案，不回显已存令牌
    const token = wrapper.find('input[type="password"]')
    expect(token.exists()).toBe(true)
    expect(inputValue(token)).toBe('')
    expect(token.attributes('placeholder')).toBe('仅写不读，留空表示不修改')

    // 流水线执行下重复导入覆盖不生效，环境声明文件只读
    expect(wrapper.find('.gl-switch-field').exists()).toBe(false)
    expect(wrapper.find('input[placeholder="common/env/env.yaml"]').attributes('disabled')).toBeDefined()

    expect(wrapper.find('.gl-badge').text()).toContain('已连通')
  })

  it('锁定字段被拒（1000017711）时以错误码 + 原因提示，留在基础页签且保留修正内容', async () => {
    mocks.fetchGitRepo.mockResolvedValue(makeRepo())
    mocks.updateGitRepo.mockRejectedValue({ code: 1000017711, message: '锁定字段不可变更' })
    const wrapper = await mountPage()

    await wrapper.find('input[placeholder="如：订单接口自动化"]').setValue('新名称')
    await buttonByText(wrapper, '保存')?.trigger('click')
    await flushPromises()

    expect(mocks.updateGitRepo).toHaveBeenCalledWith(
      'repo-1',
      expect.objectContaining({ name: '新名称' }),
    )
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017711 锁定字段不可变更')
    expect(activeTabText(wrapper)).toBe('基础配置')
    expect(inputValue(wrapper.find('input[placeholder="如：订单接口自动化"]'))).toBe('新名称')
  })

  it('令牌只写不回显且不落本地存储：保存载荷携带令牌，成功后清空并落到导入页签', async () => {
    mocks.fetchGitRepo.mockResolvedValue(makeRepo())
    mocks.updateGitRepo.mockResolvedValue(true)
    const wrapper = await mountPage()

    await wrapper.find('input[type="password"]').setValue('glpat-secret')
    // 令牌仅存内存表单，输入过程不触发任何本地存储写入
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)

    await buttonByText(wrapper, '保存')?.trigger('click')
    await flushPromises()

    expect(mocks.updateGitRepo).toHaveBeenCalledWith(
      'repo-1',
      expect.objectContaining({ token: 'glpat-secret' }),
    )
    expect(activeTabText(wrapper)).toBe('导入')
    // 保存成功后不再回显，任何本地存储仍为空
    expect(inputValue(wrapper.find('input[type="password"]'))).toBe('')
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
  })

  it('同步任务占用拒删（1000017712）时以错误码 + 原因提示，配置保留', async () => {
    mocks.fetchGitRepo.mockResolvedValue(makeRepo())
    mocks.deleteGitRepo.mockRejectedValue({ code: 1000017712, message: '存在启用中的同步任务' })
    const wrapper = await mountPage()

    const item = await openMoreMenu(wrapper)
    item.click()
    await flushPromises()

    expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
    expect(mocks.deleteGitRepo).toHaveBeenCalledWith('repo-1')
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('1000017712 存在启用中的同步任务')
    // 配置保留，未切页签
    expect(wrapper.find('.gl-locked').exists()).toBe(true)
    expect(activeTabText(wrapper)).toBe('基础配置')
  })

  it('连通性失败以错误码 + 原因行内展示，徽标转不可达并可展开详情', async () => {
    mocks.fetchGitRepo.mockResolvedValue(makeRepo())
    mocks.testGitRepoConnection.mockRejectedValue({
      code: 1000017702,
      message: 'GitLab 不可达或令牌无效',
    })
    const wrapper = await mountPage()

    await buttonByText(wrapper, '连通性测试')?.trigger('click')
    await flushPromises()

    expect(wrapper.find('.gl-result--fail').text()).toContain('1000017702 GitLab 不可达或令牌无效')
    const badge = wrapper.find('.gl-badge')
    expect(badge.classes()).toContain('gl-badge--down')
    expect(badge.text()).toContain('不可达')

    await badge.trigger('click')
    await nextTick()
    expect(wrapper.find('.gl-conn-detail .el-alert__title').text()).toBe(
      '1000017702 GitLab 不可达或令牌无效',
    )
  })
})
