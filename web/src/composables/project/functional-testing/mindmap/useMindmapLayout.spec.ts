import { describe, expect, it, vi } from 'vitest'
import { useMindmapLayout } from './useMindmapLayout'

describe('useMindmapLayout', () => {
  function makeSut() {
    const exec = vi.fn()
    return { sut: useMindmapLayout(exec), exec }
  }

  describe('初始状态', () => {
    it('包含 6 个模板', () => {
      const { sut } = makeSut()
      expect(sut.templates).toHaveLength(6)
    })

    it('currentTemplate 默认为 default', () => {
      const { sut } = makeSut()
      expect(sut.currentTemplate.value).toBe('default')
    })

    it('currentTemplateLabel 默认为思维导图', () => {
      const { sut } = makeSut()
      expect(sut.currentTemplateLabel.value).toBe('思维导图')
    })
  })

  describe('currentTemplateLabel', () => {
    it('匹配已知模板时返回对应 label', () => {
      const { sut } = makeSut()
      sut.currentTemplate.value = 'fish-bone'
      expect(sut.currentTemplateLabel.value).toBe('鱼骨图')
    })

    it('未知模板名称时回退为模板名本身', () => {
      const { sut } = makeSut()
      sut.currentTemplate.value = 'unknown'
      expect(sut.currentTemplateLabel.value).toBe('unknown')
    })
  })

  describe('switchTemplate', () => {
    it('调用 exec 传递 template 命令', () => {
      const { sut, exec } = makeSut()
      sut.switchTemplate('structure')
      expect(exec).toHaveBeenCalledWith('template', 'structure')
    })
  })

  describe('tidyLayout', () => {
    it('调用 exec 传递 resetlayout 命令', () => {
      const { sut, exec } = makeSut()
      sut.tidyLayout()
      expect(exec).toHaveBeenCalledWith('resetlayout')
    })
  })

  describe('updateTemplate', () => {
    it('更新 currentTemplate 值', () => {
      const { sut } = makeSut()
      sut.updateTemplate('tianpan')
      expect(sut.currentTemplate.value).toBe('tianpan')
    })

    it('更新后 currentTemplateLabel 联动变化', () => {
      const { sut } = makeSut()
      sut.updateTemplate('right')
      expect(sut.currentTemplateLabel.value).toBe('右侧分布')
    })
  })
})
