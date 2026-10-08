<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Check, Paperclip, Search } from '@element-plus/icons-vue'
import { useTraceNodePicker, type TraceNodeOption } from '@/composables/project/trace/useTraceNodePicker'
import type { AiAssistantEntityRef, TraceNodeType } from '@/types'

interface PickerType {
  type: TraceNodeType
  label: string
}

/** 合法引用类型（详设 3.4）的可选子集：test_case 无全局列表接口，由链路树上下文提供 */
const PICKER_TYPES: PickerType[] = [
  { type: 'requirement', label: '需求' },
  { type: 'mindmap_document', label: '用例文档' },
  { type: 'module', label: '模块' },
  { type: 'test_review', label: '评审' },
  { type: 'test_plan', label: '计划' },
]

const KEYWORD_DEBOUNCE_MS = 300
/** 与触发按钮 aria-label 保持一致，用于弹层外点击的判定 */
const TRIGGER_SELECTOR = '[aria-label="添加上下文附件"]'

const props = defineProps<{
  /** 当前待发附件：重复项直接关闭弹层，避免重复携带 */
  selected: AiAssistantEntityRef[]
  disabled: boolean
}>()

const emit = defineEmits<{ add: [ref: AiAssistantEntityRef] }>()

const open = ref(false)
const activeType = ref<TraceNodeType>('requirement')
const keyword = ref('')
const contentEl = ref<HTMLElement | null>(null)
const { options, loading, load } = useTraceNodePicker()

let keywordTimer: ReturnType<typeof setTimeout> | null = null

function refresh(): void {
  void load(activeType.value, keyword.value)
}

/** 弹层外点击收起：触发钮交由自身 click 切换，避免按下与点击各处理一次 */
function onDocMousedown(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (!target) return
  if (contentEl.value?.contains(target)) return
  if (target.closest(TRIGGER_SELECTOR)) return
  open.value = false
}

// 打开即按当前类型与关键词取候选并监听外部点击；关键词与类型变更走即时 / 防抖刷新
watch(open, (visible) => {
  if (visible) {
    document.addEventListener('mousedown', onDocMousedown)
    refresh()
  } else {
    document.removeEventListener('mousedown', onDocMousedown)
  }
})
watch(activeType, refresh)
watch(keyword, () => {
  if (!open.value) return
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    keywordTimer = null
    refresh()
  }, KEYWORD_DEBOUNCE_MS)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMousedown)
  if (keywordTimer) clearTimeout(keywordTimer)
})

function toggleOpen(): void {
  open.value = !open.value
}

function isSelected(option: TraceNodeOption): boolean {
  return props.selected.some(
    (item) => item.entityType === activeType.value && item.entityId === option.id,
  )
}

function pick(option: TraceNodeOption): void {
  if (!isSelected(option)) {
    emit('add', {
      entityType: activeType.value,
      entityId: option.id,
      entityTitle: option.label,
    })
  }
  open.value = false
}
</script>

<template>
  <!-- 绑定 :visible 后 EP 进入受控模式（trigger 处理器全部短路），开合完全由本组件控制 -->
  <el-popover :visible="open" placement="top-start" :width="320">
    <template #reference>
      <!-- aria-expanded 会被 popover 对 reference 的 clone 覆盖，展开态改用类名标记 -->
      <el-button
        aria-label="添加上下文附件"
        :class="{ 'ai-context-picker__trigger--open': open }"
        :icon="Paperclip"
        :disabled="disabled"
        @click="toggleOpen"
      />
    </template>
    <div ref="contentEl" class="ai-context-picker">
      <el-radio-group v-model="activeType" size="small" class="ai-context-picker__types">
        <el-radio-button v-for="item in PICKER_TYPES" :key="item.type" :value="item.type">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        size="small"
        clearable
        placeholder="检索候选…"
        class="ai-context-picker__keyword"
        :prefix-icon="Search"
      />
      <div class="ai-context-picker__list">
        <p v-if="loading" class="ai-context-picker__hint">加载中…</p>
        <p v-else-if="options.length === 0" class="ai-context-picker__hint">无匹配候选</p>
        <template v-else>
          <button
            v-for="option in options"
            :key="option.id"
            type="button"
            class="ai-context-picker__option"
            :class="{ 'ai-context-picker__option--selected': isSelected(option) }"
            @click="pick(option)"
          >
            <span class="ai-context-picker__label">{{ option.label }}</span>
            <el-icon v-if="isSelected(option)" :size="14"><Check /></el-icon>
          </button>
        </template>
      </div>
    </div>
  </el-popover>
</template>

<style scoped lang="scss">
.ai-context-picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);

  &__types {
    align-self: flex-start;
  }

  &__keyword {
    width: 100%;
  }

  &__list {
    max-height: 180px;
    overflow-y: auto;
  }

  &__hint {
    margin: 0;
    padding: var(--space-sm) 0;
    color: var(--color-neutral-400);
    font-size: var(--font-size-xs);
    text-align: center;
  }

  &__option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-xs);
    width: 100%;
    padding: 6px var(--space-xs);
    border: none;
    border-radius: var(--radius-sm);
    background: none;
    color: var(--color-neutral-800);
    font-size: 13px;
    text-align: left;
    cursor: pointer;

    &:hover {
      background: var(--color-neutral-50);
    }

    &--selected {
      color: var(--color-primary-500);
    }
  }

  &__label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
