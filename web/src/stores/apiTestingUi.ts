import { defineStore } from 'pinia'
import type { ApiDebugKeyValue, ApiInterfaceItem } from '@/types'

/** 列表行「调试」联动的请求预填快照（接口管理 → 快速调试跨子页传递） */
export interface PendingDebugRequest {
  name: string
  method: string
  path: string
  headers?: ApiDebugKeyValue[] | null
  params?: ApiDebugKeyValue[] | null
  body?: { type?: string; content?: unknown } | null
  source?: Pick<ApiInterfaceItem, 'id' | 'name'>
}

export const useApiTestingUiStore = defineStore('apiTestingUi', {
  state: () => ({
    pendingDebugRequest: null as PendingDebugRequest | null,
  }),
  actions: {
    /** 一次性消费：读取后即清除，避免重复注入 */
    consumePendingRequest(): PendingDebugRequest | null {
      const pending = this.pendingDebugRequest
      this.pendingDebugRequest = null
      return pending
    },
  },
})
