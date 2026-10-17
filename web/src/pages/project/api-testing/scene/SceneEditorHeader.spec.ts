import { describe, expect, it } from 'vitest'
import headerSource from './SceneEditorHeader.vue?raw'
import typeSource from '../../../../types/project/api-testing/scene.ts?raw'

// 执行入口文案（需求 F1.3 / 交互 26 §2、10 §1.9）：CI 与平台统一为「运行」
describe('SceneEditorHeader 执行入口文案', () => {
  it('按钮文案固定为「运行」且三处入口（只读/创建/编辑）共用同一常量', () => {
    expect(headerSource).toContain("const runLabel = '▶ 运行'")
    expect(headerSource.match(/\{\{ runLabel \}\}/g)?.length).toBe(3)
    expect(headerSource).not.toContain('流水线运行')
  })

  it('场景详情类型登记派生字段 engine', () => {
    expect(typeSource).toContain('engine?: string | null')
  })
})
