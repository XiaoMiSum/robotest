// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AiReviewPanel from './AiReviewPanel.vue'
import type { AssistRow } from '@/composables/project/ai/useAssistTask'
import type { AiProposalConfirmResult, AiReviewSuggestion } from '@/types'

function suggestion(overrides: Partial<AiReviewSuggestion> = {}): AiReviewSuggestion {
  return {
    reviewId: 'rv-1',
    snapshotNodeId: 'snap-1',
    caseTitle: '获取验证码',
    suggestedMark: 'fail',
    reason: '缺少验证码错误分支的预期',
    issues: [
      {
        severity: 'medium',
        category: '预期不完整',
        description: '未覆盖验证码输入错误的预期',
        path: '获取验证码 › 步骤 2',
      },
      {
        severity: 'high',
        category: '前置缺失',
        description: '未声明验证码有效期',
        path: '获取验证码 › 前置条件',
      },
    ],
    sourceRefs: [{ id: 'r1', title: 'REQ-001', quote: '验证码错误时提示重新输入' }],
    ...overrides,
  }
}

function reviewRow(overrides: Partial<AssistRow> = {}): AssistRow {
  return {
    key: 'case-1',
    title: 'TC-001 获取验证码',
    confirmStatus: 'pending',
    confirmLabel: '待确认',
    confirmTagType: 'info',
    suggestion: suggestion(),
    ...overrides,
  }
}

