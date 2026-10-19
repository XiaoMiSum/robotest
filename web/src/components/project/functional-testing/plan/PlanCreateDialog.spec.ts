import { describe, expect, it } from 'vitest'
import dialogSource from './PlanCreateDialog.vue?raw'

describe('PlanCreateDialog time fields', () => {
  it('起止时间以无时区墙钟值提交', () => {
    // 00-spec 01-api.md §4.3：缺 value-format 会让 Date 被 JSON 序列化为 Z 串，
    // 服务端按服务器时区二次换算导致起止日期整体前移
    expect(dialogSource.match(/value-format="YYYY-MM-DDTHH:mm:ss"/g)).toHaveLength(2)
    expect(dialogSource).toContain('startTime: form.startTime || null')
    expect(dialogSource).toContain('endTime: form.endTime || null')
  })
})
