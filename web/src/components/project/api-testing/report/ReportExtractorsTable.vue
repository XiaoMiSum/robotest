<script setup lang="ts">
import type { ApiReportExtractor } from '@/types'
import { useReportDisplay } from '@/composables/project/api-testing/report/useReportDisplay'

defineProps<{ extractors: ApiReportExtractor[] }>()

const { displayValue, badgeClass } = useReportDisplay()
</script>

<template>
  <div class="rr-block">
    <div class="rr-block-title">提取器（{{ extractors.length }}）</div>
    <table class="rr-tbl">
      <thead>
        <tr>
          <th>引用名</th>
          <th>字段</th>
          <th>提取值</th>
          <th>默认值</th>
          <th>结果</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(e, i) in extractors" :key="`${e.refName}-${i}`">
          <td class="rr-ref">{{ e.refName }}</td>
          <td class="rr-monotag">{{ e.field ?? '-' }}</td>
          <td class="rr-monotag rr-val-ok">{{ displayValue(e.value) }}</td>
          <td class="rr-null">{{ e.defaultValue ? '是' : '-' }}</td>
          <td>
            <span class="rr-badge" :class="badgeClass(e.message ? 'failed' : 'success')">
              {{ e.message ? '提取失败' : '成功' }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped lang="scss">
.rr-block {
  margin-top: 14px;
}

.rr-block-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--color-neutral-600);
  text-transform: uppercase;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;

  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--color-neutral-200);
  }
}

.rr-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.6px;
  border: 1px solid var(--color-neutral-200);
  border-radius: 9px;
  overflow: hidden;

  th {
    background: var(--color-neutral-50);
    color: var(--color-neutral-600);
    font-weight: 600;
    text-align: left;
    padding: 8px 12px;
    border-bottom: 1px solid var(--color-neutral-200);
    font-size: 12px;
    white-space: nowrap;
  }

  td {
    padding: 8px 12px;
    border-bottom: 1px solid var(--color-neutral-200);
    vertical-align: top;
    word-break: break-all;
  }

  tr:last-child td {
    border-bottom: none;
  }
}

.rr-monotag {
  font-family: var(--font-mono);
}

.rr-ref {
  font-family: var(--font-mono);
  color: var(--color-success);
  font-weight: 600;
}

.rr-val-ok {
  color: var(--color-success-strong);
  font-weight: 600;
}

.rr-null {
  color: var(--color-neutral-500);
  font-style: italic;
}
</style>
