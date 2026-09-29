// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { reactive } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import EnvironmentProcessorPane from './EnvironmentProcessorPane.vue'
import type { ApiProcessor, ApiProcessorType } from '@/types'
import type { ProcDraftMode } from '@/composables/project/api-testing/environment/useEnvironmentProcessors'

// jsdom 未实现 ResizeObserver，el-table 的尺寸监听依赖它
if (typeof ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}

// el-form-item 的错误文案经 refDebounced(100ms) 后才上屏
const ERROR_SHOW_DELAY = 150

function makeProcessor(id: string, overrides: Partial<ApiProcessor> = {}): ApiProcessor {
  return {
    id,
    processorType: 'preprocessor',
    name: '',
    config: {},
    enabled: true,
    sortOrder: 0,
    ...overrides,
  }
}

const defaultProps = {
  processorType: 'preprocessor' as ApiProcessorType,
  emptyDescription: '暂无前置处理器',
  count: 1,
  canEdit: true,
  processors: [] as ApiProcessor[],
  draft: null as ApiProcessor | null,
  draftMode: 'none' as ProcDraftMode,
  expandedId: '',
  configForms: [],
  dsForms: [],
  procTags: (): { text: string; type: 'info' }[] => [],
  procDisplayName: (processor: ApiProcessor, index: number): string =>
    processor.name ? processor.name : `处理器 ${index + 1}`,
}

function mountPane(props: Record<string, unknown> = {}): VueWrapper {
  return mount(EnvironmentProcessorPane, {
    props: { ...defaultProps, ...props },
    global: { plugins: [ElementPlus] },
  })
}

function rowButtons(wrapper: VueWrapper, index: number) {
  const row = wrapper.findAll('.env-proc-pane__item')[index]
  return row.findAll('button')
}

