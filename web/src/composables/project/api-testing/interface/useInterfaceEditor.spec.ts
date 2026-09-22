import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApiComponentListItem } from '@/types'

const makeComponent = (id: string): ApiComponentListItem => ({
  id,
  scope: 'project',
  type: 'extractor',
  name: `Component ${id}`,
  description: null,
  sortOrder: 0,
  config: null,
  enabled: true,
  updatedAt: '2024-01-01T00:00:00Z',
})

const mocks = vi.hoisted(() => ({
  createInterface: vi.fn<() => Promise<string>>(),
  fetchInterfaceDetail: vi.fn<() => Promise<never>>(),
  updateInterface: vi.fn<() => Promise<boolean>>(),
  fetchProjectModuleTree: vi.fn<() => Promise<never>>(),
  fetchComponents: vi.fn<() => Promise<{ list: ApiComponentListItem[]; total: number }>>(),
  validatorFromComponent: vi.fn(),
  extractorFromComponent: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/services/project/api-testing/interface', () => ({
  createInterface: mocks.createInterface,
  fetchInterfaceDetail: mocks.fetchInterfaceDetail,
  updateInterface: mocks.updateInterface,
}))

vi.mock('@/services/project', () => ({
  fetchProjectModuleTree: mocks.fetchProjectModuleTree,
}))

vi.mock('@/services/project/api-testing/component', () => ({
  fetchComponents: mocks.fetchComponents,
}))

