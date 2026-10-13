import { describe, expect, it } from 'vitest'
import headerSource from './SceneEditorHeader.vue?raw'
import pageSource from './SceneEditorPage.vue?raw'
import typeSource from '../../../../types/project/api-testing/scene.ts?raw'

// 执行入口文案随引擎派生（需求 F1.3 / 交互 26 §2、10 §1.9）：gitlab_ci 为「流水线运行」，其余为「运行场景」
describe('SceneEditorHeader 执行入口文案', () => {
  it('按钮文案按 engine 派生且两处入口共用同一派生值', () => {
    expect(headerSource).toContain("props.engine === 'gitlab_ci' ? '▶ 流水线运行' : '▶ 运行场景'")
    expect(headerSource.match(/\{\{ runLabel \}\}/g)?.length).toBe(2)
    expect(headerSource).not.toContain('▶ 运行场景</el-button>')
  })

  it('创建态无场景详情时文案回退平台引擎', () => {
    expect(pageSource).toContain(':engine="editor.detail.value?.engine ?? null"')
  })

  it('场景详情类型登记派生字段 engine', () => {
    expect(typeSource).toContain('engine?: string | null')
  })
})