function mountPanel(props: Record<string, unknown> = {}): VueWrapper {
  return mount(AiReviewPanel, {
    props: { rows: [], canAdopt: true, confirming: false, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function actionButton(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!button) throw new Error(`未找到按钮：${text}`)
  return button
}

function findInTree(root: ParentNode, text: string): HTMLElement | null {
  return (
    Array.from(root.querySelectorAll('button')).find((item) =>
      item.textContent?.includes(text),
    ) ?? null
  )
}

describe('AiReviewPanel', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  describe('建议列表渲染', () => {
    it('逐用例卡片展示标题、建议标记、理由、问题清单与来源引用', () => {
      const wrapper = mountPanel({ rows: [reviewRow()] })
      const text = wrapper.text()

      expect(text).toContain('获取验证码')
      expect(text).toContain('建议不通过')
      expect(text).toContain('缺少验证码错误分支的预期')
      expect(text).toContain('预期不完整')
      expect(text).toContain('获取验证码 › 步骤 2')
      expect(text).toContain('前置缺失')
      expect(text).toContain('REQ-001')
      expect(wrapper.find('.el-link').exists()).toBe(true)
    })

    it('建议通过时展示通过徽标且无问题时展示理由为空占位', () => {
      const wrapper = mountPanel({
        rows: [reviewRow({
          suggestion: suggestion({ suggestedMark: 'pass', reason: '', issues: [], sourceRefs: [] }),
        })],
      })

      expect(wrapper.text()).toContain('建议通过')
      expect(wrapper.text()).toContain('理由：—')
      expect(wrapper.findAll('.review-card__issue')).toHaveLength(0)
    })

    it('来源引用点击透传给宿主跳需求详情', async () => {
      const wrapper = mountPanel({ rows: [reviewRow()] })

      await wrapper.find('.el-link').trigger('click')

      expect(wrapper.emitted('openSource')).toHaveLength(1)
      expect(wrapper.emitted('openSource')?.[0]?.[0]).toMatchObject({
        id: 'r1',
        title: 'REQ-001',
      })
    })

    it('加载中渲染骨架，空行渲染占位', () => {
      const loading = mountPanel({ rows: [reviewRow({ suggestion: null })], loading: true })
      expect(loading.find('.el-skeleton').exists()).toBe(true)

      const empty = mountPanel({ rows: [] })
      expect(empty.text()).toContain('暂无评审建议')
    })

    it('内容缺失时禁用处置动作', () => {
      const wrapper = mountPanel({ rows: [reviewRow({ suggestion: null })] })

      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '驳回').attributes('disabled')).toBeDefined()
    })
  })

  describe('采纳与驳回', () => {
    it('采纳按建议原值透传 key', async () => {
      const wrapper = mountPanel({ rows: [reviewRow()] })

      await actionButton(wrapper, '采纳').trigger('click')

      expect(wrapper.emitted('adopt')).toEqual([['case-1']])
    })

    it('编辑后采纳提交人工修正的结论与理由', async () => {
      const wrapper = mountPanel({ rows: [reviewRow()] })

      await actionButton(wrapper, '编辑后采纳').trigger('click')
      const select = wrapper.findComponent({ name: 'ElSelect' })
      select.vm.$emit('update:modelValue', 'pass')
      await nextTick()
      const textarea = wrapper.find('textarea')
      expect(textarea.exists()).toBe(true)
      await textarea.setValue('人工复核后认为预期充分')
      await actionButton(wrapper, '提交').trigger('click')

      expect(wrapper.emitted('adoptEdited')).toEqual([[
        'case-1',
        { suggestedMark: 'pass', reason: '人工复核后认为预期充分' },
      ]])
    })

    it('编辑区可取消且恢复默认初值', async () => {
      const wrapper = mountPanel({ rows: [reviewRow()] })

      await actionButton(wrapper, '编辑后采纳').trigger('click')
      expect(wrapper.find('textarea').exists()).toBe(true)
      await actionButton(wrapper, '取消').trigger('click')
      expect(wrapper.find('textarea').exists()).toBe(false)

      await actionButton(wrapper, '编辑后采纳').trigger('click')
      expect(wrapper.find('textarea').element.value).toBe('缺少验证码错误分支的预期')
    })

    it('驳回填写原因后透传，空白原因省略', async () => {
      const wrapper = mountPanel({ rows: [reviewRow()] })

      await actionButton(wrapper, '驳回').trigger('click')
      await flushPromises()
      const textarea = (wrapper.element.querySelector('textarea')
        ?? document.body.querySelector('textarea')) as HTMLTextAreaElement | null
      expect(textarea).not.toBeNull()
      if (textarea) {
        textarea.value = '本期不评审'
        textarea.dispatchEvent(new Event('input', { bubbles: true }))
      }
      await nextTick()
      const confirm = findInTree(wrapper.element, '确认驳回') ?? findInTree(document.body, '确认驳回')
      expect(confirm).not.toBeNull()
      confirm?.click()
      await nextTick()

      expect(wrapper.emitted('reject')).toEqual([['case-1', '本期不评审']])
    })
  })

  describe('分区、回执与状态分支', () => {
    it('按处置状态分区，已处置卡片只读回看', () => {
      const wrapper = mountPanel({
        rows: [
          reviewRow({
            key: 'case-2',
            title: 'TC-002 登录成功',
            confirmStatus: 'adopted',
            confirmLabel: '已采纳',
            confirmTagType: 'success',
            suggestion: suggestion({
              caseTitle: '登录成功', suggestedMark: 'pass', issues: [],
            }),
          }),
          reviewRow(),
        ],
      })

      expect(wrapper.text()).toContain('待处理建议（1）')
      expect(wrapper.text()).toContain('已处理（1）')
      expect(wrapper.text()).toContain('TC-002 登录成功')
      expect(wrapper.findAll('.review-card')).toHaveLength(2)
      expect(wrapper.findAll('.review-card__actions')).toHaveLength(1)
    })

    it('内容刷新后停在首个待处置建议', async () => {
      const second = reviewRow({ key: 'case-2', title: 'TC-002 登录成功' })
      const wrapper = mountPanel({ rows: [reviewRow(), second] })
      const cards = wrapper.findAll('.review-card')
      expect(cards[0].classes()).toContain('review-card--active')
      expect(cards[1].classes()).not.toContain('review-card--active')

      await wrapper.setProps({
        rows: [reviewRow({ confirmStatus: 'rejected', confirmLabel: '已驳回' }), second],
      })

      const refreshed = wrapper.findAll('.review-card')
      expect(refreshed[0].classes()).toContain('review-card--active')
      expect(refreshed[1].classes()).not.toContain('review-card--active')
    })

    it('逐项回执渲染成败并可清空、重试回放动作', async () => {
      const receipt: AiProposalConfirmResult[] = [
        { key: 'case-1', action: 'adopted', success: true, createdId: null, errorCode: null, errorMsg: null },
        {
          key: 'case-2', action: 'adopted', success: false, createdId: null,
          errorCode: 1000018314, errorMsg: '评审已终态',
        },
      ]
      const wrapper = mountPanel({
        rows: [reviewRow(), reviewRow({ key: 'case-2', title: 'TC-002 登录成功' })],
        receipt,
      })

      expect(wrapper.text()).toContain('部分确认失败')
      expect(wrapper.text()).toContain('评审已终态（1000018314）')

      await actionButton(wrapper, '收起回执').trigger('click')
      expect(wrapper.emitted('clearReceipt')).toHaveLength(1)

      const replay = mountPanel({
        rows: [reviewRow()],
        receipt: [
          {
            key: 'case-1', action: 'adopted', success: false, createdId: null,
            errorCode: 1000018117, errorMsg: '模型超时',
          },
        ],
      })
      await actionButton(replay, '采纳').trigger('click')
      await nextTick()
      await actionButton(replay, '重试').trigger('click')

      expect(replay.emitted('adopt')).toEqual([['case-1'], ['case-1']])
    })

    it('不可处置时置灰并说明原因', () => {
      const wrapper = mountPanel({ rows: [reviewRow()], canAdopt: false })

      expect(wrapper.text()).toContain('当前评审不可处置建议')
      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeDefined()
      expect(actionButton(wrapper, '驳回').attributes('disabled')).toBeDefined()
    })

    it('来源变更提示不阻断采纳，重新执行与关闭按钮透传', async () => {
      const wrapper = mountPanel({ rows: [reviewRow()], sourceStale: true, retryable: true })

      expect(wrapper.text()).toContain('来源需求已变更')
      expect(actionButton(wrapper, '采纳').attributes('disabled')).toBeUndefined()

      await actionButton(wrapper, '重新执行').trigger('click')
      await actionButton(wrapper, '关闭').trigger('click')

      expect(wrapper.emitted('retry')).toHaveLength(1)
      expect(wrapper.emitted('close')).toHaveLength(1)
    })
  })
})
