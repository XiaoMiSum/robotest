import { describe, expect, it } from 'vitest'
import pageSource from './StepDebugResultDialog.vue?raw'

describe('StepDebugResultDialog 状态三重编码', () => {
  it('步骤状态以中文文案与圆点形状呈现，不暴露原始英文枚举', () => {
    expect(pageSource).toContain("success: '成功'")
    expect(pageSource).toContain("failed: '失败'")
    expect(pageSource).toContain('{{ statusLabel(result.stepResult.status) }}')
    expect(pageSource).toContain('class="debug-result__dot"')
  })

  it('验证器结果与步骤状态使用一致的圆点形状', () => {
    expect(pageSource).toContain("{{ row.passed ? '通过' : '失败' }}")
    expect(pageSource).toContain('class="debug-result__dot"')
    expect(pageSource).toContain('background: currentColor')
  })
})
