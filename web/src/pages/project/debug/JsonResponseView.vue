<script setup lang="ts">
import { computed, ref } from 'vue'

defineOptions({ name: 'JsonNode' })

const props = defineProps<{
  value: unknown
  path: string
  depth: number
  /** 父级为对象成员时为 true（数组元素不展示下标 key） */
  parentHasKey?: boolean
}>()

const collapsed = ref(false)

const kind = computed(() => {
  if (props.value === null) return 'null'
  if (Array.isArray(props.value)) return 'array'
  if (typeof props.value === 'object') return 'object'
  return 'primitive'
})

const tokenClass = computed(() => {
  if (props.value === null) return 'is-null'
  if (typeof props.value === 'boolean') return 'is-bool'
  if (typeof props.value === 'number') return 'is-number'
  return 'is-string'
})

const entries = computed<Array<[string, unknown]>>(() => {
  if (kind.value === 'array') {
    return (props.value as unknown[]).map((item: unknown, index: number) => [String(index), item])
  }
  if (kind.value === 'object') {
    return Object.entries(props.value as Record<string, unknown>)
  }
  return []
})

const summary = computed(() => {
  const count = entries.value.length
  return kind.value === 'array' ? `Array(${count})` : `Object {${count}}`
})

function prettyPrimitive(): string {
  const v = props.value
  if (v === null) return 'null'
  if (typeof v === 'string') return JSON.stringify(v)
  return String(v)
}

function toggle() {
  collapsed.value = !collapsed.value
}

const childIndent = computed(() => ({ 'padding-left': `${(props.depth + 1) * 16}px` }))
</script>

<template>
  <div class="jn-wrap">
    <div class="jn-line">
      <span
        v-if="kind !== 'primitive'"
        class="jn-toggle"
        role="button"
        tabindex="0"
        @click="toggle"
        @keyup.enter="toggle"
      >
        {{ collapsed ? '▶' : '▼' }}
      </span>
      <span v-if="props.parentHasKey" class="jn-key">"{{ path }}": </span>
      <template v-if="kind === 'primitive'">
        <span class="jn-token" :class="tokenClass">{{ prettyPrimitive() }}</span>
      </template>
      <template v-else-if="collapsed">
        <span class="jn-token">{{ kind === 'array' ? '[' : '{' }}</span>
        <span class="jn-summary">{{ summary }}</span>
        <span class="jn-token">{{ kind === 'array' ? ']' : '}' }}</span>
      </template>
      <template v-else>
        <span class="jn-token">{{ kind === 'array' ? '[' : '{' }}</span>
      </template>
    </div>
    <template v-if="kind !== 'primitive' && !collapsed">
      <div
        v-for="[key, item] in entries"
        :key="key"
        class="jn-child"
        :style="childIndent"
      >
        <JsonNode :value="item" :path="key" :depth="depth + 1" :parent-has-key="kind === 'object'" />
      </div>
      <div class="jn-line" :style="childIndent">
        <span class="jn-token">{{ kind === 'array' ? ']' : '}' }}</span>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.jn-wrap {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  line-height: 1.7;
  color: #d4d4d4;
  background: #1e1e1e;
}

.jn-line {
  display: flex;
  align-items: baseline;
  min-height: 18px;
  padding: 0 4px;

  &:hover {
    background: rgba(255, 255, 255, 0.04);
  }
}

.jn-toggle {
  width: 14px;
  flex-shrink: 0;
  cursor: pointer;
  color: #8a8a8a;
  font-size: 10px;
  user-select: none;
}

.jn-key {
  color: #9cdcfe;
  margin-right: 4px;
}

.jn-token {
  color: #d4d4d4;
}

.jn-summary {
  color: #8a8a8a;
  margin: 0 4px;
}

.jn-child {
  border-left: 1px solid rgba(255, 255, 255, 0.07);
}

.is-string {
  color: #ce9178;
}

.is-number {
  color: #b5cea8;
}

.is-bool,
.is-null {
  color: #569cd6;
}
</style>