<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ApiReportAssertion, ApiReportExtractor, ApiReportStepResult } from '@/types'

/**
 * 场景步骤卡列表：工具栏「全部展开/收起」+ 步骤卡（details）。展开含
 * 请求块 / 响应块 / 验证器表 / 提取器表 / 错误告警；失败与错误步骤默认展开。
 */
const props = defineProps<{
  steps: ApiReportStepResult[]
}>()

watch(
  () => props.steps.length,
  () => {
    // 数据变化（报告重载）时重置展开状态为默认
    openKeys.value = defaultOpenKeys()
  },
)

const openKeys = ref<Set<string>>(new Set(defaultOpenKeys()))

function defaultOpenKeys(): Set<string> {
  return new Set(
    props.steps
      .filter((s) => s.status === 'failed' || s.status === 'error')
      .map((step, index) => stepKey(step, index)),
  )
}

function stepKey(step: ApiReportStepResult, index: number): string {
  return step.stepId ?? `step-${index}`
}

function isOpen(key: string): boolean {
  return openKeys.value.has(key)
}

function onToggle(key: string, event: Event) {
  const open = (event.target as HTMLDetailsElement).open
  const set = new Set(openKeys.value)
  if (open) set.add(key)
  else set.delete(key)
  openKeys.value = set
}

const allOpen = ref(false)

function toggleAll() {
  const set = new Set(openKeys.value)
  if (allOpen.value) {
    set.clear()
  } else {
    props.steps.forEach((step, index) => set.add(stepKey(step, index)))
  }
  openKeys.value = set
  allOpen.value = !allOpen.value
}

// ==================== 状态与格式化 ====================
function statusLabel(status: string | null | undefined): string {
  const map: Record<string, string> = {
    success: '成功',
    passed: '成功',
    failed: '失败',
    skipped: '跳过',
    error: '错误',
    not_executed: '未执行',
  }
  return map[status ?? ''] ?? status ?? '-'
}

function assertionLabel(status: string | null | undefined): string {
  if (status === 'passed' || status === 'success') return '通过'
  if (status === 'failed') return '失败'
  if (status === 'skipped') return '跳过'
  return status ?? '-'
}

function badgeClass(status: string | null | undefined): string {
  if (status === 'success' || status === 'passed') return 'rsc-badge--ok'
  if (status === 'failed') return 'rsc-badge--fail'
  return 'rsc-badge--skip'
}

function methodChipClass(method: string | null | undefined): string {
  const m = method?.toUpperCase() ?? ''
  if (m === 'GET') return 'rsc-chip--get'
  if (m === 'POST') return 'rsc-chip--post'
  if (m === 'PUT') return 'rsc-chip--put'
  if (m === 'DELETE') return 'rsc-chip--delete'
  return ''
}

function pretty(value: unknown): string {
  if (value == null) return '-'
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  return JSON.stringify(value, null, 2)
}

