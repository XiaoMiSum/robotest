// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import AiAssistantEntry from './AiAssistantEntry.vue'
import { useAiAssistantStore } from '@/stores/aiAssistant'
import type { AiAssistantConversation, AiAssistantMessage } from '@/types'

const mocks = vi.hoisted(() => ({
  fetchAiStatus: vi.fn(),
  hasPermission: vi.fn(),
  fetchAiAssistantConversations: vi.fn(),
  createAiAssistantConversation: vi.fn(),
  updateAiAssistantConversation: vi.fn(),
  deleteAiAssistantConversation: vi.fn(),
  fetchAiAssistantMessages: vi.fn(),
  executeAiAssistantMessage: vi.fn(),
  cancelAiAssistantMessage: vi.fn(),
  streamAssistantMessage: vi.fn(),
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageWarning: vi.fn(),
  prompt: vi.fn(),
  confirm: vi.fn(),
  loadTraceNode: vi.fn(),
}))

// Markdown 渲染由 MarkdownView 既有单测覆盖，此处只断言本组件的组装行为
vi.mock('@/components/common/MarkdownView.vue', async () => {
  const { h } = await import('vue')
  return {
    default: {
      name: 'MarkdownView',
      props: { content: { type: String, required: true } },
      setup: (props: { content: string }) => () => h('div', { class: 'md-stub' }, props.content),
    },
  }
})

vi.mock('@/composables/project/trace/useTraceNodePicker', async () => {
  const { ref } = await import('vue')
  return {
    useTraceNodePicker: () => ({
      options: ref([{ id: 'r1', label: '登录需求' }]),
      loading: ref(false),
      load: mocks.loadTraceNode,
    }),
  }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: {
      error: mocks.messageError,
      success: mocks.messageSuccess,
      warning: mocks.messageWarning,
    },
    ElMessageBox: {
      prompt: mocks.prompt,
      confirm: mocks.confirm,
    },
  }
})

vi.mock('@/services/ai', () => ({ fetchAiStatus: mocks.fetchAiStatus }))

vi.mock('@/services/aiAssistant', () => ({
  fetchAiAssistantConversations: mocks.fetchAiAssistantConversations,
  createAiAssistantConversation: mocks.createAiAssistantConversation,
  updateAiAssistantConversation: mocks.updateAiAssistantConversation,
  deleteAiAssistantConversation: mocks.deleteAiAssistantConversation,
  fetchAiAssistantMessages: mocks.fetchAiAssistantMessages,
  executeAiAssistantMessage: mocks.executeAiAssistantMessage,
  cancelAiAssistantMessage: mocks.cancelAiAssistantMessage,
}))

vi.mock('@/services/assistantSse', () => ({
  streamAssistantMessage: mocks.streamAssistantMessage,
  AssistantStreamError: class extends Error {},
  AssistantStreamInterruptedError: class extends Error {},
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasPermission: mocks.hasPermission }),
}))

type Store = ReturnType<typeof useAiAssistantStore>

let store: Store
let router: Router
let activeWrapper: VueWrapper | null = null

function makeConversation(overrides: Partial<AiAssistantConversation> = {}): AiAssistantConversation {
  return {
    id: 'c1',
    title: '登录评审',
    status: 'active',
    lastMessageAt: null,
    messageCount: 0,
    contextSnapshot: null,
    ...overrides,
  }
}

function makeMessage(overrides: Partial<AiAssistantMessage> = {}): AiAssistantMessage {
  return {
    id: 'm1',
    conversationId: 'c1',
    role: 'assistant',
    content: '答案',
    attachments: null,
    intent: null,
    citations: [],
    execution: null,
    status: 'done',
    createdAt: '2026-10-08T00:00:00',
    ...overrides,
  }
}

async function mountEntry(): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  store = useAiAssistantStore()
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }],
  })
  activeWrapper = mount(AiAssistantEntry, {
    global: { plugins: [ElementPlus, pinia, router] },
  })
  await flushPromises()
  return activeWrapper
}

async function openPanel(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('.ai-entry__ball').trigger('click')
  await flushPromises()
}

