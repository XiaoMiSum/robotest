// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import PreviewCard from './PreviewCard.vue'
import type { AiAssistantIntent } from '@/types'

/** 后端返回无时区标识的 UTC 值（详设 4.3），按 UTC 截断到秒 */
function utcNoZone(ms: number): string {
  return new Date(ms).toISOString().slice(0, 19)
}

function makeIntent(overrides: Partial<AiAssistantIntent> = {}): AiAssistantIntent {
  return {
    kind: 'create_case',
    targetType: 'test_case',
    targetTitle: '登录用例',
    createCount: 3,
    changes: [
      { field: 'title', op: 'add', value: '登录成功用例' },
      { field: 'priority', op: 'replace', value: 'P0 → P1' },
    ],
    scope: { workspaceId: 'w1', projectId: 'p1', projectName: '演示项目' },
    expiresAt: utcNoZone(Date.now() + 10 * 60 * 1000),
    ...overrides,
  }
}

interface CardProps {
  intent?: AiAssistantIntent
  executing?: boolean
  readonly?: boolean
  editable?: boolean
}

let activeWrapper: VueWrapper | null = null

function mountCard(props: CardProps = {}): VueWrapper {
  const wrapper = mount(PreviewCard, {
    props: {
      intent: makeIntent(),
      executing: false,
      readonly: false,
      editable: true,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
  activeWrapper = wrapper
  return wrapper
}

function findButton(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`未找到按钮：${text}`)
  return found
}

describe('components/ai/assistant/PreviewCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = null
    vi.useRealTimers()
  })

  it('展示动作、目标、创建数量与字段级变更', () => {
    const wrapper = mountCard()

    expect(wrapper.get('.ai-preview').attributes('aria-label')).toBe('变更预览')
    expect(wrapper.text()).toContain('创建用例')
    expect(wrapper.text()).toContain('登录用例')
    expect(wrapper.text()).toContain('将创建 3 条')
    expect(wrapper.text()).toContain('项目：演示项目')
    expect(wrapper.text()).toContain('标题')
    expect(wrapper.text()).toContain('优先级')
    expect(wrapper.text()).toContain('登录成功用例')
    // replace 拆出原值与新值两个 diff 段
    expect(wrapper.text()).toContain('P0')
    expect(wrapper.text()).toContain('P1')
    expect(wrapper.findAll('.ai-preview__seg--del')).toHaveLength(1)
    expect(wrapper.findAll('.ai-preview__seg--add')).toHaveLength(2)
  })

  it('未过期倒计时展示并可确认、取消与返回修改', async () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toMatch(/有效期剩余 \d{2}:\d{2}/)
    expect(findButton(wrapper, '确认执行').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[aria-label="变更预览"]').text()).not.toContain('预览已过期')

    await findButton(wrapper, '确认执行').trigger('click')
    await findButton(wrapper, '取消').trigger('click')
    await findButton(wrapper, '返回修改').trigger('click')

    expect(wrapper.emitted('execute')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('edit')).toHaveLength(1)
    expect(wrapper.emitted('reparse')).toBeUndefined()
  })

  it('过期后确认置灰并提供重新解析，取消仍可用', async () => {
    const wrapper = mountCard({
      intent: makeIntent({ expiresAt: utcNoZone(Date.now() - 60 * 1000) }),
    })

    expect(wrapper.text()).toContain('预览已过期')
    expect(findButton(wrapper, '确认执行').attributes('disabled')).toBeDefined()
    expect(findButton(wrapper, '取消').attributes('disabled')).toBeUndefined()

    await findButton(wrapper, '重新解析').trigger('click')
    expect(wrapper.emitted('reparse')).toHaveLength(1)

    await findButton(wrapper, '取消').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('倒计时到点自动切换为过期态并停用确认', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-08T09:00:00.000Z'))
    const wrapper = mountCard({ intent: makeIntent({ expiresAt: '2026-10-08T09:10:00' }) })

    expect(wrapper.text()).toContain('有效期剩余 10:00')
    expect(findButton(wrapper, '确认执行').attributes('disabled')).toBeUndefined()

    vi.advanceTimersByTime(601_000)
    await nextTick()

    expect(wrapper.text()).toContain('预览已过期')
    expect(findButton(wrapper, '确认执行').attributes('disabled')).toBeDefined()
    expect(findButton(wrapper, '重新解析')).toBeDefined()
  })

  it('执行中与归档只读禁用全部操作', async () => {
    const wrapper = mountCard({
      intent: makeIntent({ expiresAt: utcNoZone(Date.now() - 60 * 1000) }),
      executing: true,
      readonly: true,
    })

    expect(findButton(wrapper, '确认执行').attributes('disabled')).toBeDefined()
    expect(findButton(wrapper, '取消').attributes('disabled')).toBeDefined()
    expect(findButton(wrapper, '返回修改').attributes('disabled')).toBeDefined()
    expect(findButton(wrapper, '重新解析').attributes('disabled')).toBeDefined()

    await findButton(wrapper, '确认执行').trigger('click')
    await findButton(wrapper, '取消').trigger('click')
    expect(wrapper.emitted('execute')).toBeUndefined()
    expect(wrapper.emitted('cancel')).toBeUndefined()
  })

  it('无解析摘要时返回修改禁用', async () => {
    const wrapper = mountCard({ editable: false })

    expect(findButton(wrapper, '返回修改').attributes('disabled')).toBeDefined()
    await findButton(wrapper, '返回修改').trigger('click')
    expect(wrapper.emitted('edit')).toBeUndefined()
  })
})
