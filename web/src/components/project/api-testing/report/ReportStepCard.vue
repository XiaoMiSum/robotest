<script setup lang="ts">
import { useReportStepCard } from '@/composables/project/api-testing/report/useReportStepCard'
import type { ApiReportStepResult } from '@/types'
import ReportStepAssertions from './ReportStepAssertions.vue'
import ReportStepExtractors from './ReportStepExtractors.vue'

const props = defineProps<{
  steps: ApiReportStepResult[]
}>()

const {
  allOpen,
  stepKey,
  isOpen,
  onToggle,
  toggleAll,
  statusLabel,
  badgeClass,
  methodChipClass,
  pretty,
  displayValue,
  formatDuration,
  headersEntries,
  responseMeta,
  assertionLabel,
} = useReportStepCard(() => props.steps)
</script>

<template>
  <div class="rsc">
    <div class="rsc__toolbar">
      <span class="rsc__title">测试步骤（{{ steps.length }}）</span>
      <button v-if="steps.length" type="button" class="rsc__btn" @click="toggleAll">
        {{ allOpen ? '全部收起' : '全部展开' }}
      </button>
    </div>

    <el-empty v-if="!steps.length" description="该场景无步骤明细" :image-size="40" />

    <details
      v-for="(step, index) in steps"
      :key="stepKey(step, index)"
      class="rsc__card"
      :open="isOpen(stepKey(step, index))"
      @toggle="onToggle(stepKey(step, index), $event)"
    >
      <summary class="rsc__head">
        <span class="rsc__index">{{ index + 1 }}</span>
        <span class="rsc__name">{{ step.name ?? '-' }}</span>
        <span class="rsc-chip" :class="methodChipClass(step.request?.method ?? null)">
          {{ step.request?.method ?? 'HTTP' }}
        </span>
        <span class="rsc-badge" :class="badgeClass(step.status)">{{ statusLabel(step.status) }}</span>
        <span
          v-if="step.assertions?.length"
          class="rsc-chip"
          :class="step.assertions.some((a) => a.status === 'failed') ? 'rsc-chip--delete' : 'rsc-chip--post'"
        >
          验证器 {{ step.assertions.length }}
        </span>
        <span class="rsc__right">
          <span class="rsc__dur">{{ formatDuration(step.durationMs ?? null) }}</span>
        </span>
      </summary>

      <div class="rsc__body">
        <div v-if="step.request" class="rsc__block">
          <div class="rsc__block-title">请求</div>
          <div class="rsc__reqline">
            <span class="rsc-chip" :class="methodChipClass(step.request.method ?? null)">
              {{ step.request.method ?? 'HTTP' }}
            </span>
            <span class="rsc__url">{{ step.request.url == null ? '-' : String(step.request.url) }}</span>
          </div>
          <div
            v-if="headersEntries(step.request.headers ?? null).length || step.request.query != null || step.request.body != null"
            class="rsc__grid2"
          >
            <div v-if="headersEntries(step.request.headers ?? null).length">
              <p class="rsc__sub-label">请求头</p>
              <table class="rsc__kv">
                <tr v-for="[k, v] in headersEntries(step.request.headers ?? null)" :key="k">
                  <th>{{ k }}</th>
                  <td>{{ displayValue(v) }}</td>
                </tr>
              </table>
            </div>
            <div v-if="step.request.query != null || step.request.body != null">
              <p v-if="step.request.query != null" class="rsc__sub-label">Query 参数</p>
              <pre v-if="step.request.query != null" class="rsc__code rsc__code--light">{{ pretty(step.request.query) }}</pre>
              <p v-if="step.request.body != null" class="rsc__sub-label" style="margin-top: 10px">请求体</p>
              <pre v-if="step.request.body != null" class="rsc__code rsc__code--light">{{ pretty(step.request.body) }}</pre>
            </div>
          </div>
        </div>

        <div v-if="step.response" class="rsc__block">
          <div class="rsc__block-title">响应</div>
          <div class="rsc__reqline">
            <span class="rsc-chip rsc-chip--post rsc-chip--status">
              {{ step.response.status ?? '-' }}
            </span>
            <span class="rsc__url">{{ responseMeta(step) }}</span>
          </div>
          <pre v-if="step.response.body != null" class="rsc__code">{{ pretty(step.response.body) }}</pre>
          <p v-if="headersEntries(step.response.headers ?? null).length" class="rsc__sub-label" style="margin-top: 12px">
            响应头
          </p>
          <table v-if="headersEntries(step.response.headers ?? null).length" class="rsc__kv">
            <tr v-for="[k, v] in headersEntries(step.response.headers ?? null)" :key="k">
              <th>{{ k }}</th>
              <td>{{ displayValue(v) }}</td>
            </tr>
          </table>
        </div>

        <ReportStepAssertions
          v-if="step.assertions?.length"
          :assertions="step.assertions"
          :display-value="displayValue"
          :badge-class="badgeClass"
          :assertion-label="assertionLabel"
        />

        <ReportStepExtractors
          v-if="step.extractors?.length"
          :extractors="step.extractors"
          :display-value="displayValue"
          :badge-class="badgeClass"
        />

        <div v-if="step.errorMessage" class="rsc__block">
          <el-alert :title="step.errorMessage" type="error" show-icon :closable="false" />
        </div>
      </div>
    </details>
  </div>
