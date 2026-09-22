<script setup lang="ts">
import type { ApiReportAssertion } from '@/types'
import { useReportDisplay } from '@/composables/useReportDisplay'

defineProps<{ assertions: ApiReportAssertion[] }>()

const { displayValue, badgeClass, assertionLabel } = useReportDisplay()
</script>

<template>
  <div class="rr-block">
    <div class="rr-block-title">验证器（{{ assertions.length }}）</div>
    <table class="rr-tbl">
      <thead>
        <tr>
          <th>字段</th>
          <th>规则</th>
          <th>期望值</th>
          <th>实际值</th>
          <th>结果</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(a, i) in assertions" :key="`${a.field}-${i}`">
          <td class="rr-monotag">{{ a.field ?? '-' }}</td>
          <td>{{ a.rule ?? '-' }}</td>
          <td class="rr-monotag">{{ displayValue(a.expected) }}</td>
          <td class="rr-monotag" :class="{ 'rr-val-ok': a.status === 'passed' || a.status === 'success' }">
            {{ displayValue(a.actual) }}
          </td>
          <td>
            <span class="rr-badge" :class="badgeClass(a.status)">
              {{ assertionLabel(a.status) }}
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
  color: #64748b;
  text-transform: uppercase;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;

  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: #eef1f5;
  }
}

.rr-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.6px;
  border: 1px solid #eef1f5;
  border-radius: 9px;
  overflow: hidden;

  th {
    background: #f8fafc;
    color: #64748b;
    font-weight: 600;
    text-align: left;
    padding: 8px 12px;
    border-bottom: 1px solid #eef1f5;
    font-size: 12px;
    white-space: nowrap;
  }

  td {
    padding: 8px 12px;
    border-bottom: 1px solid #f4f7fa;
    vertical-align: top;
    word-break: break-all;
  }

  tr:last-child td {
    border-bottom: none;
  }
}

.rr-monotag {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rr-val-ok {
  color: #15803d;
  font-weight: 600;
}
</style>
