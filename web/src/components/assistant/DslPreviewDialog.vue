<script setup lang="ts">
import { computed } from 'vue'
import type { AiPriority } from '@/types'
import { TYPE_LABELS, type DslPlan } from '@/components/project/minder/ai/dslRunner'

/**
 * 命中预览确认弹窗（全局智能助手交互设计 6.2）：
 * 纯展示 + emit——命中数量、各命令命中清单、「将跳过」清单及原因；
 * 确认/取消由宿主（消息流）负责执行 DSL 与追加本地提示，本组件不触碰编辑内核。
 */
const props = defineProps<{
  plan: DslPlan | null
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const PRIORITY_LABELS: Record<AiPriority, string> = {
  P0: 'P0（最高）',
  P1: 'P1（高）',
  P2: 'P2（中）',
  P3: 'P3（低）',
}

interface CommandView {
  summary: string
  hits: string[]
  skipped: { title: string; reason: string }[]
}

/** 命令清单：动作摘要（move 展示解析后的目标父节点标题，比原始 @selected 占位更可读）+ 命中/跳过明细 */
const commandViews = computed<CommandView[]>(() => {
  if (!props.plan) return []
  return props.plan.commands.map((entry) => {
    const { command } = entry
    let summary = ''
    switch (command.action.type) {
      case 'mark_type':
        summary = `标记为「${TYPE_LABELS[command.action.params.nodeType]}」`
        break
      case 'mark_priority':
        summary = `设置优先级 ${PRIORITY_LABELS[command.action.params.priority]}`
        break
      case 'highlight':
        summary = '高亮节点'
        break
      case 'move':
        summary = `移动到「${entry.targetParent?.data.text ?? command.action.params.targetParentTitle}」下`
        break
      case 'add_child':
        summary = `添加 ${command.action.params.nodes.length} 个子节点`
        break
    }
    return {
      summary,
      hits: entry.execute.map((node) => String(node.data.text ?? '')),
      skipped: entry.skipped.map((item) => ({
        title: String(item.node.data.text ?? ''),
        reason: item.reason,
      })),
    }
  })
})

/** 全部命中均被跳过（无实际可执行项）时禁用确认 */
const canConfirm = computed(() => (props.plan?.commands.some((c) => c.execute.length > 0) ?? false))

function handleConfirm(): void {
  emit('confirm')
  visible.value = false
}

function handleCancel(): void {
  emit('cancel')
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" title="命中预览确认" width="520px" :close-on-click-modal="false">
    <div v-if="plan" class="dsl-preview">
      <div class="dsl-preview__summary">
        命中 <b>{{ plan.totalHits }}</b> 个节点
        <span v-if="plan.totalSkipped" class="dsl-preview__skipped-count">，将跳过 {{ plan.totalSkipped }} 个</span>
      </div>

      <div v-for="(view, index) in commandViews" :key="index" class="dsl-preview__command">
        <div class="dsl-preview__command-title">{{ index + 1 }}. {{ view.summary }}</div>
        <ul v-if="view.hits.length" class="dsl-preview__hits">
          <li v-for="(title, hitIndex) in view.hits" :key="hitIndex">{{ title }}</li>
        </ul>
        <div v-else class="dsl-preview__no-hit">无匹配节点</div>

        <div v-if="view.skipped.length" class="dsl-preview__skipped">
          <div class="dsl-preview__skipped-label">将跳过</div>
          <ul>
            <li v-for="(item, skipIndex) in view.skipped" :key="skipIndex">
              <span class="dsl-preview__skipped-title">{{ item.title }}</span>
              <span class="dsl-preview__skipped-reason">{{ item.reason }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :disabled="!canConfirm" @click="handleConfirm">确认执行</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.dsl-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 60vh;
  overflow: auto;
}

.dsl-preview__summary {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.dsl-preview__skipped-count {
  color: var(--el-color-warning);
}

.dsl-preview__command {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px 12px;
}

.dsl-preview__command-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 6px;
}

.dsl-preview__hits {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.dsl-preview__no-hit {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.dsl-preview__skipped {
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  background: var(--el-color-warning-light-9);
}

.dsl-preview__skipped-label {
  font-size: 12px;
  color: var(--el-color-warning);
}

.dsl-preview__skipped ul {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
}

.dsl-preview__skipped-title {
  color: var(--el-text-color-primary);
  margin-right: 8px;
}

.dsl-preview__skipped-reason {
  color: var(--el-text-color-secondary);
}
</style>
