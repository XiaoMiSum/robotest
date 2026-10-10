// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AssistComparePanel from './AssistComparePanel.vue'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiArtifactConfirmResult } from '@/types'

type PanelKind = 'case_complete' | 'case_priority'

function completeRow(overrides: Partial<AssistRow> = {}): AssistRow {
  return {
    key: 'node-1',
    title: '验证码 60 秒重发限制',
    confirmStatus: 'pending',
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: {
      nodeId: 'n1',
      fields: {
        precondition: { existing: ['手机号已输入'], suggested: ['手机号已输入'] },
        steps: {
          existing: ['点击获取验证码'],
          suggested: ['点击获取验证码', '60 秒内再次点击'],
        },
        expected: { existing: [], suggested: ['发送成功提示'] },
        tags: { existing: ['登录'], suggested: ['登录'] },
      },
      extraNodes: [
        { title: '验证码 10 分钟后过期校验', isTestCase: true },
        { title: '网络中断时重发', isTestCase: false },
      ],
      sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '验证码 60 秒内不可重发' }],
    },
    ...overrides,
  }
}

function priorityRow(overrides: Partial<AssistRow> = {}): AssistRow {
  return {
    key: 'node-p1',
    title: '登录-连续错误锁定',
    confirmStatus: 'pending',
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: {
      nodeId: 'n1',
      current: 'medium',
      suggested: 'high',
      reason: '该链路缺陷集中',
      sourceRefs: [],
    },
    ...overrides,
  }
}