vi.mock('@/composables/project/api-testing/processorFormModel', () => ({
  validatorFromComponent: mocks.validatorFromComponent,
  extractorFromComponent: mocks.extractorFromComponent,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useInterfaceEditor } from './useInterfaceEditor'

function makeEmit() {
  const back = vi.fn()
  const titleUpdate = vi.fn()
  const dirtyChange = vi.fn()
  const emit = Object.assign(
    vi.fn((e: string, ...args: unknown[]) => {
      if (e === 'back') back(...args)
      else if (e === 'title-update') titleUpdate(...args)
      else if (e === 'dirty-change') dirtyChange(...args)
    }),
    { back, 'title-update': titleUpdate, 'dirty-change': dirtyChange },
  )
  return emit
}

describe('useInterfaceEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function makeSut(opts?: { createMode?: boolean; interfaceId?: string; moduleId?: string }) {
    const emit = makeEmit()
    const sut = useInterfaceEditor(
      { createMode: opts?.createMode ?? true, interfaceId: opts?.interfaceId, moduleId: opts?.moduleId },
      emit as Parameters<typeof useInterfaceEditor>[1],
    )
    return { sut, emit }
  }

  describe('initial state', () => {
    it('create mode defaults activeTab to headers', () => {
      const { sut } = makeSut({ createMode: true })
      expect(sut.activeTab.value).toBe('headers')
    })

    it('edit mode defaults activeTab to basic', () => {
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      expect(sut.activeTab.value).toBe('basic')
    })

    it('loading and saving are false initially', () => {
      const { sut } = makeSut()
      expect(sut.loading.value).toBe(false)
      expect(sut.saving.value).toBe(false)
    })

    it('form has default values', () => {
      const { sut } = makeSut()
      expect(sut.form.value.name).toBe('')
      expect(sut.form.value.method).toBe('GET')
      expect(sut.form.value.path).toBe('/')
      expect(sut.form.value.bodyType).toBe('none')
    })
  })

  describe('constants', () => {
    it('METHOD_OPTIONS contains all HTTP methods', () => {
      const { sut } = makeSut()
      expect(sut.METHOD_OPTIONS).toEqual(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'CONNECT'])
    })

    it('PROTOCOL_OPTIONS has http only', () => {
      const { sut } = makeSut()
      expect(sut.PROTOCOL_OPTIONS).toEqual([{ value: 'http', label: 'http' }])
    })

    it('ASSET_TITLE has validator and extractor keys', () => {
      const { sut } = makeSut()
      expect(sut.ASSET_TITLE.validator).toBeTruthy()
      expect(sut.ASSET_TITLE.extractor).toBeTruthy()
    })
  })

  describe('handlePathBlur', () => {
    it('warns when path does not start with /', () => {
      const { sut } = makeSut()
      sut.form.value.path = 'api/test'
      sut.handlePathBlur()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('路径需以 / 开头')
    })

    it('no warning when path starts with /', () => {
      const { sut } = makeSut()
      sut.form.value.path = '/api/test'
      sut.handlePathBlur()
      expect(mocks.ElMessage.warning).not.toHaveBeenCalled()
    })

    it('splits query params into form.params', () => {
      const { sut } = makeSut()
      sut.form.value.path = '/api?foo=bar&baz=qux'
      sut.handlePathBlur()
      expect(sut.form.value.path).toBe('/api')
      expect(sut.form.value.params.length).toBeGreaterThanOrEqual(2)
      const fooParam = sut.form.value.params.find((p: { key: string }) => p.key === 'foo')
      expect(fooParam).toBeDefined()
      expect(fooParam!.value).toBe('bar')
      expect(sut.activeTab.value).toBe('query')
    })

    it('filters out empty key entries', () => {
      const { sut } = makeSut()
      sut.form.value.path = '/api?=&foo=bar'
      sut.form.value.params = []
      sut.handlePathBlur()
      const fooParam = sut.form.value.params.find((p: { key: string }) => p.key === 'foo')
      expect(fooParam).toBeDefined()
      const emptyParam = sut.form.value.params.find((p: { key: string }) => p.key === '')
      expect(emptyParam).toBeUndefined()
    })

    it('preserves existing non-empty params', () => {
      const { sut } = makeSut()
      sut.form.value.path = '/api?key=val'
      sut.form.value.params = [{ key: 'existing', value: 'v', enabled: true }]
      sut.handlePathBlur()
      expect(sut.form.value.params.length).toBeGreaterThanOrEqual(2)
      expect(sut.form.value.params[0].key).toBe('existing')
    })

    it('empty path does not warn', () => {
      const { sut } = makeSut()
      sut.form.value.path = ''
      sut.handlePathBlur()
      expect(mocks.ElMessage.warning).not.toHaveBeenCalled()
    })
  })

  describe('addValidator / addExtractor', () => {
    it('addValidator appends a default validator', () => {
      const { sut } = makeSut()
      sut.addValidator()
      expect(sut.form.value.validators.length).toBe(1)
      expect(sut.form.value.validators[0]).toEqual({
        enabled: true,
        target: 'status_code',
        expression: '',
        condition: 'equals',
        expected: '',
      })
    })

    it('addExtractor appends a default extractor', () => {
      const { sut } = makeSut()
      sut.addExtractor()
      expect(sut.form.value.extractors.length).toBe(1)
      expect(sut.form.value.extractors[0]).toEqual({
        enabled: true,
        source: 'json_field',
        expression: '',
        variableName: '',
      })
    })

    it('multiple addValidator calls append multiple entries', () => {
      const { sut } = makeSut()
      sut.addValidator()
      sut.addValidator()
      expect(sut.form.value.validators.length).toBe(2)
    })
  })

  describe('paneValidators / paneExtractors', () => {
    it('paneValidators reflects form.validators', () => {
      const { sut } = makeSut()
      sut.form.value.validators = [{ enabled: true, target: 'status_code', expression: '', condition: 'equals', expected: '' }]
      expect(sut.paneValidators.value.length).toBe(1)
    })

    it('paneExtractors reflects form.extractors', () => {
      const { sut } = makeSut()
      sut.form.value.extractors = [{ enabled: true, source: 'json_field', expression: '', variableName: '' }]
      expect(sut.paneExtractors.value.length).toBe(1)
    })
  })

  describe('handleValidatorsUpdate / handleExtractorsUpdate', () => {
    it('handleValidatorsUpdate replaces form.validators', () => {
      const { sut } = makeSut()
      sut.handleValidatorsUpdate([{ enabled: false, target: 'regex', expression: 'x', condition: 'contains', expected: '' }])
      expect(sut.form.value.validators.length).toBe(1)
      expect(sut.form.value.validators[0].enabled).toBe(false)
    })

    it('handleExtractorsUpdate replaces form.extractors', () => {
      const { sut } = makeSut()
      sut.handleExtractorsUpdate([{ enabled: true, source: 'response_header', expression: '', variableName: 'h' }])
      expect(sut.form.value.extractors.length).toBe(1)
      expect(sut.form.value.extractors[0].variableName).toBe('h')
    })
  })

  describe('asset picker', () => {
    it('initial state is hidden with empty items', () => {
      const { sut } = makeSut()
      expect(sut.assetPickerVisible.value).toBe(false)
      expect(sut.assetPickerLoading.value).toBe(false)
      expect(sut.assetPickerItems.value).toEqual([])
      expect(sut.assetPickerKeyword.value).toBe('')
      expect(sut.assetPickerKind.value).toBe('validator')
    })

    it('openAssetPicker opens panel and loads items', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [makeComponent('c1')], total: 1 })
      const { sut } = makeSut()
      sut.openAssetPicker('extractor')
      expect(sut.assetPickerVisible.value).toBe(true)
      expect(sut.assetPickerKind.value).toBe('extractor')
      expect(sut.assetPickerKeyword.value).toBe('')
      await nextTick()
      expect(mocks.fetchComponents).toHaveBeenCalledWith({
        type: 'extractor',
        enabled: true,
        pageNo: 1,
        pageSize: 100,
        keyword: undefined,
      })
      expect(sut.assetPickerItems.value).toEqual([makeComponent('c1')])
    })

    it('loadAssetPicker with keyword sends keyword', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [], total: 0 })
      const { sut } = makeSut()
      sut.assetPickerKeyword.value = 'test'
      await sut.loadAssetPicker()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'test' }))
    })

    it('loadAssetPicker with blank keyword omits keyword', async () => {
      mocks.fetchComponents.mockResolvedValue({ list: [], total: 0 })
      const { sut } = makeSut()
      sut.assetPickerKeyword.value = '   '
      await sut.loadAssetPicker()
      expect(mocks.fetchComponents).toHaveBeenCalledWith(expect.objectContaining({ keyword: undefined }))
    })

    it('loadAssetPicker failure shows error', async () => {
      mocks.fetchComponents.mockRejectedValue(new Error('network'))
      const { sut } = makeSut()
      await sut.loadAssetPicker()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('公共组件加载失败')
      expect(sut.assetPickerLoading.value).toBe(false)
    })

    it('handleAssetPicked with validators pushes to form', () => {
      mocks.validatorFromComponent.mockReturnValue({ target: 'status_code' })
      const { sut } = makeSut()
      sut.assetPickerKind.value = 'validator'
      sut.handleAssetPicked([makeComponent('c1'), makeComponent('c2')])
      expect(sut.form.value.validators.length).toBe(2)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已引入 2 个验证器')
    })

    it('handleAssetPicked with extractors pushes to form', () => {
      mocks.extractorFromComponent.mockReturnValue({ source: 'json_field' })
      const { sut } = makeSut()
      sut.assetPickerKind.value = 'extractor'
      sut.handleAssetPicked([makeComponent('c1')])
      expect(sut.form.value.extractors.length).toBe(1)
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已引入 1 个提取器')
    })

    it('handleAssetPicked with empty array does nothing', () => {
      const { sut } = makeSut()
      sut.handleAssetPicked([])
      expect(sut.form.value.validators.length).toBe(0)
      expect(sut.form.value.extractors.length).toBe(0)
      expect(mocks.ElMessage.success).not.toHaveBeenCalled()
    })
  })

  describe('save', () => {
    it('create mode success', async () => {
      mocks.createInterface.mockResolvedValue('new-id')
      const { sut, emit } = makeSut({ createMode: true })
      sut.form.value.name = 'new interface'
      await sut.save()
      expect(mocks.createInterface).toHaveBeenCalled()
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('接口已创建')
      expect(emit).toHaveBeenCalledWith('back')
    })

    it('create mode warns on empty name', async () => {
      const { sut } = makeSut({ createMode: true })
      sut.form.value.name = ''
      await sut.save()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写接口名称')
      expect(mocks.createInterface).not.toHaveBeenCalled()
    })

    it('create mode warns on whitespace-only name', async () => {
      const { sut } = makeSut({ createMode: true })
      sut.form.value.name = '   '
      await sut.save()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('请填写接口名称')
      expect(mocks.createInterface).not.toHaveBeenCalled()
    })

    it('edit mode success', async () => {
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      mocks.fetchInterfaceDetail.mockResolvedValue({ name: 'loaded', method: 'GET', path: '/api', changeVersion: 1 } as never)
      mocks.updateInterface.mockResolvedValue(true)
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      sut.form.value.name = 'edit interface'
      await sut.save()
      expect(mocks.updateInterface).toHaveBeenCalledWith('123', expect.objectContaining({ changeVersion: 1 }))
      expect(mocks.ElMessage.success).toHaveBeenCalledWith('已保存')
      vi.unstubAllGlobals()
    })

    it('edit mode conflict triggers reload', async () => {
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      mocks.fetchInterfaceDetail.mockResolvedValue({ name: 'loaded', method: 'GET', path: '/api', changeVersion: 1 } as never)
      mocks.updateInterface.mockRejectedValue(new Error('版本冲突 7105'))
      mocks.ElMessageBox.confirm.mockResolvedValue(undefined)
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      sut.form.value.name = 'edit'
      await sut.save()
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      vi.unstubAllGlobals()
    })

    it('non-conflict error shows error message', async () => {
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      mocks.fetchInterfaceDetail.mockResolvedValue({ name: 'loaded', method: 'GET', path: '/api', changeVersion: 1 } as never)
      mocks.updateInterface.mockRejectedValue(new Error('save failed'))
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      sut.form.value.name = 'edit'
      await sut.save()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('save failed')
      vi.unstubAllGlobals()
    })

    it('non-Error exception shows generic error', async () => {
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      mocks.fetchInterfaceDetail.mockResolvedValue({ name: 'loaded', method: 'GET', path: '/api', changeVersion: 1 } as never)
      mocks.updateInterface.mockRejectedValue('string err')
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      sut.form.value.name = 'edit'
      await sut.save()
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('保存失败')
      vi.unstubAllGlobals()
    })

    it('toCreatePayload error shows warning', async () => {
      const interfacesModel = await import('@/composables/project/api-testing/interface/interfacesModel')
      const spy = vi.spyOn(interfacesModel, 'toCreatePayload')
      spy.mockReturnValue({ req: {} as never, error: 'missing field' })
      const { sut } = makeSut({ createMode: true })
      sut.form.value.name = 'test'
      await sut.save()
      expect(mocks.ElMessage.warning).toHaveBeenCalledWith('missing field')
      expect(mocks.createInterface).not.toHaveBeenCalled()
      spy.mockRestore()
    })

    it('does not save while already saving', async () => {
      const { sut } = makeSut({ createMode: true })
      sut.form.value.name = 'test'
      sut.saving.value = true
      await sut.save()
      expect(mocks.createInterface).not.toHaveBeenCalled()
    })
  })

  describe('module tree', () => {
    it('moduleOptions starts empty', () => {
      const { sut } = makeSut()
      expect(sut.moduleOptions.value).toEqual([])
    })
  })

  describe('loadDetail', () => {
    it('create mode does not call fetchInterfaceDetail', async () => {
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      const { sut } = makeSut({ createMode: true })
      sut.mount()
      await nextTick()
      expect(mocks.fetchInterfaceDetail).not.toHaveBeenCalled()
      vi.unstubAllGlobals()
    })

    it('edit mode calls fetchInterfaceDetail', async () => {
      mocks.fetchInterfaceDetail.mockResolvedValue({ name: 'loaded', changeVersion: 1 } as never)
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      const { sut, emit } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      expect(mocks.fetchInterfaceDetail).toHaveBeenCalledWith('123')
      expect(emit).toHaveBeenCalledWith('title-update', expect.any(String))
      vi.unstubAllGlobals()
    })

    it('edit mode load failure shows error', async () => {
      mocks.fetchInterfaceDetail.mockRejectedValue(new Error('load fail'))
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('load fail')
      vi.unstubAllGlobals()
    })

    it('edit mode non-Error rejection shows generic message', async () => {
      mocks.fetchInterfaceDetail.mockRejectedValue('string err')
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      const { sut } = makeSut({ createMode: false, interfaceId: '123' })
      sut.mount()
      await vi.waitFor(() => { expect(sut.loading.value).toBe(false) })
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('接口详情加载失败')
      vi.unstubAllGlobals()
    })

    it('create mode with moduleId sets it on form', async () => {
      vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn() })
      const { sut } = makeSut({ createMode: true, moduleId: 'mod-1' })
      sut.mount()
      await nextTick()
      expect(sut.form.value.moduleId).toBe('mod-1')
      vi.unstubAllGlobals()
    })
  })

  describe('name watch', () => {
    it('name change triggers title-update', async () => {
      const { sut, emit } = makeSut({ createMode: true })
      sut.form.value.name = 'new name'
      await nextTick()
      expect(emit).toHaveBeenCalledWith('title-update', 'new name')
    })

    it('empty name triggers title-update with default for create mode', async () => {
      const { sut, emit } = makeSut({ createMode: true })
      sut.form.value.name = 'something'
      await nextTick()
      sut.form.value.name = ''
      await nextTick()
      expect(emit).toHaveBeenCalledWith('title-update', '新接口')
    })

    it('edit mode empty name triggers empty string', async () => {
      const { sut, emit } = makeSut({ createMode: false, interfaceId: '123' })
      sut.form.value.name = 'something'
      await nextTick()
      sut.form.value.name = ''
      await nextTick()
      expect(emit).toHaveBeenCalledWith('title-update', '')
    })
  })

  describe('drag divider', () => {
    it('containerRef starts undefined', () => {
      const { sut } = makeSut()
      expect(sut.containerRef.value).toBeUndefined()
    })

    it('requestHeight starts at 50', () => {
      const { sut } = makeSut()
      expect(sut.requestHeight.value).toBe(50)
    })

    it('onDividerMouseDown calls preventDefault', () => {
      vi.stubGlobal('document', { addEventListener: vi.fn(), removeEventListener: vi.fn(), body: { style: {} } })
      const { sut } = makeSut()
      const e = { preventDefault: vi.fn() } as unknown as MouseEvent
      sut.onDividerMouseDown(e)
      expect(e.preventDefault).toHaveBeenCalled()
      vi.unstubAllGlobals()
    })
  })
})
