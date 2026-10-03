import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import type { RequirementListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchRequirements: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  confirmRequirement: vi.fn(),
  archiveRequirement: vi.fn(),
  unarchiveRequirement: vi.fn(),
  fetchMembers: vi.fn(),
  useAuthStore: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchRequirements: mocks.fetchRequirements,
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  confirmRequirement: mocks.confirmRequirement,
  archiveRequirement: mocks.archiveRequirement,
  unarchiveRequirement: mocks.unarchiveRequirement,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useRequirementList } from './useRequirementList'

function makeItem(overrides: Partial<RequirementListItem> = {}): RequirementListItem {
  return {
    id: 'r1',
    code: 'REQ-001',
    title: '登录验证码',
    moduleId: 'm1',
    moduleName: '登录模块',
    systemVersion: 'V2.3',
    status: 'draft',
    coverageStatus: null,
    priority: 'high',
    ownerId: 'u1',
    ownerName: '张三',
    source: 'manual',
    updatedAt: '2026-10-02T08:00:00Z',
    ...overrides,
  }
}

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

async function flush(): Promise<void> {
  // 测试统一启用假定时器：推进极小步长以排空微任务链，避免真实等待
  await vi.advanceTimersByTimeAsync(10)
}

function setup(): void {
  setActivePinia(createPinia())
  mocks.fetchRequirements.mockResolvedValue({ list: [makeItem()], total: 1 })
  mocks.fetchProjectModuleTree.mockResolvedValue([
    { id: 'm1', parentId: null, type: 'directory', name: '登录模块', sortOrder: 1, children: [] },
  ])
  mocks.fetchMembers.mockResolvedValue({
    list: [{ userId: 'u1', username: 'zhangsan', name: '张三' }],
    total: 1,
  })
  mocks.confirmRequirement.mockResolvedValue({ status: 'confirmed' })
  mocks.archiveRequirement.mockResolvedValue({ status: 'archived' })
  mocks.unarchiveRequirement.mockResolvedValue({ status: 'draft' })
  mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
  mocks.useAuthStore.mockReturnValue({
    hasPermission: vi.fn(() => true),
  })
}