function displayValue(value: unknown): string {
  if (value == null) return '-'
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

function headersEntries(headers: Record<string, unknown> | null | undefined): Array<[string, unknown]> {
  return headers ? Object.entries(headers) : []
}

function headerValue(headers: Record<string, unknown> | null | undefined, key: string): string | null {
  if (!headers) return null
  const hit = Object.keys(headers).find((k) => k.toLowerCase() === key.toLowerCase())
  if (hit == null) return null
  const v = headers[hit]
  return v == null ? null : typeof v === 'string' ? v : String(v)
}

function responseMeta(step: ApiReportStepResult): string {
  const contentType = headerValue(step.response?.headers ?? null, 'content-type') ?? ''
  return ['HTTP/1.1', contentType].filter(Boolean).join(' · ')
}
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
          :class="step.assertions.some((a) => (a as ApiReportAssertion).status === 'failed') ? 'rsc-chip--delete' : 'rsc-chip--post'"
        >
          验证器 {{ step.assertions.length }}
        </span>
        <span class="rsc__right">
          <span class="rsc__dur">{{ formatDuration(step.durationMs ?? null) }}</span>
        </span>
      </summary>

      <div class="rsc__body">
        <!-- 请求 -->
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
            class="rsc__grid2">
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

        <!-- 响应 -->
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

        <!-- 验证器 -->
        <div v-if="step.assertions?.length" class="rsc__block">
          <div class="rsc__block-title">验证器（{{ step.assertions.length }}）</div>
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
              <tr v-for="(a, i) in step.assertions" :key="`${(a as ApiReportAssertion).field}-${i}`">
                <td class="rsc__mono">{{ (a as ApiReportAssertion).field ?? '-' }}</td>
                <td>{{ (a as ApiReportAssertion).rule ?? '-' }}</td>
                <td class="rsc__mono">{{ displayValue((a as ApiReportAssertion).expected) }}</td>
                <td
                  class="rsc__mono"
                  :class="{
                    'rsc__val-ok':
                      (a as ApiReportAssertion).status === 'passed' || (a as ApiReportAssertion).status === 'success',
                  }"
                >
                  {{ displayValue((a as ApiReportAssertion).actual) }}
                </td>
                <td>
                  <span class="rsc-badge" :class="badgeClass((a as ApiReportAssertion).status)">
                    {{ assertionLabel((a as ApiReportAssertion).status) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 提取器 -->
        <div v-if="step.extractors?.length" class="rsc__block">
          <div class="rsc__block-title">提取器（{{ step.extractors.length }}）</div>
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
              <tr v-for="(e, i) in step.extractors" :key="`${(e as ApiReportExtractor).refName}-${i}`">
                <td class="rsc__ref">{{ (e as ApiReportExtractor).refName }}</td>
                <td class="rsc__mono">{{ (e as ApiReportExtractor).field ?? '-' }}</td>
                <td class="rsc__mono rsc__val-ok">{{ displayValue((e as ApiReportExtractor).value) }}</td>
                <td class="rsc__null">{{ (e as ApiReportExtractor).defaultValue ? '是' : '-' }}</td>
                <td>
                  <span
                    class="rsc-badge"
                    :class="badgeClass((e as ApiReportExtractor).message ? 'failed' : 'success')"
                  >
                    {{ (e as ApiReportExtractor).message ? '提取失败' : '成功' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 错误信息 -->
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
  border-bottom: 1px solid #eef1f5;
}

.rsc__title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #64748b;
  text-transform: uppercase;
}

.rsc__btn {
  border: 1px solid #dfe5ee;
  background: #fff;
  color: #475569;
  border-radius: 8px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;

  &:hover {
    border-color: #c7d2e4;
    color: var(--color-success);
    background: #f0fdf4;
  }
}

.rsc__card {
  border: 1px solid #e8ecf2;
  border-radius: 12px;
  background: #fff;
  margin-bottom: 12px;
  overflow: hidden;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;

  &:last-child {
    margin-bottom: 0;
  }

  &[open] {
    border-color: #dbe3ee;
    box-shadow: 0 2px 10px -6px rgba(16, 24, 40, 0.18);
  }
}

.rsc__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  font-size: 13.5px;
  flex-wrap: wrap;
  background: linear-gradient(180deg, #fbfdff, #fff);
  cursor: pointer;
  list-style: none;
  user-select: none;
  transition: background 0.15s;

  &::-webkit-details-marker {
    display: none;
  }

  &:hover {
    background: #f6fbf8;
  }

  .rsc__card[open] > & {
    border-bottom: 1px solid #eef1f5;
  }

  &::after {
    content: '';
    width: 7px;
    height: 7px;
    margin-left: 8px;
    border-right: 2px solid #9aa4b2;
    border-bottom: 2px solid #9aa4b2;
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
  background: #e8f3ec;
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
  color: #111827;
}

.rsc__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.rsc__dur {
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
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

.rsc__reqline {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f8fafc;
  border: 1px solid #eef1f5;
  border-radius: 9px;
  padding: 9px 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.rsc__url {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.6px;
  color: #334155;
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
  color: #64748b;
  margin: 0 0 6px;
}

.rsc__kv {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.6px;
  background: #fff;
  border: 1px solid #eef1f5;
  border-radius: 9px;
  overflow: hidden;

  th,
  td {
    text-align: left;
    padding: 7px 12px;
    border-bottom: 1px solid #f1f5f9;
    vertical-align: top;
  }

  tr:last-child td,
  tr:last-child th {
    border-bottom: none;
  }

  th {
    background: #f8fafc;
    color: #64748b;
    font-weight: 600;
    width: 40%;
  }

  td {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    color: #1f2937;
    word-break: break-all;
  }
}

.rsc__code {
  margin: 0;
  background: #1c2620;
  color: #e2e8e4;
  border-radius: 9px;
  padding: 12px 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.4px;
  line-height: 1.65;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;

  &--light {
    background: #f8fafc;
    color: #334155;
    border: 1px solid #eef1f5;
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

.rsc-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  &--get {
    background: #e0f2fe;
    color: #0369a1;
  }

  &--post {
    background: #dcfce7;
    color: #15803d;
  }

  &--put {
    background: #fef3c7;
    color: #b45309;
  }

  &--delete {
    background: #fee2e2;
    color: #b91c1c;
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