<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ApiReportAssertion, ApiReportExtractor, ApiReportStepResult } from '@/types'

/**
 * 前置/后置处理器 Tab（任务级与场景级复用）：数量角标 + 状态圆点 + 执行顺序提示链，
 * 处理器小卡默认折叠展示摘要（名称 / 方法 / 状态 / URL），展开核对提取器与验证器结果。
 */
const props = defineProps<{
  level: 'task' | 'scene'
  pre: ApiReportStepResult[]
  post: ApiReportStepResult[]
}>()

const activeTab = ref<'pre' | 'post'>('pre')

watch(
  () => [props.pre.length, props.post.length] as const,
  ([pre, post]) => {
    if (activeTab.value === 'pre' && pre === 0 && post > 0) activeTab.value = 'post'
    if (activeTab.value === 'post' && post === 0 && pre > 0) activeTab.value = 'pre'
  },
  { immediate: true },
)

const paneGroups = computed<Array<{ tab: 'pre' | 'post'; list: ApiReportStepResult[] }>>(() => {
  const groups: Array<{ tab: 'pre' | 'post'; list: ApiReportStepResult[] }> = []
  if (props.pre.length) groups.push({ tab: 'pre', list: props.pre })
  if (props.post.length) groups.push({ tab: 'post', list: props.post })
  return groups
})

// ==================== 折叠状态 ====================
const collapsedKeys = ref<Set<string>>(new Set())

function procKey(step: ApiReportStepResult, tab: 'pre' | 'post', index: number): string {
  return step.stepId ?? `${props.level}-${tab}-${index}`
}

function allProcKeys(): Set<string> {
  const keys = new Set<string>()
  paneGroups.value.forEach((group) => {
    group.list.forEach((step, index) => keys.add(procKey(step, group.tab, index)))
  })
  return keys
}

watch(
  () => [props.pre, props.post] as const,
  () => {
    // 处理器小卡默认收起，仅展开核对请求/响应/验证器/提取器（交互设计 2.2 默认折叠）
    collapsedKeys.value = allProcKeys()
  },
  { immediate: true, deep: true },
)

function isCollapsed(key: string): boolean {
  return collapsedKeys.value.has(key)
}

function toggleCollapse(key: string) {
  const set = new Set(collapsedKeys.value)
  if (set.has(key)) set.delete(key)
  else set.add(key)
  collapsedKeys.value = set
}

// ==================== 执行顺序提示链 ====================
const chain = computed(() => {
  if (props.level === 'task') {
    return [
      { label: '任务前置', key: 'task-pre' },
      { label: '场景前置', key: 'scene-pre' },
      { label: '步骤', key: 'steps' },
      { label: '场景后置', key: 'scene-post' },
      { label: '任务后置', key: 'task-post' },
    ]
  }
  return [
    { label: '场景前置', key: 'scene-pre' },
    { label: '步骤', key: 'steps' },
    { label: '场景后置', key: 'scene-post' },
  ]
})

const chainHighlightKey = computed(() => {
  if (props.level === 'task') return activeTab.value === 'pre' ? 'task-pre' : 'task-post'
  return activeTab.value === 'pre' ? 'scene-pre' : 'scene-post'
})

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
  if (status === 'success' || status === 'passed') return 'rr-badge--ok'
  if (status === 'failed') return 'rr-badge--fail'
  return 'rr-badge--skip'
}

function methodChipClass(method: string | null | undefined): string {
  const m = method?.toUpperCase() ?? ''
  if (m === 'GET') return 'rr-chip--get'
  if (m === 'POST') return 'rr-chip--post'
  if (m === 'PUT') return 'rr-chip--put'
  if (m === 'DELETE') return 'rr-chip--delete'
  return ''
}

