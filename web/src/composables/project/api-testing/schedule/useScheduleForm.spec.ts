import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const mocks = vi.hoisted(() => ({
  createSchedule: vi.fn(),
  updateSchedule: vi.fn(),
  validateCron: vi.fn(),
  fetchScenePage: vi.fn(),
  fetchEnvironments: vi.fn(),
  fetchProjectModuleTree: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/services/project/schedule', () => ({
  createSchedule: mocks.createSchedule,
  updateSchedule: mocks.updateSchedule,
  validateCron: mocks.validateCron,
}))

vi.mock('@/services/project/scene', () => ({
  fetchScenePage: mocks.fetchScenePage,
}))

vi.mock('@/services/project/environment', () => ({
  fetchEnvironments: mocks.fetchEnvironments,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

import { useScheduleForm } from './useScheduleForm'

function pageResult<T>(list: T[] = [], total = list.length) {
  return { list, total }
}

describe('useScheduleForm', () => {
  const loadPage = vi.fn().mockResolvedValue(undefined)

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchEnvironments.mockResolvedValue([])
    mocks.fetchProjectModuleTree.mockResolvedValue([])
    mocks.fetchScenePage.mockResolvedValue(pageResult())
    mocks.validateCron.mockResolvedValue({ valid: true, description: '每天凌晨2点', nextExecutions: [] })
    loadPage.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function makeSut() {
    return useScheduleForm(loadPage)
  }

  describe('初始状态', () => {
    it('form 默认值正确', () => {
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

    it('对话框和编辑状态默认为关闭', () => {
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

    it('模块选项和加载状态默认为空', () => {
      const s = makeSut()
      expect(s.moduleOptions.value).toEqual([])
      expect(s.scopeOptionLoading.value).toBe(false)
    })

    it('环境选项和加载状态默认为空', () => {
      const s = makeSut()
      expect(s.environmentOptions.value).toEqual([])
      expect(s.environmentLoading.value).toBe(false)
    })

    it('isTestPlanTask 计算属性', () => {
      const s = makeSut()
      expect(s.isTestPlanTask.value).toBe(true)
      s.form.taskType = 'import_swagger'
      expect(s.isTestPlanTask.value).toBe(false)
    })

    it('导出 MAX_VISIBLE_SCENE_TAGS 常量', () => {
      const s = makeSut()
      expect(s.MAX_VISIBLE_SCENE_TAGS).toBe(8)
    })
  })

  describe('resetForm', () => {
    it('重置所有表单字段为默认值', () => {
      const s = makeSut()
      s.form.name = '已填值'
      s.form.cronExpression = '0 2 * * *'
      s.form.executionScope = 'modules'
      s.form.moduleIds = ['m1']
      s.form.sceneIds = ['s1']
      s.form.openapiUrl = 'https://example.com'
      s.form.environmentId = 'env-1'
      s.form.enabled = false
      s.editingId.value = 'old-id'
      s.selectedScenes.value = [{ id: 's1', name: '场景1' }]
      s.moduleOptions.value = [{ id: 'm1', name: '模块1' } as never]

      s.openCreate()

      expect(s.form.name).toBe('')
      expect(s.form.cronExpression).toBe('')
      expect(s.form.executionScope).toBe('all')
      expect(s.form.moduleIds).toEqual([])
      expect(s.form.sceneIds).toEqual([])
      expect(s.form.openapiUrl).toBe('')
      expect(s.form.environmentId).toBeUndefined()
      expect(s.form.enabled).toBe(true)
      expect(s.editingId.value).toBeNull()
      expect(s.selectedScenes.value).toEqual([])
      expect(s.moduleOptions.value).toEqual([])
    })
  })

  describe('openCreate', () => {
    it('打开创建对话框并加载环境', async () => {
      const s = makeSut()
      s.openCreate()
      expect(s.showFormDialog.value).toBe(true)
      expect(s.editingId.value).toBeNull()
      await vi.waitFor(() => {
        expect(mocks.fetchEnvironments).toHaveBeenCalled()
      })
    })

    it('打开后切换 scope 为 modules 时加载模块树', async () => {
      const s = makeSut()
      s.openCreate()
      s.form.executionScope = 'modules'
      await vi.waitFor(() => {
        expect(mocks.fetchProjectModuleTree).toHaveBeenCalledWith('scene')
      })
    })
  })

  describe('openEdit', () => {
    function buildItem(partial: Record<string, unknown> = {}) {
      return {
        id: 'task-1',
        taskType: 'scene_execute',
        name: '测试任务',
        description: null,
        executionScope: 'all',
        moduleIds: null,
        sceneIds: null,
        openapiUrl: null,
        environmentId: null,
        cronExpression: '0 2 * * *',
        enabled: true,
        ...partial,
      } as never
    }

    it('回填表单字段并打开弹窗', async () => {
      const s = makeSut()
      const item = buildItem({
        id: 'edit-1',
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

    it('description 为 null 时回填空字符串', () => {
      const s = makeSut()
      s.openEdit(buildItem({ description: null }))
      expect(s.form.description).toBe('')
    })

    it('nullable 字段为 null 时回填默认值', () => {
      const s = makeSut()
      s.openEdit(buildItem({
        moduleIds: null,
        sceneIds: null,
        openapiUrl: null,
        environmentId: null,
        executionScope: null,
      }))
      expect(s.form.moduleIds).toEqual([])
      expect(s.form.sceneIds).toEqual([])
      expect(s.form.openapiUrl).toBe('')
      expect(s.form.environmentId).toBeUndefined()
      expect(s.form.executionScope).toBe('all')
    })
  })

  describe('loadScopeOptions', () => {
    it('modules 范围加载模块树', async () => {
      const s = makeSut()
      mocks.fetchProjectModuleTree.mockResolvedValue([{ id: 'm1', name: '模块1' }])
      s.openCreate()
      s.form.executionScope = 'modules'
      await vi.waitFor(() => {
        expect(mocks.fetchProjectModuleTree).toHaveBeenCalledWith('scene')
      })
    })

    it('加载失败显示错误消息', async () => {
      const s = makeSut()
      mocks.fetchProjectModuleTree.mockRejectedValue(new Error('网络错误'))
      s.openCreate()
      s.form.executionScope = 'modules'
      await vi.waitFor(() => {
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('模块加载失败：网络错误')
      })
    })

    it('加载失败非 Error 显示通用消息', async () => {
      const s = makeSut()
      mocks.fetchProjectModuleTree.mockRejectedValue('string err')
      s.openCreate()
      s.form.executionScope = 'modules'
      await vi.waitFor(() => {
        expect(mocks.ElMessage.error).toHaveBeenCalledWith('模块加载失败')
      })
    })
  })

  describe('loadEnvironments', () => {
    it('加载成功更新环境列表', async () => {
      const s = makeSut()
      mocks.fetchEnvironments.mockResolvedValue([{ id: 'env-1', name: '测试环境' }])
      s.openCreate()
      await vi.waitFor(() => {
        expect(mocks.fetchEnvironments).toHaveBeenCalled()
      })
    })

    it('加载失败时环境列表为空', async () => {
      const s = makeSut()
      mocks.fetchEnvironments.mockRejectedValue(new Error('加载失败'))
      s.openCreate()
      await vi.waitFor(() => {
        expect(s.environmentOptions.value).toEqual([])
        expect(s.environmentLoading.value).toBe(false)
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

  describe('hydrateSelectedSceneNames', () => {
    it('sceneIds 为空时清空 selectedScenes', async () => {
      const s = makeSut()
      s.form.sceneIds = []
      s.selectedScenes.value = [{ id: 's1', name: '旧场景' }]
      await s.openEdit({
        id: 'task-1',
        taskType: 'scene_execute',
        name: '任务',
        cronExpression: '0 2 * * *',
        enabled: true,
        executionScope: 'scenes',
        sceneIds: [],
      } as never)
      expect(s.selectedScenes.value).toEqual([])
    })

    it('分页加载场景名', async () => {
      mocks.fetchScenePage.mockResolvedValue({ list: [{ id: 's1', name: '场景1' }, { id: 's2', name: '场景2' }], total: 2 })
      const s = makeSut()
      s.form.sceneIds = ['s1', 's2']
      await s.openEdit({
        id: 'task-1',
        taskType: 'scene_execute',
        name: '任务',
        cronExpression: '0 2 * * *',
        enabled: true,
        executionScope: 'scenes',
        sceneIds: ['s1', 's2'],
      } as never)
      await vi.waitFor(() => {
        expect(s.selectedScenes.value).toEqual([
          { id: 's1', name: '场景1' },
          { id: 's2', name: '场景2' },
        ])
      })
    })
  })

  describe('form.taskType watch', () => {
    it('切换到 import_swagger 时重置相关字段', async () => {
      const s = makeSut()
      s.form.executionScope = 'modules'
      s.form.moduleIds = ['m1']
      s.form.sceneIds = ['s1']
      s.form.environmentId = 'env-1'
      s.form.taskType = 'import_swagger'
      await nextTick()
      expect(s.form.executionScope).toBe('all')
      expect(s.form.moduleIds).toEqual([])
      expect(s.form.sceneIds).toEqual([])
      expect(s.form.environmentId).toBeUndefined()
    })

    it('切换到 scene_execute 时清空 openapiUrl', async () => {
      const s = makeSut()
      s.form.taskType = 'import_swagger'
      s.form.openapiUrl = 'https://example.com'
      await nextTick()
      s.form.taskType = 'scene_execute'
      await nextTick()
      expect(s.form.openapiUrl).toBe('')
    })
  })

  describe('form.executionScope watch', () => {
    it('切换到 modules 时加载模块树', async () => {
      const s = makeSut()
      s.form.executionScope = 'modules'
      await vi.waitFor(() => {
        expect(mocks.fetchProjectModuleTree).toHaveBeenCalledWith('scene')
      })
    })

    it('切换到 scenes 时加载场景名', async () => {
      const s = makeSut()
      s.form.sceneIds = ['s1']
      s.form.executionScope = 'scenes'
      await vi.waitFor(() => {
        expect(mocks.fetchScenePage).toHaveBeenCalled()
      })
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
      expect(loadPage).toHaveBeenCalled()
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
      expect(loadPage).toHaveBeenCalled()
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

    it('openapiUrl 为空白时被验证拦截', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-8' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'import_swagger'
      s.form.openapiUrl = '   '
      s.form.name = '同步任务'
      await s.handleSave()
      expect(mocks.createSchedule).not.toHaveBeenCalled()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('接口同步任务需填写接口文档 URL')
    })

    it('openapiUrl 有值时 trim 后传递', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-9' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '0 2 * * *'
      s.form.taskType = 'import_swagger'
      s.form.openapiUrl = '  https://example.com/api  '
      s.form.name = '同步任务'
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        openapiUrl: 'https://example.com/api',
      }))
    })

    it('cronExpression trim 后传递', async () => {
      mocks.createSchedule.mockResolvedValue({ id: 'new-10' })
      const s = makeSut()
      s.formRef.value = makeFormRefMock()
      s.form.cronExpression = '  0 2 * * *  '
      s.form.taskType = 'scene_execute'
      s.form.environmentId = 'env-1'
      await s.handleSave()
      expect(mocks.createSchedule).toHaveBeenCalledWith(expect.objectContaining({
        cronExpression: '0 2 * * *',
      }))
    })
  })
})
