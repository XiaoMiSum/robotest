// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AssistComparePanel from './AssistComparePanel.vue'
import source from './AssistComparePanel.vue?raw'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type {
  AiCaseOptimizeAddOperation,
  AiCaseOptimizeOperation,
  AiCaseOptimizeUpdateOperation,
  AiProposalConfirmResult,
} from '@/types'

function titleOp(overrides: Partial<AiCaseOptimizeUpdateOperation> = {}): AiCaseOptimizeOperation {
  return {
    op: 'update', nodeId: 'n1', field: 'title',
    existing: ['输入邮箱'], suggested: ['输入邮箱和密码'],
    path: '登录用例 › 步骤 1', reason: '补充账号类型说明',
    ...overrides,
  }
}

function priorityOp(overrides: Partial<AiCaseOptimizeUpdateOperation> = {}): AiCaseOptimizeOperation {
  return {
    op: 'update', nodeId: 'n1', field: 'priority',
    existing: ['P1'], suggested: ['P0'],
    path: '登录用例', reason: '上游需求 REQ-001 优先级为高',
    ...overrides,
  }
}

function tagsOp(overrides: Partial<AiCaseOptimizeUpdateOperation> = {}): AiCaseOptimizeOperation {
  return {
    op: 'update', nodeId: 'n1', field: 'tags',
    existing: ['登录'], suggested: ['登录'],
    path: '登录用例', reason: '',
    ...overrides,
  }
}

function addOp(overrides: Partial<AiCaseOptimizeAddOperation> = {}): AiCaseOptimizeOperation {
  return {
    op: 'add', parentId: 'n1', nodeType: 'expected',
    title: '发送成功提示', path: '登录用例', reason: '补充预期',
    ...overrides,
  }
}

function optimizeRow(overrides: Partial<AssistRow> = {}): AssistRow {
  return {
    key: 'node-1',
    title: '验证码 60 秒重发限制',
    confirmStatus: 'pending',
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: {
      nodeId: 'n1',
      operations: [titleOp(), tagsOp(), priorityOp(), addOp()],
      sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '验证码 60 秒内不可重发' }],
    },
    ...overrides,
  }
}

