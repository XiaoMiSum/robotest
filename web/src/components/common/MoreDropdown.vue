<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import type { ElDropdown } from 'element-plus'

export interface MoreAction {
  key: string
  label: string
  disabled?: boolean
  divided?: boolean
  tone?: 'default' | 'warning' | 'danger'
}

defineOptions({ inheritAttrs: false })

const props = withDefaults(
  defineProps<{
    actions: MoreAction[]
    placement?: 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end' | 'bottom' | 'top'
  }>(),
  { placement: 'bottom-end' },
)

const emit = defineEmits<{ command: [key: string] }>()

const dropdownRef = ref<InstanceType<typeof ElDropdown> | null>(null)
const triggerRef = ref<HTMLElement | null>(null)

const instanceId = useId()
const popperClass = `more-dropdown-${instanceId.replace(/[^a-zA-Z0-9_-]/g, '')}`

// 原型为单实例浮层：同一时刻只允许一个「更多」菜单展开
const activeId = ref<string | null>(null)
const isOpen = ref(false)

watch(isOpen, (open) => {
  if (open) activeId.value = instanceId
  else if (activeId.value === instanceId) activeId.value = null
})

watch(activeId, (current) => {
  if (isOpen.value && current !== instanceId) close()
})

function onTriggerClick(): void {
  isOpen.value = !isOpen.value
}

function close(): void {
  isOpen.value = false
  dropdownRef.value?.handleClose()
}

function onDocumentMousedown(event: MouseEvent): void {
  if (!isOpen.value) return
  const target = event.target
  if (!(target instanceof Node)) return
  if (triggerRef.value?.contains(target)) return
  if (event.composedPath().some((node) => node instanceof Element && node.closest(`.${popperClass}`))) {
    return
  }
  close()
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !isOpen.value) return
  close()
}

function onCommand(command: unknown): void {
  isOpen.value = false
  emit('command', String(command))
}

function itemStyle(tone?: MoreAction['tone']): Record<string, string> | undefined {
  if (tone === 'danger') return { color: 'var(--el-color-danger)' }
  if (tone === 'warning') return { color: 'var(--el-color-warning)' }
  return undefined
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMousedown, true)
  document.addEventListener('keydown', onDocumentKeydown, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMousedown, true)
  document.removeEventListener('keydown', onDocumentKeydown, true)
  if (activeId.value === instanceId) activeId.value = null
})
</script>

<template>
  <el-dropdown
    ref="dropdownRef"
    v-bind="$attrs"
    trigger="click"
    :placement="placement"
    :popper-class="popperClass"
    @command="onCommand"
  >
    <span
      ref="triggerRef"
      class="more-dropdown__trigger"
      @click.capture="onTriggerClick"
    ><slot name="trigger" /></span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="action in props.actions"
          :key="action.key"
          :command="action.key"
          :disabled="action.disabled"
          :divided="action.divided"
          :style="itemStyle(action.tone)"
        >{{ action.label }}</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped lang="scss">
.more-dropdown__trigger {
  display: inline-flex;
  align-items: center;
}
</style>