function buttonByText(buttons: ReturnType<typeof rowButtons>, text: string) {
  const button = buttons.find((item) => item.text() === text)
  if (!button) throw new Error(`未找到按钮 ${text}`)
  return button
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

describe('EnvironmentProcessorPane', () => {
  describe('列表行', () => {
    it('空列表显示引导文案', () => {
      const wrapper = mountPane()
      expect(wrapper.findAll('.env-proc-pane__item')).toHaveLength(0)
      expect(wrapper.find('.env-proc-pane__empty').text()).toBe('暂无前置处理器')
    })

    it('渲染序号与名称，点击行外发 toggle-detail', async () => {
      const processor = makeProcessor('p1', { name: 'Token 预置' })
      const wrapper = mountPane({ processors: [processor] })
      const row = wrapper.find('.env-proc-pane__row')
      expect(row.find('.env-proc-pane__num').text()).toBe('1')
      expect(row.text()).toContain('Token 预置')
      await row.trigger('click')
      expect(wrapper.emitted('toggle-detail')).toHaveLength(1)
      expect(wrapper.emitted('toggle-detail')?.[0]).toEqual([processor])
    })

    it('首行上移与末行下移禁用', () => {
      const wrapper = mountPane({
        processors: [makeProcessor('p1'), makeProcessor('p2')],
        count: 2,
      })
      expect(buttonByText(rowButtons(wrapper, 0), '上移').attributes('disabled')).toBeDefined()
      expect(buttonByText(rowButtons(wrapper, 0), '下移').attributes('disabled')).not.toBeDefined()
      expect(buttonByText(rowButtons(wrapper, 1), '下移').attributes('disabled')).toBeDefined()
    })

    it('行内操作外发 move/edit/copy/remove 且不触发行展开', async () => {
      const processor = makeProcessor('p1')
      const wrapper = mountPane({ processors: [processor, makeProcessor('p2')], count: 2 })
      await buttonByText(rowButtons(wrapper, 0), '下移').trigger('click')
      await buttonByText(rowButtons(wrapper, 0), '编辑').trigger('click')
      await buttonByText(rowButtons(wrapper, 0), '复制').trigger('click')
      await buttonByText(rowButtons(wrapper, 0), '删除').trigger('click')
      expect(wrapper.emitted('move')?.[0]).toEqual([0, 1])
      expect(wrapper.emitted('edit')?.[0]).toEqual([processor])
      expect(wrapper.emitted('copy')?.[0]).toEqual([processor])
      expect(wrapper.emitted('remove')?.[0]).toEqual([processor])
      expect(wrapper.emitted('toggle-detail')).toBeUndefined()
    })

    it('只读时隐藏行内操作并禁用添加', () => {
      const wrapper = mountPane({ processors: [makeProcessor('p1')], canEdit: false })
      expect(rowButtons(wrapper, 0).some((item) => item.text() === '编辑')).toBe(false)
      const head = wrapper.find('.env-proc-pane__head')
      expect(head.findAll('button').every((item) => item.attributes('disabled') !== undefined)).toBe(true)
    })
  })

  describe('编辑表单', () => {
    it('草稿命中行时该行渲染常显取消/保存', () => {
      const draft = reactive(makeProcessor('p1', { name: '原名' }))
      const wrapper = mountPane({ processors: [draft], draft, draftMode: 'edit' })
      const texts = rowButtons(wrapper, 0).map((item) => item.text())
      expect(texts).toContain('取消')
      expect(texts).toContain('保存')
      expect(texts).not.toContain('编辑')
      expect(wrapper.find('.env-proc-pane__body').exists()).toBe(true)
    })

    it('名称为空时保存不外发且提示错误', async () => {
      const draft = reactive(makeProcessor('p1', { name: '' }))
      const wrapper = mountPane({ processors: [draft], draft, draftMode: 'edit' })
      await buttonByText(rowButtons(wrapper, 0), '保存').trigger('click')
      expect(wrapper.emitted('save')).toBeUndefined()
      expect(wrapper.find('.el-form-item').classes()).toContain('is-error')
      await delay(ERROR_SHOW_DELAY)
      expect(wrapper.find('.el-form-item__error').text()).toBe('请输入处理器名称')
    })

    it('填写名称后清除错误态并外发 save', async () => {
      const draft = reactive(makeProcessor('p1', { name: '' }))
      const wrapper = mountPane({ processors: [draft], draft, draftMode: 'edit' })
      await buttonByText(rowButtons(wrapper, 0), '保存').trigger('click')
      expect(wrapper.find('.el-form-item').classes()).toContain('is-error')
      await wrapper.find('.env-proc-pane__name-item .el-input__inner').setValue('登录前置')
      expect(wrapper.find('.el-form-item').classes()).not.toContain('is-error')
      await buttonByText(rowButtons(wrapper, 0), '保存').trigger('click')
      expect(wrapper.emitted('save')).toHaveLength(1)
      expect(draft.name).toBe('登录前置')
    })

    it('点击取消外发 cancel', async () => {
      const draft = reactive(makeProcessor('p1', { name: '原名' }))
      const wrapper = mountPane({ processors: [draft], draft, draftMode: 'edit' })
      await buttonByText(rowButtons(wrapper, 0), '取消').trigger('click')
      expect(wrapper.emitted('cancel')).toHaveLength(1)
    })
  })

  describe('新增草稿行', () => {
    it('列表末尾追加草稿行且不显示为空态', () => {
      const draft = reactive(makeProcessor('', { processorType: 'preprocessor' }))
      const wrapper = mountPane({ processors: [], count: 0, draft, draftMode: 'add' })
      expect(wrapper.findAll('.env-proc-pane__item')).toHaveLength(1)
      expect(wrapper.find('.env-proc-pane__empty').exists()).toBe(false)
      expect(wrapper.find('.env-proc-pane__num').text()).toBe('1')
    })

    it('草稿类型不匹配本页签时不渲染且保持空态', () => {
      const draft = reactive(makeProcessor('', { processorType: 'postprocessor' }))
      const wrapper = mountPane({ processors: [], count: 0, draft, draftMode: 'add' })
      expect(wrapper.findAll('.env-proc-pane__item')).toHaveLength(0)
      expect(wrapper.find('.env-proc-pane__empty').exists()).toBe(true)
    })

    it('点击草稿行不外发行展开，保存与取消照常外发', async () => {
      const draft = reactive(makeProcessor('', { processorType: 'postprocessor' }))
      const wrapper = mountPane({
        processors: [],
        count: 0,
        draft,
        draftMode: 'add',
        processorType: 'postprocessor',
      })
      await wrapper.find('.env-proc-pane__row').trigger('click')
      expect(wrapper.emitted('toggle-detail')).toBeUndefined()
      await wrapper.find('.env-proc-pane__name-item .el-input__inner').setValue('响应断言')
      await buttonByText(rowButtons(wrapper, 0), '保存').trigger('click')
      await buttonByText(rowButtons(wrapper, 0), '取消').trigger('click')
      expect(wrapper.emitted('save')).toHaveLength(1)
      expect(wrapper.emitted('cancel')).toHaveLength(1)
    })
  })

  describe('只读明细', () => {
    it('展开行渲染配置摘要', () => {
      const wrapper = mountPane({ processors: [makeProcessor('p1')], expandedId: 'p1' })
      const body = wrapper.find('.env-proc-pane__body')
      expect(body.find('.processor-detail__k').text()).toBe('处理器类型')
      expect(body.find('.processor-detail__v').text()).toBe('HTTP')
      expect(wrapper.find('.env-proc-pane__item').classes()).toContain('is-open')
    })

    it('已展开行再次点击仍外发 toggle-detail 由组合式决定收起', async () => {
      const wrapper = mountPane({ processors: [makeProcessor('p1')], expandedId: 'p1' })
      await wrapper.find('.env-proc-pane__row').trigger('click')
      expect(wrapper.emitted('toggle-detail')).toHaveLength(1)
    })

    it('提取器非空时渲染只读表格', async () => {
      const wrapper = mountPane({
        processors: [
          makeProcessor('p1', {
            config: {
              testclass: 'http',
              config: {},
              extractors: [
                { enabled: true, source: 'resp', expression: '$.token', variableName: 'token', description: '—' },
              ],
            },
          }),
        ],
        expandedId: 'p1',
      })
      // el-table 的列宽计算有 50ms 防抖，列上屏需等待其完成
      await delay(ERROR_SHOW_DELAY)
      const headers = wrapper.findAll('.el-table__header th').map((item) => item.text().trim())
      expect(headers).toEqual(['来源', '表达式', '目标变量名', '描述'])
      expect(wrapper.find('.el-table__body').text()).toContain('$.token')
    })

    it('未展开行不渲染明细', () => {
      const wrapper = mountPane({ processors: [makeProcessor('p1')], expandedId: '' })
      expect(wrapper.find('.env-proc-pane__body').exists()).toBe(false)
    })
  })
})
