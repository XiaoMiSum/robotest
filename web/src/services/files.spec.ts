// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  del: vi.fn(),
  apiGet: vi.fn(),
}))

vi.mock('@/services', () => ({
  default: { get: mocks.apiGet },
  get: mocks.get,
  del: mocks.del,
}))

import { deleteFile, downloadFile, fetchFileAccessUrl, fetchFiles } from './files'

describe('文件管理服务（详设 4.1）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // jsdom 未实现 createObjectURL：按需桩接，覆盖下载触发链路
    URL.createObjectURL = vi.fn(() => 'blob:mock')
    URL.revokeObjectURL = vi.fn()
  })

  it('fetchFiles 组装分页与文件名过滤参数', async () => {
    mocks.get.mockResolvedValue({ list: [], total: 0 })

    await fetchFiles({ fileName: 'SRS', pageNo: 1, pageSize: 20 })

    expect(mocks.get).toHaveBeenCalledWith('/files', {
      fileName: 'SRS',
      pageNo: 1,
      pageSize: 20,
    })
  })

  it('fetchFileAccessUrl 命中换签端点并透传时效', async () => {
    mocks.get.mockResolvedValue({ url: 'http://localhost:9000/robotest/objects/x?sig=1', expiresIn: 900 })

    const result = await fetchFileAccessUrl('id-1')

    expect(mocks.get).toHaveBeenCalledWith('/files/id-1/access-url')
    expect(result.expiresIn).toBe(900)
  })

  it('deleteFile 走 DELETE 带资源 ID', async () => {
    mocks.del.mockResolvedValue(undefined)

    await deleteFile('id-1')

    expect(mocks.del).toHaveBeenCalledWith('/files/id-1')
  })

  it('downloadFile 以 blob 响应触发浏览器保存', async () => {
    mocks.apiGet.mockResolvedValue(new Blob(['content'], { type: 'text/plain' }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    await downloadFile('id-1', '设计说明.md')

    expect(mocks.apiGet).toHaveBeenCalledWith('/files/id-1/download', { responseType: 'blob' })
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(click).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
    click.mockRestore()
  })
})