describe('useRequirementList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setup()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('列表加载', () => {
    it('成功加载后填充行视图模型与总数', async () => {
      const s = useRequirementList()
      await s.load()
      expect(s.rows.value).toHaveLength(1)
      expect(s.rows.value[0].statusMeta.label).toBe('草稿')
      expect(s.rows.value[0].coverageMeta).toBeNull()
      expect(s.total.value).toBe(1)
      expect(s.hasLoaded.value).toBe(true)
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('失败时记录页面级错误且不进入已加载态', async () => {
      mocks.fetchRequirements.mockRejectedValue(new Error('服务不可用'))
      const s = useRequirementList()
      await s.load()
      expect(s.loadError.value).toBe('服务不可用')
      expect(s.hasLoaded.value).toBe(false)
      expect(s.loading.value).toBe(false)
    })

    it('非 Error 异常回退到默认文案', async () => {
      mocks.fetchRequirements.mockRejectedValue('boom')
      const s = useRequirementList()
      await s.load()
      expect(s.loadError.value).toBe('加载需求列表失败')
    })

    it('retry 重新拉取并清空错误', async () => {
      mocks.fetchRequirements.mockRejectedValueOnce(new Error('网络异常'))
      const s = useRequirementList()
      await s.load()
      expect(s.loadError.value).toBe('网络异常')
      s.retry()
      await flush()
      expect(s.loadError.value).toBe('')
      expect(s.hasLoaded.value).toBe(true)
    })

    it('过期响应不覆盖新结果', async () => {
      const slow = deferred<{ list: RequirementListItem[]; total: number }>()
      const fast = deferred<{ list: RequirementListItem[]; total: number }>()
      mocks.fetchRequirements
        .mockReturnValueOnce(slow.promise)
        .mockReturnValueOnce(fast.promise)

      const s = useRequirementList()
      const first = s.load()
      const second = s.load()

      fast.resolve({ list: [makeItem({ id: 'r2', title: '新结果' })], total: 1 })
      await second
      slow.resolve({ list: [makeItem({ id: 'r1', title: '旧结果' })], total: 9 })
      await first

      expect(s.rows.value[0].id).toBe('r2')
      expect(s.total.value).toBe(1)
    })

    it('版本选项按当前页数据去重累计', async () => {
      mocks.fetchRequirements.mockResolvedValue({
        list: [makeItem({ systemVersion: 'V1' }), makeItem({ id: 'r2', systemVersion: 'V2' })],
        total: 2,
      })
      const s = useRequirementList()
      await s.load()
      expect(s.versionOptions.value).toEqual(['V1', 'V2'])
      await s.load()
      expect(s.versionOptions.value).toEqual(['V1', 'V2'])
    })
  })

  describe('筛选', () => {
    it('search 拼接逗号多选条件并回到第一页', async () => {
      const s = useRequirementList()
      s.changePage(3)
      await flush()

      s.filters.status = ['draft', 'changed']
      s.filters.moduleIds = ['m1', 'm2']
      s.filters.ownerId = 'u1'
      s.filters.systemVersion = 'V2.3'
      s.filters.keyword = ' 登录 '
      s.search()
      await flush()

      expect(s.pageNo.value).toBe(1)
      expect(mocks.fetchRequirements).toHaveBeenLastCalledWith(
        expect.objectContaining({
          status: 'draft,changed',
          moduleIds: 'm1,m2',
          ownerId: 'u1',
          systemVersion: 'V2.3',
          keyword: '登录',
          pageNo: 1,
          pageSize: 20,
        }),
      )
    })

    it('空筛选不传对应参数', async () => {
      const s = useRequirementList()
      await s.load()
      const params = mocks.fetchRequirements.mock.calls.at(-1)?.[0] as Record<string, unknown>
      expect(params.status).toBeUndefined()
      expect(params.moduleIds).toBeUndefined()
      expect(params.ownerId).toBeUndefined()
      expect(params.keyword).toBeUndefined()
    })

    it('filterCount 统计生效条件数', async () => {
      const s = useRequirementList()
      await s.load()
      expect(s.filterCount.value).toBe(0)
      s.filters.status = ['archived']
      s.filters.ownerId = 'u1'
      expect(s.filterCount.value).toBe(2)
    })

    it('resetFilters 清空条件并回到第一页', async () => {
      const s = useRequirementList()
      s.changePage(2)
      s.filters.status = ['archived']
      s.filters.keyword = 'abc'
      s.resetFilters()
      await flush()
      expect(s.filters.status).toEqual([])
      expect(s.filters.keyword).toBe('')
      expect(s.filterCount.value).toBe(0)
      expect(s.pageNo.value).toBe(1)
      expect(mocks.fetchRequirements).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageNo: 1 }),
      )
    })

    it('关键词 1 秒防抖自动查询', async () => {
      const s = useRequirementList()
      await s.load()
      const callsAfterMount = mocks.fetchRequirements.mock.calls.length

      s.filters.keyword = '登'
      await nextTick()
      s.filters.keyword = '登录'
      await nextTick()
      await vi.advanceTimersByTimeAsync(400)
      expect(mocks.fetchRequirements.mock.calls.length).toBe(callsAfterMount)

      await vi.advanceTimersByTimeAsync(700)
      expect(mocks.fetchRequirements.mock.calls.length).toBe(callsAfterMount + 1)
      expect(mocks.fetchRequirements).toHaveBeenLastCalledWith(
        expect.objectContaining({ keyword: '登录' }),
      )
    })

    it('关键词与已搜索值相同时不重复查询', async () => {
      const s = useRequirementList()
      s.filters.keyword = '登录'
      s.search()
      await flush()
      const calls = mocks.fetchRequirements.mock.calls.length
      s.filters.keyword = '登录 '
      await nextTick()
      await vi.advanceTimersByTimeAsync(1200)
      // trim 后与上次搜索值一致 → 不重复发请求
      expect(mocks.fetchRequirements.mock.calls.length).toBe(calls)
    })  })

  describe('分页', () => {
    it('翻页与改 pageSize 的取数参数', async () => {
      const s = useRequirementList()
      await s.load()
      s.changePage(3)
      await flush()
      expect(mocks.fetchRequirements).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageNo: 3, pageSize: 20 }),
      )
      s.changePageSize(50)
      await flush()
      expect(mocks.fetchRequirements).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageNo: 1, pageSize: 50 }),
      )
    })
  })

  describe('状态操作', () => {
    it('确认成功后刷新行状态与提示', async () => {
      const s = useRequirementList()
      await s.load()
      // 确认后统一重取列表，重取结果同样是 confirmed，行状态保持一致
      mocks.fetchRequirements.mockResolvedValue({
        list: [makeItem({ status: 'confirmed' })],
        total: 1,
      })
      await s.handleConfirm(s.rows.value[0])
      expect(mocks.confirmRequirement).toHaveBeenCalledWith('r1')
      expect(s.rows.value[0].status).toBe('confirmed')
      expect(s.rows.value[0].statusMeta.label).toBe('已确认')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('需求已确认')
    })

    it('归档二次确认取消时不发请求', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue('cancel')
      const s = useRequirementList()
      await s.load()
      await s.handleArchive(s.rows.value[0])
      expect(mocks.archiveRequirement).not.toHaveBeenCalled()
    })

    it('归档确认后调用服务端', async () => {
      const s = useRequirementList()
      await s.load()
      await s.handleArchive(s.rows.value[0])
      expect(mocks.archiveRequirement).toHaveBeenCalledWith('r1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('需求已归档')
    })

    it('取消归档成功提示回到草稿', async () => {
      mocks.fetchRequirements.mockResolvedValue({
        list: [makeItem({ status: 'archived' })],
        total: 1,
      })
      const s = useRequirementList()
      await s.load()
      await s.handleUnarchive(s.rows.value[0])
      expect(mocks.unarchiveRequirement).toHaveBeenCalledWith('r1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已取消归档，条目回到草稿状态')
    })

    it('操作失败提示服务端文案', async () => {
      mocks.confirmRequirement.mockRejectedValue(new Error('当前状态不允许该操作'))
      const s = useRequirementList()
      await s.load()
      await s.handleConfirm(s.rows.value[0])
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('当前状态不允许该操作')
      expect(s.rows.value[0].status).toBe('draft')
    })
  })

  describe('筛选项选项', () => {
    it('模块树只保留目录节点', async () => {
      mocks.fetchProjectModuleTree.mockResolvedValue([
        {
          id: 'm1',
          parentId: null,
          type: 'directory',
          name: '登录模块',
          sortOrder: 1,
          children: [
            { id: 'd1', parentId: 'm1', type: 'document', name: '用例文档', sortOrder: 1, children: [] },
          ],
        },
      ])
      const s = useRequirementList()
      await s.load()
      await s.loadFilterOptions()
      expect(s.moduleTree.value).toHaveLength(1)
      expect(s.moduleTree.value[0].children).toEqual([])
    })

    it('筛选项加载失败不阻塞列表', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('树失败'))
      mocks.fetchMembers.mockRejectedValue(new Error('成员失败'))
      const s = useRequirementList()
      await s.loadFilterOptions()
      await s.load()
      expect(s.moduleTree.value).toEqual([])
      expect(s.memberOptions.value).toEqual([])
      expect(s.hasLoaded.value).toBe(true)
    })
  })

  describe('权限', () => {
    it('按权限点暴露入口', async () => {
      const hasPermission = vi.fn(
        (code: string) => code === 'requirement:create' || code === 'requirement:view',
      )
      mocks.useAuthStore.mockReturnValue({ hasPermission })
      const s = useRequirementList()
      await s.load()
      expect(s.canCreate.value).toBe(true)
      expect(s.canConfirm.value).toBe(false)
      expect(s.canEdit.value).toBe(false)
    })
  })
})
