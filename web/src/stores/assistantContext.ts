import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AiMinderCommand, AiPageContext } from '@/types'
import type { DslApplyResult, DslPlan, DslPlanResult } from '@/components/project/minder/ai/dslRunner'

/** 与 stores/auth.ts PROJECT_KEY 同源：当前项目标识（X-Active-Project 请求头亦取自该键，见 services/index.ts） */
const ACTIVE_PROJECT_KEY = 'robotest_active_project'

/**
 * DSL 执行宿主（全局智能助手详细设计 4.3/5.1）：
 * 脑图页挂载后注册 buildPlan/apply 闭包（内部持有 minder 实例，minder 仅存在于脑图组件内），
 * 助手面板消费——minder_commands 帧预览/执行均经此桥，不跨组件直接传递编辑器实例。
 */
export interface DslHost {
  documentId: string
  /** 构建命中预览计划（解析 + 命中 + 合法性过滤，4.4.1/4.4.2） */
  buildPlan(commands: AiMinderCommand[], selectedNodeId: string | null): DslPlanResult
  /** 确认后批量执行（单撤销组） */
  apply(plan: DslPlan): DslApplyResult
}

/**
 * 页面上下文桥（全局智能助手详细设计 4.4）：
 * 脑图编辑页 onMounted/onUnmounted 注册/注销 {projectId, documentId, selectedNodeId}，
 * 选中节点变化时更新；助手面板发送消息时经 buildPageContext() 注入 pageContext。
 * 非脑图页仅注入当前 projectId（若在项目内）。
 */
export const useAssistantContextStore = defineStore('assistantContext', () => {
  const documentId = ref<string | null>(null)
  const selectedNodeId = ref<string | null>(null)
  /** 脑图页注册的 DSL 执行宿主；非脑图页为空（收到 minder_commands 时按「已离开文档页」处理，5.2） */
  const dslHost = ref<DslHost | null>(null)

  /** 脑图编辑页挂载或切换文档时注册（selectionchange 前无选中节点） */
  function registerMindMap(docId: string): void {
    documentId.value = docId
    selectedNodeId.value = null
  }

  /** 选中节点变化时更新（selectionchange 回调） */
  function setSelectedNode(nodeId: string | null): void {
    selectedNodeId.value = nodeId
  }

  /** 脑图编辑页卸载时注销 */
  function unregisterMindMap(): void {
    documentId.value = null
    selectedNodeId.value = null
  }

  /** 脑图编辑页挂载/切换文档后注册 DSL 执行宿主（minder 就绪时），卸载时随 unregisterMindMap 一并注销 */
  function registerDslHost(host: DslHost): void {
    dslHost.value = host
  }

  function unregisterDslHost(): void {
    dslHost.value = null
  }

  /**
   * 组装发送消息的 pageContext：
   * projectId 直接读 localStorage（与 X-Active-Project 请求头同源，保证后端校验一致）；
   * documentId/selectedNodeId 仅脑图页存在时注入，且 selectedNodeId 显式带 null
   * （system 提示据此消歧"当前用例"指代，见 4.4）。
   */
  function buildPageContext(): AiPageContext {
    const context: AiPageContext = {}
    const projectId = localStorage.getItem(ACTIVE_PROJECT_KEY)
    if (projectId) context.projectId = projectId
    if (documentId.value) {
      context.documentId = documentId.value
      context.selectedNodeId = selectedNodeId.value
    }
    return context
  }

  return {
    documentId,
    selectedNodeId,
    dslHost,
    registerMindMap,
    setSelectedNode,
    unregisterMindMap,
    registerDslHost,
    unregisterDslHost,
    buildPageContext,
  }
})
