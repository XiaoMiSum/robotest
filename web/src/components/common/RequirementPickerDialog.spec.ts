// @vitest-environment jsdom
/* eslint-disable vue/one-component-per-file -- 测试桩组件与被测组件同文件，便于就近喂行 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { cloneVNode, defineComponent, h, nextTick, type PropType } from 'vue'
import ElementPlus from 'element-plus'
import type { RequirementListItem } from '@/types'
import RequirementPickerDialog from './RequirementPickerDialog.vue'
import dialogSource from './RequirementPickerDialog.vue?raw'

const mocks = vi.hoisted(() => ({
  fetchRequirements: vi.fn(),
  getRequirement: vi.fn(),
}))

vi.mock('@/services/project', () => ({
  fetchRequirements: mocks.fetchRequirements,
  getRequirement: mocks.getRequirement,
}))

function item(id: string, code: string, title: string): RequirementListItem {
  return {
    id,
    code,
    title,
    moduleId: null,
    moduleName: null,
    systemVersion: null,
    status: 'confirmed',
    priority: null,
    ownerId: null,
    ownerName: null,
    source: 'manual',
    updatedAt: '2026-10-04T00:00:00Z',
  }
}

const rows = [item('r1', 'REQ-001', '登录'), item('r2', 'REQ-002', '登出')]

// el-table 在 jsdom 中无布局尺寸，不渲染行；用同步 stub 把每行喂给列作用域插槽
const ColumnStub = defineComponent({
  props: { stubRow: { type: Object as PropType<RequirementListItem>, default: null } },
  setup(props, { slots }) {
    return () => h('div', { class: 'stub-col' }, slots.default?.({ row: props.stubRow }))
  },
})
const TableStub = defineComponent({
  props: { data: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        {},
        (props.data as RequirementListItem[]).flatMap((row) =>
          (slots.default?.() ?? []).map((column, index) =>
            cloneVNode(column, { stubRow: row, key: `${row.id}-${index}` }),
          ),
        ),
      )
  },
})
const SearchStub = defineComponent({ setup: () => () => h('span') })

let wrapper: ReturnType<typeof mount> | null = null

function openDialog(props: Record<string, unknown> = {}) {
  wrapper = mount(RequirementPickerDialog, {
    props: { modelValue: false, initial: [], ...props },
    global: {
      plugins: [ElementPlus],
      components: { Search: SearchStub },
      stubs: { 'el-table': TableStub, 'el-table-column': ColumnStub },
    },
  })
  return wrapper.setProps({ modelValue: true })
}

function clickConfirm(): void {
  const buttons = Array.from(document.body.querySelectorAll('.el-dialog__footer button'))
  const confirm = buttons.find((button) => button.textContent?.includes('确定'))
  expect(confirm).toBeDefined()
  ;(confirm as HTMLButtonElement).click()
}

describe('RequirementPickerDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.clearAllMocks()
    mocks.fetchRequirements.mockResolvedValue({ list: rows, total: rows.length })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('默认多选渲染复选框，确认可提交多项', async () => {
    const onConfirm = vi.fn()
    await openDialog({ onConfirm })

    expect(document.body.querySelectorAll('.el-radio')).toHaveLength(0)
    expect(document.body.querySelectorAll('.el-checkbox')).toHaveLength(rows.length)

    document.body.querySelectorAll('.el-checkbox input').forEach((input) => {
      const checkbox = input as HTMLInputElement
      checkbox.checked = true
      checkbox.dispatchEvent(new Event('change', { bubbles: true }))
    })
    await nextTick()
    clickConfirm()

    expect(onConfirm).toHaveBeenCalledWith(
      ['r1', 'r2'],
      [expect.objectContaining({ id: 'r1' }), expect.objectContaining({ id: 'r2' })],
    )
  })

  it('multiple=false 渲染单选框，并只回填与提交首项既有需求', async () => {
    const onConfirm = vi.fn()
    await openDialog({
      multiple: false,
      allowEmpty: true,
      initial: [
        { id: 'r1', code: 'REQ-001', title: '登录' },
        { id: 'r2', code: 'REQ-002', title: '登出' },
      ],
      onConfirm,
    })

    expect(document.body.querySelectorAll('.el-checkbox')).toHaveLength(0)
    expect(document.body.querySelectorAll('.el-radio')).toHaveLength(rows.length)
    expect(document.body.textContent).toContain('已选 1 项')

    clickConfirm()
    expect(onConfirm).toHaveBeenCalledWith(['r1'], [expect.objectContaining({ id: 'r1' })])
  })

  it('需求明细弹窗宽度按共享组件统一加倍', () => {
    expect(dialogSource).toContain(':width="840"')
  })
})
