<script setup lang="ts">
import type { ApiReportStepResult } from '@/types'
import ReportExtractorsTable from './ReportExtractorsTable.vue'
import ReportAssertionsTable from './ReportAssertionsTable.vue'

defineProps<{
  step: ApiReportStepResult
  tab: 'pre' | 'post'
  index: number
  procKey: (step: ApiReportStepResult, tab: 'pre' | 'post', index: number) => string
  isCollapsed: (key: string) => boolean
  toggleCollapse: (key: string) => void
  statusLabel: (status: string | null | undefined) => string
  badgeClass: (status: string | null | undefined) => string
  methodChipClass: (method: string | null | undefined) => string
  displayValue: (value: unknown) => string
  pretty: (value: unknown) => string
  procUrl: (step: ApiReportStepResult) => string
  headersEntries: (headers: Record<string, unknown> | null | undefined) => Array<[string, unknown]>
}>()
</script>

<template>
  <div class="rr-proc">
    <div class="rr-proc__head">
      <span class="rr-proc__name">{{ step.name ?? '处理器' }}</span>
      <span class="rr-chip rr-monotag" :class="methodChipClass(step.request?.method ?? null)">
        {{ step.request?.method ?? 'HTTP' }}
      </span>
      <span class="rr-badge" :class="badgeClass(step.status)">{{ statusLabel(step.status) }}</span>
      <button
        class="rr-chev"
        :class="{ 'rr-chev--open': !isCollapsed(procKey(step, tab, index)) }"
        type="button"
        :aria-label="isCollapsed(procKey(step, tab, index)) ? '展开' : '收起'"
        title="展开/收起"
        @click="toggleCollapse(procKey(step, tab, index))"
      >
        <span class="rr-chev__i"></span>
      </button>
    </div>
    <p v-if="procUrl(step)" class="rr-proc__meta">{{ procUrl(step) }}</p>
    <div v-show="!isCollapsed(procKey(step, tab, index))" class="rr-proc__body">
      <div class="rr-grid2">
        <div v-if="headersEntries(step.request?.headers ?? null).length">
          <p class="rr-sub-label">请求头</p>
          <table class="rr-kv">
            <tr v-for="[k, v] in headersEntries(step.request?.headers ?? null)" :key="k">
              <th>{{ k }}</th>
              <td>{{ displayValue(v) }}</td>
            </tr>
          </table>
        </div>
        <div v-if="step.request?.query != null || step.request?.body != null || step.response?.body != null">
          <p v-if="step.request?.query != null" class="rr-sub-label">Query 参数</p>
          <pre v-if="step.request?.query != null" class="rr-code rr-code--light">{{ pretty(step.request.query) }}</pre>
          <p v-if="step.request?.body != null" class="rr-sub-label" style="margin-top: 10px">请求体</p>
          <pre v-if="step.request?.body != null" class="rr-code rr-code--light">{{ pretty(step.request.body) }}</pre>
          <p v-if="step.response?.body != null" class="rr-sub-label" style="margin-top: 10px">响应体</p>
          <pre v-if="step.response?.body != null" class="rr-code rr-code--light">{{ pretty(step.response.body) }}</pre>
        </div>
      </div>

      <ReportExtractorsTable v-if="step.extractors?.length" :extractors="step.extractors" />
      <ReportAssertionsTable v-if="step.assertions?.length" :assertions="step.assertions" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.rr-proc {
  border: 1px solid var(--color-neutral-200);
  border-radius: 11px;
  background: var(--color-neutral-50);
  margin-bottom: 10px;
  overflow: hidden;

  &:last-child {
    margin-bottom: 0;
  }
}

.rr-proc__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: linear-gradient(180deg, var(--color-neutral-50), var(--color-neutral-0));
}

.rr-proc__name {
  font-weight: 600;
  font-size: 13px;
}

.rr-chev {
  margin-left: auto;
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  padding: 5px 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-neutral-500);
  border-radius: 6px;
  flex: 0 0 auto;
  font-family: inherit;
  transition:
    background 0.15s,
    color 0.15s;

  &:hover {
    background: var(--color-neutral-100);
    color: var(--color-neutral-600);
  }
}

.rr-chev__i {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(-45deg) translate(-1px, 1px);
  transition: transform 0.2s ease;
  pointer-events: none;
}

.rr-chev--open .rr-chev__i {
  transform: rotate(45deg) translate(-2px, -2px);
}

.rr-proc__meta {
  font-size: 12.2px;
  color: var(--color-neutral-600);
  font-family: var(--font-mono);
  word-break: break-all;
  padding: 0 14px 10px;
  margin: 0;
}

.rr-proc__body {
  padding: 0 14px 14px;
}

.rr-grid2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.rr-sub-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-600);
  margin: 0 0 6px;
}

.rr-kv {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.6px;
  background: var(--color-neutral-0);
  border: 1px solid var(--color-neutral-200);
  border-radius: 9px;
  overflow: hidden;

  th,
  td {
    text-align: left;
    padding: 7px 12px;
    border-bottom: 1px solid var(--color-neutral-200);
    vertical-align: top;
  }

  tr:last-child td,
  tr:last-child th {
    border-bottom: none;
  }

  th {
    background: var(--color-neutral-50);
    color: var(--color-neutral-600);
    font-weight: 600;
    width: 40%;
  }

  td {
    font-family: var(--font-mono);
    color: var(--color-neutral-800);
    word-break: break-all;
  }
}

.rr-code {
  margin: 0;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 9px;
  padding: 12px 14px;
  font-family: var(--font-mono);
  font-size: 12.4px;
  line-height: 1.65;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;

  &--light {
    background: var(--color-neutral-50);
    color: var(--color-neutral-800);
    border: 1px solid var(--color-neutral-200);
  }
}
</style>