describe('components/ai/assistant/AiAssistantEntry', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchAiStatus.mockResolvedValue({ available: true })
    mocks.hasPermission.mockReturnValue(true)
    mocks.fetchAiAssistantConversations.mockResolvedValue({
      list: [makeConversation()],
      total: 1,
    })
    mocks.fetchAiAssistantMessages.mockResolvedValue({ list: [], total: 0 })
    mocks.updateAiAssistantConversation.mockResolvedValue(makeConversation())
    mocks.deleteAiAssistantConversation.mockResolvedValue(undefined)
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = null
    document.body.innerHTML = ''
    Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true })
  })

  it('AI 不可用时悬浮球不渲染', async () => {
    mocks.fetchAiStatus.mockResolvedValue({ available: false })
    const wrapper = await mountEntry()

    expect(wrapper.find('.ai-entry').exists()).toBe(false)
  })

  it('缺少 ai:task 权限时悬浮球不渲染', async () => {
    mocks.hasPermission.mockReturnValue(false)
    const wrapper = await mountEntry()

    expect(wrapper.find('.ai-entry').exists()).toBe(false)
  })

  it('点击悬浮球展开面板并拉取会话，头部关闭后收起', async () => {
    const wrapper = await mountEntry()
    const ball = wrapper.get('.ai-entry__ball')
    expect(ball.attributes('aria-expanded')).toBe('false')

    await ball.trigger('click')
    await flushPromises()

    expect(store.open).toBe(true)
    expect(ball.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.ai-entry__card').exists()).toBe(true)
    expect(mocks.fetchAiAssistantConversations).toHaveBeenCalledTimes(1)

    await wrapper.get('[aria-label="关闭面板"]').trigger('click')
    await flushPromises()

    expect(store.open).toBe(false)
    expect(wrapper.find('.ai-entry__card').exists()).toBe(false)
  })

  it('会话列表加载失败页面级提示并清空错误（UI-PAGE-11）', async () => {
    mocks.fetchAiAssistantConversations.mockRejectedValue(new Error('网络错误'))
    const wrapper = await mountEntry()

    await openPanel(wrapper)

    expect(mocks.messageError).toHaveBeenCalledWith('网络错误')
    expect(store.conversationsError).toBe('')
  })

  it('消息加载失败页面级提示并清空错误（UI-PAGE-11）', async () => {
    mocks.fetchAiAssistantMessages.mockRejectedValue(new Error('消息加载失败'))
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    await wrapper.get('button[aria-label="切换会话：登录评审"]').trigger('click')
    await flushPromises()

    expect(mocks.messageError).toHaveBeenCalledWith('消息加载失败')
    expect(store.messagesError).toBe('')
  })

  it('点击会话行切换会话、加载消息并收起列表', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    expect(wrapper.get('.ai-panel-header__title').text()).toBe('助手')

    await wrapper.get('button[aria-label="切换会话：登录评审"]').trigger('click')
    await flushPromises()

    expect(store.currentId).toBe('c1')
    expect(mocks.fetchAiAssistantMessages).toHaveBeenCalledWith('c1', { pageNo: 1, pageSize: 20 })
    expect(wrapper.get('.ai-panel-header__title').text()).toBe('登录评审')
    expect(wrapper.find('.ai-session-list').exists()).toBe(false)
  })

  it('重命名提交去除首尾空白的新标题', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.prompt.mockResolvedValue({ value: '  新的登录评审  ' })

    await openPanel(wrapper)
    await wrapper.get('[aria-label="重命名会话"]').trigger('click')
    await flushPromises()

    expect(mocks.prompt).toHaveBeenCalledTimes(1)
    expect(mocks.updateAiAssistantConversation).toHaveBeenCalledWith('c1', {
      title: '新的登录评审',
    })
  })

  it('重命名校验会话名非空且不超过 100 字', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.prompt.mockResolvedValue({ value: '登录评审' })

    await openPanel(wrapper)
    await wrapper.get('[aria-label="重命名会话"]').trigger('click')
    await flushPromises()

    const options = mocks.prompt.mock.calls[0][2] as {
      inputValidator: (value: string) => boolean | string
    }
    expect(options.inputValidator('   ')).toBe('会话名称不能为空')
    expect(options.inputValidator('x'.repeat(101))).toBe('会话名称不能超过 100 字')
    expect(options.inputValidator('正常标题')).toBe(true)
  })

  it('取消重命名不调用接口也不提示错误', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.prompt.mockRejectedValue('cancel')

    await openPanel(wrapper)
    await wrapper.get('[aria-label="重命名会话"]').trigger('click')
    await flushPromises()

    expect(mocks.updateAiAssistantConversation).not.toHaveBeenCalled()
    expect(mocks.messageError).not.toHaveBeenCalled()
  })

  it('重命名失败按原因提示错误', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.prompt.mockResolvedValue({ value: '新标题' })
    mocks.updateAiAssistantConversation.mockRejectedValue(new Error('改名失败'))

    await openPanel(wrapper)
    await wrapper.get('[aria-label="重命名会话"]').trigger('click')
    await flushPromises()

    expect(mocks.messageError).toHaveBeenCalledWith('改名失败')
  })

  it('确认归档后更新会话状态', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.updateAiAssistantConversation.mockResolvedValue(
      makeConversation({ status: 'archived' }),
    )
    mocks.confirm.mockResolvedValue('confirm')

    await openPanel(wrapper)
    await wrapper.get('[aria-label="归档会话"]').trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.updateAiAssistantConversation).toHaveBeenCalledWith('c1', {
      status: 'archived',
    })
    expect(store.currentConversation?.status).toBe('archived')
  })

  it('取消归档不调用接口也不提示错误', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.confirm.mockRejectedValue('cancel')

    await openPanel(wrapper)
    await wrapper.get('[aria-label="归档会话"]').trigger('click')
    await flushPromises()

    expect(mocks.updateAiAssistantConversation).not.toHaveBeenCalled()
    expect(mocks.messageError).not.toHaveBeenCalled()
  })

  it('确认删除当前会话后重置会话上下文', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'
    mocks.confirm.mockResolvedValue('confirm')

    await openPanel(wrapper)
    await wrapper.get('[aria-label="删除会话"]').trigger('click')
    await flushPromises()

    expect(mocks.deleteAiAssistantConversation).toHaveBeenCalledWith('c1')
    expect(store.currentId).toBeNull()
    expect(store.conversations).toHaveLength(0)
  })

  it('新会话重置当前会话', async () => {
    const wrapper = await mountEntry()
    store.currentId = 'c1'

    await openPanel(wrapper)
    await wrapper.get('[aria-label="新会话"]').trigger('click')
    await flushPromises()

    expect(store.currentId).toBeNull()
  })

  it('检索输入防抖后按关键词重查', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    expect(mocks.fetchAiAssistantConversations).toHaveBeenCalledTimes(1)

    await wrapper.get('.ai-session-list input').setValue('登录')
    expect(mocks.fetchAiAssistantConversations).toHaveBeenCalledTimes(1)

    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()

    expect(mocks.fetchAiAssistantConversations).toHaveBeenCalledTimes(2)
    expect(mocks.fetchAiAssistantConversations).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: '登录' }),
    )
  })

  it('从头栏拖动卡片并按视口夹取位置，松开后停止跟随', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    wrapper.get('.ai-panel-header').element.dispatchEvent(
      new MouseEvent('mousedown', { clientX: 100, clientY: 120, bubbles: true }),
    )
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 260, clientY: 220 }))
    await nextTick()

    const card = wrapper.get('.ai-entry__card')
    expect(card.attributes('style')).toContain('left: 160px')
    expect(card.attributes('style')).toContain('top: 100px')

    window.dispatchEvent(new MouseEvent('mouseup'))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 400, clientY: 300 }))
    await nextTick()

    expect(card.attributes('style')).toContain('left: 160px')
    expect(card.attributes('style')).toContain('top: 100px')
  })

  it('小屏不下发内联定位且拖动不生效（交互 05 §2.4 小屏适配）', async () => {
    Object.defineProperty(window, 'innerWidth', { value: 500, configurable: true })
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    expect(wrapper.get('.ai-entry__card').attributes('style')).toBeUndefined()

    wrapper.get('.ai-panel-header').element.dispatchEvent(
      new MouseEvent('mousedown', { clientX: 50, clientY: 60, bubbles: true }),
    )
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 150, clientY: 160 }))
    await nextTick()

    expect(wrapper.get('.ai-entry__card').attributes('style')).toBeUndefined()
  })

  it('输入并发送：建会话、乐观上屏并清空草稿', async () => {
    mocks.createAiAssistantConversation.mockResolvedValue(makeConversation())
    mocks.streamAssistantMessage.mockImplementation(
      async (_id: string, _payload: unknown, onEvent: (e: unknown) => void) => {
        onEvent({ type: 'done', data: { messageId: 'm2' } })
      },
    )
    mocks.fetchAiAssistantMessages.mockResolvedValue({
      list: [
        makeMessage({ id: 'm2', content: '已解析意图' }),
        makeMessage({ id: 'm1', role: 'user', content: '帮我建一个登录评审' }),
      ],
      total: 2,
    })
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    await wrapper.get('.ai-composer textarea').setValue('帮我建一个登录评审')
    expect(store.draftForCurrent).toBe('帮我建一个登录评审')

    await wrapper.get('[aria-label="发送"]').trigger('click')
    await flushPromises()

    expect(mocks.createAiAssistantConversation).toHaveBeenCalledTimes(1)
    expect(mocks.streamAssistantMessage).toHaveBeenCalledWith(
      'c1',
      { content: '帮我建一个登录评审' },
      expect.any(Function),
    )
    expect(store.draftForCurrent).toBe('')
    expect(wrapper.text()).toContain('👤 用户')
    expect(wrapper.get('.md-stub').text()).toBe('已解析意图')
  })

  it('空态示例点击回填输入框且不直接发送', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    await wrapper.get('.ai-timeline__example').trigger('click')

    expect(store.draftForCurrent).toBe('帮我建一个登录评审')
    const textarea = wrapper.get('.ai-composer textarea').element as HTMLTextAreaElement
    expect(textarea.value).toBe('帮我建一个登录评审')
    expect(mocks.streamAssistantMessage).not.toHaveBeenCalled()
  })

  it('澄清选项点击回填并立即发送', async () => {
    mocks.streamAssistantMessage.mockImplementation(
      async (_id: string, _payload: unknown, onEvent: (e: unknown) => void) => {
        onEvent({ type: 'done', data: { messageId: 'm3' } })
      },
    )
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    store.messages = [
      makeMessage({
        id: 'c2',
        content: '要建哪个模块的评审？\n\n- 登录模块\n- 订单模块',
        citations: null,
      }),
    ]
    await nextTick()

    await wrapper.findAll('.ai-clarify__option')[0].trigger('click')
    await flushPromises()

    expect(mocks.streamAssistantMessage).toHaveBeenCalledWith(
      'c1',
      { content: '登录模块' },
      expect.any(Function),
    )
    expect(store.draftForCurrent).toBe('')
  })

  it('引用跳转导航到详情页并收起面板', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    store.messages = [
      makeMessage({
        citations: [{ type: 'requirement', id: 'r1', title: '登录需求', quote: '摘要' }],
      }),
    ]
    await nextTick()

    await wrapper.get('.ai-citation__jump').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/workspace/projects/requirements/r1')
    expect(store.open).toBe(false)
  })

  it('附件选择进入待发列表并可移除', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    await wrapper.get('[aria-label="添加上下文附件"]').trigger('click')
    await flushPromises()
    const option = document.body.querySelector('.ai-context-picker__option') as HTMLButtonElement
    option.click()
    await flushPromises()

    expect(store.attachmentsForCurrent).toEqual([
      { entityType: 'requirement', entityId: 'r1', entityTitle: '登录需求' },
    ])
    expect(wrapper.get('.ai-composer__attachment').text()).toContain('登录需求')

    await wrapper.get('[aria-label="移除附件：登录需求"]').trigger('click')
    expect(store.attachmentsForCurrent).toEqual([])
    expect(wrapper.find('.ai-composer__attachment').exists()).toBe(false)
  })

  it('流式生成中输入框禁用', async () => {
    const wrapper = await mountEntry()

    await openPanel(wrapper)
    store.streaming = true
    await nextTick()

    expect(wrapper.get('.ai-composer textarea').attributes('disabled')).toBeDefined()
  })
})