</template>

<style scoped lang="scss">
.rsc__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-neutral-200);
}

.rsc__title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--color-neutral-600);
  text-transform: uppercase;
}

.rsc__btn {
  border: 1px solid var(--color-neutral-300);
  background: var(--color-neutral-0);
  color: var(--color-neutral-600);
  border-radius: 8px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;

  &:hover {
    border-color: var(--color-neutral-400);
    color: var(--color-success);
    background: var(--color-success-light);
  }
}

.rsc__card {
  border: 1px solid var(--color-neutral-200);
  border-radius: 12px;
  background: var(--color-neutral-0);
  margin-bottom: 12px;
  overflow: hidden;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;

  &:last-child {
    margin-bottom: 0;
  }

  &[open] {
    border-color: var(--color-neutral-300);
    box-shadow: 0 2px 10px -6px rgba(0, 0, 0, 0.18);
  }
}

.rsc__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  font-size: 13.5px;
  flex-wrap: wrap;
  background: linear-gradient(180deg, var(--color-neutral-50), var(--color-neutral-0));
  cursor: pointer;
  list-style: none;
  user-select: none;
  transition: background 0.15s;

  &::-webkit-details-marker {
    display: none;
  }

  &:hover {
    background: var(--color-success-light);
  }

  .rsc__card[open] > & {
    border-bottom: 1px solid var(--color-neutral-200);
  }

  &::after {
    content: '';
    width: 7px;
    height: 7px;
    margin-left: 8px;
    border-right: 2px solid var(--color-neutral-500);
    border-bottom: 2px solid var(--color-neutral-500);
    transform: rotate(-45deg);
    transition: transform 0.2s ease;
    flex: 0 0 auto;
  }

  .rsc__card[open] > &::after {
    transform: rotate(45deg) translate(-2px, -2px);
  }
}

.rsc__index {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: var(--color-success-light);
  color: var(--color-success);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.rsc__name {
  font-weight: 600;
  color: var(--color-neutral-900);
}

.rsc__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.rsc__dur {
  font-size: 12px;
  color: var(--color-neutral-600);
  font-family: var(--font-mono);
  white-space: nowrap;
}

.rsc__body {
  padding: 16px 16px 18px;
}

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

.rsc__reqline {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--color-neutral-50);
  border: 1px solid var(--color-neutral-200);
  border-radius: 9px;
  padding: 9px 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.rsc__url {
  font-family: var(--font-mono);
  font-size: 12.6px;
  color: var(--color-neutral-800);
  word-break: break-all;
}

.rsc__grid2 {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.rsc__sub-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-neutral-600);
  margin: 0 0 6px;
}

.rsc__kv {
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

.rsc__code {
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

.rsc-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  font-family: var(--font-mono);

  &--get {
    background: var(--color-primary-50);
    color: var(--color-primary-700);
  }

  &--post {
    background: var(--color-success-light);
    color: var(--color-success-strong);
  }

  &--put {
    background: var(--color-warning-light);
    color: var(--color-warning-strong);
  }

  &--delete {
    background: var(--color-danger-light);
    color: var(--color-danger-strong);
  }

  &--status {
    padding: 2px 10px;
  }
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
