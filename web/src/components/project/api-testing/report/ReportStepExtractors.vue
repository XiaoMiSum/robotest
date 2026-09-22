<script setup lang="ts">
import type { ApiReportExtractor } from '@/types'

defineProps<{
  extractors: ApiReportExtractor[]
  displayValue: (v: unknown) => string
  badgeClass: (s: string | null | undefined) => string
}>()
</script>

<template>
  <div class="rsc__block">
    <div class="rsc__block-title">提取器（{{ extractors.length }}）</div>
    <table class="rsc__tbl">
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
          <td class="rsc__ref">{{ e.refName }}</td>
          <td class="rsc__mono">{{ e.field ?? '-' }}</td>
          <td class="rsc__mono rsc__val-ok">{{ displayValue(e.value) }}</td>
          <td class="rsc__null">{{ e.defaultValue ? '是' : '-' }}</td>
          <td>
            <span
              class="rsc-badge"
              :class="badgeClass(e.message ? 'failed' : 'success')"
            >
              {{ e.message ? '提取失败' : '成功' }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped lang="scss">
.rsc__block {
  margin-top: 16px;

  &:first-child {
    margin-top: 0;
  }
}

.rsc__block-title {
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

.rsc__tbl {
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

.rsc__mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rsc__ref {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--color-success);
  font-weight: 600;
}

.rsc__val-ok {
  color: #15803d;
  font-weight: 600;
}

.rsc__null {
  color: #9aa4b2;
  font-style: italic;
}

.rsc-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
  }

  &--ok {
    background: #ecfdf3;
    color: #15803d;
  }

  &--fail {
    background: #fef2f2;
    color: #b91c1c;
  }

  &--skip {
    background: #f3f4f6;
    color: #4b5563;
  }
}
</style>
