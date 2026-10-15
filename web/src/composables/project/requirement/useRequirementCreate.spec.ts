import { describe, it, expect, vi, beforeEach } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchProjectModuleTree: vi.fn(),
  createRequirement: vi.fn(),
  fetchMembers: vi.fn(),
  useRouter: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
  createRequirement: mocks.createRequirement,
}))

vi.mock('@/services/workspace', () => ({
  fetchMembers: mocks.fetchMembers,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
}))

vi.mock('vue-router', () => ({
  useRouter: mocks.useRouter,
}))

import {
  REQUIREMENT_PRIORITY_OPTIONS,
  REQUIREMENT_PRIORITY_COLOR,
  useRequirementCreate,
} from './useRequirementCreate'
import type { FormItemRule } from 'element-plus'

function setupDefaultMocks() {
  const push = vi.fn()
  mocks.useRouter.mockReturnValue({ push })
  mocks.fetchProjectModuleTree.mockResolvedValue([])
  mocks.fetchMembers.mockResolvedValue({ list: [] })
  mocks.createRequirement.mockResolvedValue({ id: 'new-req-id' })
  return { push }
}

/** 直接执行 title 规则的 validator，收集 callback 结果 */
function runTitleValidator(value: unknown): { ok: boolean; message?: string } {
  const { rules } = useRequirementCreate()
  const rule = (rules.title as FormItemRule[])[0]
  let result: { ok: boolean; message?: string } = { ok: true }
  // validator 的 source / options 参数仅 async 校验链路使用，同步 callback 断言无需真实值
  rule.validator?.(rule, value, (error?: string | Error) => {
    result = error ? { ok: false, message: error instanceof Error ? error.message : error } : { ok: true }
  }, {} as never, {} as never)
  return result
}

describe('useRequirementCreate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupDefaultMocks()
  })

  describe('初始值', () => {
    it('form 字段全部为空初值', () => {
      const { form } = useRequirementCreate()
      expect(form.title).toBe('')
      expect(form.description).toBe('')
      expect(form.moduleId).toBe('')
      expect(form.systemVersion).toBe('')
      expect(form.priority).toBe('')
      expect(form.ownerId).toBe('')
      expect(form.tags).toEqual([])
    })

    it('submitting 初始为 false，moduleTree / memberOptions 初始为空', () => {
      const { submitting, moduleTree, memberOptions } = useRequirementCreate()
      expect(submitting.value).toBe(false)
      expect(moduleTree.value).toEqual([])
      expect(memberOptions.value).toEqual([])
    })

    it('优先级选项与色值映射齐全', () => {
      expect(REQUIREMENT_PRIORITY_OPTIONS.map((o) => o.value)).toEqual(['high', 'medium', 'low'])
      expect(REQUIREMENT_PRIORITY_COLOR.high).toBe('var(--color-priority-p1)')
      expect(REQUIREMENT_PRIORITY_COLOR.medium).toBe('var(--color-priority-p2)')
      expect(REQUIREMENT_PRIORITY_COLOR.low).toBe('var(--color-priority-p3)')
    })
  })

  describe('title 校验（trim 后必填）', () => {
    it('纯空白视同未填', () => {
      expect(runTitleValidator('   ')).toEqual({ ok: false, message: '请输入需求标题' })
    })

    it('空串与 undefined 视同未填', () => {
      expect(runTitleValidator('').ok).toBe(false)
      expect(runTitleValidator(undefined).ok).toBe(false)
    })

    it('前后带空白的非空标题通过', () => {
      expect(runTitleValidator('  需求标题  ').ok).toBe(true)
    })

    it('300 字符上限由输入框 maxlength 硬限制，规则不设 max', () => {
      const { rules } = useRequirementCreate()
      const titleRule = (rules.title as FormItemRule[])[0]
      expect(titleRule.max).toBeUndefined()
      expect(runTitleValidator('x'.repeat(300)).ok).toBe(true)
    })
  })

  describe('handlePriorityClick（再次点击清空回未设置）', () => {
    it('点击已选中项时清空并阻止原生选中', () => {
      const { form, handlePriorityClick } = useRequirementCreate()
      form.priority = 'high'
      const event = { preventDefault: vi.fn() } as unknown as Event
      handlePriorityClick('high', event)
      expect(event.preventDefault).toHaveBeenCalledOnce()
      expect(form.priority).toBe('')
    })

    it('点击未选中项时不干预原生选中流程', () => {
      const { form, handlePriorityClick } = useRequirementCreate()
      form.priority = 'high'
      const event = { preventDefault: vi.fn() } as unknown as Event
      handlePriorityClick('medium', event)
      expect(event.preventDefault).not.toHaveBeenCalled()
      expect(form.priority).toBe('high')
    })
  })

  describe('handleSubmit', () => {
    it('校验失败时不发请求', async () => {
      const { formRef, form, handleSubmit } = useRequirementCreate()
      form.title = '   '
      formRef.value = { validate: vi.fn().mockRejectedValue(undefined) } as never
      await handleSubmit()
      expect(mocks.createRequirement).not.toHaveBeenCalled()
    })

    it('标题 trim 后提交，空选项落为 undefined', async () => {
      const { formRef, form, handleSubmit } = useRequirementCreate()
      form.title = '  新窗口登录  '
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      await handleSubmit()
      expect(mocks.createRequirement).toHaveBeenCalledWith(
        expect.objectContaining({
          title: '新窗口登录',
          moduleId: undefined,
          systemVersion: undefined,
          priority: undefined,
          ownerId: undefined,
          tags: [],
        }),
      )
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('需求已创建')
    })

    it('提交失败时提示错误并停留本页', async () => {
      const { formRef, form, handleSubmit } = useRequirementCreate()
      form.title = '标题'
      formRef.value = { validate: vi.fn().mockResolvedValue(true) } as never
      const { push } = setupDefaultMocks()
      mocks.createRequirement.mockRejectedValue(new Error('创建失败'))
      await handleSubmit()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('创建失败')
      expect(push).not.toHaveBeenCalled()
    })
  })
})
