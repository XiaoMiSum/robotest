<script setup lang="ts">
import type { ApiReportAssertion } from '@/types'

defineProps<{
  assertions: ApiReportAssertion[]
  displayValue: (v: unknown) => string
  badgeClass: (s: string | null | undefined) => string
  assertionLabel: (s: string | null | undefined) => string
}>()
</script>

<template>
  <div class="rsc__block">
    <div class="rsc__block-title">验证器（{{ assertions.length }}）</div>
    <table class="rsc__tbl">
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
          <td class="rsc__mono">{{ a.field ?? '-' }}</td>
          <td>{{ a.rule ?? '-' }}</td>
          <td class="rsc__mono">{{ displayValue(a.expected) }}</td>
          <td
            class="rsc__mono"
            :class="{
              'rsc__val-ok': a.status === 'passed' || a.status === 'success',
            }"
          >
            {{ displayValue(a.actual) }}
          </td>
          <td>
            <span class="rsc-badge" :class="badgeClass(a.status)">
              {{ assertionLabel(a.status) }}
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

.rsc__tbl {
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

.rsc__mono {
  font-family: var(--font-mono);
}

.rsc__val-ok {
  color: var(--color-success-strong);
  font-weight: 600;
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
    background: var(--color-success-light);
    color: var(--color-success-strong);
  }

  &--fail {
    background: var(--color-danger-light);
    color: var(--color-danger-strong);
  }

  &--skip {
    background: var(--color-neutral-100);
    color: var(--color-neutral-600);
  }
}
</style>
