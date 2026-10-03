import { get } from '@/services'
import type { AiStatus } from '@/types'

/** 业务端 AI 可用性（详设 3.2）：登录即可，路径不属业务域，请求上下文按 scope=none 不带头 */
export function fetchAiStatus(): Promise<AiStatus> {
  return get('/ai/status')
}
