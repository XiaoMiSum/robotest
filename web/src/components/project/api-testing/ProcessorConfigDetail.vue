<template>
  <div class="processor-detail">
    <div class="processor-detail__rows">
      <div
        v-for="row in rows"
        :key="row.label"
        class="processor-detail__row"
        :class="{ 'is-wide': row.kind !== 'text' }"
      >
        <span class="processor-detail__k">{{ row.label }}</span>
        <code v-if="row.kind === 'code'" class="processor-detail__code">{{ row.value }}</code>
        <div v-else-if="row.kind === 'kv'" class="processor-detail__v">
          <div v-for="pair in row.pairs" :key="pair.key" class="processor-detail__kv">
            <code class="processor-detail__kv-k">{{ pair.key }}</code>
            <span class="processor-detail__kv-v">{{ pair.value }}</span>
          </div>
        </div>
        <span v-else class="processor-detail__v">{{ row.value }}</span>
      </div>
    </div>

    <div v-if="extractors.length" class="processor-detail__extractors">
      <span class="processor-detail__label">提取器</span>
      <el-table :data="extractors" size="small" empty-text="—">
        <el-table-column label="来源" prop="source" min-width="120" />
        <el-table-column label="表达式" prop="expression" min-width="180" show-overflow-tooltip />
        <el-table-column label="目标变量名" prop="variableName" min-width="130" show-overflow-tooltip />
        <el-table-column label="描述" prop="description" min-width="140" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { buildProcessorDetailRows, buildProcessorExtractorRows } from '@/composables/project/api-testing/processorDetailModel'

const props = withDefaults(defineProps<{
  /** 处理器元素（testclass + config + extractors） */
  element?: Record<string, unknown>
}>(), { element: () => ({}) })

const rows = computed(() => buildProcessorDetailRows(props.element ?? {}))
const extractors = computed(() => buildProcessorExtractorRows(props.element ?? {}))
</script>

<style scoped lang="scss">
// 只读明细自成一块：行网格、代码块与键值对样式对齐公共组件的 cp-meta / cp-code / cp-kv，保证两页观感一致
.processor-detail {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.processor-detail__rows {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--space-md);
}

.processor-detail__row {
  min-width: 0;

  &.is-wide {
    grid-column: 1 / -1;
  }
}

.processor-detail__k {
  display: block;
  margin-bottom: 3px;
  font-size: var(--font-size-xs);
  color: var(--color-neutral-400);
}

.processor-detail__v {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-700);
  word-break: break-all;
}

.processor-detail__code {
  display: block;
  padding: 9px 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.processor-detail__kv {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.processor-detail__kv + .processor-detail__kv {
  margin-top: 5px;
}

.processor-detail__kv-k {
  flex-shrink: 0;
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-100);
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-primary-600);
}

.processor-detail__kv-v {
  font-size: var(--font-size-sm);
  color: var(--color-neutral-600);
  word-break: break-all;
}

.processor-detail__label {
  display: block;
  margin-bottom: var(--space-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--color-neutral-400);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* 提取器只读表格：表头沿用全局表格变量，仅去掉行间重边框 */
.processor-detail__extractors {
  :deep(.el-table) {
    --el-table-border-color: var(--color-neutral-100);
  }
}
</style>
