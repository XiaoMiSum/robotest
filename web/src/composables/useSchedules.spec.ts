import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import type { ApiSchedulePageItem } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchSchedulePage: vi.fn(),
  createSchedule: vi.fn(),
  updateSchedule: vi.fn(),
  toggleSchedule: vi.fn(),
  deleteSchedule: vi.fn(),
  executeSchedule: vi.fn(),
  fetchScheduleExecutions: vi.fn(),
  validateCron: vi.fn(),
  fetchScenePage: vi.fn(),
  fetchEnvironments: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
  formatDateTime: vi.fn((v?: string | null) => v ?? ''),
  formatShortDateTime: vi.fn((v?: string | null) => v ?? ''),
}))

vi.mock('@/services/apiSchedule', () => ({
  fetchSchedulePage: mocks.fetchSchedulePage,
  createSchedule: mocks.createSchedule,
  updateSchedule: mocks.updateSchedule,
  toggleSchedule: mocks.toggleSchedule,
  deleteSchedule: mocks.deleteSchedule,
  executeSchedule: mocks.executeSchedule,
  fetchScheduleExecutions: mocks.fetchScheduleExecutions,
  validateCron: mocks.validateCron,
}))

vi.mock('@/services/apiScene', () => ({
  fetchScenePage: mocks.fetchScenePage,
}))

