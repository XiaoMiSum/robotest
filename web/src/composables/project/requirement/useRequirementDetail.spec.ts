import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { RequirementChangeLog, RequirementDetail } from '@/types'
const mocks = vi.hoisted(() => ({
  route: { params: { requirementId: 'r1' } },
  router: { push: vi.fn() },
  getRequirement: vi.fn(),
  updateRequirement: vi.fn(),
  confirmRequirement: vi.fn(),
  archiveRequirement: vi.fn(),
  unarchiveRequirement: vi.fn(),
  splitRequirement: vi.fn(),
  fetchRequirementChangeLogs: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  fetchAiStatus: vi.fn(),
  fetchMembers: vi.fn(),
  useAuthStore: vi.fn(),
  takeCachedDetail: vi.fn((): RequirementDetail | null => null),
  cacheDetail: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => mocks.router,
}))

vi.mock('@/services/project', () => ({
  getRequirement: mocks.getRequirement,
  updateRequirement: mocks.updateRequirement,
  confirmRequirement: mocks.confirmRequirement,
  archiveRequirement: mocks.archiveRequirement,
  unarchiveRequirement: mocks.unarchiveRequirement,
  splitRequirement: mocks.splitRequirement,
  fetchRequirementChangeLogs: mocks.fetchRequirementChangeLogs,
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('@/services/ai', () => ({
  fetchAiStatus: mocks.fetchAiStatus,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: mocks.useAuthStore,
}))

vi.mock('@/stores/requirement', () => ({
  useRequirementStore: () => ({
    takeCachedDetail: mocks.takeCachedDetail,
    cacheDetail: mocks.cacheDetail,
  }),
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useRequirementDetail } from './useRequirementDetail'

function makeDetail(overrides: Partial<RequirementDetail> = {}): RequirementDetail {
  return {
    id: 'r1',
    code: 'REQ-001',
    title: '登录验证码',
    description: '支持短信与邮箱两种验证码',
    moduleId: 'm1',
    moduleName: '登录模块',
    systemVersion: 'V2.3',
    status: 'confirmed',
    priority: 'high',
    ownerId: 'u1',
    ownerName: '张三',
    tags: ['登录'],
    source: 'manual',
    sourceFile: null,
    confirmedAt: '2026-10-02T08:00:00Z',
    coverageStatus: null,
    createdAt: '2026-10-01T08:00:00Z',
    updatedAt: '2026-10-02T08:00:00Z',
    ...overrides,
  }
}

function makeLog(overrides: Partial<RequirementChangeLog> = {}): RequirementChangeLog {
  return {
    id: 'log1',
    changeType: 'title',
    operatorId: 'u1',
    operatorName: '张三',
    beforeSummary: { title: '登录' },
    afterSummary: { title: '登录验证码' },
    createdAt: '2026-10-02T08:30:00Z',
    ...overrides,
  }
}

function setup(): void {
  setActivePinia(createPinia())
  mocks.route.params = { requirementId: 'r1' }
  mocks.getRequirement.mockResolvedValue(makeDetail())
  mocks.fetchRequirementChangeLogs.mockResolvedValue({ list: [makeLog()], total: 1 })
  mocks.fetchProjectModuleTree.mockResolvedValue([])
  mocks.fetchMembers.mockResolvedValue({
    list: [{ userId: 'u1', username: 'zhangsan', name: '张三' }],
    total: 1,
  })
  mocks.fetchAiStatus.mockResolvedValue({ enabled: true, modelReady: true, available: true })
  mocks.updateRequirement.mockResolvedValue(makeDetail({ title: '新标题' }))
  mocks.confirmRequirement.mockResolvedValue(makeDetail({ status: 'confirmed' }))
  mocks.archiveRequirement.mockResolvedValue(makeDetail({ status: 'archived' }))
  mocks.unarchiveRequirement.mockResolvedValue(makeDetail({ status: 'draft' }))
  mocks.splitRequirement.mockResolvedValue({ taskId: 't1', splitRecordId: 's1', status: 'pending' })
  mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
  mocks.useAuthStore.mockReturnValue({ hasPermission: vi.fn(() => true) })
}

async function mount(): Promise<ReturnType<typeof useRequirementDetail>> {
  const s = useRequirementDetail()
  // onMounted 在非组件上下文不触发，按加载顺序手动驱动
  await s.load()
  await s.loadAiStatus()
  return s
}

describe('useRequirementDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    setup()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('加载', () => {
    it('成功加载详情与变更记录并写入缓存', async () => {
      const s = await mount()
      expect(s.detail.value?.title).toBe('登录验证码')
      expect(s.logs.value).toHaveLength(1)
      expect(s.logsTotal.value).toBe(1)
      expect(mocks.cacheDetail).toHaveBeenCalledWith(expect.objectContaining({ id: 'r1' }))
      expect(s.loadError.value).toBe('')
      expect(s.loading.value).toBe(false)
    })

    it('业务错误码 1000018001 切换 404 分支', async () => {
      const err = Object.assign(new Error('需求不存在或不属于当前项目'), { code: 1000018001 })
      mocks.getRequirement.mockRejectedValue(err)
      const s = useRequirementDetail()
      await s.load()
      expect(s.notFound.value).toBe(true)
      expect(s.loadError.value).toBe('')
    })

    it('其它错误记录页面级错误且可重试', async () => {
      mocks.getRequirement.mockRejectedValueOnce(new Error('服务不可用'))
      const s = useRequirementDetail()
      await s.load()
      expect(s.notFound.value).toBe(false)
      expect(s.loadError.value).toBe('服务不可用')
      s.retry()
      await s.load()
      expect(s.loadError.value).toBe('')
    })

    it('命中详情缓存时先展示旧值再刷新', async () => {
      mocks.takeCachedDetail.mockReturnValue(makeDetail({ title: '缓存标题' }))
      const s = useRequirementDetail()
      await s.load()
      expect(mocks.takeCachedDetail).toHaveBeenCalledWith('r1')
      expect(s.detail.value?.title).toBe('登录验证码')
    })
  })

  describe('标题内联编辑', () => {
    it('标题未变化不发请求', async () => {
      const s = await mount()
      s.startEditTitle()
      await s.saveTitle()
      expect(mocks.updateRequirement).not.toHaveBeenCalled()
      expect(s.titleEditing.value).toBe(false)
    })

    it('confirmed 态改标题需影响确认，放弃时不提交', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValueOnce('cancel')
      const s = await mount()
      s.startEditTitle()
      s.titleDraft.value = '新标题'
      await s.saveTitle()
      expect(mocks.updateRequirement).not.toHaveBeenCalled()
      // 放弃后恢复服务端当前值
      expect(s.titleDraft.value).toBe('登录验证码')
      expect(s.titleEditing.value).toBe(true)
    })

    it('confirmed 态确认后提交并更新详情', async () => {
      const s = await mount()
      s.startEditTitle()
      s.titleDraft.value = '新标题'
      await s.saveTitle()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', { title: '新标题' })
      expect(s.titleEditing.value).toBe(false)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('需求已更新')
    })

    it('draft 态改标题不弹影响确认', async () => {
      mocks.getRequirement.mockResolvedValue(makeDetail({ status: 'draft' }))
      const s = await mount()
      s.startEditTitle()
      s.titleDraft.value = '新标题'
      await s.saveTitle()
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', { title: '新标题' })
    })
  })

  describe('属性部分更新', () => {
    it('无变化时提示且不发请求', async () => {
      const s = await mount()
      await s.saveAttributes()
      expect(mocks.updateRequirement).not.toHaveBeenCalled()
      expect(mocks.ElMessage.info).toHaveBeenCalledWith('属性未发生变化')
    })

    it('仅传发生变化的字段', async () => {
      const s = await mount()
      s.form.systemVersion = 'V3.0'
      s.form.priority = 'low'
      s.form.tags = ['登录', '安全']
      await s.saveAttributes()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', {
        systemVersion: 'V3.0',
        priority: 'low',
        tags: ['登录', '安全'],
      })
    })

    it('清空版本与优先级传空串（三态）', async () => {
      const s = await mount()
      s.form.systemVersion = '  '
      s.form.priority = ''
      await s.saveAttributes()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', {
        systemVersion: '',
        priority: '',
      })
    })

    it('清空标签传空数组', async () => {
      const s = await mount()
      s.form.tags = []
      await s.saveAttributes()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', { tags: [] })
    })

    it('模块与负责人仅在改为非空新值时入载荷', async () => {
      const s = await mount()
      s.form.moduleId = ''
      s.form.ownerId = ''
      await s.saveAttributes()
      // 清空（空值）后端视为不修改，本地保持原值并提示无变化
      expect(mocks.updateRequirement).not.toHaveBeenCalled()
      expect(mocks.ElMessage.info).toHaveBeenCalled()
      expect(s.form.moduleId).toBe('m1')
    })

    it('版本超 50 字符前端拦截', async () => {
      const s = await mount()
      s.form.systemVersion = 'x'.repeat(51)
      await s.saveAttributes()
      expect(s.versionError.value).toContain('1000018010')
      expect(mocks.updateRequirement).not.toHaveBeenCalled()
    })

    it('confirmed 态改模块触发影响确认', async () => {
      const s = await mount()
      s.form.moduleId = 'm2'
      await s.saveAttributes()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', { moduleId: 'm2' })
    })

    it('保存失败展示服务端文案', async () => {
      mocks.updateRequirement.mockRejectedValueOnce(new Error('归档条目只读'))
      const s = await mount()
      s.form.systemVersion = 'V9'
      await s.saveAttributes()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('归档条目只读')
      expect(s.saving.value).toBe(false)
    })
  })

  describe('描述编辑', () => {
    it('描述未变化不发请求', async () => {
      const s = await mount()
      s.startEditDescription()
      await s.saveDescription()
      expect(mocks.updateRequirement).not.toHaveBeenCalled()
      expect(s.descriptionEditing.value).toBe(false)
    })

    it('confirmed 态改描述提交并进入编辑关闭态', async () => {
      const s = await mount()
      s.startEditDescription()
      s.descriptionDraft.value = '新的描述正文'
      await s.saveDescription()
      expect(mocks.updateRequirement).toHaveBeenCalledWith('r1', { description: '新的描述正文' })
      expect(s.descriptionEditing.value).toBe(false)
    })
  })

  describe('状态操作', () => {
    it('确认成功流转 confirmed', async () => {
      const s = await mount()
      s.form.moduleId = ''
      await s.handleConfirm()
      expect(mocks.confirmRequirement).toHaveBeenCalledWith('r1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('需求已确认')
    })

    it('归档需二次确认，取消时不发请求', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValueOnce('cancel')
      const s = await mount()
      await s.handleArchive()
      expect(mocks.archiveRequirement).not.toHaveBeenCalled()
    })

    it('归档确认后调用服务端', async () => {
      const s = await mount()
      await s.handleArchive()
      expect(mocks.archiveRequirement).toHaveBeenCalledWith('r1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('需求已归档')
      expect(s.isReadonly.value).toBe(true)
    })

    it('取消归档回 draft 并提示', async () => {
      mocks.getRequirement.mockResolvedValue(makeDetail({ status: 'archived' }))
      const s = await mount()
      expect(s.canUnarchive.value).toBe(true)
      await s.handleUnarchive()
      expect(mocks.unarchiveRequirement).toHaveBeenCalledWith('r1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已取消归档，条目回到草稿状态')
      expect(s.detail.value?.status).toBe('draft')
    })

    it('操作失败展示服务端文案', async () => {
      mocks.confirmRequirement.mockRejectedValueOnce(new Error('当前状态不允许该操作'))
      const s = await mount()
      await s.handleConfirm()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('当前状态不允许该操作')
    })
  })

  describe('AI 拆分', () => {
    it('AI 可用且持权限时入口可用', async () => {
      const s = await mount()
      expect(s.aiAvailable.value).toBe(true)
      expect(s.canSplit.value).toBe(true)
    })

    it('AI 不可用时入口隐藏', async () => {
      mocks.fetchAiStatus.mockResolvedValue({ enabled: false, modelReady: false, available: false })
      const s = useRequirementDetail()
      await s.load()
      await s.loadAiStatus()
      expect(s.canSplit.value).toBe(false)
    })

    it('无 requirement:edit 权限时入口不可用', async () => {
      mocks.useAuthStore.mockReturnValue({
        hasPermission: vi.fn(() => false),
      })
      const s = await mount()
      expect(s.canSplit.value).toBe(false)
    })

    it('archived 条目不可拆分', async () => {
      mocks.getRequirement.mockResolvedValue(makeDetail({ status: 'archived' }))
      const s = await mount()
      expect(s.canSplit.value).toBe(false)
    })

    it('提交成功跳转任务详情页', async () => {
      const s = await mount()
      await s.handleSplit()
      expect(mocks.splitRequirement).toHaveBeenCalledWith('r1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('拆分任务已提交')
      expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks/t1')
    })

    it('取消确认不提交', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValueOnce('cancel')
      const s = await mount()
      await s.handleSplit()
      expect(mocks.splitRequirement).not.toHaveBeenCalled()
    })

    it('提交失败展示服务端文案', async () => {
      mocks.splitRequirement.mockRejectedValueOnce(new Error('已存在进行中的导入或拆分任务'))
      const s = await mount()
      await s.handleSplit()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('已存在进行中的导入或拆分任务')
      expect(s.splitting.value).toBe(false)
    })
  })

  describe('派生状态', () => {
    it('archived 只读且隐藏确认/归档', async () => {
      mocks.getRequirement.mockResolvedValue(makeDetail({ status: 'archived' }))
      const s = await mount()
      expect(s.isReadonly.value).toBe(true)
      expect(s.canConfirm.value).toBe(false)
      expect(s.canArchive.value).toBe(false)
      expect(s.canUnarchive.value).toBe(true)
    })

    it('draft 可确认可归档', async () => {
      mocks.getRequirement.mockResolvedValue(makeDetail({ status: 'draft' }))
      const s = await mount()
      expect(s.canConfirm.value).toBe(true)
      expect(s.canArchive.value).toBe(true)
    })

    it('无 requirement:confirm 权限时隐藏确认与归档', async () => {
      mocks.useAuthStore.mockReturnValue({
        hasPermission: vi.fn((code: string) => code === 'requirement:edit'),
      })
      const s = await mount()
      expect(s.canConfirm.value).toBe(false)
      expect(s.canArchive.value).toBe(false)
      expect(s.canEdit.value).toBe(true)
    })

    it('变更记录多于首批时提供加载更多', async () => {
      mocks.fetchRequirementChangeLogs.mockResolvedValue({ list: [makeLog()], total: 40 })
      const s = await mount()
      expect(s.canLoadMoreLogs.value).toBe(true)
      s.logs.value = [makeLog(), makeLog({ id: 'log2' })]
      // total 未变时仍可加载；只有取满 total 才停止
      expect(s.canLoadMoreLogs.value).toBe(true)
      s.logs.value = Array.from({ length: 40 }, (_, i) => makeLog({ id: `log${i}` }))
      expect(s.canLoadMoreLogs.value).toBe(false)
    })

    it('归档只读时禁用标题编辑', async () => {
      mocks.getRequirement.mockResolvedValue(makeDetail({ status: 'archived' }))
      const s = await mount()
      s.startEditTitle()
      expect(s.titleEditing.value).toBe(false)
    })
  })

  describe('选项加载', () => {
    it('模块树只保留目录，失败不阻塞详情', async () => {
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('树失败'))
      const s = await mount()
      await s.loadOptions()
      expect(s.moduleTree.value).toEqual([])
      expect(s.detail.value).not.toBeNull()
    })

    it('模块树过滤文档节点', async () => {
      mocks.fetchProjectModuleTree.mockResolvedValue([
        {
          id: 'm1',
          parentId: null,
          type: 'directory',
          name: '登录模块',
          sortOrder: 1,
          children: [
            { id: 'd1', parentId: 'm1', type: 'document', name: '文档', sortOrder: 1, children: [] },
          ],
        },
      ])
      const s = await mount()
      await s.loadOptions()
      expect(s.moduleTree.value[0].children).toEqual([])
    })
  })
})