function paneTone(list: ApiReportStepResult[]): 'ok' | 'fail' | 'skip' | 'empty' {
  if (!list.length) return 'empty'
  if (list.some((s) => s.status === 'failed' || s.status === 'error')) return 'fail'
  if (list.every((s) => s.status === 'success' || s.status === 'passed')) return 'ok'
  return 'skip'
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

function procUrl(step: ApiReportStepResult): string {
  if (!step.request || step.request.url == null) return ''
  return String(step.request.url)
}

function headersEntries(headers: Record<string, unknown> | null | undefined): Array<[string, unknown]> {
  return headers ? Object.entries(headers) : []
}
</script>

<template>
  <div v-if="pre.length || post.length" class="rr-procs">
    <div class="rr-procs__nav">
      <button
        v-for="group in paneGroups"
        :key="group.tab"
        class="rr-tab"
        :class="{ 'rr-tab--active': activeTab === group.tab }"
        @click="activeTab = group.tab"
      >
        {{ group.tab === 'pre' ? '前置' : '后置' }}
        <span class="rr-tab__cnt">{{ group.list.length }}</span>
        <span class="rr-dot" :class="`rr-dot--${paneTone(group.list)}`"></span>
      </button>
    </div>

    <div v-for="group in paneGroups" v-show="activeTab === group.tab" :key="group.tab" class="rr-procs__pane">
      <p class="rr-order-tip">
        <span>执行顺序</span>
        <template v-for="(item, i) in chain" :key="item.key">
          <span class="rr-order-tip__arrow">{{ i === 0 ? '›' : '→' }}</span>
          <b :class="{ 'rr-order-tip__current': item.key === chainHighlightKey }">{{ item.label }}</b>
        </template>
      </p>

      <div v-for="(step, index) in group.list" :key="procKey(step, group.tab, index)" class="rr-proc">
        <div class="rr-proc__head">
          <span class="rr-proc__name">{{ step.name ?? '处理器' }}</span>
          <span class="rr-chip rr-monotag" :class="methodChipClass(step.request?.method ?? null)">
            {{ step.request?.method ?? 'HTTP' }}
          </span>
          <span class="rr-badge" :class="badgeClass(step.status)">{{ statusLabel(step.status) }}</span>
          <button
            class="rr-chev"
            :class="{ 'rr-chev--open': !isCollapsed(procKey(step, group.tab, index)) }"
            type="button"
            :aria-label="isCollapsed(procKey(step, group.tab, index)) ? '展开' : '收起'"
            title="展开/收起"
            @click="toggleCollapse(procKey(step, group.tab, index))"
          >
            <span class="rr-chev__i"></span>
          </button>
        </div>
        <p v-if="procUrl(step)" class="rr-proc__meta">{{ procUrl(step) }}</p>
        <div v-show="!isCollapsed(procKey(step, group.tab, index))" class="rr-proc__body">
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

          <div v-if="step.extractors?.length" class="rr-block">
            <div class="rr-block-title">提取器（{{ step.extractors.length }}）</div>
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
                <tr v-for="(e, i) in step.extractors" :key="`${(e as ApiReportExtractor).refName}-${i}`">
                  <td class="rr-ref">{{ (e as ApiReportExtractor).refName }}</td>
                  <td class="rr-monotag">{{ (e as ApiReportExtractor).field ?? '-' }}</td>
                  <td class="rr-monotag rr-val-ok">{{ displayValue((e as ApiReportExtractor).value) }}</td>
                  <td class="rr-null">{{ (e as ApiReportExtractor).defaultValue ? '是' : '-' }}</td>
                  <td>
                    <span
                      class="rr-badge"
                      :class="badgeClass((e as ApiReportExtractor).message ? 'failed' : 'success')"
                    >
                      {{ (e as ApiReportExtractor).message ? '提取失败' : '成功' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="step.assertions?.length" class="rr-block">
            <div class="rr-block-title">验证器（{{ step.assertions.length }}）</div>
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
                <tr v-for="(a, i) in step.assertions" :key="`${(a as ApiReportAssertion).field}-${i}`">
                  <td class="rr-monotag">{{ (a as ApiReportAssertion).field ?? '-' }}</td>
                  <td>{{ (a as ApiReportAssertion).rule ?? '-' }}</td>
                  <td class="rr-monotag">{{ displayValue((a as ApiReportAssertion).expected) }}</td>
                  <td
                    class="rr-monotag"
                    :class="{
                      'rr-val-ok':
                        (a as ApiReportAssertion).status === 'passed' || (a as ApiReportAssertion).status === 'success',
                    }"
                  >
                    {{ displayValue((a as ApiReportAssertion).actual) }}
                  </td>
                  <td>
                    <span class="rr-badge" :class="badgeClass((a as ApiReportAssertion).status)">
                      {{ assertionLabel((a as ApiReportAssertion).status) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rr-procs__nav {
  display: flex;
  gap: 2px;
  padding: 8px 14px 0;
  border-bottom: 1px solid #eef1f5;
  background: linear-gradient(180deg, #fbfdff, #fff);
  flex-wrap: wrap;
}

.rr-tab {
  appearance: none;
  border: 1px solid transparent;
  border-bottom: none;
  background: transparent;
  color: #64748b;
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: 9px 9px 0 0;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  position: relative;
  top: 1px;
  transition:
    color 0.15s,
    background 0.15s;
  white-space: nowrap;

  &:hover {
    color: var(--color-success);
    background: #f0fdf4;
  }

  &--active {
    background: #fff;
    color: var(--color-success);
    border-color: #eef1f5;
    box-shadow: inset 0 2px 0 var(--color-success);
  }
}

.rr-tab__cnt {
  background: #e5e9f0;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
  padding: 0 6px;
  border-radius: 999px;
  min-width: 18px;
  text-align: center;
  line-height: 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  .rr-tab--active & {
    background: #d1fae5;
    color: #15803d;
  }
}

.rr-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #cbd5e1;
  flex: 0 0 auto;

  &--ok {
    background: #22c55e;
    box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
  }

  &--fail {
    background: #ef4444;
    box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
  }

  &--skip {
    background: #f59e0b;
  }

  &--empty {
    background: #e2e8f0;
  }
}

.rr-procs__pane {
  padding: 14px 14px 16px;
}

.rr-order-tip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: #94a3b8;
  background: #f8fafc;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  padding: 6px 11px;
  margin: 0 0 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;

  b {
    font-weight: 600;
    color: #64748b;
  }

  .rr-order-tip__current {
    color: var(--color-success);
    font-weight: 700;
  }
}

.rr-order-tip__arrow {
  color: #cbd5e1;
}

.rr-proc {
  border: 1px solid #eef1f5;
  border-radius: 11px;
  background: #fcfdff;
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
  background: linear-gradient(180deg, #fbfdff, #fff);
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
  color: #94a3b8;
  border-radius: 6px;
  flex: 0 0 auto;
  font-family: inherit;
  transition:
    background 0.15s,
    color 0.15s;

  &:hover {
    background: #eef2f7;
    color: #475569;
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
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
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
  color: #64748b;
  margin: 0 0 6px;
}

.rr-kv {
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

.rr-code {
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

.rr-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rr-monotag {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.rr-ref {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--color-success);
  font-weight: 600;
}

.rr-val-ok {
  color: #15803d;
  font-weight: 600;
}

.rr-null {
  color: #9aa4b2;
  font-style: italic;
}

.rr-chip {
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
}

.rr-badge {
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