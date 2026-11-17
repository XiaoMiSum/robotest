import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchProjectModuleTree: vi.fn(),
  ElMessage: { error: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
  _onMountedCb: null as (() => void) | null,
  _onBeforeUnmountCb: null as (() => void) | null,
  _setIntervalCb: null as (() => void) | null,
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return {
    ...actual,
    onMounted: (cb: () => void) => { mocks._onMountedCb = cb },
    onBeforeUnmount: (cb: () => void) => { mocks._onBeforeUnmountCb = cb },
  }
})

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  createProjectModule: vi.fn(),
  createTestCase: vi.fn(),
  deleteProjectModule: vi.fn(),
  deleteTestCase: vi.fn(),
  updateProjectModule: vi.fn(),
  updateTestCase: vi.fn(),
}))

import { useProjectModuleTree } from './useProjectModuleTree'

const NODE = { id: 'd1', name: '目录一', type: 'directory', children: [] }

function makeComposable() {
  return useProjectModuleTree({
    getAssetType: () => 'testcase',
    getCollapseOnParentClick: () => false,
    getTreeRef: () => ({ filter: vi.fn(), setCurrentKey: vi.fn() }),
    onSelectDocument: vi.fn(),
    onSelectModule: vi.fn(),
  })
}

describe('useProjectModuleTree · 加载失败反馈（用例页 F4，三页共享）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('setInterval', (cb: () => void) => { mocks._setIntervalCb = cb; return 1 })
    vi.stubGlobal('clearInterval', vi.fn())
    mocks._onMountedCb = null
    mocks._onBeforeUnmountCb = null
    mocks._setIntervalCb = null
    mocks.fetchProjectModuleTree.mockResolvedValue([NODE])
  })

  it('初次加载失败置 loadError=true 并统一消息提示，树数据保持为空', async () => {
    mocks.fetchProjectModuleTree.mockRejectedValueOnce(new Error('网络错误'))
    const tree = makeComposable()
    mocks._onMountedCb!()
    await vi.waitFor(() => {
      expect(tree.loadError.value).toBe(true)
    })
    expect(tree.treeData.value).toEqual([])
    expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
  })

  it('已有数据时刷新失败不清空已加载树', async () => {
    const tree = makeComposable()
    mocks._onMountedCb!()
    await vi.waitFor(() => expect(tree.treeData.value).toEqual([NODE]))
    mocks.fetchProjectModuleTree.mockRejectedValueOnce(new Error('网络错误'))
    await tree.load()
    expect(tree.loadError.value).toBe(true)
    expect(tree.treeData.value).toEqual([NODE])
  })

  it('重试成功后清除 loadError', async () => {
    mocks.fetchProjectModuleTree.mockRejectedValueOnce(new Error('网络错误'))
    const tree = makeComposable()
    mocks._onMountedCb!()
    await vi.waitFor(() => expect(tree.loadError.value).toBe(true))
    await tree.load()
    expect(tree.loadError.value).toBe(false)
    expect(tree.treeData.value).toEqual([NODE])
  })

  it('静默轮询失败不提示且不改加载失败态，成功时自愈清除 loadError', async () => {
    mocks.fetchProjectModuleTree.mockRejectedValueOnce(new Error('网络错误'))
    const tree = makeComposable()
    mocks._onMountedCb!()
    await vi.waitFor(() => expect(tree.loadError.value).toBe(true))
    const errorsAfterLoad = mocks.ElMessage.error.mock.calls.length

    mocks.fetchProjectModuleTree.mockRejectedValueOnce(new Error('轮询失败'))
    await mocks._setIntervalCb!()
    expect(mocks.ElMessage.error.mock.calls.length).toBe(errorsAfterLoad)
    expect(tree.loadError.value).toBe(true)

    await mocks._setIntervalCb!()
    expect(tree.loadError.value).toBe(false)
  })
})