vi.mock('@/services/apiEnvironment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

vi.mock('@/utils/format', () => ({
  formatDateTime: mocks.formatDateTime,
  formatShortDateTime: mocks.formatShortDateTime,
}))

import { useSchedules } from './useSchedules'

function buildItem(partial: Partial<ApiSchedulePageItem> = {}): ApiSchedulePageItem {
  return {
    id: 'task-1',
    taskType: 'scene_execute',
    name: '测试任务',
    description: null,
    boundObjectId: null,
    boundObjectName: null,
    executionScope: 'all',
    moduleIds: null,
    sceneIds: null,
    openapiUrl: null,
    environmentId: null,
    environmentName: null,
    cronExpression: '0 2 * * *',
    enabled: true,
    lastExecutionStatus: null,
    lastExecutionAt: null,
    nextExecutions: [],
    createdAt: '2026-01-01T00:00:00',
    ...partial,
  }
}

function pageResult<T>(list: T[] = [], total = list.length) {
  return { list, total }
}

describe('useSchedules', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchSchedulePage.mockResolvedValue(pageResult())
    mocks.fetchEnvironments.mockResolvedValue([])
    mocks.fetchProjectModuleTree.mockResolvedValue([])
    mocks.fetchScenePage.mockResolvedValue(pageResult())
    mocks.validateCron.mockResolvedValue({ valid: true, description: '每天凌晨2点', nextExecutions: [] })
    mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function makeSut() {
    return useSchedules()
  }

  describe('初始状态', () => {
    it('列表状态默认为空', () => {
      const s = makeSut()
      expect(s.rows.value).toEqual([])
      expect(s.total.value).toBe(0)
      expect(s.pageNo.value).toBe(1)
      expect(s.loading.value).toBe(false)
      expect(s.typeFilter.value).toBe('')
    })

    it('表单默认值正确', () => {
      const s = makeSut()
      expect(s.form.taskType).toBe('scene_execute')
      expect(s.form.name).toBe('')
      expect(s.form.description).toBe('')
      expect(s.form.executionScope).toBe('all')
      expect(s.form.moduleIds).toEqual([])
      expect(s.form.sceneIds).toEqual([])
      expect(s.form.openapiUrl).toBe('')
      expect(s.form.environmentId).toBeUndefined()
      expect(s.form.cronExpression).toBe('')
      expect(s.form.enabled).toBe(true)
    })

    it('编辑状态默认为 null', () => {
      const s = makeSut()
      expect(s.showFormDialog.value).toBe(false)
      expect(s.editingId.value).toBeNull()
      expect(s.saving.value).toBe(false)
    })

    it('场景选择器默认关闭', () => {
      const s = makeSut()
      expect(s.scenePickerVisible.value).toBe(false)
      expect(s.selectedScenes.value).toEqual([])
      expect(s.sceneTagsExpanded.value).toBe(false)
    })

    it('Cron 校验默认为空', () => {
      const s = makeSut()
      expect(s.cronValidation.value).toBeNull()
      expect(s.cronValidating.value).toBe(false)
    })

    it('Cron 构建器默认值', () => {
      const s = makeSut()
      expect(s.cronBuilder.minute).toBe('0')
      expect(s.cronBuilder.hour).toBe('2')
      expect(s.cronBuilder.day).toBe('*')
      expect(s.cronBuilder.month).toBe('*')
      expect(s.cronBuilder.weekday).toBe('*')
      expect(s.showCronBuilder.value).toBe(false)
    })

    it('执行记录抽屉默认关闭', () => {
      const s = makeSut()
      expect(s.showExecutionDrawer.value).toBe(false)
      expect(s.executionTask.value).toBeNull()
      expect(s.executionRows.value).toEqual([])
      expect(s.executionTotal.value).toBe(0)
      expect(s.executionPageNo.value).toBe(1)
      expect(s.executionLoading.value).toBe(false)
    })

    it('isTestPlanTask 计算属性', () => {
      const s = makeSut()
      expect(s.isTestPlanTask.value).toBe(true)
      s.form.taskType = 'import_swagger'
      expect(s.isTestPlanTask.value).toBe(false)
    })
  })

  describe('loadPage', () => {
    it('加载成功更新 rows 和 total', async () => {
      const s = makeSut()
      const items = [buildItem({ id: '1' }), buildItem({ id: '2' })]
      mocks.fetchSchedulePage.mockResolvedValue(pageResult(items, 2))
      await s.loadPage()
      expect(s.rows.value).toEqual(items)
      expect(s.total.value).toBe(2)
      expect(s.loading.value).toBe(false)
    })

    it('加载失败显示错误消息', async () => {
      const s = makeSut()
      mocks.fetchSchedulePage.mockRejectedValue(new Error('网络错误'))
      await s.loadPage()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('网络错误')
      expect(s.loading.value).toBe(false)
    })

    it('加载失败非 Error 显示通用消息', async () => {
      const s = makeSut()
      mocks.fetchSchedulePage.mockRejectedValue('string err')
      await s.loadPage()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('定时任务列表加载失败')
    })

    it('带 taskType 筛选时传递参数', async () => {
      const s = makeSut()
      s.typeFilter.value = 'import_swagger'
      await nextTick()
      await s.loadPage()
      expect(mocks.fetchSchedulePage).toHaveBeenCalledWith({
        pageNo: 1,
        pageSize: 20,
        taskType: 'import_swagger',
      })
    })
  })

  describe('typeFilter watch', () => {
    it('切换筛选条件时重置 pageNo 并重新加载', async () => {
      const s = makeSut()
      s.pageNo.value = 3
      s.typeFilter.value = 'import_swagger'
      await nextTick()
      expect(s.pageNo.value).toBe(1)
      await vi.waitFor(() => {
        expect(mocks.fetchSchedulePage).toHaveBeenCalled()
      })
    })
  })

  describe('openCreate', () => {
    it('重置表单并打开弹窗', async () => {
      const s = makeSut()
      s.form.name = '已填值'
      s.editingId.value = 'old-id'
      s.openCreate()
      expect(s.showFormDialog.value).toBe(true)
      expect(s.editingId.value).toBeNull()
      expect(s.form.name).toBe('')
      expect(s.form.taskType).toBe('scene_execute')
      await vi.waitFor(() => {
        expect(mocks.fetchEnvironments).toHaveBeenCalled()
      })
    })
  })

  describe('openEdit', () => {
    it('回填表单字段并打开弹窗', async () => {
      const s = makeSut()
      const item = buildItem({
        id: 'edit-1',
        taskType: 'scene_execute',
        name: '编辑任务',
        description: 'desc',
        executionScope: 'modules',
        moduleIds: ['m1'],
        sceneIds: [],
        cronExpression: '0 3 * * *',
        enabled: false,
        environmentId: 'env-1',
      })
      s.openEdit(item)
      expect(s.editingId.value).toBe('edit-1')
      expect(s.form.name).toBe('编辑任务')
      expect(s.form.description).toBe('desc')
      expect(s.form.executionScope).toBe('modules')
      expect(s.form.moduleIds).toEqual(['m1'])
      expect(s.form.cronExpression).toBe('0 3 * * *')
      expect(s.form.enabled).toBe(false)
      expect(s.form.environmentId).toBe('env-1')
      expect(s.showFormDialog.value).toBe(true)
      await nextTick()
      expect(mocks.fetchProjectModuleTree).toHaveBeenCalled()
      expect(mocks.fetchEnvironments).toHaveBeenCalled()
    })

    it('import_swagger 类型回填 openapiUrl', () => {
      const s = makeSut()
      const item = buildItem({
        id: 'edit-2',
        taskType: 'import_swagger',
        openapiUrl: 'https://example.com/api',
      })
      s.openEdit(item)
      expect(s.form.taskType).toBe('import_swagger')
      expect(s.form.openapiUrl).toBe('https://example.com/api')
    })

    it('非 import_swagger 的 taskType 按 scene_execute 打开', () => {
      const s = makeSut()
      const item = buildItem({ taskType: 'scene_execute' })
      s.openEdit(item)
      expect(s.form.taskType).toBe('scene_execute')
    })

    it('scenes 范围时加载已选场景名', async () => {
      mocks.fetchScenePage.mockResolvedValue(pageResult([
        { id: 's1', name: '场景1' },
        { id: 's2', name: '场景2' },
      ], 2))
      const s = makeSut()
      const item = buildItem({ executionScope: 'scenes', sceneIds: ['s1', 's2'] })
      s.openEdit(item)
      await vi.waitFor(() => {
        expect(s.selectedScenes.value).toEqual([
          { id: 's1', name: '场景1' },
          { id: 's2', name: '场景2' },
        ])
      })
    })

    it('scenes 范围场景已删除时显示占位名', async () => {
      mocks.fetchScenePage.mockResolvedValue(pageResult([{ id: 's1', name: '场景1' }], 1))
      const s = makeSut()
      const item = buildItem({ executionScope: 'scenes', sceneIds: ['s1', 's999'] })
      s.openEdit(item)
      await vi.waitFor(() => {
        expect(s.selectedScenes.value).toEqual([
          { id: 's1', name: '场景1' },
          { id: 's999', name: '（已删除场景）' },
        ])
      })
    })
  })

  describe('form.taskType watch', () => {
    it('openEdit import_swagger 后表单包含 openapiUrl', () => {
      const s = makeSut()
      s.openEdit(buildItem({ taskType: 'import_swagger', openapiUrl: 'https://example.com/api' }))
      expect(s.form.taskType).toBe('import_swagger')
      expect(s.form.openapiUrl).toBe('https://example.com/api')
    })

    it('openEdit scene_execute 后表单无 openapiUrl', () => {
      const s = makeSut()
      s.openEdit(buildItem({ taskType: 'scene_execute', openapiUrl: null }))
      expect(s.form.taskType).toBe('scene_execute')
      expect(s.form.openapiUrl).toBe('')
    })
  })

  describe('form.executionScope watch', () => {
    it('openEdit modules 范围时加载模块树', async () => {
      const s = makeSut()
      s.openEdit(buildItem({ executionScope: 'modules', moduleIds: ['m1'] }))
      await vi.waitFor(() => {
        expect(mocks.fetchProjectModuleTree).toHaveBeenCalledWith('scene')
      })
      expect(s.form.moduleIds).toEqual(['m1'])
    })

    it('openEdit scenes 范围时加载场景名', async () => {
      mocks.fetchScenePage.mockResolvedValue({ list: [{ id: 's1', name: '场景1' }], total: 1 })
      const s = makeSut()
      s.openEdit(buildItem({ executionScope: 'scenes', sceneIds: ['s1'] }))
      await vi.waitFor(() => {
        expect(mocks.fetchScenePage).toHaveBeenCalled()
      })
    })
  })

  describe('场景选择', () => {
    it('handleSceneConfirm 更新选中场景', () => {
      const s = makeSut()
      s.handleSceneConfirm([{ id: 's1', name: '场景1' }, { id: 's2', name: '场景2' }])
      expect(s.selectedScenes.value).toEqual([
        { id: 's1', name: '场景1' },
        { id: 's2', name: '场景2' },
      ])
      expect(s.form.sceneIds).toEqual(['s1', 's2'])
    })

    it('removeScene 移除指定场景', () => {
      const s = makeSut()
      s.handleSceneConfirm([{ id: 's1', name: '场景1' }, { id: 's2', name: '场景2' }])
      s.removeScene('s1')
      expect(s.selectedScenes.value).toEqual([{ id: 's2', name: '场景2' }])
      expect(s.form.sceneIds).toEqual(['s2'])
    })

    it('removeScene 移除不存在的 id 不影响', () => {
      const s = makeSut()
      s.handleSceneConfirm([{ id: 's1', name: '场景1' }])
      s.removeScene('nonexistent')
      expect(s.selectedScenes.value).toEqual([{ id: 's1', name: '场景1' }])
    })
  })

  describe('visibleSceneTags', () => {
    it('未展开时最多显示 MAX_VISIBLE_SCENE_TAGS 个', () => {
      const s = makeSut()
      const scenes = Array.from({ length: 12 }, (_, i) => ({ id: `s${i}`, name: `场景${i}` }))
      s.handleSceneConfirm(scenes)
      expect(s.visibleSceneTags.value).toHaveLength(8)
    })

    it('展开时显示全部', () => {
      const s = makeSut()
      const scenes = Array.from({ length: 12 }, (_, i) => ({ id: `s${i}`, name: `场景${i}` }))
      s.handleSceneConfirm(scenes)
      s.sceneTagsExpanded.value = true
      expect(s.visibleSceneTags.value).toHaveLength(12)
    })

    it('少于 MAX_VISIBLE_SCENE_TAGS 时全部显示', () => {
      const s = makeSut()
      s.handleSceneConfirm([{ id: 's1', name: '场景1' }])
      expect(s.visibleSceneTags.value).toHaveLength(1)
    })
  })

  describe('Cron 校验', () => {
    it('handleValidateCron 成功时更新 cronValidation', async () => {
      const s = makeSut()
      s.form.cronExpression = '0 2 * * *'
      mocks.validateCron.mockResolvedValue({ valid: true, description: '每天凌晨2点', nextExecutions: ['2026-01-02'] })
      await s.handleValidateCron()
      expect(s.cronValidation.value).toEqual({ valid: true, description: '每天凌晨2点', nextExecutions: ['2026-01-02'] })
      expect(s.cronValidating.value).toBe(false)
    })

    it('handleValidateCron 失败时设置无效状态', async () => {
      const s = makeSut()
      s.form.cronExpression = 'invalid'
      mocks.validateCron.mockRejectedValue(new Error('格式错误'))
      await s.handleValidateCron()
      expect(s.cronValidation.value).toEqual({ valid: false, description: null, nextExecutions: null })
      expect(s.cronValidating.value).toBe(false)
    })

    it('空 cron 表达式不调用 API', async () => {
      const s = makeSut()
      s.form.cronExpression = '   '
      await s.handleValidateCron()
      expect(mocks.validateCron).not.toHaveBeenCalled()
    })

    it('handlePresetSelect 设置表达式并触发校验', async () => {
      const s = makeSut()
      s.handlePresetSelect('0 2 * * *')
      expect(s.form.cronExpression).toBe('0 2 * * *')
      await vi.waitFor(() => {
        expect(mocks.validateCron).toHaveBeenCalled()
      })
    })
  })

  describe('Cron 构建器', () => {
    it('applyCronBuilder 拼接表达式并关闭构建器', async () => {
      const s = makeSut()
      s.showCronBuilder.value = true
      s.cronBuilder.minute = '30'
      s.cronBuilder.hour = '8'
      s.cronBuilder.day = '1'
      s.cronBuilder.month = '*'
      s.cronBuilder.weekday = '1-5'
      s.applyCronBuilder()
      expect(s.form.cronExpression).toBe('30 8 1 * 1-5')
      expect(s.showCronBuilder.value).toBe(false)
      await vi.waitFor(() => {
        expect(mocks.validateCron).toHaveBeenCalled()
      })
    })
  })

  describe('handleSave', () => {
    function makeFormRefMock(resolves = true) {
      return { validate: vi.fn().mockResolvedValue(resolves ? undefined : Promise.reject(new Error('验证失败'))) } as never
    }

    it('formRef 不存在时静默返回', async () => {
      const s = makeSut()
      s.formRef.value = undefined
      await s.handleSave()
      expect(mocks.createSchedule).not.toHaveBeenCalled()
    })

    it('验证失败时不保存', async () => {
      const s = makeSut()
      s.formRef.value = makeFormRefMock(false)
      await s.handleSave().catch(() => {})
      expect(mocks.createSchedule).not.toHaveBeenCalled()
    })

    it('cron 表达式为空时提示', async () => {
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '   '
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请输入 Cron 表达式')
      expect(mocks.createSchedule).not.toHaveBeenCalled()
    })

    it('测试计划任务缺少环境 ID 时提示', async () => {
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = undefined
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('测试计划任务需选择目标环境')
    })

    it('modules 范围无选中模块时提示', async () => {
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.executionScope = 'modules'
      s.form.moduleIds = []
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择执行模块')
    })

    it('scenes 范围无选中场景时提示', async () => {
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.executionScope = 'scenes'
      s.form.sceneIds = []
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请选择执行场景')
    })

    it('import_swagger 缺少 URL 时提示', async () => {
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'import_swagger'
      s.form.openapiUrl = '   '
      await s.handleSave()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('接口同步任务需填写接口文档 URL')
    })

    it('新建成功调用 createSchedule', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-1' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.name = '新任务'
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        taskType: 'scene_execute',
        name: '新任务',
        cronExpression: '0 2 * * *',
        environmentId: 'env-1',
        enabled: true,
      }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已创建')
      expect(s.showFormDialog.value).toBe(false)
      expect(s.saving.value).toBe(false)
    })

    it('编辑成功调用 updateSchedule', async () => {
      mocks.updateSchedule.mockResolvedValue(true)
      const s = makeSut()
      s.editingId.value = 'edit-1'
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.name = '更新任务'
      await s.handleSave()
      expect(mocks.updateSchedule).toHaveBeenCalledWith('edit-1', expect.objectContaining({
        taskType: 'scene_execute',
        name: '更新任务',
      }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已更新')
    })

    it('保存失败显示错误消息', async () => {
      mocks.createSchedule.mockRejectedValue(new Error('保存失败'))
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
      expect(s.saving.value).toBe(false)
    })

    it('保存失败非 Error 显示通用消息', async () => {
      mocks.createSchedule.mockRejectedValue(42)
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      await s.handleSave()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
    })

    it('import_swagger 保存时传递 openapiUrl', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-2' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'import_swagger'
      s.form.openapiUrl = 'https://example.com/api'
      s.form.name = '同步任务'
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        taskType: 'import_swagger',
        openapiUrl: 'https://example.com/api',
        executionScope: undefined,
        moduleIds: undefined,
        sceneIds: undefined,
        environmentId: undefined,
      }))
    })

    it('modules 范围保存时传递 moduleIds', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-3' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.executionScope = 'modules'
      s.form.moduleIds = ['m1', 'm2']
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        executionScope: 'modules',
        moduleIds: ['m1', 'm2'],
      }))
    })

    it('scenes 范围保存时传递 sceneIds', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-4' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.executionScope = 'scenes'
      s.form.sceneIds = ['s1']
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        executionScope: 'scenes',
        sceneIds: ['s1'],
      }))
    })

    it('all 范围保存时 moduleIds 和 sceneIds 为 undefined', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-5' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.executionScope = 'all'
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        moduleIds: undefined,
        sceneIds: undefined,
      }))
    })

    it('description 为空时传递 undefined', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-6' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.description = ''
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        description: undefined,
      }))
    })

    it('description 有值时 trim 后传递', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-7' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      s.form.description = '  描述  '
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        description: '描述',
      }))
    })
  })

  describe('handleToggle', () => {
    it('启用任务成功', async () => {
      mocks.toggleSchedule.mockResolvedValue(true)
      const s = makeSut()
      const item = buildItem({ id: 't1', enabled: false })
      await s.handleToggle(item)
      expect(mocks.toggleSchedule).toHaveBeenCalledWith('t1', { enabled: true })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已启用')
    })

    it('停用任务成功', async () => {
      mocks.toggleSchedule.mockResolvedValue(true)
      const s = makeSut()
      const item = buildItem({ id: 't2', enabled: true })
      await s.handleToggle(item)
      expect(mocks.toggleSchedule).toHaveBeenCalledWith('t2', { enabled: false })
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已停用')
    })

    it('启停失败显示错误消息', async () => {
      mocks.toggleSchedule.mockRejectedValue(new Error('操作失败'))
      const s = makeSut()
      await s.handleToggle(buildItem())
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('操作失败')
    })

    it('启停失败非 Error 显示通用消息', async () => {
      mocks.toggleSchedule.mockRejectedValue('err')
      const s = makeSut()
      await s.handleToggle(buildItem({ enabled: true }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('停用失败')
    })
  })

  describe('handleDelete', () => {
    it('确认后删除任务', async () => {
      mocks.deleteSchedule.mockResolvedValue(true)
      const s = makeSut()
      await s.handleDelete(buildItem({ id: 'd1', name: '删除任务' }))
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.deleteSchedule).toHaveBeenCalledWith('d1')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已删除')
    })

    it('删除最后一条记录且 pageNo > 1 时翻页', async () => {
      mocks.deleteSchedule.mockResolvedValue(true)
      const s = makeSut()
      s.rows.value = []
      s.pageNo.value = 2
      await s.handleDelete(buildItem({ id: 'd2', name: '任务' }))
      expect(s.pageNo.value).toBe(1)
    })

    it('删除非最后一条时正常刷新', async () => {
      mocks.deleteSchedule.mockResolvedValue(true)
      const s = makeSut()
      s.rows.value = [buildItem({ id: 'd3' }), buildItem({ id: 'd4' })]
      await s.handleDelete(buildItem({ id: 'd3', name: '任务' }))
      expect(mocks.fetchSchedulePage).toHaveBeenCalled()
    })

    it('删除失败显示错误消息', async () => {
      mocks.deleteSchedule.mockRejectedValue(new Error('删除失败'))
      const s = makeSut()
      await s.handleDelete(buildItem({ name: '任务' }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
    })

    it('删除失败非 Error 显示通用消息', async () => {
      mocks.deleteSchedule.mockRejectedValue('err')
      const s = makeSut()
      await s.handleDelete(buildItem({ name: '任务' }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('删除失败')
    })

    it('用户取消确认时抛出错误', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const s = makeSut()
      await expect(s.handleDelete(buildItem())).rejects.toThrow()
      expect(mocks.deleteSchedule).not.toHaveBeenCalled()
    })
  })

  describe('handleExecuteNow', () => {
    it('正在执行时提示不能重复触发', async () => {
      const s = makeSut()
      await s.handleExecuteNow(buildItem({ lastExecutionStatus: 'running' }))
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('上一次执行未结束，请稍后再试')
      expect(mocks.ElMessageBox.confirm).not.toHaveBeenCalled()
    })

    it('确认后执行任务', async () => {
      mocks.executeSchedule.mockResolvedValue({ executionId: 'exec-1', status: 'running' })
      const s = makeSut()
      await s.handleExecuteNow(buildItem({ id: 'exec-task', name: '执行任务' }))
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.executeSchedule).toHaveBeenCalledWith('exec-task')
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已触发执行')
    })

    it('执行失败显示错误消息', async () => {
      mocks.executeSchedule.mockRejectedValue(new Error('执行失败'))
      const s = makeSut()
      await s.handleExecuteNow(buildItem({ name: '任务' }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行失败')
    })

    it('执行失败非 Error 显示通用消息', async () => {
      mocks.executeSchedule.mockRejectedValue('err')
      const s = makeSut()
      await s.handleExecuteNow(buildItem({ name: '任务' }))
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行失败')
    })

    it('用户取消确认时抛出错误', async () => {
      mocks.ElMessageBox.confirm.mockRejectedValue(new Error('cancel'))
      const s = makeSut()
      await expect(s.handleExecuteNow(buildItem())).rejects.toThrow()
      expect(mocks.executeSchedule).not.toHaveBeenCalled()
    })
  })

  describe('执行记录抽屉', () => {
    it('openExecutions 打开抽屉并加载记录', async () => {
      const execRows = [{ id: 'e1', triggerType: 'manual', status: 'success' }]
      mocks.fetchScheduleExecutions.mockResolvedValue(pageResult(execRows, 1))
      const s = makeSut()
      const item = buildItem({ id: 'task-1' })
      await s.openExecutions(item)
      expect(s.executionTask.value).toEqual(item)
      expect(s.showExecutionDrawer.value).toBe(true)
      expect(s.executionRows.value).toEqual(execRows)
      expect(s.executionTotal.value).toBe(1)
      expect(s.executionLoading.value).toBe(false)
    })

    it('loadExecutions 无 task 时静默返回', async () => {
      const s = makeSut()
      s.executionTask.value = null
      await s.loadExecutions()
      expect(mocks.fetchScheduleExecutions).not.toHaveBeenCalled()
    })

    it('loadExecutions 失败显示错误消息', async () => {
      mocks.fetchScheduleExecutions.mockRejectedValue(new Error('加载失败'))
      const s = makeSut()
      s.executionTask.value = buildItem()
      await s.loadExecutions()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('加载失败')
      expect(s.executionLoading.value).toBe(false)
    })

    it('loadExecutions 失败非 Error 显示通用消息', async () => {
      mocks.fetchScheduleExecutions.mockRejectedValue('err')
      const s = makeSut()
      s.executionTask.value = buildItem()
      await s.loadExecutions()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('执行记录加载失败')
    })
  })

  describe('triggerTypeLabel', () => {
    it('manual 返回手动', () => {
      const s = makeSut()
      expect(s.triggerTypeLabel('manual')).toBe('手动')
    })

    it('scheduled 返回定时', () => {
      const s = makeSut()
      expect(s.triggerTypeLabel('scheduled')).toBe('定时')
    })
  })

  describe('formatDuration', () => {
    it('null 返回 -', () => {
      const s = makeSut()
      expect(s.formatDuration(null)).toBe('-')
    })

    it('小于 1000ms 显示毫秒', () => {
      const s = makeSut()
      expect(s.formatDuration(500)).toBe('500ms')
    })

    it('大于等于 1000ms 显示秒', () => {
      const s = makeSut()
      expect(s.formatDuration(1500)).toBe('1.5s')
      expect(s.formatDuration(3000)).toBe('3.0s')
      expect(s.formatDuration(1000)).toBe('1.0s')
    })
  })

  describe('常量导出', () => {
    it('导出所有常量', () => {
      const s = makeSut()
      expect(s.SCHEDULE_TASK_TYPES).toBeDefined()
      expect(s.EXECUTION_SCOPES).toBeDefined()
      expect(s.CRON_PRESETS).toBeDefined()
      expect(s.taskExecutionSummary).toBeTypeOf('function')
      expect(s.execStatusLabel).toBeTypeOf('function')
      expect(s.execStatusType).toBeTypeOf('function')
      expect(s.formatDateTime).toBeTypeOf('function')
      expect(s.formatShortDateTime).toBeTypeOf('function')
      expect(s.MAX_VISIBLE_SCENE_TAGS).toBe(8)
    })
  })
})
