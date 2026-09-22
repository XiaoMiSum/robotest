/**
 * 脑图布局模板管理
 * core 原生 6 模板；template 命令触发 contentchange，自动搭上 Yjs 同步与落库管道
 */
import { ref, computed } from 'vue'

export function useMindmapLayout(exec: (cmd: string, ...args: unknown[]) => void) {
  const templates = [
    { name: 'default', label: '思维导图' },
    { name: 'right', label: '右侧分布' },
    { name: 'structure', label: '组织结构' },
    { name: 'filetree', label: '目录' },
    { name: 'fish-bone', label: '鱼骨图' },
    { name: 'tianpan', label: '天盘' },
  ]

  const currentTemplate = ref('default')
  const currentTemplateLabel = computed(
    () => templates.find((t) => t.name === currentTemplate.value)?.label ?? currentTemplate.value,
  )

  function switchTemplate(name: string) {
    exec('template', name)
  }

  function tidyLayout() {
    exec('resetlayout')
  }

  function updateTemplate(template: string) {
    currentTemplate.value = template
  }

  return {
    templates,
    currentTemplate,
    currentTemplateLabel,
    switchTemplate,
    tidyLayout,
    updateTemplate,
  }
}
