import { beforeEach, describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({
  router: { push: vi.fn() },
  importRequirement: vi.fn(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRouter: () => mocks.router,
}))

vi.mock('@/services/project', () => ({
  importRequirement: mocks.importRequirement,
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.ElMessage,
  ElMessageBox: mocks.ElMessageBox,
}))

import { useRequirementImport } from './useRequirementImport'

function makeFile(name: string, size = 1024): File {
  const file = new File(['x'], name, { type: 'application/octet-stream' })
  // jsdom 不支持按内容推导大小，直接覆写以驱动超限分支
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function inProgressError(): Error & { code: number } {
  const err = new Error('存在进行中的导入任务') as Error & { code: number }
  err.code = 1000018013
  return err
}

describe('useRequirementImport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.importRequirement.mockResolvedValue({ taskId: 't1', splitRecordId: 's1', status: 'pending' })
    mocks.ElMessageBox.confirm.mockResolvedValue('confirm')
  })

  describe('pickFile 预校验', () => {
    it('白名单外的扩展名拒绝且不发请求', () => {
      const s = useRequirementImport()
      expect(s.pickFile(makeFile('需求.doc'))).toBe(false)
      expect(s.selectedFile.value).toBeNull()
      expect(s.fileError.value).toContain('不支持的文件类型')
    })

    it('超过 20MB 拒绝', () => {
      const s = useRequirementImport()
      expect(s.pickFile(makeFile('需求.md', 20 * 1024 * 1024 + 1))).toBe(false)
      expect(s.fileError.value).toContain('20MB')
    })

    it('空文件拒绝', () => {
      const s = useRequirementImport()
      expect(s.pickFile(makeFile('需求.md', 0))).toBe(false)
      expect(s.fileError.value).toContain('为空')
    })

    it('合法文件通过并清空错误', () => {
      const s = useRequirementImport()
      s.pickFile(makeFile('bad.exe'))
      expect(s.pickFile(makeFile('需求.md'))).toBe(true)
      expect(s.selectedFile.value?.name).toBe('需求.md')
      expect(s.fileError.value).toBe('')
    })
  })

  describe('submit', () => {
    it('未选文件时不发请求', async () => {
      const s = useRequirementImport()
      await expect(s.submit()).resolves.toBe(false)
      expect(mocks.importRequirement).not.toHaveBeenCalled()
      expect(s.fileError.value).toContain('请先选择')
    })

    it('提交成功关闭流程并跳转任务详情页', async () => {
      const s = useRequirementImport()
      s.pickFile(makeFile('需求.docx'))
      await expect(s.submit()).resolves.toBe(true)
      expect(mocks.importRequirement).toHaveBeenCalledWith(s.selectedFile.value)
      expect(mocks.ElMessage.success).toHaveBeenCalled()
      expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks/t1')
      expect(s.submitting.value).toBe(false)
    })

    it('1000018013 引导前往任务中心', async () => {
      mocks.importRequirement.mockRejectedValueOnce(inProgressError())
      const s = useRequirementImport()
      s.pickFile(makeFile('需求.md'))
      await expect(s.submit()).resolves.toBe(false)
      expect(mocks.ElMessageBox.confirm).toHaveBeenCalled()
      expect(mocks.router.push).toHaveBeenCalledWith('/workspace/projects/ai/tasks')
      expect(mocks.ElMessage.error).not.toHaveBeenCalled()
    })

    it('1000018013 选择留在本页时不跳转', async () => {
      mocks.importRequirement.mockRejectedValueOnce(inProgressError())
      mocks.ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
      const s = useRequirementImport()
      s.pickFile(makeFile('需求.md'))
      await expect(s.submit()).resolves.toBe(false)
      expect(mocks.router.push).not.toHaveBeenCalled()
    })

    it('其他错误保留文件并提示', async () => {
      mocks.importRequirement.mockRejectedValueOnce(new Error('导入文件类型不支持'))
      const s = useRequirementImport()
      s.pickFile(makeFile('需求.md'))
      await expect(s.submit()).resolves.toBe(false)
      expect(mocks.ElMessage.error).toHaveBeenCalledWith('导入文件类型不支持')
      expect(s.selectedFile.value).not.toBeNull()
      expect(s.submitting.value).toBe(false)
    })
  })

  it('reset 清空选中与错误', () => {
    const s = useRequirementImport()
    s.pickFile(makeFile('需求.md'))
    s.reset()
    expect(s.selectedFile.value).toBeNull()
    expect(s.fileError.value).toBe('')
  })
})