function mountPanel(kind: PanelKind, props: Record<string, unknown> = {}): VueWrapper {
  return mount(AssistComparePanel, {
    props: { kind, rows: [], canAdopt: true, confirming: false, ...props },
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

  describe('两栏对照与折叠', () => {
    it('默认只展示变更项，无变化项折叠为计数并可展开', async () => {
      const wrapper = mountPanel('case_complete', { rows: [completeRow()] })

      const visible = wrapper.findAll('.assist-compare__row')
      expect(visible).toHaveLength(2)
      expect(visible.every((row) => row.classes('assist-compare__row--changed'))).toBe(true)
      expect(wrapper.text()).toContain('展开共有 2 项与现有内容相同')
      expect(wrapper.text()).not.toContain('前置条件')

      await actionButton(wrapper, '展开共有').trigger('click')

      expect(wrapper.findAll('.assist-compare__row')).toHaveLength(4)
      expect(wrapper.text()).toContain('前置条件')
      expect(wrapper.text()).toContain('收起无变化项')
    })

    it('补全与变更分别打徽标并渲染补充节点树预览', () => {
      const wrapper = mountPanel('case_complete', { rows: [completeRow()] })

      expect(wrapper.text()).toContain('补全')
      expect(wrapper.text()).toContain('变更')
      const nodes = wrapper.findAll('.assist-extra__list li')
      expect(nodes).toHaveLength(2)
      expect(nodes[0].text()).toContain('验证码 10 分钟后过期校验')
      expect(nodes[0].text()).toContain('用例')
      expect(nodes[1].text()).toContain('网络中断时重发')
      expect(nodes[1].text()).toContain('结构')
    })

    it('来源引用点击透传给宿主跳需求详情', async () => {
      const wrapper = mountPanel('case_complete', { rows: [completeRow()] })

      await wrapper.find('.assist-panel__sources .el-link').trigger('click')

      expect(wrapper.emitted('openSource')).toHaveLength(1)
      expect(wrapper.emitted('openSource')?.[0]?.[0]).toMatchObject({
        id: 'r1',
        title: 'REQ-001',
      })
    })

    it('全部与现状一致时提示无变化并置灰采纳', () => {
      const row = completeRow({
        suggestion: {
          nodeId: 'n1',
          fields: {
            precondition: { existing: ['手机号已输入'], suggested: ['手机号已输入'] },
            steps: { existing: ['点击获取验证码'], suggested: ['点击获取验证码'] },
            expected: { existing: ['发送成功'], suggested: ['发送成功'] },
            tags: { existing: ['登录'], suggested: ['登录'] },
          },
          extraNodes: [],
          sourceRefs: [],
        },
      })
      const wrapper = mountPanel('case_complete', { rows: [row] })

      expect(wrapper.text()).toContain('建议与现有内容一致，无需变更')
      expect(actionButton(wrapper, '逐条采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '批量采纳').attributes('disabled')).toBeDefined()
      // 全无变化按交互 2.3 口径仅可关闭，驳回与原因文案不得与之矛盾
      expect(actionButton(wrapper, '驳回').attributes('disabled')).toBeDefined()
    })

    it('内容缺失时给出占位并禁止采纳', () => {
      const wrapper = mountPanel('case_complete', {
        rows: [completeRow({ suggestion: null })],
      })

      expect(wrapper.text()).toContain('建议内容暂不可用')
      expect(actionButton(wrapper, '逐条采纳').attributes('disabled')).toBeDefined()
    })

    it('加载中渲染骨架而非空态', () => {
      const wrapper = mountPanel('case_complete', {
        rows: [completeRow({ suggestion: null })],
        loading: true,
      })

      expect(wrapper.find('.el-skeleton').exists()).toBe(true)
    })
  })

  describe('采纳与驳回', () => {
    it('逐条采纳只回写变更字段，单值字段按字符串提交', async () => {
      const row = completeRow({
        suggestion: {
          nodeId: 'n1',
          fields: {
            precondition: { existing: ['手机号已输入'], suggested: ['手机号已输入并获取过码'] },
            steps: { existing: ['点击获取验证码'], suggested: ['点击获取验证码'] },
            expected: { existing: [], suggested: ['发送成功提示'] },
            tags: { existing: ['登录'], suggested: ['登录'] },
          },
          extraNodes: [],
          sourceRefs: [],
        },
      })
      const wrapper = mountPanel('case_complete', { rows: [row] })

      await actionButton(wrapper, '逐条采纳').trigger('click')

      expect(wrapper.emitted('adoptEdited')).toHaveLength(1)
      expect(wrapper.emitted('adoptEdited')?.[0]).toEqual([
        'node-1',
        {
          fields: {
            precondition: '手机号已输入并获取过码',
            expected: ['发送成功提示'],
          },
        },
      ])
    })

    it('多节点时逐条采纳后推进到下一个待处理建议', async () => {
      const second = completeRow({
        key: 'node-2',
        title: '登录-图形验证码',
        suggestion: {
          nodeId: 'n2',
          fields: {
            precondition: { existing: [], suggested: ['页面已打开'] },
            steps: { existing: [], suggested: ['输入图形验证码'] },
            expected: { existing: [], suggested: [] },
            tags: { existing: [], suggested: [] },
          },
          extraNodes: [],
          sourceRefs: [{ id: 'r2', title: 'REQ-002', quote: '' }],
        },
      })
      const wrapper = mountPanel('case_complete', { rows: [completeRow(), second] })

      expect(wrapper.find('.assist-panel__switch').exists()).toBe(true)
      expect(nodeTitle(wrapper)).toBe('REQ-001')

      await actionButton(wrapper, '逐条采纳').trigger('click')

      expect(wrapper.emitted('adoptEdited')?.[0]?.[0]).toBe('node-1')
      await nextTick()
      expect(nodeTitle(wrapper)).toBe('REQ-002')
    })

    it('级别推荐按整条采纳，不带编辑载荷', async () => {
      const wrapper = mountPanel('case_priority', { rows: [priorityRow()] })

      expect(wrapper.text()).toContain('P1·中')
      expect(wrapper.text()).toContain('P0·高')
      expect(wrapper.text()).toContain('用例级别')

      await actionButton(wrapper, '逐条采纳').trigger('click')

      expect(wrapper.emitted('adopt')).toHaveLength(1)
      expect(wrapper.emitted('adopt')?.[0]).toEqual(['node-p1'])
      expect(wrapper.emitted('adoptEdited')).toBeUndefined()
    })

    it('级别相同项默认折叠为无变化计数', () => {
      const unchanged = priorityRow({
        suggestion: {
          nodeId: 'n1',
          current: 'medium',
          suggested: 'medium',
          reason: '',
          sourceRefs: [],
        },
      })
      const wrapper = mountPanel('case_priority', { rows: [unchanged] })

      expect(wrapper.findAll('.assist-compare__row')).toHaveLength(0)
      expect(wrapper.text()).toContain('展开共有 1 项与现有内容相同')
      expect(wrapper.text()).toContain('建议与现有内容一致，无需变更')
      expect(actionButton(wrapper, '逐条采纳').attributes('disabled')).toBeDefined()
    })

    it('批量采纳提交全部待处理项', async () => {
      const wrapper = mountPanel('case_complete', {
        rows: [completeRow(), completeRow({ key: 'node-2', title: '登录-图形验证码' })],
      })

      await actionButton(wrapper, '批量采纳').trigger('click')

      expect(wrapper.emitted('batchAdopt')?.[0]).toEqual([['node-1', 'node-2']])
    })

    it('驳回带说明提交，空白说明省略', async () => {
      const wrapper = mountPanel('case_complete', { rows: [completeRow()] })

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
    it('无编辑权限时置灰并提示原因', () => {
      const wrapper = mountPanel('case_complete', {
        rows: [completeRow()],
        canAdopt: false,
      })

      expect(actionButton(wrapper, '逐条采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '批量采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '驳回').attributes('disabled')).toBeDefined()
      expect(wrapper.text()).toContain('当前节点只读或没有脑图编辑权限')
    })

    it('逐项回执渲染成败并可清空', async () => {
      const receipt: AiArtifactConfirmResult[] = [
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
      const wrapper = mountPanel('case_complete', {
        rows: [completeRow(), completeRow({ key: 'node-2', title: '登录-图形验证码' })],
        receipt,
      })

      expect(wrapper.text()).toContain('部分确认失败')
      expect(wrapper.text()).toContain('登录-图形验证码')
      expect(wrapper.text()).toContain('无权限（1000018306）')

      await actionButton(wrapper, '收起回执').trigger('click')
      expect(wrapper.emitted('clearReceipt')).toHaveLength(1)
    })

    it('回执重试回放当时的提交动作', async () => {
      const wrapper = mountPanel('case_priority', {
        rows: [priorityRow()],
        receipt: [
          {
            key: 'node-p1',
            action: 'adopted',
            success: false,
            createdId: null,
            errorCode: 1000018117,
            errorMsg: '模型超时',
          },
        ],
      })

      await actionButton(wrapper, '逐条采纳').trigger('click')
      await nextTick()
      await actionButton(wrapper, '重试').trigger('click')

      expect(wrapper.emitted('adopt')).toHaveLength(2)
      expect(wrapper.emitted('adopt')?.[1]).toEqual(['node-p1'])
    })

    it('来源需求已变更时顶部提示但仍可采纳', async () => {
      const wrapper = mountPanel('case_complete', {
        rows: [completeRow()],
        sourceStale: true,
      })

      expect(wrapper.text()).toContain('来源需求已变更')
      expect(actionButton(wrapper, '逐条采纳').attributes('disabled')).toBeUndefined()

      await actionButton(wrapper, '关闭').trigger('click')
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('已采纳计数按处理状态统计', () => {
      const wrapper = mountPanel('case_complete', {
        rows: [
          completeRow({ confirmStatus: 'adopted', confirmLabel: '已采纳', confirmTagType: 'success' }),
          completeRow({ key: 'node-2', confirmStatus: 'rejected', confirmLabel: '已驳回' }),
          completeRow({ key: 'node-3' }),
        ],
      })

      expect(wrapper.text()).toContain('已采纳 1 / 共 3')
      expect(actionButton(wrapper, '批量采纳').attributes('disabled')).toBeUndefined()
    })
  })
})