function mountPanel(props: Record<string, unknown> = {}): VueWrapper {
  return mount(AssistComparePanel, {
    props: { rows: [], canAdopt: true, confirming: false, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function actionButton(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!button) throw new Error(`未找到按钮：${text}`)
  return button
}

function nodeTitle(wrapper: VueWrapper): string {
  const link = wrapper.find('.assist-panel__sources .el-link')
  return link.exists() ? link.text() : ''
}

function findInTree(root: ParentNode, text: string): HTMLElement | null {
  return (
    Array.from(root.querySelectorAll('button')).find((item) =>
      item.textContent?.includes(text),
    ) ?? null
  )
}

describe('AssistComparePanel', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  describe('操作卡与折叠', () => {
    it('默认折叠同值标题更新，同级 / 标签一致与新增照常展示', async () => {
      const folding = optimizeRow({
        suggestion: {
          nodeId: 'n1',
          operations: [
            titleOp({ existing: ['相同标题'], suggested: ['相同标题'] }),
            tagsOp(),
            priorityOp({ suggested: ['P1'] }),
            addOp(),
          ],
          sourceRefs: [],
        },
      })
      const wrapper = mountPanel({ rows: [folding] })

      expect(wrapper.findAll('.assist-op-card')).toHaveLength(3)
      expect(wrapper.text()).toContain('展开共有 1 项与现有内容相同')
      expect(wrapper.text()).not.toContain('· 标题')

      await actionButton(wrapper, '展开共有').trigger('click')

      expect(wrapper.findAll('.assist-op-card')).toHaveLength(4)
      expect(wrapper.text()).toContain('收起无变化项')
    })

    it('更新卡显示节点路径、字段与理由，新增卡标记节点类型', () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })

      const text = wrapper.text()
      expect(text).toContain('登录用例 › 步骤 1')
      expect(text).toContain('标题')
      expect(text).toContain('补充账号类型说明')
      expect(text).toContain('新增预期结果')
      expect(text).toContain('上游需求 REQ-001 优先级为高')
      expect(wrapper.find('.assist-op-card__level--p0').exists()).toBe(true)
    })

    it('来源引用点击透传给宿主跳需求详情', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })

      await wrapper.find('.assist-panel__sources .el-link').trigger('click')

      expect(wrapper.emitted('openSource')).toHaveLength(1)
      expect(wrapper.emitted('openSource')?.[0]?.[0]).toMatchObject({
        id: 'r1',
        title: 'REQ-001',
      })
    })

    it('全部与现状一致时提示无变化并置灰采纳', () => {
      const row = optimizeRow({
        suggestion: {
          nodeId: 'n1',
          operations: [tagsOp(), priorityOp({ suggested: ['P1'] })],
          sourceRefs: [],
        },
      })
      const wrapper = mountPanel({ rows: [row] })

      expect(wrapper.text()).toContain('建议与现有内容一致，无需变更')
      expect(wrapper.text()).toContain('同级')
      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '驳回').attributes('disabled')).toBeDefined()
    })

    it('内容缺失时给出占位并禁止采纳', () => {
      const wrapper = mountPanel({ rows: [optimizeRow({ suggestion: null })] })

      expect(wrapper.text()).toContain('建议内容暂不可用')
      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeDefined()
    })

    it('加载中渲染骨架而非空态', () => {
      const wrapper = mountPanel({ rows: [optimizeRow({ suggestion: null })], loading: true })

      expect(wrapper.find('.el-skeleton').exists()).toBe(true)
    })
  })

  describe('采纳与驳回', () => {
    it('动作按钮右对齐，顺序为驳回、采纳，均为 link 且按危险/成功着色', () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })
      const buttons = wrapper.find('.assist-panel__actions').findAll('button')

      expect(buttons.map((button) => button.text())).toEqual(['驳回', '采纳'])
      expect(buttons.every((button) => button.classes().includes('is-link'))).toBe(true)
      expect(buttons[0]?.classes()).toContain('el-button--danger')
      expect(buttons[1]?.classes()).toContain('el-button--success')
      expect(source).toMatch(/\.assist-panel__actions\s*\{[^}]*justify-content:\s*flex-end;/)
    })

    it('采纳提交全部操作，仅携带定位字段与建议值', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })

      await actionButton(wrapper, '采纳').trigger('click')

      expect(wrapper.emitted('adoptEdited')).toHaveLength(1)
      expect(wrapper.emitted('adoptEdited')?.[0]).toEqual([
        'node-1',
        {
          operations: [
            { op: 'update', nodeId: 'n1', field: 'title', suggested: '输入邮箱和密码' },
            { op: 'update', nodeId: 'n1', field: 'tags', suggested: ['登录'] },
            { op: 'update', nodeId: 'n1', field: 'priority', suggested: 'P0' },
            { op: 'add', parentId: 'n1', nodeType: 'expected', title: '发送成功提示' },
          ],
        },
      ])
    })

    it('人工编辑标题后按编辑值提交', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })
      const input = wrapper.findAll('.assist-op-card')[0].find('input')
      expect(input.exists()).toBe(true)

      await input.setValue('输入邮箱和图形验证码')

      await actionButton(wrapper, '采纳').trigger('click')
      const payload = wrapper.emitted('adoptEdited')?.[0]?.[1] as { operations: Record<string, unknown>[] }
      expect(payload.operations[0]).toMatchObject({
        op: 'update', nodeId: 'n1', field: 'title', suggested: '输入邮箱和图形验证码',
      })
    })

    it('跳过的操作不进入采纳载荷', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })
      const card = wrapper.findAll('.assist-op-card')[0]
      const skip = card.findAll('button').find((item) => item.text().includes('跳过'))
      expect(skip).toBeDefined()

      await skip!.trigger('click')
      expect(wrapper.text()).toContain('恢复')
      const hint = actionButton(wrapper, '采纳')
      expect(hint.attributes('disabled')).toBeUndefined()

      await actionButton(wrapper, '采纳').trigger('click')
      const payload = wrapper.emitted('adoptEdited')?.[0]?.[1] as { operations: Record<string, unknown>[] }
      expect(payload.operations.some((op) => op['field'] === 'title')).toBe(false)
    })

    it('多节点时采纳后推进到下一个待处理建议', async () => {
      const second = optimizeRow({
        key: 'node-2',
        title: '登录-图形验证码',
        suggestion: {
          nodeId: 'n2',
          operations: [addOp({ parentId: 'n2', title: '输入图形验证码' })],
          sourceRefs: [{ id: 'r2', title: 'REQ-002', quote: '' }],
        },
      })
      const wrapper = mountPanel({ rows: [optimizeRow(), second] })

      expect(wrapper.find('.assist-panel__switch').exists()).toBe(true)
      expect(nodeTitle(wrapper)).toBe('REQ-001')

      await actionButton(wrapper, '采纳').trigger('click')

      expect(wrapper.emitted('adoptEdited')?.[0]?.[0]).toBe('node-1')
      await nextTick()
      expect(nodeTitle(wrapper)).toBe('REQ-002')
    })

    it('驳回带说明提交，空白说明省略', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })

      await actionButton(wrapper, '驳回').trigger('click')
      await nextTick()
      const textarea =
        (wrapper.element.querySelector('textarea') ??
          document.body.querySelector('textarea')) as HTMLTextAreaElement | null
      expect(textarea).not.toBeNull()
      if (textarea) {
        textarea.value = '与人工执行顺序冲突'
        textarea.dispatchEvent(new Event('input', { bubbles: true }))
      }
      await nextTick()
      const confirm =
        findInTree(wrapper.element, '确认驳回') ?? findInTree(document.body, '确认驳回')
      expect(confirm).not.toBeNull()
      confirm?.click()
      await nextTick()

      expect(wrapper.emitted('reject')?.[0]).toEqual(['node-1', '与人工执行顺序冲突'])
    })
  })

  describe('状态分支与回执', () => {
    it('只读节点置灰并提示原因', () => {
      const wrapper = mountPanel({ rows: [optimizeRow()], canAdopt: false })

      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '驳回').attributes('disabled')).toBeDefined()
      expect(wrapper.text()).toContain('当前节点只读')
    })

    it('逐项回执渲染成败并可清空', async () => {
      const receipt: AiProposalConfirmResult[] = [
        {
          key: 'node-1',
          action: 'adopted',
          success: true,
          createdId: null,
          errorCode: null,
          errorMsg: null,
        },
        {
          key: 'node-2',
          action: 'adopted',
          success: false,
          createdId: null,
          errorCode: 1000018306,
          errorMsg: '无权限',
        },
      ]
      const wrapper = mountPanel({
        rows: [optimizeRow(), optimizeRow({ key: 'node-2', title: '登录-图形验证码' })],
        receipt,
      })

      expect(wrapper.text()).toContain('部分确认失败')
      expect(wrapper.text()).toContain('登录-图形验证码')
      expect(wrapper.text()).toContain('无权限（1000018306）')

      await actionButton(wrapper, '收起回执').trigger('click')
      expect(wrapper.emitted('clearReceipt')).toHaveLength(1)
    })

    it('回执重试回放当时的提交动作', async () => {
      const wrapper = mountPanel({
        rows: [optimizeRow()],
        receipt: [
          {
            key: 'node-1',
            action: 'adopted_edited',
            success: false,
            createdId: null,
            errorCode: 1000018117,
            errorMsg: '模型超时',
          },
        ],
      })

      await actionButton(wrapper, '采纳').trigger('click')
      await nextTick()
      await actionButton(wrapper, '重试').trigger('click')

      expect(wrapper.emitted('adoptEdited')).toHaveLength(2)
      expect(wrapper.emitted('adoptEdited')?.[1]?.[0]).toBe('node-1')
    })

    it('来源需求已变更时顶部提示但仍可采纳', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()], sourceStale: true })

      expect(wrapper.text()).toContain('来源需求已变更')
      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeUndefined()

      await actionButton(wrapper, '关闭').trigger('click')
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('任务执行完毕时提供重新执行入口并透传事件', async () => {
      const wrapper = mountPanel({ rows: [optimizeRow()], retryable: true })

      await actionButton(wrapper, '重新执行').trigger('click')

      expect(wrapper.emitted('retry')).toHaveLength(1)
    })

    it('未执行完毕时不展示重新执行入口', () => {
      const wrapper = mountPanel({ rows: [optimizeRow()] })

      expect(
        wrapper.findAll('button').some((item) => item.text().includes('重新执行')),
      ).toBe(false)
    })

    it('已采纳计数按处理状态统计', () => {
      const wrapper = mountPanel({
        rows: [
          optimizeRow({ confirmStatus: 'adopted', confirmLabel: '已采纳', confirmTagType: 'success' }),
          optimizeRow({ key: 'node-2', confirmStatus: 'rejected', confirmLabel: '已驳回' }),
          optimizeRow({ key: 'node-3' }),
        ],
      })

      expect(wrapper.text()).toContain('已采纳 1 / 共 3')
    })
  })
})
